"""
Crisis Assistance Engine + Institution Routing + Anonymous Crisis Mode.

New router, additive only — does not modify chat.py, cases.py, or any
existing endpoint. Reuses existing models (Case, Message, GuardianEvent)
and services (crisis_service, institution_routing_service,
anonymous_case_service, case_memory_service, guardian_event_service) so
crisis cases are ordinary Cases that show up correctly in every existing
view (timeline, evidence, guardian dashboard) — nothing needs a special
code path elsewhere in the app.
"""

import logging

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.models.db import get_db, Case, Message
from app.models.schemas import (
    CrisisAssessIn, CrisisAssessOut,
    AnonymousCaseStartIn, AnonymousCaseStartOut,
    AnonymousCaseLinkIn,
    InstitutionProfileOut,
)
from app.services.crisis_service import assess_crisis
from app.services.risk_engine import risk_rank
from app.services.case_memory_service import update_case_facts
from app.services.anonymous_case_service import (
    start_anonymous_case, link_anonymous_case_to_account, generate_case_code,
)
from app.services.guardian_event_service import record_event, EventType
from app.knowledge_base.institution_profiles import list_institution_profiles
from app.routers.ws_case import broadcast_case_update

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/crisis", tags=["crisis"])


@router.post("/assess", response_model=CrisisAssessOut)
async def crisis_assess(payload: CrisisAssessIn, db: Session = Depends(get_db)):
    """
    Feature 1 + Feature 2 combined entry point: runs the Crisis Assistance
    Engine on a message and, if an institution_id is supplied, applies
    Institution Routing on top. Optionally persists to an existing or new
    Case exactly like /api/chat does, so crisis conversations show up in
    the normal case/timeline/guardian views.
    """
    case = None
    case_code = None

    if payload.case_id:
        case = db.query(Case).filter(Case.id == payload.case_id).first()
        if not case:
            raise HTTPException(404, "Case not found")
    elif payload.anon_user_id:
        # Persist under the caller's existing anon_user_id, same pattern as
        # /api/chat's get-or-create.
        case = Case(anon_user_id=payload.anon_user_id)
        db.add(case)
        db.commit()
        db.refresh(case)

    history = []
    if case:
        history = [
            {"role": m.role, "content": m.content}
            for m in db.query(Message).filter(Message.case_id == case.id).order_by(Message.created_at).all()
        ]
        case.facts_json = update_case_facts(case.facts_json, payload.message)
        db.commit()
        case_code = generate_case_code()  # display-only convenience code for this response

    assessment = assess_crisis(
        message=payload.message,
        conversation_history=history,
        institution_id=payload.institution_id,
        facts_json=case.facts_json if case else None,
    )

    if case:
        db.add(Message(case_id=case.id, role="user", content=payload.message))
        db.add(Message(case_id=case.id, role="assistant", content=assessment.guidance_text))
        if assessment.category:
            case.category = assessment.category

        # assessment.risk_level is the canonical risk_engine.RiskLevel value
        # (e.g. "High Risk") computed once in crisis_service — no separate
        # mapping needed here. Escalate-only, same rule as chat.py: a case
        # already at a higher tier (e.g. set via the manual assess-risk
        # form) is never silently downgraded by an automatic assessment.
        if risk_rank(assessment.risk_level) >= risk_rank(case.risk_level):
            case.risk_level = assessment.risk_level
            case.risk_score = max(case.risk_score or 0, assessment.risk_score)
        if assessment.human_assistance_recommended:
            case.status = "escalated"

        db.commit()

        try:
            record_event(
                db=db,
                anon_user_id=case.anon_user_id,
                event_type=EventType.RISK_INCREASED if assessment.human_assistance_recommended else EventType.NOTIFICATION_SCANNED,
                severity=assessment.emergency_level.lower(),
                risk_score=assessment.risk_score,
                summary=f"Crisis Assistance Engine assessment: {assessment.emergency_level}"
                        + (f" ({assessment.category})" if assessment.category else ""),
                explanation=assessment.current_risk,
                case_id=case.id,
                source="crisis_engine",
                extra={"emergency_type": assessment.emergency_type, "institution_recommended": bool(assessment.institution_support)},
            )
        except Exception:
            logger.exception("Failed to record Guardian event for crisis assessment on case %s", case.id)

        await broadcast_case_update(case.id, {
            "case_id": case.id,
            "status": case.status,
            "risk_level": case.risk_level,
            "risk_score": case.risk_score,
            "emergency_level": assessment.emergency_level,
        })

    return CrisisAssessOut(
        case_id=case.id if case else None,
        case_code=case_code,
        emergency_level=assessment.emergency_level,
        human_assistance_recommended=assessment.human_assistance_recommended,
        category=assessment.category,
        emergency_type=assessment.emergency_type,
        situation_summary=assessment.situation_summary,
        current_risk=assessment.current_risk,
        immediate_safety_advice=assessment.immediate_safety_advice,
        evidence_to_preserve=assessment.evidence_to_preserve,
        recommended_human_assistance=assessment.recommended_human_assistance,
        institution_recommended=assessment.institution_recommended,
        institution_name=assessment.institution_name,
        institution_routing_reason=assessment.institution_routing_reason,
        institution_support=assessment.institution_support,
        government_support=assessment.government_support,
        recovery_steps=assessment.recovery_steps,
        next_question=assessment.next_question,
        guidance_text=assessment.guidance_text,
        cited_sources=assessment.cited_sources,
        used_llm=assessment.used_llm,
        risk_score=assessment.risk_score,
        triggered_factors=assessment.triggered_factors,
    )


@router.get("/institutions", response_model=list[InstitutionProfileOut])
def list_institutions():
    """Feature 2: lists available institution profiles for a picker UI."""
    return list_institution_profiles()


@router.post("/anonymous/start", response_model=AnonymousCaseStartOut)
def anonymous_start(payload: AnonymousCaseStartIn, db: Session = Depends(get_db)):
    """
    Feature 3: starts a brand-new anonymous case with zero identity
    collected. Client stores the returned anon_user_id + case_id locally
    to continue the conversation (e.g. via /api/crisis/assess or
    /api/chat, both of which accept case_id/anon_user_id already).
    """
    handle = start_anonymous_case(db, category=payload.category)
    return AnonymousCaseStartOut(
        case_id=handle.case_id,
        anon_user_id=handle.anon_user_id,
        case_code=handle.case_code,
    )


@router.post("/anonymous/link")
def anonymous_link(payload: AnonymousCaseLinkIn, db: Session = Depends(get_db)):
    """
    Feature 3: explicitly links an anonymous case to a registered
    account's anon_user_id (obtained from /api/auth/register or
    /api/auth/login). Opt-in only — nothing links automatically.
    """
    ok = link_anonymous_case_to_account(db, payload.case_id, payload.account_anon_user_id)
    if not ok:
        raise HTTPException(404, "Case not found")
    return {"status": "linked", "case_id": payload.case_id}
