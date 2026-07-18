"""
Crisis Assistance Engine.

Recognizes crisis situations (cyberbullying, harassment, blackmail,
sextortion, revenge porn, image morphing, fake profiles, identity theft,
stalking, physical threats, suicidal messages, minor safety, women's
safety, college harassment/ragging, relationship extortion, family digital
abuse) and produces a calm, structured, non-judgmental response in the
fixed 9-section crisis format:

  1. Situation Summary
  2. Current Risk
  3. Immediate Safety Advice
  4. Evidence To Preserve
  5. Recommended Human Assistance
  6. Institution Support (if applicable)
  7. Government Support
  8. Recovery Steps
  9. Next Question

This module is a thin orchestration layer — it does NOT duplicate
detection logic. It reuses:
  - intent_classifier.classify_intent()      -> category + emergency type
  - risk_engine.auto_assess_risk_from_text() -> numeric risk + LOW/MED/HIGH/CRITICAL
  - agent_service.generate_response()        -> the actual guidance text
    (LLM-generated or template fallback), which already produces
    trauma-informed, non-blaming, structured content per its system prompt
  - institution_routing_service.route()      -> institution-first vs
    government-only contact routing (Feature 2)
  - legal_kb.HELPLINES                       -> government support numbers
  - knowledge_base.playbooks for incident-specific evidence guidance

The result is assembled into explicit fields (not just one prose blob) so
a frontend can render each of the 9 sections distinctly, while the prose
`guidance_text` field still carries the full natural-language reply for
clients that just want to display one message.
"""

import logging
from dataclasses import dataclass, field
from typing import List, Optional

from app.services.intent_classifier import classify_intent, IntentResult
from app.services.risk_engine import auto_assess_risk_from_text, RiskLevel
from app.services.agent_service import generate_response, get_helplines_for_critical
from app.services.institution_routing_service import route as route_institution, InstitutionRoutingResult
from app.knowledge_base.playbooks import match_playbook

logger = logging.getLogger(__name__)


class EmergencyLevel:
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


_RISK_TO_EMERGENCY_LEVEL = {
    RiskLevel.LOW.value: EmergencyLevel.LOW,
    RiskLevel.MEDIUM.value: EmergencyLevel.MEDIUM,
    RiskLevel.HIGH.value: EmergencyLevel.HIGH,
    RiskLevel.CRITICAL.value: EmergencyLevel.CRITICAL,
}

# Emergency types (from intent_classifier) that always warrant recommending
# immediate human assistance, regardless of the numeric risk score — a
# single strong qualitative signal (e.g. a suicidal statement) should never
# be out-argued by a lower point total.
_ALWAYS_RECOMMEND_HUMAN_HELP = {
    "self_harm", "imminent_danger", "child_safety", "active_exploitation", "account_takeover",
}


@dataclass
class CrisisAssessment:
    emergency_level: str                       # LOW | MEDIUM | HIGH | CRITICAL
    human_assistance_recommended: bool
    category: Optional[str]
    emergency_type: Optional[str]               # self_harm | imminent_danger | child_safety | ...
    situation_summary: str
    current_risk: str
    immediate_safety_advice: List[str] = field(default_factory=list)
    evidence_to_preserve: List[str] = field(default_factory=list)
    recommended_human_assistance: List[str] = field(default_factory=list)
    institution_recommended: bool = False
    institution_name: Optional[str] = None
    institution_routing_reason: str = ""
    institution_support: List[dict] = field(default_factory=list)
    government_support: List[dict] = field(default_factory=list)
    recovery_steps: List[str] = field(default_factory=list)
    next_question: Optional[str] = None
    guidance_text: str = ""                     # full prose reply (from agent_service)
    cited_sources: List[str] = field(default_factory=list)
    used_llm: bool = False
    risk_score: int = 0
    triggered_factors: List[str] = field(default_factory=list)
    risk_level: str = ""  # canonical risk_engine.RiskLevel value, e.g. "High Risk" — what gets persisted to Case.risk_level verbatim


