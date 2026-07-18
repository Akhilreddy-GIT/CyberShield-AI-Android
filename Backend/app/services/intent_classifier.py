"""
Intent Classification & Domain Guardrail.

Two-stage + semantic approach so the system works even WITHOUT an LLM API
key connected:
  1. Fast keyword/rule-based pre-filter (works offline, zero cost, zero
     latency) — expanded to cover the full range of cybercrime patterns
     (phishing, UPI/bank fraud, SIM swap, ransomware, deepfakes, job/loan/
     parcel scams, QR/OTP scams, AI voice scams, etc).
  2. A lightweight TF-IDF semantic layer (scikit-learn, fully offline, no
     network/model download) that catches paraphrased or oddly-worded
     messages the keyword list misses — e.g. "someone is pretending to be
     my bank on the phone" without the literal word "phishing".
  3. If an LLM client is available, agent_service uses it to refine the
     final conversational response; classification itself stays
     deterministic and explainable so a risk/emergency decision can always
     be audited.

This means a reviewer who clones the repo without adding an API key can
still see the guardrail and classification logic working end-to-end.
"""

import logging
import re
from enum import Enum
from typing import List, Optional
from dataclasses import dataclass, field

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

logger = logging.getLogger(__name__)


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


# Expanded keyword coverage. Each category maps to the surface phrases most
# real victims actually type — deliberately including casual/typo-prone
# phrasing, not just formal terms, since this is the first line of triage.
CYBER_KEYWORDS = {
    CyberCategory.CYBERBULLYING: [
        "bully", "bullying", "mocking", "trolling", "mean comments", "making fun of me",
        "spreading rumors", "humiliating me online", "group chat against me",
    ],
    CyberCategory.ONLINE_HARASSMENT: [
        "harass", "harassment", "unwanted messages", "abuse online", "spamming me",
        "won't leave me alone online", "sending vulgar messages",
    ],
    CyberCategory.BLACKMAIL: [
        "blackmail", "threatening me", "extort", "extortion", "demanding money",
        "threat", "intimidat", "will leak", "pay or i will post", "threatening to expose",
    ],
    CyberCategory.SEXTORTION: [
        "sextortion", "nude", "nudes", "explicit photo", "intimate video", "leak my photo",
        "private pictures", "revenge porn", "morphed photo", "morphed picture",
        "threatening to post my photos", "video call recorded me undressed",
    ],
    CyberCategory.FINANCIAL_FRAUD: [
        "scam", "fraud", "otp", "upi", "bank fraud", "lost money", "fake investment",
        "phishing", "phishing link", "fake link", "clicked a link", "sent me a link",
        "debited from my account", "money deducted", "unauthorized transaction",
        "fake customer care", "customer care number", "screen share", "screen sharing app",
        "anydesk", "teamviewer", "quick support", "remote access app",
        "qr code", "scan qr", "loan app", "loan harassment", "instant loan app",
        "job scam", "fake job offer", "registration fee", "work from home scam",
        "parcel", "courier", "customs duty", "customs fee", "fedex scam",
        "crypto scam", "crypto investment", "trading app", "guaranteed returns",
        "ponzi", "investment group", "telegram investment", "double my money",
        "sim swap", "sim card stopped working", "no network suddenly", "duplicate sim",
        "insurance scam", "lottery", "you have won", "kbc lottery", "prize money",
    ],
    CyberCategory.IDENTITY_THEFT: [
        "identity theft", "stole my identity", "used my photos", "pretending to be me",
        "opened an account in my name", "pan card misused", "aadhaar leaked",
        "aadhaar misused", "someone used my aadhaar", "someone used my pan",
    ],
    CyberCategory.STALKING: [
        "stalking", "following me", "won't stop messaging", "keeps contacting",
        "shows up wherever i go", "tracking my location", "knows where i live",
        "keeps calling from different numbers",
    ],
    CyberCategory.FAKE_PROFILE: [
        "fake profile", "fake account", "impersonat", "catfish", "cloned my profile",
        "someone made an account with my photos",
    ],
    CyberCategory.CHILD_SAFETY: [
        "my child", "minor", "underage", "my kid", "grooming", "my son", "my daughter",
        "child predator", "talking to my child online", "csam",
    ],
    CyberCategory.HACKING: [
        "hacked", "account compromised", "unauthorized access", "someone accessed my account",
        "can't log in anymore", "password changed without", "locked out of my account",
        "ransomware", "files encrypted", "ransom demand", "malware", "virus on my phone",
        "virus on my laptop", "device infected", "spyware", "keylogger",
        "whatsapp hacked", "instagram hacked", "facebook hacked", "gmail hacked",
        "email hacked", "account taken over", "someone is posting as me",
    ],
    CyberCategory.PRIVACY_VIOLATION: [
        "photographed without consent", "recorded me without", "privacy violat",
        "hidden camera", "recorded without my knowledge", "deepfake", "fake video of me",
        "ai voice", "voice clone", "cloned voice", "ai generated video of me",
    ],
    CyberCategory.SOCIAL_MEDIA_ABUSE: [
        "social media abuse", "instagram harassment", "facebook abuse", "twitter abuse",
        "x abuse", "comments section abuse",
    ],
}

