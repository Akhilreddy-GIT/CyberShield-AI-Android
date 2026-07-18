"""
Incident Timeline Engine.

Automatically reconstructs a chronological sequence of what happened in a
case — "clicked phishing link" -> "credentials entered" -> "OTP shared" ->
"money transferred" -> "bank contacted" -> "complaint filed" — from the raw
text of chat messages and evidence uploads, without the user manually
filling out a timeline form (the manual `/api/cases/timeline` endpoint
already existed and still works unchanged; this module is what feeds it
automatically).

Design: same auditable-regex philosophy as case_memory_service and
evidence_intelligence_service — every detected event traces to a specific
matched action phrase in the user's own text, not an LLM's inference. This
keeps the timeline legally usable (a human investigator can see exactly
why each entry was added) and means it works with zero LLM cost.

Each detected action maps to a canonical, human-readable timeline
description and an approximate ordering rank, so multiple actions
mentioned in one message are still inserted in a sane chronological order
even without explicit timestamps. If the user does give a real time
("10:32", "yesterday 6pm"), that's attached as the event's event_time;
otherwise event_time is left None and the row's created_at (insertion
order) stands in as the ordering signal — consistent with TimelineEvent's
existing schema, no migration needed.
"""

import re
import logging
from dataclasses import dataclass
from typing import List, Optional

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Canonical incident-lifecycle actions, roughly in the order they typically
# occur (used as a stable secondary sort key when several are mentioned in
# a single message with no explicit times). Each entry: (key, rank,
# description, trigger phrases).
# ---------------------------------------------------------------------------
_ACTION_STAGES = [
    ("received_message", 10, "Received suspicious message/call", [
        "received a message", "got a message", "received a call", "got a call",
        "someone messaged me", "someone contacted me", "i got an email", "received an email",
    ]),
    ("clicked_link", 20, "Clicked a suspicious link", [
        "clicked the link", "clicked a link", "clicked on link", "opened the link", "clicked on it",
    ]),
    ("scanned_qr", 25, "Scanned a QR code", [
        "scanned the qr", "scanned a qr", "scanned qr code",
    ]),
    ("entered_credentials", 30, "Entered login credentials on a page/app", [
        "entered my password", "entered my login", "entered credentials", "logged in on that page",
        "entered my username and password",
    ]),
    ("otp_shared", 40, "OTP was shared", [
        "shared my otp", "shared the otp", "gave my otp", "gave the otp", "told them the otp",
        "entered the otp", "sent the otp", "otp was shared", "i ended up sharing my otp",
    ]),
    ("pin_shared", 45, "PIN/CVV was shared", [
        "shared my pin", "shared the pin", "gave my pin", "shared my cvv", "gave my cvv",
    ]),
    ("app_installed", 50, "Remote-access or unknown app installed", [
        "installed the app", "installed anydesk", "installed teamviewer", "downloaded the app",
        "installed that app", "gave remote access",
    ]),
    ("money_transferred", 60, "Money was transferred / debited", [
        "money was transferred", "transferred money", "paid the amount", "money got deducted",
        "amount was debited", "sent the payment", "money was debited", "got deducted", "lost rs",
        "i paid", "i transferred",
    ]),
    ("account_locked", 65, "Account access lost / locked out", [
        "locked out", "lost access", "can't log in anymore", "account was hacked", "account got hacked",
        "changed my password", "password was changed",
    ]),
    ("content_leaked_threatened", 68, "Threat to leak / leaked content", [
        "threatened to leak", "threatening to leak", "posted my photos", "leaked my photos",
        "threatening to post", "will leak", "will post",
    ]),
    ("noticed_incident", 70, "Noticed something was wrong", [
        "i noticed", "i realized", "then i saw", "i found out",
    ]),
    ("bank_contacted", 80, "Bank / platform support contacted", [
        "called the bank", "contacted the bank", "called my bank", "informed the bank",
        "contacted support", "raised a ticket", "called customer care",
    ]),
    ("card_blocked", 82, "Card / account blocked as containment", [
        "blocked my card", "blocked the card", "froze my account", "card was blocked",
    ]),
    ("complaint_filed", 90, "Complaint / FIR filed", [
        "filed a complaint", "filed an fir", "filed a police complaint", "reported to cybercrime",
        "complaint filed", "raised a complaint on cybercrime.gov.in", "called 1930",
    ]),
]

_TIME_HINT_RE = re.compile(
    r"\b(\d{1,2}[:.]\d{2}\s?(?:[APap][Mm])?|\d{1,2}\s?[APap][Mm]|"
    r"yesterday|today|this morning|last night|just now|"
    r"\d+\s*(?:minutes?|hours?|days?)\s*ago)\b",
    re.IGNORECASE,
)


@dataclass
class TimelineCandidate:
    key: str
    rank: int
    description: str
    event_time: Optional[str]
    matched_phrase: str


