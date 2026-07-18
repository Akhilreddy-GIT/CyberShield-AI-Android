"""
Risk Assessment Engine.

Rule-based scoring so it's transparent, auditable, and works without an LLM
API key. Each signal adds points; thresholds map to a risk band.
This is deliberately explainable (not a black-box LLM judgment) because a
risk level here can direct someone toward emergency services — it must be
possible to show *why* a case was scored the way it was.

Two entry points:
  - assess_risk(RiskFactors) — the ORIGINAL explicit-form scoring, used by
    POST /api/cases/assess-risk. UNCHANGED contract; the Android app's
    existing form flow keeps working exactly as before.
  - auto_assess_risk_from_text(...) — NEW: derives risk automatically from
    the conversation text itself (money lost, OTP/password/Aadhaar shared,
    SIM swap, ransomware, sextortion, suicidal statements, time since
    incident, etc), so every chat turn gets a real risk signal instead of
    defaulting to LOW when no one has filled in the manual form yet. Used
    internally by the chat router; does not replace or alter the manual
    assessment endpoint.
"""

import re
import logging
from dataclasses import dataclass, field
from typing import List, Optional
from enum import Enum

logger = logging.getLogger(__name__)


class RiskLevel(str, Enum):
    LOW = "Low Risk"
    MEDIUM = "Medium Risk"
    HIGH = "High Risk"
    CRITICAL = "Critical Emergency"


@dataclass
class RiskFactors:
    is_repeated_incident: bool = False
    involves_threat_of_harm: bool = False
    involves_minor: bool = False
    involves_financial_loss: bool = False
    involves_explicit_content: bool = False
    accused_knows_victim_location: bool = False
    ongoing_blackmail: bool = False
    victim_reports_feeling_unsafe: bool = False
    has_evidence: bool = False


@dataclass
class RiskAssessment:
    level: RiskLevel
    score: int
    triggered_factors: List[str] = field(default_factory=list)
    explanation: str = ""


# ---------------------------------------------------------------------------
# SINGLE SOURCE OF TRUTH for risk-level ordering/escalation.
#
# Every caller that needs to compare or escalate a case's stored risk_level
# (chat.py, crisis.py, guardian.py, ...) must use these instead of
# redefining its own {"Low Risk": 0, ...} dict. Previously this mapping was
# copy-pasted in three different routers; a typo in one copy would silently
# break escalation logic differently in each place. Case risk is
# escalate-only by product design (de-escalation is a deliberate action,
# never an automatic side effect of a new signal), so "is this new
# level/score an escalation over the stored one" is the one comparison
# every caller actually needs.
# ---------------------------------------------------------------------------
RISK_LEVEL_ORDER = {
    RiskLevel.LOW.value: 0,
    RiskLevel.MEDIUM.value: 1,
    RiskLevel.HIGH.value: 2,
    RiskLevel.CRITICAL.value: 3,
}


def risk_rank(level: Optional[str]) -> int:
    """Numeric rank for a stored risk_level string. Unknown/None -> 0 (Low)."""
    return RISK_LEVEL_ORDER.get(level or RiskLevel.LOW.value, 0)


def is_escalation(current_level: Optional[str], candidate_level: Optional[str]) -> bool:
    """True if candidate_level is strictly higher tier than current_level."""
    return risk_rank(candidate_level) > risk_rank(current_level)


