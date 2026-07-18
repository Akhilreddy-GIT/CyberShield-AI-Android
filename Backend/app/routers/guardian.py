"""
Guardian API — Feature 1 (Smart Notification Guardian), Feature 2 (Call
Guardian Intelligence Engine), and Feature 3/4 (Guardian Event Engine /
Timeline / Correlation).

Backend-only, per spec: these endpoints are designed for a future Android
client to call (NotificationListenerService payloads, call-report
payloads), but nothing here assumes Android — any client can POST the same
JSON. No existing router, endpoint, or contract is modified; this is a
purely additive router mounted alongside the existing ones in main.py.

Every scan/analysis is logged as a GuardianEvent (Feature 3) and, when a
case_id is supplied, correlated against recent Guardian activity for that
user (Feature 4) — if the accumulated pattern crosses the escalation
threshold, the case's risk_level/risk_score is updated and pushed over the
existing case WebSocket, reusing broadcast_case_update exactly as
cases.py's assess-risk endpoint already does.
"""

import logging
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.models.db import get_db, Case, NotificationScan, CallAnalysis, gen_id
from app.models.schemas import (
    NotificationScanIn, NotificationScanOut,
    CallAnalysisIn, CallAnalysisOut,
    GuardianEventOutSchema,
)
from app.services.notification_guardian_service import analyze_notification
from app.services.call_guardian_service import analyze_call
from app.services import guardian_llm_service
from app.services.guardian_event_service import (
    record_event, get_guardian_timeline, correlate_and_escalate, EventType,
)
from app.services.risk_engine import risk_rank
from app.routers.ws_case import broadcast_case_update

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/guardian", tags=["guardian"])


def _notification_event_type(category: str) -> str:
    mapping = {
        "Suspicious WhatsApp Message": EventType.SUSPICIOUS_WHATSAPP,
        "OTP Scam": EventType.OTP_REQUEST_DETECTED,
        "Remote Access Scam": EventType.REMOTE_ACCESS_ATTEMPT,
        "Investment Scam": EventType.INVESTMENT_SCAM_DETECTED,
        "Credential Harvesting": EventType.HIGH_RISK_PHISHING,
    }
    return mapping.get(category, EventType.NOTIFICATION_SCANNED)


async def _apply_escalation_if_any(db: Session, case_id: str, correlation: dict):
    if not correlation.get("escalated") or not case_id:
        return
    case = db.query(Case).filter(Case.id == case_id).first()
    if not case:
        return
    recommended = correlation["recommended_level"]
    # Only move risk upward from correlation — never downgrade automatically;
    # de-escalation should be a deliberate action (e.g. recovery_completed),
    # not an artifact of the correlation window sliding.
    if risk_rank(recommended) > risk_rank(case.risk_level):
        case.risk_level = recommended
        case.risk_score = max(case.risk_score, correlation["combined_score"])
        if recommended == "Critical Emergency":
            case.status = "escalated"
        db.commit()
        await broadcast_case_update(case.id, {
            "case_id": case.id,
            "status": case.status,
            "risk_level": case.risk_level,
            "risk_score": case.risk_score,
            "source": "guardian_correlation",
        })