# Short reference sentence per category used for TF-IDF semantic matching.
# This lets the classifier catch paraphrased messages the exact keyword
# list above misses, while staying fully offline/deterministic.
_CATEGORY_REFERENCE_TEXT = {
    cat: f"{cat.value.replace('_', ' ')} " + " ".join(kws)
    for cat, kws in CYBER_KEYWORDS.items()
}
_SEMANTIC_CATEGORIES = list(_CATEGORY_REFERENCE_TEXT.keys())
_SEMANTIC_MIN_SIMILARITY = 0.12  # conservative threshold, tuned for short chat messages

try:
    _vectorizer = TfidfVectorizer(stop_words="english", max_features=1500)
    _category_matrix = _vectorizer.fit_transform(
        [_CATEGORY_REFERENCE_TEXT[c] for c in _SEMANTIC_CATEGORIES]
    )
except Exception as e:  # pragma: no cover - defensive, sklearn is a hard dep here
    logger.warning("Semantic classifier init failed, falling back to keywords only: %s", e)
    _vectorizer = None
    _category_matrix = None


def _semantic_match(text: str) -> Optional[CyberCategory]:
    """Best-effort TF-IDF cosine-similarity match against category exemplars.
    Returns None if similarity is too low to be confident, or the vectorizer
    failed to initialize (keyword matching still runs regardless)."""
    if _vectorizer is None:
        return None
    try:
        query_vec = _vectorizer.transform([text])
        sims = cosine_similarity(query_vec, _category_matrix)[0]
        best_idx = sims.argmax()
        if sims[best_idx] >= _SEMANTIC_MIN_SIMILARITY:
            return _SEMANTIC_CATEGORIES[best_idx]
    except Exception as e:  # pragma: no cover
        logger.debug("Semantic match failed: %s", e)
    return None


# Explicitly non-cyber domains the agent must refuse
OFF_TOPIC_MARKERS = [
    "movie", "film review", "cricket", "football match", "ipl", "recipe", "cook",
    "who won the election", "write code for", "solve this math", "homework help with",
    "date me", "dating advice", "weather today", "song lyrics", "write a poem",
    "college assignment", "essay on", "translate this sentence", "sports score",
    "who is the prime minister", "capital of", "tell me a joke", "general knowledge",
]