# Generic, always-safe safety advice per emergency type — used to guarantee
# Immediate Safety Advice is never empty even before/without an LLM call,
# and to keep this list auditable rather than left entirely to the model.
_SAFETY_ADVICE_BY_EMERGENCY_TYPE = {
    "self_harm": [
        "Your safety matters right now more than anything else — you are not alone in this.",
        "Please reach out to a crisis helpline immediately — iCall Psychosocial Helpline: 9152987821.",
        "If you are in immediate danger, contact Police Emergency: 112, or go to the nearest hospital.",
        "Consider telling a trusted person near you right now so you're not alone.",
    ],
    "imminent_danger": [
        "If you are in immediate physical danger, contact Police Emergency (112) right now.",
        "Move to a safe, populated location if you can do so safely.",
        "Do not confront the person making the threat.",
        "Preserve any messages or calls that contain the threat — do not delete them.",
    ],
    "child_safety": [
        "This is being treated as the highest priority.",
        "Do not confront the other party directly — this can escalate risk to the child.",
        "Preserve all messages, images, or account details without viewing/sharing them further.",
        "Contact Childline India (1098) and a trusted adult immediately.",
    ],
    "active_exploitation": [
        "Do not pay any money or send anything further — payment does not guarantee the threat stops.",
        "Do not delete anything — screenshots and messages are your evidence.",
        "Limit contact with the person threatening you; avoid replying beyond what's necessary.",
        "This is not your fault, and you will not be judged for what happened.",
    ],
    "account_takeover": [
        "Contact your bank or the platform's official support immediately to freeze/secure the account.",
        "Change passwords on any other accounts that reused the same password.",
        "Do not share any further OTPs, PINs, or codes with anyone.",
    ],
}

_DEFAULT_SAFETY_ADVICE = [
    "You are not to blame for what happened — this response is here to help you take clear next steps.",
    "Take a moment to breathe; you don't need to solve everything at once.",
    "Preserve evidence before taking any other action, where it's safe to do so.",
]

_RECOVERY_STEPS_DEFAULT = [
    "File a formal complaint (cybercrime.gov.in or 1930) once immediate safety is addressed.",
    "Keep a record of every step you take and when, in case it's needed later.",
    "Consider talking to a counsellor or trusted support person — going through this alone is harder than it needs to be.",
]


def _safety_advice_for(emergency_type: Optional[str]) -> List[str]:
    if emergency_type and emergency_type in _SAFETY_ADVICE_BY_EMERGENCY_TYPE:
        return _SAFETY_ADVICE_BY_EMERGENCY_TYPE[emergency_type]
    return _DEFAULT_SAFETY_ADVICE


def _evidence_advice_for(category_value: Optional[str], incident_text: str) -> List[str]:
    """Reuses the existing playbook matcher so evidence guidance stays
    incident-specific instead of a generic list, without duplicating that
    logic here."""
    playbook = match_playbook(incident_text, category_value)
    if playbook and playbook.evidence_to_preserve:
        return list(playbook.evidence_to_preserve[:6])
    return [
        "Screenshots of every relevant message, post, or profile (include timestamps).",
        "Any transaction IDs, phone numbers, usernames, or URLs involved.",
        "Do not delete or edit anything, even if it's upsetting to look at.",
    ]


def _recommended_human_assistance(
    human_assistance_recommended: bool,
    emergency_type: Optional[str],
    institution_result: InstitutionRoutingResult,
) -> List[str]:
    if not human_assistance_recommended:
        return [
            "Not urgent right now — you can continue getting guidance here, and reach out to a "
            "helpline any time if things change."
        ]
    items = []
    if emergency_type == "self_harm":
        items.append("Crisis counsellor — iCall Psychosocial Helpline: 9152987821.")
    if institution_result.institution_recommended:
        items.append(
            f"{institution_result.institution_profile.institution_name} — see Institution Support below."
        )
    items.append("National Cyber Crime Helpline — 1930 (for financial fraud, blackmail, or urgent cyber incidents).")
    if emergency_type == "child_safety":
        items.append("Childline India — 1098.")
    if emergency_type == "imminent_danger":
        items.append("Police Emergency — 112.")
    return items


