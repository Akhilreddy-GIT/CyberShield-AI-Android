"""
Recovery Lifecycle Engine.

ROOT CAUSE this exists to fix: the API had no field at all representing
"how far along is this case's recovery" — only the coarse `status` column
(open | under_review | escalated | closed), with `under_review` never
actually set by any code path and no way to reach `closed` either. With
nothing truthful to bind to, a client has no honest source for a recovery
percentage or a human-readable stage, and any UI showing one is (by
necessity) inventing it. This module is the backend's single source of
truth for that stage/percentage, computed only from real, already-persisted
case state — never a placeholder, never advanced by anything other than a
verified action actually taken on the case.

Design: a strictly ordered lifecycle. Stage only ever moves forward
(mirrors the existing escalate-only design of risk_level in risk_engine.py)
and every stage's condition is a plain, auditable check against real
columns/rows — no LLM judgment, no guessing.

    open
      -> immediate_action_required   (risk escalated to High/Critical)
      -> in_progress                 (a real conversation is underway: >=2 user messages)
      -> evidence_uploaded           (>=1 Evidence row exists for the case)
      -> authorities_contacted       (timeline shows bank/platform support contacted
                                       or a complaint/FIR filed)
      -> recovery_ongoing            (authorities contacted AND evidence uploaded —
                                       both real recovery actions are in motion)
      -> resolved                    (explicitly marked by the victim/reviewer — see
                                       mark_resolved()/mark_closed() below; never inferred)
      -> closed                      (explicitly marked — see below)

A case that never escalates (stays Low/Medium risk, e.g. an educational
back-and-forth that became a real but minor report) still progresses
through in_progress/evidence_uploaded/authorities_contacted on the same
real-action basis; "immediate_action_required" is skipped for it, which is
correct — that stage specifically means "this needs urgent attention",
not "any case exists".

`recovery_progress_percent` is derived directly from which stage was
reached — evenly spaced across the 8 stages (0, 14, 29, 43, 57, 71, 86,
100) — never an independently-invented number. A brand-new case is always
stage "open" / 0%, matching the audit requirement exactly: recovery
progress must never show any completion for a case that was just created.
"""

from dataclasses import dataclass
from typing import List, Optional

from app.services.risk_engine import RiskLevel, risk_rank

# ---------------------------------------------------------------------------
# Ordered stages. Index in this list IS the progress ordinal used for both
# "is this an escalation" comparisons and the percent calculation below.
# ---------------------------------------------------------------------------
STAGES = [
    "open",
    "immediate_action_required",
    "in_progress",
    "evidence_uploaded",
    "authorities_contacted",
    "recovery_ongoing",
    "resolved",
    "closed",
]

STAGE_LABELS = {
    "open": "Open",
    "immediate_action_required": "Immediate Action Required",
    "in_progress": "In Progress",
    "evidence_uploaded": "Evidence Uploaded",
    "authorities_contacted": "Authorities Contacted",
    "recovery_ongoing": "Recovery Ongoing",
    "resolved": "Resolved",
    "closed": "Closed",
}

_STAGE_INDEX = {s: i for i, s in enumerate(STAGES)}
_LAST_INDEX = len(STAGES) - 1


def stage_rank(stage: Optional[str]) -> int:
    """Numeric rank for a lifecycle stage. Unknown/None -> 0 (open)."""
    return _STAGE_INDEX.get(stage or "open", 0)


def progress_percent_for_stage(stage: Optional[str]) -> int:
    """Evenly spaced percent for a stage's position in the lifecycle.
    A brand-new case ("open") is always 0 — never any other value."""
    idx = stage_rank(stage)
    return round((idx / _LAST_INDEX) * 100)


# Authorities-contacted is proven by real timeline stages already logged by
# timeline_service.py — reusing those exact canonical descriptions rather
# than re-deriving a second, possibly-inconsistent detector.
_AUTHORITY_CONTACT_DESCRIPTIONS = {
    "Bank / platform support contacted",
    "Card / account blocked as containment",
    "Complaint / FIR filed",
}


@dataclass
class RecoveryState:
    stage: str
    label: str
    progress_percent: int
    reason: str  # short audit trail: why this stage, for debugging/support


