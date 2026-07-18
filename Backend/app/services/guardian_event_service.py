"""
Guardian Event Engine — Feature 3 (event log/timeline) and Feature 4
(correlation/intelligence across signals).

Every Guardian activity (notification scanned, call analyzed, evidence
uploaded, risk changed) is logged as one GuardianEvent row. This module
owns:
  - recording events (record_event)
  - building the chronological Guardian Timeline for a user/case
    (get_guardian_timeline)
  - correlating recent signals to decide whether accumulated risk should
    escalate a case beyond what any single signal implied on its own
    (correlate_and_escalate) — Feature 4's "Guardian gets smarter as more
    evidence arrives"

Design: reuses the existing risk_engine.RiskLevel enum and timeline
philosophy (auditable, explainable escalation — every correlation decision
traces to the specific prior events that triggered it) rather than
introducing a second, incompatible risk vocabulary.
"""

import json
import logging
from dataclasses import dataclass, field
from datetime import datetime, timezone, timedelta
from typing import List, Optional

from sqlalchemy.orm import Session

from app.models.db import GuardianEvent, Case, gen_id
from app.services.risk_engine import RiskLevel

logger = logging.getLogger(__name__)


class EventType:
    """Canonical Guardian event type strings — kept as plain constants
    (not an Enum stored in the DB) so new event types can be added without
    a migration, consistent with Case.status's existing plain-string
    convention in this codebase."""
    NOTIFICATION_SCANNED = "notification_scanned"
    SCAM_SMS_DETECTED = "scam_sms_detected"
    SUSPICIOUS_WHATSAPP = "suspicious_whatsapp_message"
    CALL_ANALYZED = "call_analyzed"
    HIGH_RISK_PHISHING = "high_risk_phishing"
    INVESTMENT_SCAM_DETECTED = "investment_scam_detected"
    OTP_REQUEST_DETECTED = "otp_request_detected"
    REMOTE_ACCESS_ATTEMPT = "remote_access_attempt"
    EVIDENCE_UPLOADED = "evidence_uploaded"
    RISK_INCREASED = "risk_increased"
    RISK_DECREASED = "risk_decreased"
    RECOVERY_COMPLETED = "recovery_completed"


# Severity ranking used for correlation comparisons — mirrors the bands in
# notification_guardian_service / call_guardian_service.
_SEVERITY_RANK = {"info": 0, "low": 1, "medium": 2, "high": 3, "critical": 4}

# A correlation window: signals older than this don't count toward the
# "recent pattern" that triggers escalation, so a scam SMS from three
# months ago doesn't combine with an unrelated call today.
_CORRELATION_WINDOW = timedelta(hours=72)


@dataclass
class GuardianEventOut:
    id: str
    event_type: str
    severity: str
    risk_score: int
    summary: str
    explanation: Optional[str]
    related_case_id: Optional[str]
    related_evidence_id: Optional[str]
    source: str
    created_at: datetime


def record_event(
    db: Session,
    anon_user_id: str,
    event_type: str,
    severity: str,
    risk_score: int,
    summary: str,
    explanation: Optional[str] = None,
    case_id: Optional[str] = None,
    related_evidence_id: Optional[str] = None,
    source: str = "system",
    extra: Optional[dict] = None,
) -> GuardianEvent:
    """Persists one Guardian event. Never raises past this call succeeding
    or failing on its own — callers decide whether a logging failure
    should block the parent operation (it shouldn't; wrap in try/except at
    the call site if needed)."""
    event = GuardianEvent(
        id=gen_id("GEVT"),
        anon_user_id=anon_user_id,
        case_id=case_id,
        event_type=event_type,
        severity=severity,
        risk_score=risk_score,
        summary=summary,
        explanation=explanation,
        related_evidence_id=related_evidence_id,
        source=source,
        extra_json=json.dumps(extra) if extra else None,
    )
    db.add(event)
    db.commit()
    db.refresh(event)
    return event


