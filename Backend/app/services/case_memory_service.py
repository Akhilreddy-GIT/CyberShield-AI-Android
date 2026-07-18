"""
Case Memory & Evidence Entity Extraction.

Goal: within a single case, the agent should never re-ask for a fact the
user already gave it — amount lost, bank/platform involved, phone numbers,
UPI IDs, emails, URLs, timeline. This module extracts those entities from
every message with plain regex (auditable, offline, zero-cost — consistent
with the rest of this codebase's design philosophy of explainable,
non-black-box logic) and merges them into a small persisted JSON blob on
the Case row (Case.facts_json).

This does NOT change any existing endpoint contract:
- chat.py calls `update_case_facts(...)` after persisting messages, and
  `facts_summary_for_prompt(...)` to build a short "known facts" block fed
  into the LLM system prompt so it stops re-asking known information.
- No response schema changes; this is purely server-side context building.
"""

import re
import json
import logging
from dataclasses import dataclass, field, asdict
from typing import Dict, List, Optional

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Entity patterns — deliberately simple/auditable regex, not NLP. Evidence
# should be traceable to what the user literally typed.
# ---------------------------------------------------------------------------
_PHONE_RE = re.compile(r"\b(?:\+91[\s-]?)?[6-9]\d{9}\b")
_EMAIL_RE = re.compile(r"\b[\w.+-]+@[\w-]+\.[\w.-]+\b")
_UPI_RE = re.compile(r"\b[\w.\-]{2,256}@[a-zA-Z]{2,64}\b")  # e.g. name@okhdfcbank / name@upi
_URL_RE = re.compile(r"\bhttps?://[^\s]+|\bwww\.[^\s]+", re.IGNORECASE)
_AMOUNT_RE = re.compile(
    r"(?:₹|rs\.?|inr)\s?([\d,]+(?:\.\d+)?)\s*(k|lakh|lakhs|crore)?"
    r"|([\d,]+(?:\.\d+)?)\s*(k|lakh|lakhs|crore)?\s*(?:rupees|rs\.?)",
    re.IGNORECASE,
)
_TXN_ID_RE = re.compile(r"\b(?:txn|transaction|utr|ref(?:erence)?)[\s#:]*([a-zA-Z0-9]{6,25})\b", re.IGNORECASE)

# Public (no leading underscore) — reused by evidence_intelligence_service
# so bank/platform vocabulary stays defined in exactly one place.
BANK_NAMES = [
    "sbi", "state bank", "hdfc", "icici", "axis bank", "kotak", "pnb",
    "punjab national bank", "bank of baroda", "canara bank", "union bank",
    "idfc", "yes bank", "indusind", "boi", "bank of india", "paytm bank",
]
PLATFORM_NAMES = [
    "whatsapp", "instagram", "facebook", "telegram", "gmail", "outlook",
    "snapchat", "twitter", "x.com", "paytm", "phonepe", "google pay", "gpay",
    "amazon pay", "linkedin", "discord",
]
# Backward-compatible aliases — pre-existing code in this module below
# still references the underscore-prefixed names.
_BANK_NAMES = BANK_NAMES
_PLATFORM_NAMES = PLATFORM_NAMES

# Fields tracked per case. Each is a single "best known value" (last
# non-empty wins) except list-type fields which accumulate uniquely.
_SCALAR_FIELDS = ["amount_lost", "bank", "platform", "timeline"]
_LIST_FIELDS = ["phone_numbers", "emails", "upi_ids", "urls", "transaction_ids"]


@dataclass
class CaseFacts:
    amount_lost: Optional[str] = None
    bank: Optional[str] = None
    platform: Optional[str] = None
    timeline: Optional[str] = None
    phone_numbers: List[str] = field(default_factory=list)
    emails: List[str] = field(default_factory=list)
    upi_ids: List[str] = field(default_factory=list)
    urls: List[str] = field(default_factory=list)
    transaction_ids: List[str] = field(default_factory=list)

    def to_json(self) -> str:
        return json.dumps(asdict(self))

    @staticmethod
    def from_json(raw: Optional[str]) -> "CaseFacts":
        if not raw:
            return CaseFacts()
        try:
            data = json.loads(raw)
            return CaseFacts(**{k: data.get(k) for k in _SCALAR_FIELDS + _LIST_FIELDS if k in data} or {})
        except Exception:
            logger.warning("Failed to parse stored case facts JSON, starting fresh.")
            return CaseFacts()