def compute_recovery_state(
    *,
    status: str,
    risk_level: str,
    user_message_count: int,
    has_evidence: bool,
    timeline_descriptions: Optional[List[str]] = None,
    manual_stage_override: Optional[str] = None,
) -> RecoveryState:
    """
    Computes the truthful current lifecycle stage from real, already-
    persisted case state. Never invents a stage beyond what the evidence
    in front of it supports.

    manual_stage_override: only ever "resolved" or "closed", and only ever
    set by an explicit user/reviewer action (see cases.py's
    /resolve and /close endpoints) — these two terminal stages are
    deliberately never auto-inferred from message/evidence/timeline
    content, because "the case is actually resolved" is a real-world
    judgment only the victim (or a reviewer) can make, not something that
    should be guessed from text.
    """
    timeline_descriptions = timeline_descriptions or []

    if manual_stage_override in ("resolved", "closed"):
        stage = manual_stage_override
        reason = f"Explicitly marked {manual_stage_override} by the case owner."
        return RecoveryState(
            stage=stage,
            label=STAGE_LABELS[stage],
            progress_percent=progress_percent_for_stage(stage),
            reason=reason,
        )

    if status == "closed":
        # Legacy/manual closure via the pre-existing status column, without
        # having gone through the new explicit mark_closed action — still
        # honored, still terminal, still never a guess.
        stage = "closed"
        reason = "Case status is closed."
        return RecoveryState(
            stage=stage, label=STAGE_LABELS[stage],
            progress_percent=progress_percent_for_stage(stage), reason=reason,
        )

    authorities_contacted = any(
        any(marker in d for marker in _AUTHORITY_CONTACT_DESCRIPTIONS)
        for d in timeline_descriptions
    )

    # Compute the highest stage whose real-world condition is actually met.
    # Order matters: check from the top down and stop at the first satisfied
    # condition, since satisfying a later stage's condition also implies the
    # case has moved past earlier ones (e.g. authorities contacted implies
    # it's also "in progress").
    if authorities_contacted and has_evidence:
        stage, reason = "recovery_ongoing", "Both evidence uploaded and authorities contacted."
    elif authorities_contacted:
        stage, reason = "authorities_contacted", "Timeline shows bank/platform contacted or a complaint filed."
    elif has_evidence:
        stage, reason = "evidence_uploaded", "At least one evidence item has been uploaded to this case."
    elif user_message_count >= 2:
        stage, reason = "in_progress", "An active conversation is underway (2+ messages from the user)."
    elif risk_rank(risk_level) >= risk_rank(RiskLevel.HIGH.value):
        stage, reason = "immediate_action_required", "Risk level has escalated to High or Critical."
    else:
        stage, reason = "open", "Case was just created; no recovery action has been taken yet."

    return RecoveryState(
        stage=stage,
        label=STAGE_LABELS[stage],
        progress_percent=progress_percent_for_stage(stage),
        reason=reason,
    )


def compute_recovery_state_for_case(case, db) -> RecoveryState:
    """Convenience wrapper: gathers the real state (message count, evidence,
    timeline) for a given Case ORM object and computes its recovery stage.
    Shared by both routers/cases.py and routers/chat.py so the two never
    diverge in how they derive it (same single-source-of-truth pattern used
    for risk_level and helplines elsewhere in this codebase)."""
    # Imported here (not at module top) to avoid a circular import between
    # this service and app.models.db at import time.
    from app.models.db import Message, Evidence, TimelineEvent

    user_message_count = (
        db.query(Message)
        .filter(Message.case_id == case.id, Message.role == "user")
        .count()
    )
    has_evidence = db.query(Evidence).filter(Evidence.case_id == case.id).count() > 0
    timeline_descriptions = [
        e.description for e in
        db.query(TimelineEvent).filter(TimelineEvent.case_id == case.id).all()
    ]
    return compute_recovery_state(
        status=case.status,
        risk_level=case.risk_level,
        user_message_count=user_message_count,
        has_evidence=has_evidence,
        timeline_descriptions=timeline_descriptions,
        manual_stage_override=case.recovery_stage_override,
    )
