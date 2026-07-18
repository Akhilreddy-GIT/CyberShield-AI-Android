"""
Smart Notification Guardian — scam detection for Android notifications.

Same auditable-regex philosophy as risk_engine.py and the rest of this
codebase: every category/score is traceable to a specific matched signal
in the notification's own title/text, not an opaque model judgment. This
matters here more than almost anywhere else in the system, because this
engine's output can tell a user "this is dangerous, do not tap it" —
that call has to be explainable and auditable, and must keep working with
zero LLM cost if GROQ_API_KEY isn't set (same fallback contract as
agent_service.py).

Entry point: analyze_notification(...) -> NotificationAssessment.

The LLM (via guardian_llm_service) is used ONLY to turn the already-decided
category/score/signals into a short natural-language explanation and
recommendation — never to decide the category or score itself. If the LLM
is unavailable, a deterministic template fallback produces the same fields
from the triggered signal labels directly.
"""

import re
import logging
from dataclasses import dataclass, field
from typing import List, Optional

logger = logging.getLogger(__name__)


def _rx(*fragments: str) -> "re.Pattern":
    return re.compile("|".join(fragments), re.IGNORECASE)


# ---------------------------------------------------------------------------
# Category detectors. Each: (category, threat_type, points, detector_fn).
# Ordered roughly by severity; multiple categories can fire on one
# notification (e.g. urgency manipulation + fake bank alert), and the
# highest-scoring category becomes the primary notification_category.
# ---------------------------------------------------------------------------

_OTP_RE = _rx(
    r"\botp\b.{0,30}\b(?:share|do not share|expire|valid for)\b",
    r"one[\s-]?time password", r"verification code", r"\bcode is\b.{0,10}\d{4,6}",
    r"\botp\b.{0,15}\d{4,6}", r"\byour otp\b", r"do not share (?:this|your) otp",
)
_BANK_ALERT_RE = _rx(
    r"account (?:will be |has been )?(?:suspended|blocked|frozen|deactivated)",
    r"kyc (?:update|expire|verify|pending)", r"unusual activity (?:on|detected in) your account",
    r"your account (?:has been|will be) (?:limited|restricted)", r"update your (?:kyc|bank details|pan)",
    r"debit card (?:will be |has been )?(?:blocked|suspended)", r"account (?:number )?(?:ending|xx+)\d+.{0,20}(?:debited|credited)",
    r"click (?:here|the link) to (?:unblock|reactivate|verify) your account",
)
_KYC_RE = _rx(
    r"\bkyc\b.{0,20}\b(?:update|expire|pending|verify|re-?kyc)\b",
    r"\b(?:update|verify|complete)\w*\b.{0,20}\bkyc\b",
    r"kyc (?:will )?expire\w* (?:today|tomorrow|in \d+ days?)",
)
_COURIER_RE = _rx(
    r"(?:parcel|package|courier|shipment).{0,25}\b(?:held|pending|customs|failed|delivery)\b",
    r"pay.{0,15}(?:customs|duty|clearance) fee", r"reschedule your delivery",
    r"(?:fedex|dhl|bluedart|delhivery|india post).{0,25}(?:pending|hold|customs|action required)",
)
_INVESTMENT_RE = _rx(
    r"guaranteed returns", r"double your (?:money|investment)", r"\btrading (?:tips|signals|opportunity)\b",
    r"invest (?:now|today) and earn", r"crypto (?:opportunity|investment|giveaway)",
    r"join (?:our|this) (?:telegram|whatsapp) (?:group|channel) for (?:trading|profits?|stock tips)",
)
_LOAN_RE = _rx(r"instant loan approved", r"pre-?approved loan", r"loan (?:disbursed|sanctioned)", r"low interest instant loan", r"loan without documents")
_CUSTOMER_CARE_RE = _rx(r"customer care", r"call (?:this|the) (?:number|helpline)", r"toll[\s-]?free", r"support team will contact")
_GIFT_LOTTERY_RE = _rx(
    r"you (?:have )?won", r"claim your (?:prize|reward|gift)", r"congratulations.{0,20}(?:selected|winner)",
    r"\blucky draw\b", r"\blottery\b", r"free (?:gift|voucher|coupon)", r"kbc lottery", r"prize money",
)
_GOVT_RE = _rx(
    r"income tax (?:refund|notice|department)", r"\baadhaar\b.{0,20}(?:link|update|suspend|verify)",
    r"\buidai\b", r"pan (?:card )?(?:link|update|deactivat)", r"government (?:scheme|subsidy|benefit) approved",
)
_CRED_HARVEST_RE = _rx(
    r"verify your (?:account|identity|details)", r"confirm your (?:password|pin|details)",
    r"login to (?:secure|verify|update) your account", r"click (?:here|below) to verify",
    r"\bclick\w*\b.{0,25}\bverify\b", r"\bverify\b.{0,25}\bclick\w*\b",
    r"update (?:your )?payment (?:details|method)", r"suspicious (?:sign-?in|login) attempt",
)
_URGENCY_RE = _rx(
    r"\bact now\b", r"\bimmediately\b", r"\burgent(?:ly)?\b", r"expires? (?:today|in \d+ (?:minutes?|hours?))",
    r"within \d+ (?:minutes?|hours?)", r"last chance", r"limited time", r"final (?:notice|reminder|warning)",
)
_FEAR_RE = _rx(
    r"legal action will be taken", r"you (?:will be|are) (?:arrested|prosecuted)", r"case (?:has been|will be) filed against you",
    r"account (?:permanently )?(?:closed|terminated|blocked) if",
)
_REMOTE_ACCESS_RE = _rx(r"\banydesk\b", r"\bteamviewer\b", r"install this app to (?:fix|resolve|verify)", r"screen share")
_SOCIAL_DM_RE = _rx(r"(?:whatsapp|telegram|instagram|facebook).{0,25}(?:job offer|earn from home|work from home)")
_EMPLOYMENT_RE = _rx(r"work from home.{0,20}earn", r"part[\s-]?time job.{0,20}(?:daily|per day) payment", r"hiring.{0,15}no experience.{0,15}high (?:salary|pay)")
_DEEPFAKE_RE = _rx(r"\bai[\s-]?generated voice\b", r"voice (?:clone|cloning)", r"\bdeepfake\b")

