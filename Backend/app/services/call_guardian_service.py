"""
Call Guardian Intelligence Engine — scam detection for reported phone calls.

Same auditable-regex philosophy as notification_guardian_service.py and
risk_engine.py. Critical design constraint from spec: an unknown caller
number is NEVER, by itself, evidence of a scam. Classification depends
entirely on conversation behaviour/content the user reports (user_notes,
conversation_summary, follow-up answers) — is_unknown_number is stored as
metadata only and never contributes to risk_score or scam_category.

Entry point: analyze_call(...) -> CallAssessment.
"""

import re
import logging
from dataclasses import dataclass, field
from typing import List, Optional

logger = logging.getLogger(__name__)


def _rx(*fragments: str) -> "re.Pattern":
    return re.compile("|".join(fragments), re.IGNORECASE)


# ---------------------------------------------------------------------------
# Category detectors — fire on reported call CONTENT (user_notes +
# conversation_summary + follow-up answers), never on the number alone.
# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------
# Category detectors — fire on reported call CONTENT (user_notes +
# conversation_summary + follow-up answers), never on the number alone.
# Patterns are deliberately loose on word order/tense (verb-flexible, not
# rigid exact phrases) to match how victims actually describe a call —
# often terse, in Indian-English/Hinglish phrasing, or third-person
# retellings rather than the literal script the scammer used.
# ---------------------------------------------------------------------------
_BANK_IMPERSONATION_RE = _rx(
    r"calling from.{0,15}bank", r"claim(?:ed|ing)? to be.{0,15}bank", r"bank official",
    r"your (?:card|account) (?:will be|has been) blocked", r"from (?:the )?bank.{0,20}(?:said|told|asked)",
    r"bank (?:executive|manager|representative)", r"(?:said|told me)\b.{0,20}\bfrom (?:sbi|hdfc|icici|axis|pnb|bank)\b",
    r"account (?:will|is going to) be blocked",
)
_UPI_SCAM_RE = _rx(
    r"\bupi\b.{0,20}(?:pin|verify|collect request)", r"accept(?:ed)? a (?:upi )?collect request",
    r"scan (?:this|the) qr to receive", r"sent (?:me )?a (?:payment |collect )?request.{0,20}(?:accept|approve)",
    r"(?:gpay|phonepe|paytm).{0,20}(?:collect request|refund|verify|pin)",
    r"upi pin.{0,20}(?:asked|share|enter)",
)
_POLICE_IMPERSONATION_RE = _rx(
    r"claim(?:ed|ing)? to be.{0,15}police", r"calling from.{0,15}(?:police|cyber cell|cbi|customs|narcotics|enforcement directorate|ed\b)",
    r"digital arrest", r"case (?:has been )?filed against you", r"warrant (?:issued|out) (?:in|against) your name",
    r"video call.{0,20}(?:police|uniform|arrest)", r"parcel.{0,20}(?:drugs|illegal).{0,20}(?:police|cbi|customs)",
    r"stay on (?:the )?(?:video|call) (?:or|until) (?:you will be |you'll be )?arrest",
)
_INCOME_TAX_RE = _rx(r"income tax (?:department|officer|notice)", r"tax (?:refund|evasion) (?:call|notice)", r"\bitr\b.{0,20}(?:pending|refund|notice)")
_COURIER_CALL_RE = _rx(
    r"(?:parcel|courier|package).{0,20}(?:illegal|drugs|held by customs)", r"fedex|dhl|bluedart|delhivery",
    r"customs (?:duty|clearance) (?:call|payment)", r"parcel.{0,20}(?:your name|your aadhaar).{0,20}(?:seized|held)",
)
_SIM_SWAP_CALL_RE = _rx(
    r"sim (?:card )?(?:will be|is being) (?:deactivat|swap|block)", r"sim upgrade", r"port your (?:sim|number)",
    r"press \d.{0,20}(?:sim|network)", r"upgrade.{0,15}4g.{0,15}5g.{0,15}sim",
)
_INSURANCE_RE = _rx(r"insurance (?:policy|premium|bonus).{0,20}(?:matur|claim|refund)", r"policy (?:bonus|refund) (?:call|due)", r"lic\b.{0,20}(?:bonus|refund|matured)")
_JOB_SCAM_RE = _rx(
    r"job offer.{0,20}(?:advance|registration) (?:fee|payment)", r"selected for (?:a |the )?job.{0,15}pay",
    r"work from home.{0,20}(?:pay|fee|deposit)", r"part[\s-]?time job.{0,20}(?:daily|whatsapp)",
    r"job.{0,30}(?:registration|advance|processing) fee", r"registration fee.{0,30}job",
)
_LOAN_CALL_RE = _rx(r"loan (?:approved|disbursed).{0,20}(?:processing fee|advance)", r"instant loan.{0,20}pay first", r"pre-?approved loan.{0,20}(?:fee|advance)")
_INVESTMENT_CALL_RE = _rx(
    r"guaranteed (?:profit|returns)", r"trading (?:group|tips|advisor)", r"invest.{0,15}double",
    r"telegram (?:group|channel).{0,20}(?:trading|invest|profit)", r"crypto.{0,20}(?:double|guaranteed|profit)",
)
_ROMANCE_SCAM_RE = _rx(r"met (?:him|her|them) online", r"asked (?:me )?for money.{0,20}(?:relationship|love|dating)", r"never met in person", r"online (?:boyfriend|girlfriend).{0,20}(?:money|stuck|emergency)")
_TECH_SUPPORT_RE = _rx(r"microsoft support", r"your computer (?:has|is) a virus", r"remote access.{0,20}(?:fix|resolve) your (?:computer|device)", r"anydesk|teamviewer|quick support")
_FAMILY_EMERGENCY_RE = _rx(r"claim(?:ed|ing)? to be my (?:son|daughter|relative|husband|wife)", r"emergency.{0,15}send money (?:immediately|right now)", r"(?:accident|hospital|jail).{0,20}send money (?:now|urgently)")
_EXTORTION_RE = _rx(r"threaten(?:ed|ing) (?:me|to)", r"pay or (?:i will|they will)", r"demanding money")
_BLACKMAIL_CALL_RE = _rx(r"\bblackmail", r"threatened to (?:leak|post|expose)")