# ---------------------------------------------------------------------------
# Conversational / small-talk gate.
#
# CyberShield AI is a dedicated cyber-safety assistant, not a general
# chatbot — a bare "hi", "thanks", or "ok" must never create a Case, run
# risk scoring, or produce a fabricated legal citation. This is checked
# BEFORE the ambiguous/OTHER_CYBER fallback so pure small talk short-
# circuits to a polite redirect with zero side effects.
#
# Deliberately conservative: the WHOLE message (after trimming punctuation)
# must match a known conversational pattern with nothing else meaningful in
# it. "hi, someone is blackmailing me" or "thanks, what should I do about
# the OTP I shared" must NOT be caught here — the cyber keyword match runs
# first and takes priority; this gate only applies when no cyber category
# and no off-topic marker matched.
# ---------------------------------------------------------------------------
_SMALL_TALK_TOKEN = (
    r"(?:"
    r"hi+|hello+|hey+|yo|hii+|hiya|"
    r"good\s?(?:morning|afternoon|evening|night)|"
    r"how\s?(?:are\s?you|r\s?u|are\s?u)\??|"
    r"what'?s\s?up|wassup|sup|"
    r"who\s?(?:are\s?you|made\s?you|created\s?you|built\s?you)\??|"
    r"what\s?(?:is\s?your\s?name|are\s?you)\??|"
    r"tell\s?me\s?a\s?joke|"
    r"thank\s?(?:you|s)+|thanks\s?(?:a\s?lot|so\s?much)?|ty|thx|"
    r"ok(?:ay)?|okk+|cool|nice|great|good|fine|alright|sure|"
    r"bye+|goodbye|see\s?you|see\s?ya|"
    r"yes|no|yeah|yep|nope|k|kk|hmm+|"
    r"test|testing|"
    r"good\s?(?:job|bot)|nice\s?(?:job|bot|work)|"
    r"random\s?chat(?:ting)?|just\s?chatting|"
    r"hru|gm|gn|np|welcome|"
    # Harmless filler words that commonly trail a greeting with no added
    # content of their own — "hello there", "hey buddy", "hi again",
    # "good morning friend". Without these, any greeting plus one of these
    # words fell through _is_pure_small_talk (not an exact token match) all
    # the way to the final "ambiguous — treated as cyber-related" fallback,
    # which incorrectly created a case for a plain greeting.
    r"there|friend|buddy|dude|bro|man|bud|pal|mate|again|everyone|all"
    r")"
)
# A pure small-talk message is one or more of the above tokens, separated
# only by light punctuation/whitespace — this lets "okay bye", "thanks
# bye", "ok cool thanks" all match, while anything containing real content
# outside this token set falls through to the cyber/ambiguous path.
_SMALL_TALK_RE = re.compile(
    rf"^(?:{_SMALL_TALK_TOKEN}[\s,.!?]*)+$",
    re.IGNORECASE,
)


def _is_pure_small_talk(text: str) -> bool:
    """True only when the ENTIRE message is small talk with no other
    content — the cyber keyword scan always runs first and takes priority
    over this gate, so a message that mixes a greeting with a real report
    is never misclassified as small talk."""
    stripped = text.strip()
    if not stripped:
        return False
    return bool(_SMALL_TALK_RE.match(stripped))


# Matches a string with no letters or digits at all — empty, whitespace,
# pure punctuation, or emoji-only. These carry no classifiable content, so
# they must never be treated as an ambiguous cyber report (which would
# otherwise create a case and produce a fabricated-sounding legal citation
# in the no-LLM template fallback). Handled as a no-content case instead.
_NO_ALPHANUMERIC_RE = re.compile(r"^[^a-zA-Z0-9]*$")


def _has_no_classifiable_content(text: str) -> bool:
    return bool(_NO_ALPHANUMERIC_RE.match(text))


# ---------------------------------------------------------------------------
# Educational / informational question detection (Goal #4).
#
# ROOT CAUSE this fixes: a cyber-education question like "How do phishing
# scams work?" or "What is ransomware?" contains a real cyber keyword
# ("phishing", "ransomware"), so it was matching a CyberCategory in the
# keyword loop above and falling straight into the incident-report branch —
# creating a Case, running risk scoring, and citing legal sections for an
# incident that never happened. The keyword match alone can't tell "asking
# about this topic" apart from "reporting this happened to me"; this check
# adds that distinction on top of it.
#
# Deliberately conservative in both directions:
#  - Only fires on a recognizable question-opener ("how do", "what is",
#    "explain", etc.) — a bare statement never qualifies as educational,
#    so it can't be used to dodge case creation for a real incident.
#  - Any first-person/incident-indicating language anywhere in the message
#    ("someone ...", "I lost/shared/gave/clicked ...", "my account was ...")
#    overrides it back to a real report, even if phrased as a question
#    (e.g. "What should I do — someone hacked my Instagram?").
#  - Critical/emergency messages are never marked educational regardless of
#    phrasing (checked by the caller via `is_critical`).
# ---------------------------------------------------------------------------
_EDUCATIONAL_QUESTION_RE = re.compile(
    r"^\s*(?:how\s+(?:do|does|can|should|would)\b|how\s+to\b|what\s+is\b|what\s+are\b|"
    r"explain\b|can\s+you\s+explain\b|tips?\s+(?:for|on)\b|is\s+it\s+safe\s+to\b|"
    r"difference\s+between\b|define\b|why\s+(?:do|does|is)\b)",
    re.IGNORECASE,
)

