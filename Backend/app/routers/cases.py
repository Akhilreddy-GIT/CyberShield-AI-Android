from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.models.db import get_db, Case, TimelineEvent, Evidence
from app.models.schemas import RiskFactorsIn, TimelineEventIn, CaseOut
from app.services.risk_engine import assess_risk, RiskFactors, risk_rank
from app.services.recovery_lifecycle_service import compute_recovery_state_for_case
from app.routers.ws_case import broadcast_case_update

router = APIRouter(prefix="/api/cases", tags=["cases"])


def _case_out(case: Case, db: Session) -> CaseOut:
    recovery = compute_recovery_state_for_case(case, db)
    return CaseOut(
        id=case.id,
        category=case.category,
        risk_level=case.risk_level,
        risk_score=case.risk_score,
        status=case.status,
        created_at=case.created_at,
        recovery_stage=recovery.stage,
        recovery_stage_label=recovery.label,
        recovery_progress_percent=recovery.progress_percent,
    )


@router.get("/{case_id}", response_model=CaseOut)
def get_case(case_id: str, db: Session = Depends(get_db)):
    case = db.query(Case).filter(Case.id == case_id).first()
    if not case:
        raise HTTPException(404, "Case not found")
    return _case_out(case, db)


@router.get("/by-user/{anon_user_id}")
def list_user_cases(anon_user_id: str, db: Session = Depends(get_db)):
    cases = db.query(Case).filter(Case.anon_user_id == anon_user_id).order_by(Case.created_at.desc()).all()
    result = []
    for c in cases:
        r = compute_recovery_state_for_case(c, db)
        result.append({
            "id": c.id, "category": c.category, "risk_level": c.risk_level, "status": c.status,
            "created_at": c.created_at,
            # Additive — same computed, backend-driven values as GET /{case_id}.
            "recovery_stage": r.stage,
            "recovery_stage_label": r.label,
            "recovery_progress_percent": r.progress_percent,
        })
    return result


@router.post("/assess-risk")
async def assess_case_risk(payload: RiskFactorsIn, db: Session = Depends(get_db)):
    case = db.query(Case).filter(Case.id == payload.case_id).first()
    if not case:
        raise HTTPException(404, "Case not found")

    has_evidence = db.query(Evidence).filter(Evidence.case_id == case.id).count() > 0

    factors = RiskFactors(
        is_repeated_incident=payload.is_repeated_incident,
        involves_threat_of_harm=payload.involves_threat_of_harm,
        involves_minor=payload.involves_minor,
        involves_financial_loss=payload.involves_financial_loss,
        involves_explicit_content=payload.involves_explicit_content,
        accused_knows_victim_location=payload.accused_knows_victim_location,
        ongoing_blackmail=payload.ongoing_blackmail,
        victim_reports_feeling_unsafe=payload.victim_reports_feeling_unsafe,
        has_evidence=has_evidence,
    )
    assessment = assess_risk(factors)

    # ROOT CAUSE BUG FIXED: this endpoint used to unconditionally overwrite
    # case.risk_level/risk_score with the manual form's result — including
    # DOWNGRADING a case that chat's auto-assessment had already escalated
    # higher (e.g. a case at "Critical Emergency" from OTP-shared + money-
    # lost language could be silently dropped to "Medium Risk" just because
    # the reviewer filling in this form didn't tick every box that chat's
    # text analysis had already found). Verified live: reproduced exactly
    # this scenario before the fix. risk_engine.py's own module docstring
    # states risk is escalate-only by design — de-escalation must be a
    # deliberate action, never an automatic side effect of a different
    # signal source. Same escalate-only comparison chat.py already uses.
    if risk_rank(assessment.level.value) > risk_rank(case.risk_level):
        case.risk_level = assessment.level.value
        case.risk_score = assessment.score
    elif risk_rank(assessment.level.value) == risk_rank(case.risk_level):
        # Same tier — still let the score reflect the fuller picture if the
        # form's factor-based score is higher (mirrors chat.py's same-tier
        # score-refresh behavior), never lower.
        case.risk_score = max(case.risk_score or 0, assessment.score)
    if assessment.level.value == "Critical Emergency":
        case.status = "escalated"
    db.commit()

    recovery = compute_recovery_state_for_case(case, db)
    await broadcast_case_update(case.id, {
        "case_id": case.id,
        "status": case.status,
        "risk_level": case.risk_level,
        "risk_score": case.risk_score,
        "recovery_stage": recovery.stage,
        "recovery_progress_percent": recovery.progress_percent,
    })

    return {
        "level": assessment.level.value,
        "score": assessment.score,
        "triggered_factors": assessment.triggered_factors,
        "explanation": assessment.explanation,
    }


@router.post("/{case_id}/resolve", response_model=CaseOut)
async def resolve_case(case_id: str, db: Session = Depends(get_db)):
    """Explicitly marks a case as Resolved. This is a deliberate action by
    the case owner (or a reviewer) — never inferred from message/evidence
    content, since "this is actually resolved" is a real-world judgment
    call, not something text patterns can safely establish on their own."""
    case = db.query(Case).filter(Case.id == case_id).first()
    if not case:
        raise HTTPException(404, "Case not found")
    case.recovery_stage_override = "resolved"
    db.commit()
    recovery = compute_recovery_state_for_case(case, db)
    await broadcast_case_update(case.id, {
        "case_id": case.id,
        "status": case.status,
        "recovery_stage": recovery.stage,
        "recovery_progress_percent": recovery.progress_percent,
    })
    return _case_out(case, db)


@router.post("/{case_id}/close", response_model=CaseOut)
async def close_case(case_id: str, db: Session = Depends(get_db)):
    """Explicitly marks a case as Closed (terminal). Same reasoning as
    resolve_case above — a deliberate action, never auto-inferred."""
    case = db.query(Case).filter(Case.id == case_id).first()
    if not case:
        raise HTTPException(404, "Case not found")
    case.recovery_stage_override = "closed"
    case.status = "closed"
    db.commit()
    recovery = compute_recovery_state_for_case(case, db)
    await broadcast_case_update(case.id, {
        "case_id": case.id,
        "status": case.status,
        "recovery_stage": recovery.stage,
        "recovery_progress_percent": recovery.progress_percent,
    })
    return _case_out(case, db)


@router.post("/timeline")
def add_timeline_event(payload: TimelineEventIn, db: Session = Depends(get_db)):
    case = db.query(Case).filter(Case.id == payload.case_id).first()
    if not case:
        raise HTTPException(404, "Case not found")
    event = TimelineEvent(case_id=case.id, description=payload.description, event_time=payload.event_time)
    db.add(event)
    db.commit()
    return {"status": "added", "event_id": event.id}


@router.get("/{case_id}/timeline")
def get_timeline(case_id: str, db: Session = Depends(get_db)):
    events = db.query(TimelineEvent).filter(TimelineEvent.case_id == case_id).order_by(TimelineEvent.created_at).all()
    return [{"id": e.id, "description": e.description, "event_time": e.event_time, "created_at": e.created_at} for e in events]
