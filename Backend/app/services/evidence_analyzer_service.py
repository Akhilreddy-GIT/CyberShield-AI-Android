"""
AI Evidence Analyzer.

Analyzes the OCR'd/extracted text of an uploaded evidence screenshot and
produces a structured threat analysis: scam pattern category, social
engineering techniques present, and specific manipulation indicators
(urgency, fake authority, requests for money/OTP/credentials).

Two layers, same graceful-degradation philosophy as agent_service.py:

1. RULE-BASED DETECTOR (`detect_scam_indicators`) — always runs, zero
   cost, deterministic, fully auditable. This is the layer the rest of the
   system (risk engine, timeline, case memory) can depend on even when no
   LLM is configured, and it's what backs the structured indicator flags
   in the response.

2. LLM NARRATIVE LAYER (`generate_evidence_analysis`) — when GROQ_API_KEY
   is set, asks the model for a short structured natural-language analysis
   (what kind of message this is, why it's suspicious, what the sender is
   trying to get the victim to do) grounded in the rule-based findings so
   it can't contradict them. Falls back to a deterministic template built
   directly from the rule-based findings if the LLM is unavailable or the
   call fails — analysis is never blank.

Nothing in this module writes to the database; the evidence router decides
what to persist.
"""

import os
import logging
from dataclasses import dataclass, field, asdict
from typing import Dict, List, Optional

from langchain_groq import ChatGroq
from langchain_core.prompts import ChatPromptTemplate

logger = logging.getLogger(__name__)

GROQ_API_KEY = os.getenv("GROQ_API_KEY")
MODEL = "llama-3.1-8b-instant"

_llm: Optional[ChatGroq] = None
if GROQ_API_KEY:
    _llm = ChatGroq(api_key=GROQ_API_KEY, model=MODEL, temperature=0.2, max_tokens=500)
else:
    logger.warning("GROQ_API_KEY not set — evidence_analyzer_service will use template fallback only.")


# ---------------------------------------------------------------------------
# Rule-based indicator detection — plain keyword/phrase families, grouped by
# the social-engineering technique they represent. Deliberately simple and
# inspectable: every flag traces to specific matched phrases, not a
# black-box score.
# ---------------------------------------------------------------------------
_INDICATOR_FAMILIES: Dict[str, List[str]] = {
    "urgency_manipulation": [
        "act now", "immediately", "urgent", "within 24 hours", "within 1 hour",
        "expire", "expiring", "last chance", "limited time", "account will be blocked",
        "account will be suspended", "will be deactivated", "today only", "hurry",
    ],
    "fake_authority": [
        "bank official", "rbi", "reserve bank", "income tax department", "cyber cell",
        "police department", "customs department", "government of india", "sbi official",
        "we are calling from", "this is regarding your", "compliance department",
        "trai", "telecom regulatory", "court notice", "legal action will be taken",
    ],
    "money_request": [
        "pay now", "transfer money", "send money", "processing fee", "advance fee",
        "customs duty", "release your parcel", "refundable deposit", "registration fee",
        "unlock fee", "penalty fee", "pay to claim", "pay to unlock",
    ],
    "otp_credential_harvesting": [
        "share your otp", "share otp", "enter your otp", "tell me the otp",
        "share your pin", "enter your pin", "cvv", "card number", "expiry date and cvv",
        "net banking password", "login credentials", "share your password",
        "verify your account by entering", "confirm your upi pin",
    ],
    "prize_lottery_bait": [
        "you have won", "you've won", "lottery", "lucky winner", "claim your prize",
        "claim your reward", "cashback offer", "gift card", "spin the wheel",
    ],
    "investment_bait": [
        "guaranteed returns", "double your money", "risk-free investment",
        "high returns in", "trading tips", "investment opportunity", "crypto doubling",
    ],
    "impersonation": [
        "hi mom", "hi dad", "this is my new number", "lost my phone", "new number save this",
        "i am stuck", "send help urgently", "emergency i need money",
    ],
    "threat_coercion": [
        "i will leak", "i will post", "pay or i will", "share this with your contacts",
        "expose you", "everyone will see", "your family will know",
    ],
    "suspicious_link_bait": [
        "click here", "click the link below", "verify your account", "update your kyc",
        "kyc update", "confirm your details", "reactivate your account",
    ],
}