_INCIDENT_LANGUAGE_RE = re.compile(
    r"\bi\s+(?:lost|was|got|received|shared|gave|gave\s?up|clicked|paid|transferred|noticed|found|reported)\b"
    r"|\bmy\s+\w+\s+(?:was|is|got|has\s+been)\b"
    r"|\bsomeone\b|\bthey\s+(?:are|have|threatened|hacked|stole|took)\b"
    r"|\bhappened\s+to\s+me\b|\bhelp\s+me\b",
    re.IGNORECASE,
)


def _is_educational_question(text: str) -> bool:
    """True for a general 'how/what/why does this work' question with no
    accompanying incident language — see module docstring above."""
    return bool(_EDUCATIONAL_QUESTION_RE.search(text)) and not bool(_INCIDENT_LANGUAGE_RE.search(text))


# Emergency markers grouped by why they're urgent, so the reasoning stays
# auditable rather than one flat list. Any single hit marks is_critical=True.
# Regex-based (word-order/tense flexible) rather than rigid exact-phrase
# substrings, so "leak my private photos" matches the same as "leaked my
# photos" — real victim phrasing varies far more than a fixed phrase list
# can cover, and a missed match here means a missed emergency escalation.
_SELF_HARM_MARKERS = [
    "kill myself", "want to die", "end my life", "suicide", "suicidal",
    "no reason to live", "hurt myself",
]
_IMMINENT_DANGER_MARKERS = [
    "going to hurt me", "physical harm", "he has my address", "coming to my house",
    "knows where i live", "threatening to come to my", "afraid for my life", "in danger right now",
]
_CHILD_SAFETY_MARKERS = [
    "child", "minor", "underage", "csam", "grooming", "my son", "my daughter", "my kid",
]

_SELF_HARM_RE = re.compile("|".join(_SELF_HARM_MARKERS), re.IGNORECASE)
_IMMINENT_DANGER_RE = re.compile("|".join(_IMMINENT_DANGER_MARKERS), re.IGNORECASE)
_CHILD_SAFETY_RE = re.compile("|".join(_CHILD_SAFETY_MARKERS), re.IGNORECASE)

# Active exploitation: any leak/post/share verb near photo/video/nudes, OR
# an explicit blackmail/extortion/publish-threat phrase — flexible enough to
# catch "leak my private photos", "posted my nude photos", "threatening to
# expose me", etc. without needing every exact wording enumerated.
_ACTIVE_EXPLOITATION_RE = re.compile(
    r"\b(?:leak\w*|post\w*|shar\w*|publish\w*|expos\w*|send\w*)\b.{0,30}\b(?:photo|photos|pic|pics|picture|pictures|video|videos|nude|nudes)\b"
    r"|\bblackmail\w*\b|\bextort\w*\b|threatening to (?:publish|post|leak|expose|send)",
    re.IGNORECASE,
)

# Account-takeover-urgent: money/account language near "gone/emptied/drained"
# or OTP/credential-share phrasing (verb-flexible, mirrors risk_engine's
# detectors), or a hacked-account phrase for major platforms.
_ACCOUNT_TAKEOVER_URGENT_RE = re.compile(
    r"\b(?:bank )?account\b.{0,20}\b(?:empt\w*|drain\w*|gone)\b"
    r"|money (?:is |has )?gone\b"
    r"|\botp\b.{0,25}\b(?:shar\w*|gave|give|told|sent)\b"
    r"|\b(?:shar\w*|gave|give|told|sent)\b.{0,25}\botp\b"
    r"|\bsim\b.{0,20}\b(?:swap|stopped|deactivat\w*|not working)\b"
    r"|\b(?:whatsapp|instagram|facebook|gmail|email)\b.{0,15}\bhack\w*\b"
    r"|\baadhaar\b.{0,20}\bleak\w*\b",
    re.IGNORECASE,
)

# Kept as flat lists too (for CRITICAL_MARKERS export / any external
# reference) alongside the regex versions used for actual detection.
_ACTIVE_EXPLOITATION_MARKERS = [
    "leaked my private photos", "leaked my photos", "posted my nudes", "shared my nudes",
    "blackmailing me", "extorting me", "threatening to publish",
]
_ACCOUNT_TAKEOVER_URGENT_MARKERS = [
    "my bank account is emptied", "money is gone", "otp was shared", "shared my otp",
    "sim stopped working", "sim card stopped", "whatsapp is hacked", "instagram is hacked",
    "aadhaar is leaked", "aadhaar leaked",
]