_CATEGORY_DETECTORS = [
    ("Bank Impersonation", "impersonation", 8, _BANK_IMPERSONATION_RE),
    ("UPI Scam", "financial_fraud", 8, _UPI_SCAM_RE),
    ("Police Impersonation", "impersonation", 9, _POLICE_IMPERSONATION_RE),
    ("Income Tax Scam", "impersonation", 7, _INCOME_TAX_RE),
    ("Courier Scam", "advance_fee_fraud", 6, _COURIER_CALL_RE),
    ("SIM Swap", "account_takeover_attempt", 8, _SIM_SWAP_CALL_RE),
    ("Insurance Scam", "financial_fraud", 5, _INSURANCE_RE),
    ("Job Scam", "financial_fraud", 5, _JOB_SCAM_RE),
    ("Loan Scam", "financial_fraud", 5, _LOAN_CALL_RE),
    ("Investment Scam", "financial_fraud", 6, _INVESTMENT_CALL_RE),
    ("Romance Scam", "social_engineering", 6, _ROMANCE_SCAM_RE),
    ("Tech Support Scam", "account_takeover_attempt", 7, _TECH_SUPPORT_RE),
    ("Family Emergency Scam", "social_engineering", 7, _FAMILY_EMERGENCY_RE),
    ("Extortion", "threat_of_harm", 8, _EXTORTION_RE),
    ("Blackmail", "threat_of_harm", 8, _BLACKMAIL_CALL_RE),
]

# Psychological manipulation tactics — reported alongside category, not
# used to pick it.
_URGENCY_TACTIC_RE = _rx(r"\burgent(?:ly)?\b", r"act (?:now|immediately)", r"within (?:the next )?\d+ (?:minutes?|hours?)")
_FEAR_TACTIC_RE = _rx(r"threaten\w*", r"arrest", r"legal action", r"account (?:will be )?block\w*")
_AUTHORITY_TACTIC_RE = _rx(r"claim(?:ed|ing)? to be (?:an? )?(?:officer|official|agent|representative)")
_SECRECY_TACTIC_RE = _rx(r"told me not to tell", r"keep this (?:confidential|secret)", r"don'?t (?:tell|inform) (?:anyone|your family|the bank)")
_ISOLATION_TACTIC_RE = _rx(r"stay on the (?:phone|line)", r"don'?t hang up", r"kept me on the (?:phone|call)")