@router.post("/notifications/scan", response_model=NotificationScanOut)
async def scan_notification(payload: NotificationScanIn, db: Session = Depends(get_db)):
    """Feature 1. Analyzes a single Android notification and returns a
    structured threat assessment. Persists the scan and logs a Guardian
    event regardless of outcome (Feature 3), so benign notifications still
    contribute signal history for later correlation (Feature 4)."""
    if payload.case_id:
        case = db.query(Case).filter(Case.id == payload.case_id).first()
        if not case:
            raise HTTPException(404, "Case not found")

    assessment = analyze_notification(
        app_name=payload.app_name,
        notification_title=payload.notification_title,
        notification_text=payload.notification_text,
    )

    explanation, recommendation = guardian_llm_service.explain_notification(
        category=assessment.notification_category if assessment.risk_score > 0 else None,
        risk_score=assessment.risk_score,
        severity=assessment.severity,
        signals=assessment.triggered_signals,
        dangerous=assessment.is_interaction_dangerous,
    )
    proactive_warning = guardian_llm_service.generate_proactive_warning(
        category=assessment.notification_category if assessment.risk_score > 0 else None,
        severity=assessment.severity,
        signals=assessment.triggered_signals,
    )

    import json as _json
    scan = NotificationScan(
        id=gen_id("NSCAN"),
        anon_user_id=payload.anon_user_id,
        case_id=payload.case_id,
        app_name=payload.app_name,
        notification_title=payload.notification_title,
        notification_text=payload.notification_text,
        notification_category=assessment.notification_category,
        threat_type=assessment.threat_type,
        risk_score=assessment.risk_score,
        confidence_score=assessment.confidence_score,
        severity=assessment.severity,
        is_interaction_dangerous=assessment.is_interaction_dangerous,
        explanation=explanation,
        recommendation=recommendation,
        triggered_signals_json=_json.dumps(assessment.triggered_signals),
        reported_at=payload.timestamp,
    )
    db.add(scan)
    db.commit()
    db.refresh(scan)

    record_event(
        db=db,
        anon_user_id=payload.anon_user_id,
        event_type=_notification_event_type(assessment.notification_category),
        severity=assessment.severity,
        risk_score=assessment.risk_score,
        summary=f"Notification scanned: {assessment.notification_category} ({payload.app_name or 'unknown app'})",
        explanation=explanation,
        case_id=payload.case_id,
        source="notification",
        extra={"scan_id": scan.id, "confidence_score": assessment.confidence_score},
    )

    if payload.case_id:
        correlation = correlate_and_escalate(db, payload.anon_user_id, payload.case_id)
        await _apply_escalation_if_any(db, payload.case_id, correlation)

    next_action = (
        "Do not tap any link or share any information from this notification. Verify independently."
        if assessment.is_interaction_dangerous
        else "No immediate action required; continue monitoring."
    )

    return NotificationScanOut(
        scan_id=scan.id,
        notification_category=assessment.notification_category,
        threat_type=assessment.threat_type,
        risk_score=assessment.risk_score,
        confidence_score=assessment.confidence_score,
        severity=assessment.severity,
        explanation=explanation,
        immediate_recommendation=recommendation,
        is_interaction_dangerous=assessment.is_interaction_dangerous,
        recommended_next_action=next_action,
        triggered_signals=assessment.triggered_signals,
        proactive_warning=proactive_warning,
    )