def get_guardian_timeline(
    db: Session,
    anon_user_id: str,
    case_id: Optional[str] = None,
    limit: int = 100,
) -> List[GuardianEventOut]:
    """Chronological Guardian Timeline for a user, optionally scoped to one
    case. Oldest first, matching TimelineEvent's existing ordering
    convention in cases.py."""
    query = db.query(GuardianEvent).filter(GuardianEvent.anon_user_id == anon_user_id)
    if case_id:
        query = query.filter(GuardianEvent.case_id == case_id)
    rows = query.order_by(GuardianEvent.created_at).limit(limit).all()
    return [
        GuardianEventOut(
            id=r.id, event_type=r.event_type, severity=r.severity, risk_score=r.risk_score,
            summary=r.summary, explanation=r.explanation, related_case_id=r.case_id,
            related_evidence_id=r.related_evidence_id, source=r.source, created_at=r.created_at,
        )
        for r in rows
    ]


def correlate_and_escalate(db: Session, anon_user_id: str, case_id: Optional[str] = None) -> dict:
    """
    Feature 4: looks at recent Guardian events (within _CORRELATION_WINDOW)
    for this user and decides whether the ACCUMULATED pattern warrants a
    higher risk read than any single event implied — e.g. suspicious SMS
    -> unknown call -> OTP request reported -> evidence uploaded should
    read as escalating even if each individual event was only MEDIUM.

    Returns a dict describing the correlation outcome; does not itself
    raise a case's risk_level (callers — e.g. the notification/call
    routers — decide whether to apply it to a Case), but DOES log a
    RISK_INCREASED GuardianEvent when escalation is detected, so the
    timeline shows the correlation happened and why.
    """
    try:
        cutoff = datetime.now(timezone.utc) - _CORRELATION_WINDOW
        query = db.query(GuardianEvent).filter(
            GuardianEvent.anon_user_id == anon_user_id,
            GuardianEvent.created_at >= cutoff,
        )
        if case_id:
            query = query.filter(GuardianEvent.case_id == case_id)
        recent = query.order_by(GuardianEvent.created_at).all()

        actionable = [
            e for e in recent
            if e.event_type not in (EventType.RISK_INCREASED, EventType.RISK_DECREASED, EventType.RECOVERY_COMPLETED)
        ]

        distinct_sources = {e.source for e in actionable}
        distinct_types = {e.event_type for e in actionable}
        max_single_severity = max((_SEVERITY_RANK.get(e.severity, 0) for e in actionable), default=0)
        combined_score = sum(e.risk_score for e in actionable)

        # Escalation rule: multiple independent signal SOURCES (e.g. a
        # notification AND a call, not two notifications) within the
        # window, each carrying real signal, is what indicates an
        # unfolding pattern rather than one flagged message.
        should_escalate = (
            len(distinct_sources) >= 2
            and len(actionable) >= 2
            and max_single_severity < _SEVERITY_RANK["critical"]
            and combined_score >= 20
        )

        result = {
            "escalated": False,
            "recommended_level": None,
            "combined_score": combined_score,
            "contributing_event_count": len(actionable),
            "distinct_sources": sorted(distinct_sources),
            "distinct_event_types": sorted(distinct_types),
        }

        if should_escalate:
            recommended = RiskLevel.HIGH if combined_score < 35 else RiskLevel.CRITICAL
            result["escalated"] = True
            result["recommended_level"] = recommended.value

            contributing_summaries = "; ".join(e.summary for e in actionable[-5:])
            record_event(
                db=db,
                anon_user_id=anon_user_id,
                event_type=EventType.RISK_INCREASED,
                severity="high" if recommended == RiskLevel.HIGH else "critical",
                risk_score=min(combined_score, 100),
                summary=f"Risk increased — {len(actionable)} correlated signals across {len(distinct_sources)} sources in the last 72 hours.",
                explanation=(
                    f"Multiple independent signals were detected in a short window, which increases "
                    f"confidence this is a real, unfolding incident rather than an isolated flagged message: "
                    f"{contributing_summaries}"
                ),
                case_id=case_id,
                source="system",
            )

        return result
    except Exception:
        logger.exception("Guardian correlation failed; returning no-escalation default.")
        return {
            "escalated": False, "recommended_level": None, "combined_score": 0,
            "contributing_event_count": 0, "distinct_sources": [], "distinct_event_types": [],
        }