_TACTIC_DETECTORS = [
    ("Urgency manipulation", _URGENCY_TACTIC_RE),
    ("Fear tactics / threat framing", _FEAR_TACTIC_RE),
    ("False authority claim", _AUTHORITY_TACTIC_RE),
    ("Secrecy pressure (told not to tell anyone)", _SECRECY_TACTIC_RE),
    ("Isolation tactic (kept on call/line)", _ISOLATION_TACTIC_RE),
]

_OTP_REQUESTED_RE = _rx(r"asked (?:me )?for (?:the |my )?otp", r"requested (?:the |my )?otp")
_MONEY_MOVED_RE = _rx(r"(?:i |we )?(?:transferred|sent|paid)\b.{0,25}(?:money|amount|rs\.?|₹)", r"money (?:was |got )?deducted")

_RECOVERY_BY_CATEGORY = {
    "Bank Impersonation": ["Call your bank's official number (from the card/passbook, not any number given on the call) to verify.", "Do not share OTP, PIN, or CVV with anyone claiming to be from the bank.", "If any transaction happened, ask your bank to freeze the account immediately."],
    "UPI Scam": ["Never approve a UPI collect request or scan a QR code to 'receive' money — those actions send money, not receive it.", "Report the UPI transaction ID to your bank and via cybercrime.gov.in."],
    "Police Impersonation": ["Real police/CBI/customs do not conduct 'digital arrest' or demand payment over a call — hang up.", "Verify independently by calling the local police station's published number.", "Do not transfer any money under threat of arrest."],
    "Income Tax Scam": ["The Income Tax Department does not call demanding immediate payment — verify via the official incometax.gov.in portal.", "Do not share PAN or bank details over the call."],
    "Courier Scam": ["Legitimate courier/customs holds are resolved via the courier company's official app or website, not a phone payment.", "Do not pay any 'clearance fee' requested by phone."],
    "SIM Swap": ["Contact your telecom provider immediately through their official app/store, not a number given on the call.", "Watch for sudden loss of signal — that can indicate an in-progress SIM swap."],
    "Insurance Scam": ["Verify any policy bonus/refund claim directly with the insurer via their official contact details."],
    "Job Scam": ["Legitimate employers do not ask for payment to confirm a job offer.", "Verify the company independently before sharing any documents."],
    "Loan Scam": ["Do not pay an upfront 'processing fee' for a loan — legitimate lenders deduct fees from the disbursed amount, not before."],
    "Investment Scam": ["Be skeptical of any 'guaranteed returns' claim — all real investments carry risk.", "Verify any advisor's SEBI registration before investing."],
    "Romance Scam": ["Be cautious of any online relationship that moves quickly toward requests for money.", "Never send money to someone you have not met in person and cannot verify."],
    "Tech Support Scam": ["Microsoft/Google/Apple do not call you proactively about a virus on your device.", "Do not install remote-access software at a caller's request; if already installed, uninstall it and disconnect from the internet."],
    "Family Emergency Scam": ["Verify by calling the family member directly on their known number before sending any money."],
    "Extortion": ["Do not pay. Preserve all messages/call logs as evidence.", "Report to cybercrime.gov.in or call 1930 immediately."],
    "Blackmail": ["Do not pay or engage further. Preserve all evidence.", "Report immediately via cybercrime.gov.in or 1930."],
}

_AUTHORITIES_BY_THREAT = {
    "financial_fraud": ["1930 (National Cyber Crime Helpline)", "cybercrime.gov.in", "Your bank's fraud department"],
    "impersonation": ["cybercrime.gov.in", "Local police station"],
    "account_takeover_attempt": ["1930 (National Cyber Crime Helpline)", "Your bank / telecom provider"],
    "social_engineering": ["cybercrime.gov.in"],
    "threat_of_harm": ["112 (Police Emergency)", "cybercrime.gov.in", "1930"],
}