@router.post("/calls/analyze", response_model=CallAnalysisOut)
async def analyze_call_report(payload: CallAnalysisIn, db: Session = Depends(get_db)):
    """Feature 2. Analyzes a reported call. Unknown numbers are NEVER
    auto-classified as scams — scoring depends only on reported
    conversation content (see call_guardian_service.analyze_call)."""
    if payload.case_id:
        case = db.query(Case).filter(Case.id == payload.case_id).first()
        if not case:
            raise HTTPException(404, "Case not found")

    assessment = analyze_call(
        caller_number=payload.caller_number,
        contact_name=payload.contact_name,
        conversation_summary=payload.conversation_summary,
        user_notes=payload.user_notes,
        follow_up_answers=payload.follow_up_answers,
    )

    explanation, recommendation = guardian_llm_service.explain_call(
        category=assessment.scam_category,
        risk_score=assessment.risk_score,
        urgency_level=assessment.urgency_level,
        signals=assessment.triggered_signals,
    )
    severity_for_warning = {"high": "high", "medium": "medium", "low": "low"}.get(assessment.urgency_level, "low") if assessment.scam_category else "info"
    proactive_warning = guardian_llm_service.generate_proactive_warning(
        category=assessment.scam_category,
        severity=severity_for_warning,
        signals=assessment.triggered_signals,
    )

    evidence_recommendations = [
        "Save the call log (number, date, time, duration).",
        "Write down everything said as soon as possible, while memory is fresh.",
    ]
    if assessment.scam_category:
        evidence_recommendations.append("If any payment or OTP was shared, screenshot the transaction/message immediately.")

    import json as _json
    call_row = CallAnalysis(
        id=gen_id("CALL"),
        anon_user_id=payload.anon_user_id,
        case_id=payload.case_id,
        caller_number=payload.caller_number,
        contact_name=payload.contact_name,
        call_duration_seconds=payload.call_duration_seconds,
        conversation_summary=payload.conversation_summary,
        user_notes=payload.user_notes,
        scam_category=assessment.scam_category,
        is_unknown_number=assessment.is_unknown_number,
        risk_score=assessment.risk_score,
        confidence_score=assessment.confidence_score,
        urgency_level=assessment.urgency_level,
        manipulation_tactics_json=_json.dumps(assessment.manipulation_tactics),
        possible_impersonation=assessment.possible_impersonation,
        explanation=explanation,
        recovery_recommendations_json=_json.dumps(assessment.recovery_recommendations),
        authorities_to_contact_json=_json.dumps(assessment.authorities_to_contact),
        triggered_signals_json=_json.dumps(assessment.triggered_signals),
        reported_at=payload.timestamp,
    )
    db.add(call_row)
    db.commit()
    db.refresh(call_row)

    severity_for_event = severity_for_warning
    record_event(
        db=db,
        anon_user_id=payload.anon_user_id,
        event_type=EventType.CALL_ANALYZED,
        severity=severity_for_event,
        risk_score=assessment.risk_score,
        summary=f"Call analyzed: {assessment.scam_category or 'no category matched'} ({'unknown' if assessment.is_unknown_number else 'known'} number)",
        explanation=explanation,
        case_id=payload.case_id,
        source="call",
        extra={"call_id": call_row.id, "confidence_score": assessment.confidence_score},
    )

    if payload.case_id:
        correlation = correlate_and_escalate(db, payload.anon_user_id, payload.case_id)
        await _apply_escalation_if_any(db, payload.case_id, correlation)

    return CallAnalysisOut(
        call_id=call_row.id,
        scam_category=assessment.scam_category,
        threat_type=assessment.threat_type,
        risk_score=assessment.risk_score,
        confidence_score=assessment.confidence_score,
        urgency_level=assessment.urgency_level,
        is_unknown_number=assessment.is_unknown_number,
        possible_impersonation=assessment.possible_impersonation,
        manipulation_tactics=assessment.manipulation_tactics,
        explanation=explanation,
        recovery_recommendations=assessment.recovery_recommendations,
        evidence_recommendations=evidence_recommendations,
        authorities_to_contact=assessment.authorities_to_contact,
        triggered_signals=assessment.triggered_signals,
        proactive_warning=proactive_warning,
    )


@router.get("/timeline/{anon_user_id}", response_model=list[GuardianEventOutSchema])
def get_timeline(anon_user_id: str, case_id: str | None = None, db: Session = Depends(get_db)):
    """Feature 3. Chronological Guardian Timeline across all Guardian
    activity for this user, optionally scoped to a single case."""
    events = get_guardian_timeline(db, anon_user_id, case_id=case_id)
    return [
        GuardianEventOutSchema(
            id=e.id, event_type=e.event_type, severity=e.severity, risk_score=e.risk_score,
            summary=e.summary, explanation=e.explanation, related_case_id=e.related_case_id,
            related_evidence_id=e.related_evidence_id, source=e.source, created_at=e.created_at,
        )
        for e in events
    ]


@router.get("/correlate/{anon_user_id}")
def get_correlation(anon_user_id: str, case_id: str | None = None, db: Session = Depends(get_db)):
    """Feature 4. On-demand view of the current correlation state — lets a
    client check "has risk accumulated?" without waiting for the next scan
    to trigger it. Read-only; does not itself log a new event unless it
    detects a fresh escalation (same function used internally by the scan
    endpoints above)."""
    return correlate_and_escalate(db, anon_user_id, case_id=case_id)
