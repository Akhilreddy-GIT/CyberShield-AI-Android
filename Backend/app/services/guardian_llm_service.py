"""
Guardian LLM Explanation Layer.

Hybrid design: notification_guardian_service.py and call_guardian_service.py
decide the category/risk score/confidence entirely with auditable rules —
the LLM is NEVER used to make that call. This module's only job is to turn
an already-decided assessment into a short, calm, natural-language
explanation, an immediate recommendation, and (for Feature 5) a proactive
warning line — using the same ChatGroq setup as agent_service.py, with the
same graceful template fallback if GROQ_API_KEY isn't set, so the Guardian
stays fully functional and demoable with zero API cost.

Reuses the existing `_llm`-or-None pattern rather than re-instantiating a
second client, and keeps prompts short/cheap since this runs on every
notification and call, potentially at high volume.
"""

import os
import logging
from typing import List, Optional

from langchain_groq import ChatGroq
from langchain_core.messages import HumanMessage, SystemMessage

logger = logging.getLogger(__name__)

GROQ_API_KEY = os.getenv("GROQ_API_KEY")
MODEL = "llama-3.1-8b-instant"

_llm: Optional[ChatGroq] = None
if GROQ_API_KEY:
    _llm = ChatGroq(api_key=GROQ_API_KEY, model=MODEL, temperature=0.2, max_tokens=220)
else:
    logger.warning("GROQ_API_KEY not set — guardian_llm_service will use template fallback only.")

_SYSTEM_PROMPT = (
    "You are the explanation layer of a cyber-safety Guardian. You are given a scam "
    "category, a risk score, and a list of already-detected signals — you do NOT decide "
    "the category or score, only explain them. Write in a calm, short, non-alarming tone. "
    "Never invent facts not present in the signals given to you. Respond in plain text with "
    "exactly two short parts separated by a pipe character '|': "
    "first an EXPLANATION (1-2 sentences, plain language, why this was flagged), "
    "then a RECOMMENDATION (1 sentence, concrete, actionable next step). "
    "Do not use markdown. Do not add any other text."
)


def _build_user_prompt(
    kind: str,
    category: Optional[str],
    risk_score: int,
    severity: str,
    signals: List[str],
    extra_context: str = "",
) -> str:
    signal_list = ", ".join(signals) if signals else "none"
    return (
        f"Type: {kind}\n"
        f"Category: {category or 'none matched'}\n"
        f"Risk score: {risk_score}/100\n"
        f"Severity: {severity}\n"
        f"Detected signals: {signal_list}\n"
        f"{extra_context}"
    )


def _parse_llm_output(raw: str) -> tuple:
    parts = raw.strip().split("|", 1)
    if len(parts) == 2:
        return parts[0].strip(), parts[1].strip()
    return raw.strip(), ""


def _template_explanation(category: Optional[str], signals: List[str], severity: str, dangerous: bool = False) -> tuple:
    """Deterministic fallback — same graceful-degradation contract as
    agent_service._template_response. Built directly from signal labels,
    zero LLM cost."""
    if not category or severity == "info":
        explanation = "No known scam pattern was detected. This is not a guarantee of safety — stay generally cautious with unfamiliar senders or callers."
        recommendation = "No specific action needed right now."
        return explanation, recommendation

    signal_text = ", ".join(signals[:3]) if signals else "the content reported"
    explanation = f"This was flagged as a possible {category.lower()} based on: {signal_text}."
    if severity in ("high", "critical") or dangerous:
        recommendation = "Do not share OTP, passwords, or make any payment — verify independently using an official number before doing anything."
    elif severity == "medium":
        recommendation = "Be cautious — verify the sender/caller independently before taking any action they're requesting."
    else:
        recommendation = "Low risk detected, but keep an eye out if similar messages or calls continue."
    return explanation, recommendation


def explain_notification(category: Optional[str], risk_score: int, severity: str, signals: List[str], dangerous: bool = False) -> tuple:
    """Returns (explanation, recommendation) strings."""
    if _llm is not None:
        try:
            prompt = _build_user_prompt("notification", category, risk_score, severity, signals)
            result = _llm.invoke([SystemMessage(content=_SYSTEM_PROMPT), HumanMessage(content=prompt)])
            explanation, recommendation = _parse_llm_output(result.content)
            if explanation:
                return explanation, recommendation or _template_explanation(category, signals, severity, dangerous)[1]
        except Exception as e:
            logger.error("Guardian LLM explanation failed for notification, using template: %s", e)
    return _template_explanation(category, signals, severity, dangerous)


def explain_call(category: Optional[str], risk_score: int, urgency_level: str, signals: List[str]) -> tuple:
    """Returns (explanation, recommendation) strings. urgency_level stands
    in for severity in the prompt context for calls."""
    severity_map = {"high": "high", "medium": "medium", "low": "low"}
    severity = severity_map.get(urgency_level, "low") if category else "info"
    if _llm is not None:
        try:
            prompt = _build_user_prompt("call", category, risk_score, severity, signals)
            result = _llm.invoke([SystemMessage(content=_SYSTEM_PROMPT), HumanMessage(content=prompt)])
            explanation, recommendation = _parse_llm_output(result.content)
            if explanation:
                return explanation, recommendation or _template_explanation(category, signals, severity)[1]
        except Exception as e:
            logger.error("Guardian LLM explanation failed for call, using template: %s", e)
    return _template_explanation(category, signals, severity)


def generate_proactive_warning(category: Optional[str], severity: str, signals: List[str]) -> Optional[str]:
    """Feature 5: short, calm, actionable one-liner. Returns None when
    nothing warrants a proactive warning (info/no category), rather than
    manufacturing false alarms."""
    if not category or severity == "info":
        return None

    templates = {
        "OTP Scam": "This notification resembles an OTP scam — never share an OTP, even if asked urgently.",
        "Fake Bank Alert": "This message resembles a fake bank alert. Don't tap any link — contact your bank directly instead.",
        "Fake KYC": "This resembles a fake KYC update request — banks don't ask you to update KYC via a link in a text.",
        "Fake Courier Scam": "This resembles a common fake courier/customs scam — don't pay any fee through this message.",
        "Investment Scam": "This resembles an investment scam promising guaranteed returns — real investments always carry risk.",
        "Loan Scam": "This resembles a fake instant-loan message — legitimate lenders don't ask for upfront fees this way.",
        "Credential Harvesting": "This message is asking you to 'verify' account details — that's a common credential-theft pattern.",
        "Remote Access Scam": "This is asking you to install a remote-access app — a common scam pattern for taking over a device.",
        "Fake Government Message": "This resembles a fake government message — verify independently via the official portal, not this link.",
        "Bank Impersonation": "This call pattern resembles bank impersonation — hang up and call your bank's official number to check.",
        "Police Impersonation": "This call resembles a police-impersonation scam — real police do not conduct arrests or demand money by phone.",
        "UPI Scam": "This resembles a UPI scam — accepting a 'collect request' or scanning a QR code sends money, it doesn't receive it.",
    }
    if category in templates:
        return templates[category]

    # Generic fallback built from the strongest signal, kept short and calm.
    if signals:
        return f"This resembles a known scam pattern ({signals[0].lower()}) — proceed carefully and avoid sharing sensitive information."
    return f"This resembles a {category.lower()} — proceed with caution."