CRITICAL_MARKERS = (
    _SELF_HARM_MARKERS
    + _IMMINENT_DANGER_MARKERS
    + _CHILD_SAFETY_MARKERS
    + _ACTIVE_EXPLOITATION_MARKERS
    + _ACCOUNT_TAKEOVER_URGENT_MARKERS
)


@dataclass
class IntentResult:
    is_cyber_related: bool
    category: Optional[CyberCategory]
    is_critical: bool
    confidence: str  # "high" | "medium" | "low"
    reasoning: str
    emergency_type: Optional[str] = None  # "self_harm" | "imminent_danger" | "child_safety" | "active_exploitation" | "account_takeover" | None
    matched_keywords: List[str] = field(default_factory=list)
    is_educational: bool = False  # general cyber-safety question, not an incident report — never creates a case (Goal #4)


def _classify_emergency_type(text: str) -> Optional[str]:
    """Identify *which kind* of emergency this is, so downstream response
    generation and helpline selection can prioritize correctly (e.g. a
    self-harm signal should surface crisis support, not just cybercrime
    helplines)."""
    if _SELF_HARM_RE.search(text):
        return "self_harm"
    if _IMMINENT_DANGER_RE.search(text):
        return "imminent_danger"
    if _CHILD_SAFETY_RE.search(text):
        return "child_safety"
    if _ACTIVE_EXPLOITATION_RE.search(text):
        return "active_exploitation"
    if _ACCOUNT_TAKEOVER_URGENT_RE.search(text):
        return "account_takeover"
    return None