_CATEGORY_DETECTORS = [
    ("OTP Scam", "credential_harvesting", 8, _OTP_RE),
    ("Fake Bank Alert", "account_takeover_attempt", 8, _BANK_ALERT_RE),
    ("Fake KYC", "credential_harvesting", 7, _KYC_RE),
    ("Fake Courier Scam", "advance_fee_fraud", 6, _COURIER_RE),
    ("Investment Scam", "financial_fraud", 7, _INVESTMENT_RE),
    ("Loan Scam", "financial_fraud", 6, _LOAN_RE),
    ("Customer Care Scam", "social_engineering", 5, _CUSTOMER_CARE_RE),
    ("Gift/Lottery Scam", "advance_fee_fraud", 6, _GIFT_LOTTERY_RE),
    ("Fake Government Message", "impersonation", 8, _GOVT_RE),
    ("Credential Harvesting", "credential_harvesting", 8, _CRED_HARVEST_RE),
    ("Remote Access Scam", "account_takeover_attempt", 9, _REMOTE_ACCESS_RE),
    ("Employment Scam", "financial_fraud", 5, _EMPLOYMENT_RE),
    ("Social Engineering DM", "social_engineering", 5, _SOCIAL_DM_RE),
    ("Deepfake/AI Voice Scam", "impersonation", 7, _DEEPFAKE_RE),
]

# Manipulation-pattern modifiers — these don't set the category themselves,
# but add to the score and are reported as separate triggered signals.
_MODIFIER_DETECTORS = [
    ("Urgency manipulation", 3, _URGENCY_RE),
    ("Fear tactics / threat of legal action", 4, _FEAR_RE),
]

_APP_CATEGORY_HINTS = {
    "whatsapp": "Suspicious WhatsApp Message",
    "telegram": "Telegram Scam",
    "instagram": "Instagram Scam",
    "facebook": "Facebook Scam",
    "messenger": "Facebook Scam",
}