def assess_risk(factors: RiskFactors) -> RiskAssessment:
    """ORIGINAL explicit-form risk scoring. Contract unchanged — do not
    modify without checking the Android /api/cases/assess-risk flow."""
    score = 0
    triggered = []

    # Critical-tier signals - any ONE of these alone should push toward critical
    if factors.involves_minor:
        score += 10
        triggered.append("Case involves a minor")
    if factors.involves_threat_of_harm:
        score += 8
        triggered.append("Credible threat of physical harm")
    if factors.accused_knows_victim_location:
        score += 6
        triggered.append("Accused has knowledge of victim's physical location")
    if factors.victim_reports_feeling_unsafe:
        score += 5
        triggered.append("Victim reports feeling immediately unsafe")

    # High-tier signals
    if factors.ongoing_blackmail:
        score += 5
        triggered.append("Ongoing blackmail/extortion")
    if factors.involves_explicit_content:
        score += 5
        triggered.append("Involves non-consensual explicit content")
    if factors.is_repeated_incident:
        score += 3
        triggered.append("Repeated/pattern of incidents")

    # Medium-tier signals
    if factors.involves_financial_loss:
        score += 3
        triggered.append("Financial loss occurred")
    if not factors.has_evidence:
        score += 1
        triggered.append("No evidence collected yet (increases uncertainty)")

    if score >= 10:
        level = RiskLevel.CRITICAL
    elif score >= 7:
        level = RiskLevel.HIGH
    elif score >= 3:
        level = RiskLevel.MEDIUM
    else:
        level = RiskLevel.LOW

    explanation = (
        f"Risk score {score}/10+ based on {len(triggered)} factor(s). "
        f"This is an automated first-pass triage, not a substitute for professional judgment."
    )

    return RiskAssessment(level=level, score=score, triggered_factors=triggered, explanation=explanation)


# ---------------------------------------------------------------------------
# Automatic text-based risk scoring (REDESIGNED — factor-based, not rigid
# keyword substrings)
# ---------------------------------------------------------------------------
# Each signal is a (points, label, detector) tuple where detector(text) ->
# bool. Detectors use regex with flexible word order / verb tense instead of
# exact phrases, so "I shared my OTP", "I ended up sharing my OTP", and "I
# gave them the OTP" all match the same underlying factor. This stays fully
# auditable (every detector is a plain, inspectable regex — no black-box
# model) while no longer breaking on ordinary phrasing variation. Points are
# calibrated so a single severe signal (child exploitation, suicidal
# statement, SIM swap + money gone) can reach HIGH/CRITICAL alone, while
# lower-severity signals accumulate toward MEDIUM.


def _rx(*fragments: str) -> "re.Pattern":
    """Builds a case-insensitive OR-pattern from regex fragments."""
    return re.compile("|".join(fragments), re.IGNORECASE)


# --- financial loss, amount-aware -------------------------------------------
_MONEY_VERB = r"(?:deduct|debit|lost|lose|gone|drain|withdraw|transfer|empt(?:y|ied)|stole|steal)"
_MONEY_CONTEXT_RE = re.compile(
    rf"\b{_MONEY_VERB}\w*\b.{{0,40}}\b(?:account|money|rs\.?|inr|₹|rupees|amount|bank)\b"
    rf"|\b(?:account|money|rs\.?|inr|₹|rupees|amount|bank)\b.{{0,40}}\b{_MONEY_VERB}\w*\b",
    re.IGNORECASE,
)
_LARGE_AMOUNT_RE = re.compile(r"(?:₹|rs\.?|inr)\s?([\d,]+)|([\d,]{4,})\s*(?:rupees|rs\.?)", re.IGNORECASE)


def _has_financial_loss(text: str) -> bool:
    return bool(_MONEY_CONTEXT_RE.search(text))


def _has_large_financial_loss(text: str) -> bool:
    """Escalates when a specific amount is mentioned and it's substantial
    (>= ₹10,000) alongside loss-context language — a real number changes
    the urgency versus a vague 'I lost some money'."""
    if not _has_financial_loss(text):
        return False
    for m in _LARGE_AMOUNT_RE.finditer(text):
        raw = (m.group(1) or m.group(2) or "").replace(",", "")
        if raw.isdigit() and int(raw) >= 10000:
            return True
    return False


# --- credential / OTP sharing (verb-flexible, not just exact phrase match) --
_OTP_SHARE_RE = _rx(
    r"\b(?:shar(?:e|ed|ing)|gave|give|told|sent|disclos\w*)\b.{0,25}\botp\b",
    r"\botp\b.{0,25}\b(?:shar(?:e|ed|ing)|gave|give|told|sent|disclos\w*)\b",
    r"\basked (?:me )?for (?:the |my )?otp\b",
)
_CRED_SHARE_RE = _rx(
    r"\b(?:shar(?:e|ed|ing)|gave|give|told|entered|enter\w*)\b.{0,25}\b(?:pin|password|cvv|card details|bank details)\b",
    r"\b(?:pin|password|cvv|card details|bank details)\b.{0,25}\b(?:shar(?:e|ed|ing)|gave|give|told|entered|enter\w*)\b",
)