def _format_amount(raw_num: str, unit: Optional[str]) -> str:
    raw_num = raw_num.replace(",", "")
    unit = (unit or "").lower()
    if unit in ("lakh", "lakhs"):
        return f"₹{raw_num} lakh"
    if unit == "crore":
        return f"₹{raw_num} crore"
    if unit == "k":
        return f"₹{raw_num}k"
    return f"₹{raw_num}"


def extract_entities(text: str) -> Dict:
    """Pulls structured entities out of a single message. Pure function,
    no side effects — caller merges results into persisted CaseFacts."""
    found: Dict = {
        "phone_numbers": sorted(set(_PHONE_RE.findall(text))),
        "emails": sorted(set(_EMAIL_RE.findall(text))),
        "urls": sorted(set(_URL_RE.findall(text))),
        "transaction_ids": sorted(set(_TXN_ID_RE.findall(text))),
    }

    # UPI IDs look like emails but with common UPI handle suffixes — only
    # keep matches that aren't already captured as a real email domain
    # (heuristic: UPI handles are short bank/app codes, not real TLDs).
    upi_candidates = set(_UPI_RE.findall(text)) - set(found["emails"])
    upi_handle_hint = re.compile(r"@(ok[a-z]+|upi|ybl|ibl|axl|paytm|apl|icici|hdfcbank)$", re.IGNORECASE)
    found["upi_ids"] = sorted(u for u in upi_candidates if upi_handle_hint.search(u))

    lower = text.lower()
    amounts = []
    for m in _AMOUNT_RE.finditer(text):
        num = m.group(1) or m.group(3)
        unit = m.group(2) or m.group(4)
        if num:
            amounts.append(_format_amount(num, unit))
    found["amount_lost"] = amounts[-1] if amounts else None

    found["bank"] = next((b for b in _BANK_NAMES if b in lower), None)
    found["platform"] = next((p for p in _PLATFORM_NAMES if p in lower), None)

    timeline_match = re.search(
        r"\b(just now|today|yesterday|last night|this morning|\d+\s*(?:minutes?|hours?|days?|weeks?)\s*ago)\b",
        lower,
    )
    found["timeline"] = timeline_match.group(0) if timeline_match else None

    return found


def merge_facts(existing: CaseFacts, new_entities: Dict) -> CaseFacts:
    """Merges freshly extracted entities into the persisted fact set.
    Scalars: keep existing unless a new non-null value is found (never
    silently overwrite a known bank with None from a message that just
    didn't mention it). Lists: union, deduplicated, capped to avoid
    unbounded growth over a long case."""
    for field_name in _SCALAR_FIELDS:
        new_val = new_entities.get(field_name)
        if new_val:
            setattr(existing, field_name, new_val)

    for field_name in _LIST_FIELDS:
        current = set(getattr(existing, field_name) or [])
        current.update(new_entities.get(field_name) or [])
        setattr(existing, field_name, sorted(current)[:20])

    return existing


def update_case_facts(existing_json: Optional[str], latest_message: str) -> str:
    """Convenience wrapper: parse -> extract -> merge -> serialize.
    Returns the new JSON string to store on Case.facts_json."""
    facts = CaseFacts.from_json(existing_json)
    entities = extract_entities(latest_message)
    facts = merge_facts(facts, entities)
    return facts.to_json()


def facts_summary_for_prompt(facts_json: Optional[str]) -> str:
    """Builds a short 'known facts, do not re-ask' block for the LLM system
    prompt. Empty string if nothing known yet."""
    facts = CaseFacts.from_json(facts_json)
    lines = []
    if facts.platform:
        lines.append(f"- Platform involved: {facts.platform}")
    if facts.bank:
        lines.append(f"- Bank involved: {facts.bank}")
    if facts.amount_lost:
        lines.append(f"- Amount mentioned: {facts.amount_lost}")
    if facts.timeline:
        lines.append(f"- Reported timing: {facts.timeline}")
    if facts.phone_numbers:
        lines.append(f"- Phone number(s) on record: {len(facts.phone_numbers)} (already captured, do not ask again)")
    if facts.emails:
        lines.append(f"- Email(s) on record: {len(facts.emails)} (already captured)")
    if facts.upi_ids:
        lines.append(f"- UPI ID(s) on record: {len(facts.upi_ids)} (already captured)")
    if facts.transaction_ids:
        lines.append(f"- Transaction/reference ID(s) on record: {len(facts.transaction_ids)} (already captured)")
    if facts.urls:
        lines.append(f"- Suspicious URL(s) on record: {len(facts.urls)} (already captured)")

    if not lines:
        return ""
    return (
        "KNOWN CASE FACTS (already told to you earlier in this case — do NOT ask for these again, "
        "just use them):\n" + "\n".join(lines)
    )
