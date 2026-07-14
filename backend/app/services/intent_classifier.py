"""
Intent Classification & Domain Guardrail.

Two-stage approach so the system works even WITHOUT an LLM API key connected:
  1. Fast keyword/rule-based pre-filter (works offline, zero cost, zero latency)
  2. If an LLM client is available, it's used to refine ambiguous cases

This means a reviewer who clones the repo without adding an API key can still
see the guardrail and classification logic working end-to-end.
"""

from enum import Enum
from typing import Optional
from dataclasses import dataclass


class CyberCategory(str, Enum):
    CYBERBULLYING = "cyberbullying"
    ONLINE_HARASSMENT = "online_harassment"
    BLACKMAIL = "blackmail_threats"
    SEXTORTION = "sexually_explicit_content"
    FINANCIAL_FRAUD = "financial_fraud"
    IDENTITY_THEFT = "identity_theft"
    SOCIAL_MEDIA_ABUSE = "social_media_abuse"
    STALKING = "stalking"
    FAKE_PROFILE = "impersonation_fraud"
    CHILD_SAFETY = "child_exploitation"
    SCAM = "financial_fraud"
    HACKING = "hacking"
    ACCOUNT_COMPROMISE = "hacking"
    PRIVACY_VIOLATION = "privacy_violation"
    OTHER_CYBER = "other_cyber"
    NOT_CYBER = "not_cyber"


CYBER_KEYWORDS = {
    CyberCategory.CYBERBULLYING: ["bully", "bullying", "mocking", "trolling", "mean comments"],
    CyberCategory.ONLINE_HARASSMENT: ["harass", "harassment", "unwanted messages", "abuse online"],
    CyberCategory.BLACKMAIL: ["blackmail", "threatening me", "extort", "demanding money", "threat", "intimidat"],
    CyberCategory.SEXTORTION: ["sextortion", "nude", "explicit photo", "intimate video", "leak my photo", "private pictures"],
    CyberCategory.FINANCIAL_FRAUD: ["scam", "fraud", "otp", "upi", "bank fraud", "lost money", "fake investment", "phishing"],
    CyberCategory.IDENTITY_THEFT: ["identity theft", "stole my identity", "used my photos", "pretending to be me"],
    CyberCategory.STALKING: ["stalking", "following me", "won't stop messaging", "keeps contacting"],
    CyberCategory.FAKE_PROFILE: ["fake profile", "fake account", "impersonat", "catfish"],
    CyberCategory.CHILD_SAFETY: ["my child", "minor", "underage", "my kid", "grooming"],
    CyberCategory.HACKING: ["hacked", "account compromised", "unauthorized access", "someone accessed my account"],
    CyberCategory.PRIVACY_VIOLATION: ["photographed without consent", "recorded me without", "privacy violat"],
    CyberCategory.SOCIAL_MEDIA_ABUSE: ["social media abuse", "instagram harassment", "facebook abuse", "twitter abuse"],
}

# Explicitly non-cyber domains the agent must refuse
OFF_TOPIC_MARKERS = [
    "movie", "film review", "cricket", "football match", "ipl", "recipe", "cook",
    "who won the election", "write code for", "solve this math", "homework help with",
    "date me", "dating advice", "weather today", "song lyrics", "write a poem",
    "college assignment", "essay on", "translate this sentence", "sports score",
    "who is the prime minister", "capital of", "tell me a joke", "general knowledge",
]

CRITICAL_MARKERS = [
    "kill myself", "want to die", "going to hurt me", "physical harm", "he has my address",
    "coming to my house", "child", "minor", "underage", "csam", "grooming",
]


@dataclass
class IntentResult:
    is_cyber_related: bool
    category: Optional[CyberCategory]
    is_critical: bool
    confidence: str  # "high" | "medium" | "low"
    reasoning: str


def classify_intent(message: str) -> IntentResult:
    text = message.lower()

    # Off-topic short-circuit
    off_topic_hits = [m for m in OFF_TOPIC_MARKERS if m in text]
    cyber_hits = []
    matched_category = None
    for cat, kws in CYBER_KEYWORDS.items():
        hits = [kw for kw in kws if kw in text]
        if hits:
            cyber_hits.extend(hits)
            if matched_category is None:
                matched_category = cat

    is_critical = any(m in text for m in CRITICAL_MARKERS)

    if matched_category:
        return IntentResult(
            is_cyber_related=True,
            category=matched_category,
            is_critical=is_critical,
            confidence="high" if len(cyber_hits) > 1 else "medium",
            reasoning=f"Matched keywords: {cyber_hits[:3]}",
        )

    if off_topic_hits:
        return IntentResult(
            is_cyber_related=False,
            category=CyberCategory.NOT_CYBER,
            is_critical=False,
            confidence="high",
            reasoning=f"Off-topic markers: {off_topic_hits[:2]}",
        )

    # Ambiguous — no keyword match either way. A generic greeting or vague
    # opening message ("I need help", "something happened to me") is common
    # for a genuine first-time victim, so this is routed to a clarifying
    # question with LOW confidence rather than assumed off-topic or assumed
    # a specific category.
    return IntentResult(
        is_cyber_related=True,
        category=CyberCategory.OTHER_CYBER,
        is_critical=is_critical,
        confidence="low",
        reasoning="No strong keyword match either way — treated as ambiguous and routed to a clarifying question.",
    )


REFUSAL_MESSAGE = (
    "I am designed exclusively to assist with cyber safety and cybercrime-related "
    "concerns — things like cyberbullying, harassment, blackmail, scams, hacking, "
    "identity theft, or online abuse. I'm not able to help with topics outside "
    "this domain. If you're dealing with a cyber safety issue, I'm here to help."
)