def _has_otp_shared(text: str) -> bool:
    return bool(_OTP_SHARE_RE.search(text))


def _has_credentials_shared(text: str) -> bool:
    return bool(_CRED_SHARE_RE.search(text))


# --- account takeover / lockout ---------------------------------------------
_ACCOUNT_TAKEOVER_RE = _rx(
    r"\bhack(?:ed|ing)?\b", r"\block(?:ed)? out\b", r"can\'?t log ?in", r"cant log ?in",
    r"password (?:was |got )?changed", r"account (?:was |got )?(?:taken over|compromised)",
    r"(?:don\'?t|dont|no longer) have access",
)


def _has_account_takeover(text: str) -> bool:
    return bool(_ACCOUNT_TAKEOVER_RE.search(text))


# --- SIM swap ----------------------------------------------------------------
_SIM_SWAP_RE = _rx(
    r"\bsim\b.{0,20}\b(?:swap|stopped|deactivat\w*|blocked|not working)\b",
    r"\bno (?:network|signal) suddenly\b", r"\bduplicate sim\b",
)


def _has_sim_swap(text: str) -> bool:
    return bool(_SIM_SWAP_RE.search(text))


def _has_credential_share_with_loss(text: str) -> bool:
    """Combination signal per spec: 'UPI PIN shared + money lost' and 'OTP
    shared + money lost' are each independently Critical — the combination
    of a credential/OTP disclosure AND an actual financial loss in the same
    report, not just one or the other."""
    return (_has_otp_shared(text) or _has_credentials_shared(text)) and _has_financial_loss(text)


def _has_active_account_takeover_with_loss(text: str) -> bool:
    """Combination signal per spec: 'Active bank account takeover' means
    the account is compromised AND funds are actually affected — pairs
    _has_account_takeover with a financial-loss mention so a takeover
    report with no money impact yet stays a (still serious) High rather
    than automatically Critical."""
    return _has_account_takeover(text) and _has_financial_loss(text)


# --- ransomware ---------------------------------------------------------------
_RANSOMWARE_RE = _rx(r"\bransomware\b", r"files? (?:got |were )?encrypt\w*", r"\bransom (?:demand|note)\b")


def _has_ransomware(text: str) -> bool:
    return bool(_RANSOMWARE_RE.search(text))


# --- stalking / location knowledge -------------------------------------------
_STALKING_RE = _rx(
    r"knows? where i live", r"track(?:ing|ed)? my location", r"shows? up wherever i go",
    r"follow(?:ing|ed)? me", r"outside my (?:house|home)",
)


def _has_stalking(text: str) -> bool:
    return bool(_STALKING_RE.search(text))


# --- Aadhaar/PAN misuse -------------------------------------------------------
_ID_DOC_RE = _rx(
    r"\baadhaar\b.{0,25}\b(?:leak\w*|misus\w*|stolen|used)\b",
    r"\bpan\b.{0,25}\b(?:leak\w*|misus\w*|stolen|used|card misus\w*)\b",
    r"opened (?:a |an )?(?:loan |bank |credit card )?account (?:using|with) my (?:aadhaar|pan)",
    r"opened (?:a |an )?(?:loan |bank |credit card )?account in my name",
)


def _has_id_doc_leak(text: str) -> bool:
    return bool(_ID_DOC_RE.search(text))


# --- identity theft WITH active misuse (critical tier) ------------------------
# Distinguishes "someone made a fake profile with my photos" (concerning but
# not yet financially/legally damaging — stays medium via _has_identity_theft
# below) from identity theft that has already been ACTED ON: a loan/account
# opened in the victim's name, funds taken, or a legal/financial consequence
# already in motion. This is what the spec calls "Identity theft with
# misuse" and it must reach Critical on its own, not just accumulate toward
# Medium alongside the base identity-theft signal.
_IDENTITY_THEFT_MISUSE_RE = _rx(
    r"opened (?:a |an )?(?:loan |bank |credit card )?account (?:using|with) my (?:aadhaar|pan|identity|documents?)",
    r"opened (?:a |an )?(?:loan |bank |credit card )?account in my name",
    r"took (?:a |an )?loan (?:using|with|in) my (?:name|aadhaar|pan|identity)",
    r"loan (?:was |has been )?(?:taken|sanctioned) in my name",
    r"using my (?:identity|aadhaar|pan) to (?:take|get|apply for) (?:a )?loan",
    r"stole my identity.{0,40}(?:loan|account|credit|debt|money)",
)