def assess_crisis(
    message: str,
    conversation_history: Optional[List[dict]] = None,
    institution_id: Optional[str] = None,
    facts_json: Optional[str] = None,
) -> CrisisAssessment:
    """
    Main entry point. Pure function over its inputs (no DB access) so it
    can be called from the chat router, a dedicated crisis endpoint, or
    tests alike — callers own persistence.
    """
    history = conversation_history or []

    intent: IntentResult = classify_intent(message)

    risk = auto_assess_risk_from_text(
        latest_message=message,
        conversation_history=history + [{"role": "user", "content": message}],
        is_critical_intent=intent.is_critical,
    )

    emergency_level = _RISK_TO_EMERGENCY_LEVEL.get(risk.level.value, EmergencyLevel.LOW)
    # A qualitative emergency-type hit always floors the level at CRITICAL
    # for human-assistance purposes, mirroring chat.py's own floor logic —
    # this keeps the two paths consistent instead of introducing a second,
    # slightly different escalation rule.
    if intent.emergency_type in _ALWAYS_RECOMMEND_HUMAN_HELP:
        emergency_level = EmergencyLevel.CRITICAL

    human_assistance_recommended = (
        emergency_level in (EmergencyLevel.HIGH, EmergencyLevel.CRITICAL)
        or intent.emergency_type in _ALWAYS_RECOMMEND_HUMAN_HELP
    )

    category_value = intent.category.value if intent.category else None

    institution_result = route_institution(
        institution_id=institution_id,
        category_value=category_value,
        incident_text=message,
    )

    # Reuse the existing generation pipeline (LLM or template fallback) for
    # the natural-language guidance — this already implements trauma-
    # informed, non-blaming, structured responses per its system prompt.
    # Pass the risk assessment already computed above so guidance_text
    # states the exact same level as current_risk/emergency_level below,
    # instead of the model independently re-judging severity in prose —
    # same fix as chat.py, same reason (single source of truth).
    generation = generate_response(
        message, history, intent, facts_json=facts_json,
        risk_assessment={"level": risk.level.value, "score": risk.score, "triggered_factors": risk.triggered_factors},
    )

    situation_summary = (
        f"You've described what appears to be a {category_value.replace('_', ' ')} situation."
        if category_value and category_value != "other_cyber"
        else "Thanks for sharing what's happening — here's an initial read on the situation."
    )

    current_risk = f"{emergency_level} — {risk.explanation}"

    government_support = institution_result.government_contacts
    if intent.emergency_type == "self_harm":
        # Always surface the crisis helpline first for self-harm, even if
        # institution routing didn't specifically flag it.
        government_support = (
            [g for g in government_support if g["name"] == "iCall Psychosocial Helpline"]
            + [g for g in government_support if g["name"] != "iCall Psychosocial Helpline"]
        )

    assessment = CrisisAssessment(
        emergency_level=emergency_level,
        human_assistance_recommended=human_assistance_recommended,
        category=category_value,
        emergency_type=intent.emergency_type,
        situation_summary=situation_summary,
        current_risk=current_risk,
        immediate_safety_advice=_safety_advice_for(intent.emergency_type),
        evidence_to_preserve=_evidence_advice_for(category_value, message),
        recommended_human_assistance=_recommended_human_assistance(
            human_assistance_recommended, intent.emergency_type, institution_result
        ),
        institution_recommended=institution_result.institution_recommended,
        institution_name=institution_result.institution_profile.institution_name if institution_result.institution_profile else None,
        institution_routing_reason=institution_result.routing_reason,
        institution_support=institution_result.institution_contacts if institution_result.institution_recommended else [],
        government_support=government_support,
        recovery_steps=_RECOVERY_STEPS_DEFAULT,
        next_question=None,  # left to the prose guidance_text / agent follow-up; not duplicated here
        guidance_text=generation["reply"],
        cited_sources=generation["cited_sources"],
        used_llm=generation["used_llm"],
        risk_score=risk.score,
        triggered_factors=risk.triggered_factors,
        risk_level=risk.level.value,
    )

    logger.info(
        "crisis assessment: level=%s human_assistance=%s category=%s emergency_type=%s institution=%s",
        emergency_level, human_assistance_recommended, category_value, intent.emergency_type,
        institution_result.institution_profile.institution_id if institution_result.institution_profile else None,
    )

    return assessment