def classify_intent(message: str) -> IntentResult:
    """Classify a single user message into a cyber-crime category (or
    off-topic/ambiguous), and flag emergencies. Deterministic and fast —
    no network calls — so it always runs even if the LLM is unavailable."""
    text = message.lower()

    off_topic_hits = [m for m in OFF_TOPIC_MARKERS if m in text]
    cyber_hits: List[str] = []
    matched_category: Optional[CyberCategory] = None
    for cat, kws in CYBER_KEYWORDS.items():
        hits = [kw for kw in kws if kw in text]
        if hits:
            cyber_hits.extend(hits)
            if matched_category is None:
                matched_category = cat

    emergency_type = _classify_emergency_type(text)
    is_critical = emergency_type is not None

    if matched_category:
        is_educational = (not is_critical) and _is_educational_question(text)
        return IntentResult(
            is_cyber_related=True,
            category=matched_category,
            is_critical=is_critical,
            confidence="high" if len(cyber_hits) > 1 else "medium",
            reasoning=f"Matched keywords: {cyber_hits[:3]}",
            emergency_type=emergency_type,
            matched_keywords=cyber_hits[:5],
            is_educational=is_educational,
        )

    # No classifiable content at all (empty, whitespace, emoji-only, pure
    # punctuation/spam symbols) — never routed to a case or the ambiguous
    # fallback. Checked before small talk since it isn't really "talk" at
    # all, just distinguished for clearer logging/auditability.
    if _has_no_classifiable_content(text) and not is_critical:
        return IntentResult(
            is_cyber_related=False,
            category=CyberCategory.NOT_CYBER,
            is_critical=False,
            confidence="high",
            reasoning="No classifiable content (empty, whitespace, or emoji/punctuation only).",
            emergency_type=None,
            matched_keywords=[],
        )

    # Pure small talk (greetings, thanks, "ok", "bye", etc.) with no cyber
    # keyword match and no emergency marker — never routed to a case.
    # Checked before the off-topic-domain check and before the ambiguous
    # fallback, since small talk isn't "off-topic" in the same sense as
    # asking for a recipe; it gets its own quiet, friendly redirect.
    if _is_pure_small_talk(text) and not is_critical:
        return IntentResult(
            is_cyber_related=False,
            category=CyberCategory.NOT_CYBER,
            is_critical=False,
            confidence="high",
            reasoning="Pure conversational small talk — no cyber-safety content.",
            emergency_type=None,
            matched_keywords=[],
        )

    if off_topic_hits and not is_critical:
        return IntentResult(
            is_cyber_related=False,
            category=CyberCategory.NOT_CYBER,
            is_critical=False,
            confidence="high",
            reasoning=f"Off-topic markers: {off_topic_hits[:2]}",
            emergency_type=None,
            matched_keywords=[],
        )

    # No literal keyword hit — try the semantic (TF-IDF) layer before
    # falling back to "ambiguous". This catches paraphrased victim messages
    # like "someone I don't know is texting me from a new number every day
    # after I blocked them" (stalking, no exact keyword present).
    semantic_category = _semantic_match(text)
    if semantic_category and not off_topic_hits:
        return IntentResult(
            is_cyber_related=True,
            category=semantic_category,
            is_critical=is_critical,
            confidence="medium",
            reasoning=f"Semantic match (no exact keyword) to category: {semantic_category.value}",
            emergency_type=emergency_type,
            matched_keywords=[],
            is_educational=(not is_critical) and _is_educational_question(text),
        )

    # Fully ambiguous — no keyword match, no confident semantic match, no
    # off-topic marker either. A generic greeting or vague opening message
    # ("I need help", "something happened to me") is common for a genuine
    # first-time victim, so this is routed to a clarifying question with
    # LOW confidence rather than assumed off-topic or assumed a specific
    # category.
    #
    # Safety net: a message this short (1-2 words) that reached this point
    # slipped through the small-talk token list without being an exact
    # match (e.g. an unlisted greeting variant, "yo man", "sup dude") — a
    # genuine first-time victim describing an incident essentially always
    # writes more than two words. Treating it as small talk here is far
    # safer than fabricating a case and a legal citation from a two-word
    # message; a real victim who only types "help" will get the polite
    # redirect and can simply say more.
    word_count = len(text.split())
    if word_count <= 2 and not is_critical:
        return IntentResult(
            is_cyber_related=False,
            category=CyberCategory.NOT_CYBER,
            is_critical=False,
            confidence="high",
            reasoning="Too short (<=2 words) with no keyword/semantic/off-topic match — treated as unrecognized small talk rather than an ambiguous cyber report.",
            emergency_type=None,
            matched_keywords=[],
        )

    return IntentResult(
        is_cyber_related=True,
        category=CyberCategory.OTHER_CYBER,
        is_critical=is_critical,
        confidence="low",
        reasoning="No strong keyword or semantic match either way — treated as ambiguous and routed to a clarifying question.",
        emergency_type=emergency_type,
        matched_keywords=[],
        is_educational=(not is_critical) and _is_educational_question(text),
    )


REFUSAL_MESSAGE = (
    "I am designed exclusively to assist with cyber safety and cybercrime-related "
    "concerns — things like cyberbullying, harassment, blackmail, scams, hacking, "
    "identity theft, or online abuse. I'm not able to help with topics outside "
    "this domain. If you're dealing with a cyber safety issue, I'm here to help."
)

# Used instead of REFUSAL_MESSAGE when the message itself carries no
# content (empty, whitespace, emoji/punctuation-only) — a gentler prompt
# rather than a "that's off-topic" framing, since nothing was actually
# said yet.
NO_CONTENT_MESSAGE = (
    "This assistant is dedicated exclusively to cybercrime, online fraud, "
    "stalking, blackmail, identity theft, digital harassment, online safety, "
    "and cybersecurity incidents.\n\n"
    "Please describe your cyber-related issue so I can assist."
)

# Some CyberCategory values don't have a dedicated legal_kb entry and should
# reuse a close match instead of silently falling through to an unfiltered
# search. Kept as an explicit mapping (rather than folding these into one
# enum value) so classify_intent's keyword dict keeps a distinct, unmerged
# entry per category — see intent_classifier keyword coverage above.
_KB_CATEGORY_ALIASES = {
    CyberCategory.ONLINE_HARASSMENT.value: "cyberbullying",
    CyberCategory.SOCIAL_MEDIA_ABUSE.value: "cyberbullying",
}


def resolve_kb_category(category_value: Optional[str]) -> Optional[str]:
    """Maps a CyberCategory value to the legal_kb category it should filter
    on. Returns the value unchanged if it already matches a real KB
    category (or has no alias)."""
    if category_value is None:
        return None
    return _KB_CATEGORY_ALIASES.get(category_value, category_value)