# ---------------------------------------------------------------------------
# Some stages also get a flexible regex pattern in addition to the exact-
# phrase list above. ROOT CAUSE this fixes: extract_timeline_events matched
# only rigid literal substrings ("shared my pin", "lost rs"), while
# risk_engine and intent_classifier use verb/word-order-flexible regexes for
# this exact same real-world vocabulary (OTP/PIN sharing, money lost).
# Real victim phrasing varies far more than a fixed phrase list covers —
# "sharing my UPI PIN" (present participle, "UPI" inserted) or "lost 50000
# rupees" (no "rs") never matched, so a message that correctly triggered
# HIGH/CRITICAL risk scoring could still produce an empty Timeline. Keyed by
# the same `key` as _ACTION_STAGES so it augments rather than replaces the
# exact-phrase list (both are checked; either can produce a hit).
# ---------------------------------------------------------------------------
_ACTION_STAGE_PATTERNS: dict = {
    "otp_shared": re.compile(
        r"\b(?:shar\w*|gave|give|told|sent|enter\w*)\b.{0,20}\botp\b"
        r"|\botp\b.{0,20}\b(?:shar\w*|gave|give|told|sent)\b",
        re.IGNORECASE,
    ),
    "pin_shared": re.compile(
        r"\b(?:shar\w*|gave|give|told|sent|enter\w*)\b.{0,20}\b(?:pin|cvv)\b"
        r"|\b(?:pin|cvv)\b.{0,20}\b(?:shar\w*|gave|give|told|sent)\b",
        re.IGNORECASE,
    ),
    "money_transferred": re.compile(
        r"\b(?:lost|paid|transferred|sent|debited|deducted)\b.{0,25}\b(?:rs\.?|rupees|inr|₹|\d[\d,]*)\b"
        r"|\b(?:rs\.?|rupees|inr|₹)\s?\d[\d,]*\b.{0,25}\b(?:lost|paid|transferred|debited|deducted|gone)\b"
        r"|\bmoney\b.{0,15}\b(?:gone|deducted|debited|transferred)\b",
        re.IGNORECASE,
    ),
    "account_locked": re.compile(
        r"\b(?:account|whatsapp|instagram|facebook|gmail|email)\b.{0,20}\bhack\w*\b"
        r"|\blocked out\b|\blost access\b",
        re.IGNORECASE,
    ),
    "content_leaked_threatened": re.compile(
        r"\b(?:leak\w*|post\w*|shar\w*|publish\w*|expos\w*|send\w*)\b.{0,30}\b(?:photo|photos|pic|pics|picture|pictures|video|videos|nude|nudes)\b"
        r"|threatening to (?:publish|post|leak|expose|send)",
        re.IGNORECASE,
    ),
}


def extract_timeline_events(text: str) -> List[TimelineCandidate]:
    """Scans a single message (chat message or evidence text) for
    incident-lifecycle action phrases and returns candidate timeline
    entries, sorted by canonical stage rank. Never raises."""
    if not text:
        return []

    try:
        lower = text.lower()
        time_match = _TIME_HINT_RE.search(lower)
        shared_time = time_match.group(0) if time_match else None

        candidates: List[TimelineCandidate] = []
        for key, rank, description, phrases in _ACTION_STAGES:
            hit = next((p for p in phrases if p in lower), None)
            if not hit:
                pattern = _ACTION_STAGE_PATTERNS.get(key)
                if pattern:
                    m = pattern.search(lower)
                    if m:
                        hit = m.group(0)
            if hit:
                candidates.append(TimelineCandidate(
                    key=key, rank=rank, description=description,
                    event_time=shared_time, matched_phrase=hit,
                ))

        candidates.sort(key=lambda c: c.rank)
        return candidates
    except Exception:
        logger.exception("Timeline extraction failed for message.")
        return []


_EVIDENCE_SOURCE_SUFFIX_RE = re.compile(r"\s*\(from uploaded evidence:.*?\)\s*$")


def _canonical_description(stored_description: str) -> str:
    """Strips the evidence-source suffix (see evidence_source_note below)
    from a stored timeline description, so dedup logic compares the same
    canonical stage text regardless of whether the existing row came from a
    chat message (bare description) or an evidence upload (description +
    source suffix)."""
    return _EVIDENCE_SOURCE_SUFFIX_RE.sub("", stored_description).strip()


def dedupe_against_existing(
    candidates: List[TimelineCandidate],
    existing_descriptions: List[str],
) -> List[TimelineCandidate]:
    """Filters out candidates whose description was already recorded for
    this case, so the same stage isn't logged twice (e.g. user re-mentions
    'I shared my OTP' two turns later while giving more detail, or the same
    stage is mentioned in both a chat message and an uploaded screenshot).
    Compares against the CANONICAL (suffix-stripped) form of each existing
    description, since evidence-sourced rows are stored with a
    "(from uploaded evidence: ...)" suffix that would otherwise defeat a
    literal string match against a bare chat-sourced description."""
    existing = {_canonical_description(d) for d in existing_descriptions}
    return [c for c in candidates if c.description not in existing]


def evidence_source_note(filename: str) -> str:
    """Standard prefix used when a timeline event originates from an
    evidence upload rather than a chat message, so the timeline stays
    traceable to its source."""
    return f"(from uploaded evidence: {filename})"