_SEVERITY_BANDS = [
    (75, "critical"),
    (55, "high"),
    (30, "medium"),
    (10, "low"),
    (0, "info"),
]


@dataclass
class NotificationAssessment:
    notification_category: str
    threat_type: Optional[str]
    risk_score: int
    confidence_score: int
    severity: str
    is_interaction_dangerous: bool
    triggered_signals: List[str] = field(default_factory=list)
    explanation: str = ""
    recommendation: str = ""


def _severity_for_score(score: int) -> str:
    for threshold, label in _SEVERITY_BANDS:
        if score >= threshold:
            return label
    return "info"


def analyze_notification(
    app_name: Optional[str],
    notification_title: Optional[str],
    notification_text: Optional[str],
) -> NotificationAssessment:
    """Pure rule-based scoring — never raises. Returns a benign/info-level
    result when nothing matches, rather than forcing a category."""
    combined = f"{notification_title or ''} {notification_text or ''}".strip()
    app_lower = (app_name or "").lower()

    if not combined:
        return NotificationAssessment(
            notification_category="Uncategorized",
            threat_type=None,
            risk_score=0,
            confidence_score=0,
            severity="info",
            is_interaction_dangerous=False,
            triggered_signals=[],
            explanation="No notification content was provided to analyze.",
            recommendation="No action needed.",
        )

    try:
        matched: List[tuple] = []  # (category, threat_type, points)
        signals: List[str] = []

        for category, threat_type, points, rx in _CATEGORY_DETECTORS:
            if rx.search(combined):
                matched.append((category, threat_type, points))
                signals.append(category)

        score = sum(p for _, _, p in matched)

        for label, points, rx in _MODIFIER_DETECTORS:
            if rx.search(combined):
                score += points
                signals.append(label)

        # App-source hint: a platform-specific scam pattern on its native
        # app (e.g. a scam link inside WhatsApp itself) gets folded into
        # the category label rather than treated as a separate detector.
        app_hint_category = next((v for k, v in _APP_CATEGORY_HINTS.items() if k in app_lower), None)
        if app_hint_category and matched:
            signals.append(f"Delivered via {app_name}")
            score += 1

        if not matched:
            return NotificationAssessment(
                notification_category=app_hint_category or "Uncategorized",
                threat_type=None,
                risk_score=min(score, 100),
                confidence_score=40 if score else 10,
                severity=_severity_for_score(score),
                is_interaction_dangerous=False,
                triggered_signals=signals,
                explanation="No known scam pattern was detected in this notification's text.",
                recommendation="No specific action needed — general caution still applies with unfamiliar senders.",
            )

        matched.sort(key=lambda m: m[2], reverse=True)
        primary_category, primary_threat, _ = matched[0]
        if app_hint_category and app_hint_category not in (primary_category,):
            # Keep the specific scam category primary (more actionable than
            # just "Suspicious WhatsApp Message"), platform noted as a signal.
            pass

        score = min(score, 100)
        # Confidence rises with number of independent corroborating signals,
        # capped so a single weak match never reads as near-certain.
        confidence = min(30 + len(matched) * 15 + (10 if len(signals) > len(matched) else 0), 96)
        severity = _severity_for_score(score)
        dangerous = severity in ("high", "critical") or any(
            t in ("credential_harvesting", "account_takeover_attempt") for _, t, _ in matched
        )

        return NotificationAssessment(
            notification_category=primary_category,
            threat_type=primary_threat,
            risk_score=score,
            confidence_score=confidence,
            severity=severity,
            is_interaction_dangerous=dangerous,
            triggered_signals=sorted(set(signals)),
            explanation="",  # filled in by guardian_llm_service / template fallback
            recommendation="",
        )
    except Exception:
        logger.exception("Notification analysis failed; returning safe default.")
        return NotificationAssessment(
            notification_category="Uncategorized",
            threat_type=None,
            risk_score=0,
            confidence_score=0,
            severity="info",
            is_interaction_dangerous=False,
            triggered_signals=[],
            explanation="Analysis failed; treat with normal caution.",
            recommendation="No automated recommendation available.",
        )