@dataclass
class CallAssessment:
    scam_category: Optional[str]
    threat_type: Optional[str]
    risk_score: int
    confidence_score: int
    urgency_level: str
    is_unknown_number: bool
    possible_impersonation: Optional[str]
    manipulation_tactics: List[str] = field(default_factory=list)
    recovery_recommendations: List[str] = field(default_factory=list)
    authorities_to_contact: List[str] = field(default_factory=list)
    triggered_signals: List[str] = field(default_factory=list)
    explanation: str = ""


def analyze_call(
    caller_number: Optional[str],
    contact_name: Optional[str],
    conversation_summary: Optional[str],
    user_notes: Optional[str],
    follow_up_answers: Optional[List[str]] = None,
) -> CallAssessment:
    """Pure rule-based scoring. An unknown/unsaved number is recorded as
    metadata (is_unknown_number) but NEVER contributes to risk_score or
    scam_category — classification depends only on reported conversation
    content, per spec Feature 2. Never raises."""
    is_unknown = not bool(contact_name)
    combined = " ".join(
        [conversation_summary or "", user_notes or ""] + (follow_up_answers or [])
    ).strip()

    if not combined:
        return CallAssessment(
            scam_category=None,
            threat_type=None,
            risk_score=0,
            confidence_score=0,
            urgency_level="low",
            is_unknown_number=is_unknown,
            possible_impersonation=None,
            manipulation_tactics=[],
            recovery_recommendations=[],
            authorities_to_contact=[],
            triggered_signals=[],
            explanation="No conversation content was provided — treated as UNKNOWN, not automatically classified as a scam.",
        )

    try:
        matched: List[tuple] = []
        for category, threat_type, points, rx in _CATEGORY_DETECTORS:
            if rx.search(combined):
                matched.append((category, threat_type, points))

        tactics = [label for label, rx in _TACTIC_DETECTORS if rx.search(combined)]
        score = sum(p for _, _, p in matched) + len(tactics) * 2

        if _OTP_REQUESTED_RE.search(combined):
            score += 5
            tactics.append("OTP requested during call")
        if _MONEY_MOVED_RE.search(combined):
            score += 6
            tactics.append("Money movement reported during/after call")

        score = min(score, 100)

        if not matched:
            return CallAssessment(
                scam_category=None,
                threat_type=None,
                risk_score=score,
                confidence_score=15 if score else 0,
                urgency_level="low" if score < 10 else "medium",
                is_unknown_number=is_unknown,
                possible_impersonation=None,
                manipulation_tactics=sorted(set(tactics)),
                recovery_recommendations=[],
                authorities_to_contact=[],
                triggered_signals=sorted(set(tactics)),
                explanation="No known scam category matched the reported conversation. This does not mean the call was safe — only that it doesn't match a recognized pattern.",
            )

        matched.sort(key=lambda m: m[2], reverse=True)
        primary_category, primary_threat, _ = matched[0]
        confidence = min(35 + len(matched) * 15 + len(tactics) * 5, 96)
        urgency = "high" if score >= 25 or "Urgency manipulation" in tactics else ("medium" if score >= 10 else "low")

        recovery = _RECOVERY_BY_CATEGORY.get(primary_category, [
            "Do not share OTP, PIN, passwords, or make any payment based on this call.",
            "Verify independently using an official, publicly listed number.",
        ])
        authorities = _AUTHORITIES_BY_THREAT.get(primary_threat, ["cybercrime.gov.in", "1930 (National Cyber Crime Helpline)"])

        impersonation = primary_category if primary_threat == "impersonation" else None

        signals = [c for c, _, _ in matched] + tactics
        return CallAssessment(
            scam_category=primary_category,
            threat_type=primary_threat,
            risk_score=score,
            confidence_score=confidence,
            urgency_level=urgency,
            is_unknown_number=is_unknown,
            possible_impersonation=impersonation,
            manipulation_tactics=sorted(set(tactics)),
            recovery_recommendations=recovery,
            authorities_to_contact=authorities,
            triggered_signals=sorted(set(signals)),
            explanation="",  # filled in by guardian_llm_service / template fallback
        )
    except Exception:
        logger.exception("Call analysis failed; returning safe UNKNOWN default.")
        return CallAssessment(
            scam_category=None,
            threat_type=None,
            risk_score=0,
            confidence_score=0,
            urgency_level="low",
            is_unknown_number=is_unknown,
            possible_impersonation=None,
            explanation="Analysis failed; treated as UNKNOWN rather than guessing.",
        )