def _has_identity_theft_with_misuse(text: str) -> bool:
    return bool(_IDENTITY_THEFT_MISUSE_RE.search(text))


# --- remote access app --------------------------------------------------------
_REMOTE_ACCESS_RE = _rx(r"\banydesk\b", r"\bteamviewer\b", r"remote access app", r"screen shar\w* app", r"quick support")


def _has_remote_access_app(text: str) -> bool:
    return bool(_REMOTE_ACCESS_RE.search(text))


# --- phishing link click -------------------------------------------------------
_LINK_CLICK_RE = _rx(r"click\w* (?:on )?(?:a |the |that )?link", r"fake link", r"phishing link", r"got a (?:link|sms with a link)")


def _has_link_click(text: str) -> bool:
    return bool(_LINK_CLICK_RE.search(text))


# --- suspicious phishing message, not yet acted on (medium) -------------------
# Distinct from _has_link_click above: this fires on merely RECEIVING or
# SUSPECTING a phishing email/message/SMS, with no indication the victim
# clicked anything, entered credentials, or lost money yet. Per spec this
# should land at Medium ("Suspicious phishing email"), one tier below an
# incident where the user actually interacted with the link or lost funds.
_PHISHING_SUSPICION_RE = _rx(
    r"suspicious (?:email|mail|message|sms|text)",
    r"(?:email|mail|message|sms|text).{0,20}\blooks?\b.{0,15}\bphish\w*",
    r"\bphish\w*\b.{0,20}\b(?:email|mail|message|sms|text)\b",
    r"(?:email|mail|message).{0,20}(?:asking|asks).{0,20}(?:verify|confirm|update).{0,20}(?:account|details|password)",
)


def _has_phishing_suspicion(text: str) -> bool:
    return bool(_PHISHING_SUSPICION_RE.search(text))


# --- repeated / ongoing harassment ---------------------------------------------
_REPEATED_RE = _rx(r"won\'?t stop", r"keeps? (?:contacting|calling|messaging|texting)", r"every ?day", r"repeatedly", r"again and again", r"multiple times")


def _has_repeated_pattern(text: str) -> bool:
    return bool(_REPEATED_RE.search(text))


# --- malware / device infection -------------------------------------------------
_MALWARE_RE = _rx(r"\bmalware\b", r"\bvirus\b", r"device (?:is |got )?infect\w*", r"\bspyware\b", r"\bkeylogger\b")


def _has_malware(text: str) -> bool:
    return bool(_MALWARE_RE.search(text))


# --- identity theft / impersonation --------------------------------------------
_IDENTITY_THEFT_RE = _rx(
    r"stole my identity", r"pretend\w* to be me", r"fake (?:profile|account)",
    r"clon\w* my profile", r"without my knowledge", r"impersonat\w*",
)


def _has_identity_theft(text: str) -> bool:
    return bool(_IDENTITY_THEFT_RE.search(text))


# --- crypto / investment scam ----------------------------------------------------
_INVESTMENT_SCAM_RE = _rx(r"crypto scam", r"fake investment", r"guaranteed returns", r"\bponzi\b", r"trading app scam")


def _has_investment_scam(text: str) -> bool:
    return bool(_INVESTMENT_SCAM_RE.search(text))


# --- job / loan / parcel scam ----------------------------------------------------
_SCAM_TYPE_RE = _rx(r"job scam", r"loan app", r"loan harassment", r"parcel scam", r"customs fee")


def _has_job_loan_scam(text: str) -> bool:
    return bool(_SCAM_TYPE_RE.search(text))


# --- QR / fake customer care ------------------------------------------------------
_QR_CARE_RE = _rx(r"\bqr code\b", r"fake customer care", r"customer care number", r"claiming to be from customer care")


def _has_qr_or_fake_care(text: str) -> bool:
    return bool(_QR_CARE_RE.search(text))


