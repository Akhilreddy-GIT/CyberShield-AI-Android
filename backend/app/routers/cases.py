from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.models.db import get_db, Case, TimelineEvent, Evidence
from app.models.schemas import RiskFactorsIn, TimelineEventIn, CaseOut
from app.services.risk_engine import assess_risk, RiskFactors
from app.routers.ws_case import broadcast_case_update

router = APIRouter(prefix="/api/cases", tags=["cases"])


@router.get("/{case_id}", response_model=CaseOut)
def get_case(case_id: str, db: Session = Depends(get_db)):
    case = db.query(Case).filter(Case.id == case_id).first()
    if not case:
        raise HTTPException(404, "Case not found")
    return case


@router.get("/by-user/{anon_user_id}")
def list_user_cases(anon_user_id: str, db: Session = Depends(get_db)):
    cases = db.query(Case).filter(Case.anon_user_id == anon_user_id).order_by(Case.created_at.desc()).all()
    return [
        {"id": c.id, "category": c.category, "risk_level": c.risk_level, "status": c.status, "created_at": c.created_at}
        for c in cases
    ]


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

    case.risk_level = assessment.level.value
    case.risk_score = assessment.score
    if assessment.level.value == "Critical Emergency":
        case.status = "escalated"
    db.commit()

    await broadcast_case_update(case.id, {
        "case_id": case.id,
        "status": case.status,
        "risk_level": case.risk_level,
        "risk_score": case.risk_score,
    })

    return {
        "level": assessment.level.value,
        "score": assessment.score,
        "triggered_factors": assessment.triggered_factors,
        "explanation": assessment.explanation,
    }


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
