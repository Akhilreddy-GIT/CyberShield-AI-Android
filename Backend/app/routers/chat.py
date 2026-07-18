import logging

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.models.db import get_db, Case, Message, TimelineEvent
from app.models.schemas import ChatMessageIn, ChatMessageOut
from app.services.intent_classifier import classify_intent, REFUSAL_MESSAGE, NO_CONTENT_MESSAGE, CyberCategory
from app.services.agent_service import generate_response, get_helplines_for_critical
from app.services.risk_engine import auto_assess_risk_from_text, RiskLevel, risk_rank
from app.services.case_memory_service import update_case_facts
from app.services.timeline_service import extract_timeline_events, dedupe_against_existing
from app.services.recovery_lifecycle_service import compute_recovery_state_for_case
from app.routers.ws_case import broadcast_case_update

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/chat", tags=["chat"])


@router.post("", response_model=ChatMessageOut)
async def send_message(payload: ChatMessageIn, db: Session = Depends(get_db)):
    intent = classify_intent(payload.message)

    # Domain guardrail — checked BEFORE any case is created. Small talk and
    # off-topic messages must never produce a Case row, a risk score, or
    # any other database write tied to a case. If the caller passed an
    # existing case_id we still attach the refusal reply to it (so history
    # reads naturally in an ongoing conversation), but we never create a
    # brand-new case just to hold a "hi" / "thanks" / off-topic exchange.
    if not intent.is_cyber_related:
        case = None
        if payload.case_id:
            case = db.query(Case).filter(Case.id == payload.case_id).first()
        reply_text = NO_CONTENT_MESSAGE if not payload.message.strip() else REFUSAL_MESSAGE
        if case:
            db.add(Message(case_id=case.id, role="user", content=payload.message))
            db.add(Message(case_id=case.id, role="assistant", content=reply_text))
            db.commit()
        return ChatMessageOut(
            case_id=case.id if case else None,
            reply=reply_text,
            category=None,
            is_critical=False,
            cited_sources=[],
            used_llm=False,
        )

    # Cyber education / informational questions (Goal #4) — e.g. "How do
    # phishing scams work?", "What is ransomware?". These carry a real
    # cyber keyword (so is_cyber_related is True and would otherwise create
    # a case) but describe no incident. Must get a real, substantive answer
    # — just with no Case, no risk score, no category, no recovery plan,
    # and no timeline entry attached. If the caller passed an existing
    # case_id we still log the Q&A to that case's history for continuity,
    # but we never touch its category/risk/status and never create a new
    # case just to hold a standalone educational question.
    if intent.is_educational:
        case = None
        if payload.case_id:
            case = db.query(Case).filter(Case.id == payload.case_id).first()
        history = []
        if case:
            history = [
                {"role": m.role, "content": m.content}
                for m in db.query(Message).filter(Message.case_id == case.id).order_by(Message.created_at).all()
            ]
        result = generate_response(
            payload.message, history, intent,
            facts_json=case.facts_json if case else None,
            risk_assessment=None,
        )
        if case:
            db.add(Message(case_id=case.id, role="user", content=payload.message))
            db.add(Message(
                case_id=case.id, role="assistant", content=result["reply"],
                cited_sources=",".join(result["cited_sources"]),
            ))
            db.commit()
        return ChatMessageOut(
            case_id=case.id if case else None,
            reply=result["reply"],
            category=case.category if case else None,
            is_critical=False,
            cited_sources=result["cited_sources"],
            used_llm=result["used_llm"],
            helplines=None,
        )

    # Get or create case
    if payload.case_id:
        case = db.query(Case).filter(Case.id == payload.case_id).first()
    else:
        case = None

    if not case:
        case = Case(anon_user_id=payload.anon_user_id, category=intent.category.value if intent.category else None)
        db.add(case)
        db.commit()
        db.refresh(case)

    # Build conversation history for this case only (Phase 10: case-only memory)
    history = [
        {"role": m.role, "content": m.content}
        for m in db.query(Message).filter(Message.case_id == case.id).order_by(Message.created_at).all()
    ]

    # Case memory: extract structured facts (amounts, banks, phone numbers,
    # UPI IDs, URLs, timeline, etc.) from this message and merge into the
    # persisted per-case fact set, so the agent never re-asks for something
    # already on record. Runs before generate_response so the freshly
    # updated facts are available to this very turn's prompt.
    case.facts_json = update_case_facts(case.facts_json, payload.message)
    db.commit()

    # Incident Timeline Engine: detect incident-lifecycle actions mentioned
    # in this message (clicked link, OTP shared, money transferred, bank
    # contacted, complaint filed, etc.) and log them as TimelineEvent rows,
    # skipping stages already recorded for this case. Runs before the reply
    # is generated but doesn't block it — a timeline logging issue should
    # never prevent the user from getting a response.
    new_timeline_events = []
    try:
        candidates = extract_timeline_events(payload.message)
        if candidates:
            existing_descriptions = [
                e.description for e in
                db.query(TimelineEvent).filter(TimelineEvent.case_id == case.id).all()
            ]
            for c in dedupe_against_existing(candidates, existing_descriptions):
                event = TimelineEvent(case_id=case.id, description=c.description, event_time=c.event_time)
                db.add(event)
                new_timeline_events.append(event)
            if new_timeline_events:
                db.commit()
                for e in new_timeline_events:
                    db.refresh(e)
                await broadcast_case_update(case.id, {
                    "case_id": case.id,
                    "timeline_events_added": [
                        {"id": e.id, "description": e.description, "event_time": e.event_time, "created_at": e.created_at.isoformat()}
                        for e in new_timeline_events
                    ],
                })
    except Exception:
        logger.exception("Timeline auto-extraction failed for case %s; continuing without it.", case.id)

    # ---------------------------------------------------------------------
    # SINGLE SOURCE OF TRUTH: compute the case's risk level/score BEFORE
    # generating the reply, and persist it, so the value the LLM is told to
    # report and the value every other API (Cases, Case Details, Timeline,
    # WebSocket) reads back are the exact same number written to the same
    # row. Previously this ran AFTER generate_response, which meant the
    # chat reply's "Risk Level" section was whatever the LLM guessed on its
    # own — that's the root cause of chat/case risk mismatches, not a race
    # condition or a stale cache.
    #
    # This runs on every turn, using the full case history so far, and only
    # ever escalates the stored risk level — it never overwrites a higher
    # level a human previously set via the explicit /api/cases/assess-risk
    # form (case risk is escalate-only by design).
    # ---------------------------------------------------------------------
    auto_assessment = auto_assess_risk_from_text(
        latest_message=payload.message,
        conversation_history=history + [{"role": "user", "content": payload.message}],
        is_critical_intent=intent.is_critical,
    )
    risk_changed = False
    if risk_rank(auto_assessment.level.value) > risk_rank(case.risk_level):
        # Tier escalation (e.g. LOW -> MEDIUM): update both level and score.
        case.risk_level = auto_assessment.level.value
        case.risk_score = auto_assessment.score
        risk_changed = True
        logger.info(
            "case %s risk escalated to %s (score=%s) via auto assessment: %s",
            case.id, case.risk_level, case.risk_score, auto_assessment.triggered_factors,
        )
    elif risk_rank(auto_assessment.level.value) == risk_rank(case.risk_level) and auto_assessment.score > (case.risk_score or 0):
        # Same tier, but new signals raised the underlying score (e.g. a
        # second cyberbullying message adds detail) — keep the score
        # accurate without touching the level, which stays escalate-only.
        case.risk_score = auto_assessment.score
        risk_changed = True

    if intent.is_critical and risk_rank(case.risk_level) < risk_rank(RiskLevel.CRITICAL.value):
        case.risk_level = RiskLevel.CRITICAL.value
        case.risk_score = max(case.risk_score or 0, auto_assessment.score, 10)
        risk_changed = True

    if intent.is_critical:
        case.status = "escalated"
        risk_changed = True

    # The scored auto-assessment can independently reach CRITICAL (e.g. a
    # combination of large financial loss + OTP shared) even when the
    # narrower intent-classifier emergency markers above didn't fire. Case
    # status must reflect that too — a case shouldn't stay "open" while its
    # own risk_level says Critical Emergency.
    if case.risk_level == RiskLevel.CRITICAL.value and case.status not in ("escalated", "closed"):
        case.status = "escalated"
        risk_changed = True

    # This is now the ONE risk assessment for this turn — passed both into
    # the reply generator (so the reply can only ever state this value) and
    # persisted to the case row that every other endpoint reads from.
    canonical_risk = {
        "level": case.risk_level,
        "score": case.risk_score,
        "triggered_factors": auto_assessment.triggered_factors,
    }

    result = generate_response(
        payload.message, history, intent, facts_json=case.facts_json, risk_assessment=canonical_risk,
    )

    # Persist
    db.add(Message(case_id=case.id, role="user", content=payload.message))
    db.add(Message(
        case_id=case.id,
        role="assistant",
        content=result["reply"],
        cited_sources=",".join(result["cited_sources"]),
    ))
    if intent.category and intent.category != CyberCategory.OTHER_CYBER:
        case.category = intent.category.value
    db.commit()

    helplines = (
        get_helplines_for_critical(
            category=intent.category.value if intent.category else None,
            emergency_type=intent.emergency_type,
        )
        if intent.is_critical
        else None
    )
    if case.risk_level == RiskLevel.CRITICAL.value and helplines is None:
        helplines = get_helplines_for_critical(
            category=case.category,
            emergency_type=intent.emergency_type,
        )

    if risk_changed:
        recovery = compute_recovery_state_for_case(case, db)
        await broadcast_case_update(case.id, {
            "case_id": case.id,
            "status": case.status,
            "risk_level": case.risk_level,
            "risk_score": case.risk_score,
            "recovery_stage": recovery.stage,
            "recovery_progress_percent": recovery.progress_percent,
        })

    recovery = compute_recovery_state_for_case(case, db)

    return ChatMessageOut(
        case_id=case.id,
        reply=result["reply"],
        category=case.category,
        is_critical=intent.is_critical,
        cited_sources=result["cited_sources"],
        used_llm=result["used_llm"],
        helplines=helplines,
        recovery_stage=recovery.stage,
        recovery_progress_percent=recovery.progress_percent,
    )


@router.get("/{case_id}/history")
def get_history(case_id: str, db: Session = Depends(get_db)):
    messages = db.query(Message).filter(Message.case_id == case_id).order_by(Message.created_at).all()
    return [
        {"role": m.role, "content": m.content, "cited_sources": (m.cited_sources or "").split(",") if m.cited_sources else [], "created_at": m.created_at}
        for m in messages
    ]