# --- deepfake / AI voice ------------------------------------------------------------
_DEEPFAKE_RE = _rx(r"\bdeepfake\b", r"ai voice", r"voice clone", r"cloned voice")


def _has_deepfake(text: str) -> bool:
    return bool(_DEEPFAKE_RE.search(text))


# --- cyberbullying / non-financial harassment ---------------------------------------
_BULLYING_RE = _rx(r"\bbully\w*\b", r"\bharass\w*\b", r"\bmock\w*\b", r"\btroll\w*\b", r"spreading rumors", r"humiliat\w*")


def _has_bullying(text: str) -> bool:
    return bool(_BULLYING_RE.search(text))


# --- critical tier detectors ----------------------------------------------------------
_SUICIDE_RE = _rx(r"kill myself", r"want to die", r"end my life", r"\bsuicid\w*\b", r"no reason to live", r"hurt myself")
_CHILD_RE = _rx(r"\bchild\b", r"\bminor\b", r"\bunderage\b", r"\bcsam\b", r"\bgrooming\b", r"my (?:son|daughter|kid)\b")
_PHYSICAL_DANGER_RE = _rx(
    r"going to hurt me", r"physical harm", r"coming to my house", r"afraid for my life", r"in danger right now",
)
_SEXTORTION_RE = _rx(
    r"\bsextortion\b", r"\bnudes?\b", r"explicit photo", r"intimate (?:photo|video|picture|pic)", r"revenge porn",
    r"\b(?:leak\w*|post\w*|shar\w*|publish\w*|expos\w*|send\w*)\b.{0,30}\b(?:photo|photos|pic|pics|picture|pictures|video|videos|nude|nudes|intimate)\b",
    r"record\w*.{0,20}(?:undressing|naked|nude|undressed)",
    r"video call.{0,30}(?:undress\w*|naked|nude)",
    r"(?:undress\w*|naked|nude).{0,20}video call",
)
_BLACKMAIL_RE = _rx(r"\bblackmail\w*\b", r"\bextort\w*\b", r"pay or i will", r"demanding money", r"threatening to")


def _has_suicidal_language(text: str) -> bool:
    return bool(_SUICIDE_RE.search(text))


def _has_child_involved(text: str) -> bool:
    return bool(_CHILD_RE.search(text))


def _has_physical_danger(text: str) -> bool:
    return bool(_PHYSICAL_DANGER_RE.search(text))


def _has_sextortion(text: str) -> bool:
    return bool(_SEXTORTION_RE.search(text))


def _has_blackmail(text: str) -> bool:
    return bool(_BLACKMAIL_RE.search(text))


# Each tuple: (points, label, detector_fn(text) -> bool)
# Critical-tier signals are deliberately weighted at the FULL critical
# threshold (15) each — per spec, each of these is independently and
# unconditionally a Critical Emergency on its own (e.g. sextortion is
# Critical the moment it's detected, not "partial credit toward Critical"
# that still needs another factor to tip it over). Multiple critical
# signals firing together simply stack further above the threshold, which
# is fine — the level is still just CRITICAL, and the higher score is
# still meaningful for the *within-tier* comparisons a human reviewer might
# make (e.g. sextortion + blackmail together vs sextortion alone).
_CRITICAL_SIGNALS = [
    (15, "Suicidal statement detected", _has_suicidal_language),
    (15, "Child exploitation / minor involved", _has_child_involved),
    (15, "Physical danger / credible threat of harm", _has_physical_danger),
    (15, "Identity theft with active misuse (loan/account opened in victim's name)", _has_identity_theft_with_misuse),
    (15, "Sextortion / non-consensual explicit content", _has_sextortion),
    (15, "Active blackmail with financial demand", _has_blackmail),
    (15, "Credentials/OTP shared AND money lost", _has_credential_share_with_loss),
    (15, "Active account takeover with financial loss", _has_active_account_takeover_with_loss),
]