# Human-readable label + one-line explanation per family, used to build the
# structured response and the deterministic fallback narrative.
_INDICATOR_META: Dict[str, Dict[str, str]] = {
    "urgency_manipulation": {
        "label": "Urgency Manipulation",
        "explanation": "Creates artificial time pressure to stop the victim from thinking carefully or verifying the claim.",
    },
    "fake_authority": {
        "label": "Fake Authority / Impersonation of Officials",
        "explanation": "Claims to be a bank, government body, or law enforcement to make the request seem legitimate and non-negotiable.",
    },
    "money_request": {
        "label": "Request for Money / Fees",
        "explanation": "Asks the victim to pay a fee, deposit, or penalty upfront — a hallmark of advance-fee fraud.",
    },
    "otp_credential_harvesting": {
        "label": "OTP / Credential Harvesting Attempt",
        "explanation": "Directly asks for OTP, PIN, CVV, or login credentials — no legitimate bank or platform ever asks for these.",
    },
    "prize_lottery_bait": {
        "label": "Prize / Lottery Bait",
        "explanation": "Claims the victim won something they never entered, to lower their guard before extracting money or data.",
    },
    "investment_bait": {
        "label": "Fake Investment / Guaranteed Returns",
        "explanation": "Promises unrealistic guaranteed profits — a core marker of Ponzi and trading-app scams.",
    },
    "impersonation": {
        "label": "Impersonation of a Known Contact",
        "explanation": "Poses as a family member or friend in distress, often from a new/unknown number, to bypass normal skepticism.",
    },
    "threat_coercion": {
        "label": "Threat / Coercion (Blackmail Pattern)",
        "explanation": "Uses threats to expose or leak content unless the victim pays or complies — consistent with blackmail/sextortion tactics.",
    },
    "suspicious_link_bait": {
        "label": "Suspicious Link / Phishing Bait",
        "explanation": "Pushes the victim toward a link or 'verification' step designed to harvest credentials or install malware.",
    },
}


@dataclass
class EvidenceIndicator:
    key: str
    label: str
    explanation: str
    matched_phrases: List[str]


@dataclass
class ScamAnalysis:
    message_type: str
    threat_level: str  # informational | low | moderate | high
    indicators: List[EvidenceIndicator] = field(default_factory=list)
    requests_money: bool = False
    requests_otp_or_credentials: bool = False
    narrative: str = ""
    used_llm: bool = False

    def to_dict(self) -> Dict:
        return {
            "message_type": self.message_type,
            "threat_level": self.threat_level,
            "indicators": [asdict(i) for i in self.indicators],
            "requests_money": self.requests_money,
            "requests_otp_or_credentials": self.requests_otp_or_credentials,
            "narrative": self.narrative,
            "used_llm": self.used_llm,
        }


_MESSAGE_TYPE_HINTS = [
    ("banking_notification", ["debited", "credited", "a/c no", "account no", "available balance", "bank alert"]),
    ("otp_credential_phishing", ["enter your otp", "share your otp", "verify your account", "confirm your upi pin"]),
    ("fake_investment", ["guaranteed returns", "trading tips", "investment opportunity", "double your money"]),
    ("blackmail_sextortion", ["i will leak", "i will post", "pay or i will", "your private photos", "your nude"]),
    ("cyberbullying_harassment", ["everyone thinks you", "you are ugly", "kill yourself", "nobody likes you"]),
    ("fake_customer_care", ["customer care", "helpline", "your complaint number", "toll free"]),
    ("job_offer_scam", ["work from home", "registration fee", "job offer", "selected for the position"]),
    ("prize_lottery", ["you have won", "lottery", "lucky draw", "claim your prize"]),
    ("impersonation_chat", ["this is my new number", "hi mom", "hi dad", "lost my phone"]),
]