_HIGH_SIGNALS = [
    (7, "Large financial loss (₹10,000+)", _has_large_financial_loss),
    (6, "SIM swap detected", _has_sim_swap),
    (6, "Ransomware / files encrypted", _has_ransomware),
    (5, "OTP shared with attacker", _has_otp_shared),
    (5, "Bank credentials, PIN, or card details shared", _has_credentials_shared),
    (5, "Account takeover — locked out or hacked", _has_account_takeover),
    (5, "Stalking with location knowledge", _has_stalking),
    (9, "Malware / device infection detected", _has_malware),
    (4, "Aadhaar / PAN leaked or misused", _has_id_doc_leak),
    (4, "Remote access app installed by scammer", _has_remote_access_app),
]

_MEDIUM_SIGNALS = [
    (3, "Financial loss (amount unspecified or below ₹10,000)", _has_financial_loss),
    (3, "Phishing link interaction", _has_link_click),
    (4, "Suspicious phishing email/message reported", _has_phishing_suspicion),
    (3, "Repeated / ongoing harassment", _has_repeated_pattern),
    (3, "Identity theft / impersonation", _has_identity_theft),
    (2, "Crypto / investment scam", _has_investment_scam),
    (2, "Job / loan / parcel scam", _has_job_loan_scam),
    (2, "QR code / fake customer care scam", _has_qr_or_fake_care),
    (2, "Deepfake / AI voice scam", _has_deepfake),
    (1, "Cyberbullying / harassment (non-financial)", _has_bullying),
]

_ALL_SIGNAL_TIERS = [_CRITICAL_SIGNALS, _HIGH_SIGNALS, _MEDIUM_SIGNALS]

_TIME_RECENCY_PATTERN = re.compile(
    r"\b(just now|minutes ago|an hour ago|\d+\s*(minute|hour)s?\s*ago|today|right now)\b"
)



@dataclass
class AutoRiskAssessment:
    level: RiskLevel
    score: int
    triggered_factors: List[str] = field(default_factory=list)
    explanation: str = ""


def auto_assess_risk_from_text(
    latest_message: str,
    conversation_history: Optional[List[dict]] = None,
    is_critical_intent: bool = False,
) -> AutoRiskAssessment:
    """
    Derives a risk level automatically from what the user has actually
    said, across the whole case (latest message + prior history), instead
    of requiring the separate manual risk-factors form to be filled in
    first. This is what fixes "almost everything returns LOW" — a real
    incident description now scores appropriately from turn one.

    Deliberately keyword/rule-based (not an LLM judgment call) for the same
    auditability reason as assess_risk() above: a risk level that could
    route someone toward emergency guidance must be explainable.
    """
    history = conversation_history or []
    combined_text = " ".join(
        [m.get("content", "") for m in history if m.get("role") == "user"]
        + [latest_message]
    ).lower()

    score = 0
    triggered: List[str] = []

    for tier in _ALL_SIGNAL_TIERS:
        for points, label, detector in tier:
            try:
                if detector(combined_text):
                    score += points
                    triggered.append(label)
            except Exception as e:  # pragma: no cover - defensive, regex should not throw
                logger.warning("Risk detector failed for signal %r: %s", label, e)

    # Recency amplifies urgency slightly — an incident described as
    # happening "just now" / "today" deserves faster action framing than
    # one from weeks ago, even at the same severity.
    if _TIME_RECENCY_PATTERN.search(combined_text) and score > 0:
        score += 1
        triggered.append("Incident reported as very recent")

    # The intent classifier's own emergency detection (broader than pure
    # keyword tiers above, e.g. covers phrasing variety) also counts.
    if is_critical_intent and score < 9:
        score = max(score, 9)
        triggered.append("Flagged as emergency by intent classifier")

    if score >= 15:
        level = RiskLevel.CRITICAL
    elif score >= 9:
        level = RiskLevel.HIGH
    elif score >= 4:
        level = RiskLevel.MEDIUM
    else:
        level = RiskLevel.LOW

    explanation = (
        f"Auto risk score {score} based on {len(triggered)} signal(s) detected in the "
        f"conversation. This is an automated first-pass triage from message content, "
        f"not a substitute for professional judgment or the detailed risk-factors form."
    )

    logger.info("auto_assess_risk_from_text: score=%s level=%s factors=%s", score, level, triggered)

    return AutoRiskAssessment(level=level, score=score, triggered_factors=triggered, explanation=explanation)