def detect_scam_indicators(text: str) -> ScamAnalysis:
    """Rule-based analysis — always available, zero cost, deterministic.
    Never raises; returns a minimal 'informational' analysis on empty or
    unreadable input."""
    if not text:
        return ScamAnalysis(message_type="unknown", threat_level="informational", narrative="No text available to analyze.")

    try:
        lower = text.lower()
        indicators: List[EvidenceIndicator] = []
        for key, phrases in _INDICATOR_FAMILIES.items():
            matched = [p for p in phrases if p in lower]
            if matched:
                meta = _INDICATOR_META[key]
                indicators.append(EvidenceIndicator(
                    key=key, label=meta["label"], explanation=meta["explanation"], matched_phrases=matched,
                ))

        message_type = "general_message"
        for mtype, hints in _MESSAGE_TYPE_HINTS:
            if any(h in lower for h in hints):
                message_type = mtype
                break

        requests_money = any(i.key == "money_request" for i in indicators)
        requests_otp = any(i.key == "otp_credential_harvesting" for i in indicators)

        num_indicators = len(indicators)
        has_severe = any(i.key in ("otp_credential_harvesting", "threat_coercion") for i in indicators)
        if has_severe or num_indicators >= 3:
            threat_level = "high"
        elif num_indicators == 2:
            threat_level = "moderate"
        elif num_indicators == 1:
            threat_level = "low"
        else:
            threat_level = "informational"

        narrative = _build_fallback_narrative(message_type, indicators, threat_level)

        return ScamAnalysis(
            message_type=message_type,
            threat_level=threat_level,
            indicators=indicators,
            requests_money=requests_money,
            requests_otp_or_credentials=requests_otp,
            narrative=narrative,
            used_llm=False,
        )
    except Exception:
        logger.exception("Rule-based scam indicator detection failed.")
        return ScamAnalysis(message_type="unknown", threat_level="informational", narrative="Analysis could not be completed for this file.")


def _build_fallback_narrative(message_type: str, indicators: List[EvidenceIndicator], threat_level: str) -> str:
    type_label = message_type.replace("_", " ")
    if not indicators:
        return f"This appears to be a {type_label}. No known scam or social-engineering patterns were detected in the extracted text."
    labels = ", ".join(i.label for i in indicators)
    return (
        f"This appears to be a {type_label} containing {len(indicators)} social-engineering "
        f"indicator(s): {labels}. Overall pattern threat level: {threat_level}. "
        "Do not click any links, share OTP/PIN/CVV, or send money in response to messages like this."
    )


_ANALYSIS_PROMPT = ChatPromptTemplate.from_messages([
    ("system",
     "You are a cybersecurity analyst reviewing a screenshot's extracted text for scam/social-engineering "
     "patterns. You are given the rule-based indicators ALREADY detected — do not contradict them, just explain "
     "them in context and add any nuance a human investigator would notice. Keep your answer to 3-5 sentences, "
     "plain language, no headers, no markdown. Do not invent indicators not listed below and do not invent facts "
     "not present in the text."),
    ("human",
     "EXTRACTED TEXT:\n{text}\n\nDETECTED INDICATORS:\n{indicators}\n\nMESSAGE TYPE GUESS: {message_type}\n\n"
     "Write a short analyst narrative explaining what this message is trying to do and why it's suspicious "
     "(or why it looks benign, if no indicators were found)."),
])


def generate_evidence_analysis(text: str) -> Dict:
    """Top-level entry point for the evidence router. Runs the rule-based
    detector first (always succeeds), then optionally asks the LLM for a
    grounded narrative layer on top. Returns a plain dict."""
    analysis = detect_scam_indicators(text)

    if not text or _llm is None:
        return analysis.to_dict()

    try:
        indicator_lines = "\n".join(f"- {i.label}: {i.explanation}" for i in analysis.indicators) or "None detected."
        chain = _ANALYSIS_PROMPT | _llm
        result = chain.invoke({
            "text": text[:3000],  # cap prompt size for very long OCR dumps
            "indicators": indicator_lines,
            "message_type": analysis.message_type.replace("_", " "),
        })
        analysis.narrative = result.content.strip()
        analysis.used_llm = True
    except Exception as e:
        logger.error("LLM evidence analysis failed, keeping rule-based narrative: %s", e)

    return analysis.to_dict()
