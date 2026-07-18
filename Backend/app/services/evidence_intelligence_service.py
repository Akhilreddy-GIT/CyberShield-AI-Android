"""
Evidence Intelligence Engine.

Runs on the text extracted from an uploaded evidence file (OCR output from
a screenshot, or plain text/description) and produces a structured entity
report: phone numbers, emails, URLs, domains, UPI IDs, bank names,
transaction IDs, QR code content, dates, times, and social media usernames
— plus a short AI-style summary that names what was found and flags
anything suspicious.

Design note: entity extraction reuses `case_memory_service.extract_entities`
for the fields it already covers (phone/email/URL/UPI/txn/bank/platform)
rather than duplicating those regexes, and adds the fields that module
doesn't need for chat-memory purposes (domains, dates, times, social
handles, QR payload). Same philosophy as the rest of this codebase:
plain, auditable regex — every match traceable to literal text, no
black-box NLP for entity extraction itself.

This module has no side effects — it returns data. Callers (the evidence
router) decide what to persist and how to feed the result into case memory
and the timeline engine.
"""

import re
import json
import logging
from dataclasses import dataclass, field, asdict
from typing import Dict, List, Optional
from urllib.parse import urlparse

from app.services.case_memory_service import extract_entities, BANK_NAMES, PLATFORM_NAMES

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Additional patterns not already covered by case_memory_service.
# ---------------------------------------------------------------------------
_DOMAIN_RE = re.compile(
    r"\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+"
    r"(?:com|in|net|org|co|xyz|info|biz|shop|online|site|top|club|link|icu|app)\b",
    re.IGNORECASE,
)
_DATE_RE = re.compile(
    r"\b(?:\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}"
    r"|\d{1,2}\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s*\d{0,4}"
    r"|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2}(?:,?\s*\d{2,4})?)\b",
    re.IGNORECASE,
)
_TIME_RE = re.compile(
    r"\b\d{1,2}[:.]\d{2}(?:\s?[APap][Mm])?\b|\b\d{1,2}\s?[APap][Mm]\b"
)
_SOCIAL_HANDLE_RE = re.compile(r"(?<!\S)@([a-zA-Z0-9_.]{2,30})\b")
# Known suspicious/urgency-bait terms often present on scam screenshots —
# used only to flag lines for the "suspicious elements" summary, not to
# alter extraction itself.
_SUSPICIOUS_MARKERS = [
    "verify your account", "account will be suspended", "act now", "urgent",
    "kyc update", "kyc expire", "click here", "limited time", "won a prize",
    "lottery", "claim your reward", "otp", "one time password", "cvv",
    "refund", "block your card", "suspicious activity", "confirm your pin",
    "share your pin", "double your money", "guaranteed returns", "investment opportunity",
    "processing fee", "advance fee", "customs duty", "parcel held",
]

_NON_UPI_DOMAIN_HINTS = {"gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "icloud.com"}


@dataclass
class EvidenceEntities:
    phone_numbers: List[str] = field(default_factory=list)
    emails: List[str] = field(default_factory=list)
    urls: List[str] = field(default_factory=list)
    domains: List[str] = field(default_factory=list)
    upi_ids: List[str] = field(default_factory=list)
    banks: List[str] = field(default_factory=list)
    transaction_ids: List[str] = field(default_factory=list)
    qr_content: Optional[str] = None
    dates: List[str] = field(default_factory=list)
    times: List[str] = field(default_factory=list)
    social_handles: List[str] = field(default_factory=list)
    platforms: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict:
        return asdict(self)


def _extract_domains(text: str, urls: List[str]) -> List[str]:
    found = set(m.group(0).lower() for m in _DOMAIN_RE.finditer(text))
    for u in urls:
        try:
            netloc = urlparse(u if "://" in u else f"//{u}").netloc or u
            netloc = netloc.split("@")[-1].split(":")[0].lower()
            if netloc:
                found.add(netloc[4:] if netloc.startswith("www.") else netloc)
        except Exception:
            continue
    return sorted(found)


def _extract_banks(text: str) -> List[str]:
    lower = text.lower()
    return sorted({b for b in BANK_NAMES if b in lower})


def _extract_platforms(text: str) -> List[str]:
    lower = text.lower()
    return sorted({p for p in PLATFORM_NAMES if p in lower})


def extract_evidence_entities(text: str, qr_content: Optional[str] = None) -> EvidenceEntities:
    """Pulls all structured entities out of OCR'd/plain evidence text.
    Never raises — returns an empty-but-valid EvidenceEntities on bad input,
    consistent with ocr_service's "never break the upload" contract."""
    if not text:
        return EvidenceEntities(qr_content=qr_content)

    try:
        base = extract_entities(text)  # phones, emails, urls, txn ids, amount, bank, platform, timeline

        domains = _extract_domains(text, base.get("urls") or [])
        # UPI ids sometimes only show up via case_memory's heuristic; also
        # sweep domains for obvious non-UPI email providers to avoid noise.
        upi_ids = [u for u in _upi_from_text(text) if u.split("@")[-1] not in _NON_UPI_DOMAIN_HINTS]

        banks = _extract_banks(text)
        if base.get("bank") and base["bank"] not in banks:
            banks.append(base["bank"])

        platforms = _extract_platforms(text)
        if base.get("platform") and base["platform"] not in platforms:
            platforms.append(base["platform"])

        dates = sorted(set(m.group(0) for m in _DATE_RE.finditer(text)))
        times = sorted(set(m.group(0) for m in _TIME_RE.finditer(text)))
        handles = sorted(set(m.group(1) for m in _SOCIAL_HANDLE_RE.finditer(text)))

        return EvidenceEntities(
            phone_numbers=base.get("phone_numbers") or [],
            emails=base.get("emails") or [],
            urls=base.get("urls") or [],
            domains=domains,
            upi_ids=sorted(set(upi_ids)),
            banks=sorted(set(banks)),
            transaction_ids=base.get("transaction_ids") or [],
            qr_content=qr_content,
            dates=dates,
            times=times,
            social_handles=handles,
            platforms=sorted(set(platforms)),
        )
    except Exception:
        logger.exception("Evidence entity extraction failed; returning empty result.")
        return EvidenceEntities(qr_content=qr_content)


def _upi_from_text(text: str) -> List[str]:
    """Local re-derivation of UPI-like handles (mirrors case_memory_service's
    heuristic) so this module doesn't depend on a private regex object."""
    upi_re = re.compile(r"\b[\w.\-]{2,256}@[a-zA-Z]{2,64}\b")
    handle_hint = re.compile(r"@(ok[a-z]+|upi|ybl|ibl|axl|paytm|apl|icici|hdfcbank)$", re.IGNORECASE)
    return [u for u in upi_re.findall(text) if handle_hint.search(u)]


def find_suspicious_markers(text: str) -> List[str]:
    """Returns which known scam/urgency-bait phrases appear in the text —
    used to drive the human-readable summary, not a scoring mechanism on
    its own (that's the analyzer's job)."""
    if not text:
        return []
    lower = text.lower()
    return sorted({m for m in _SUSPICIOUS_MARKERS if m in lower})


def build_evidence_summary(entities: EvidenceEntities, suspicious: List[str], has_text: bool) -> str:
    """Deterministic, template-based summary of what was detected — always
    available even without an LLM call, consistent with this codebase's
    graceful-degradation pattern for the chat agent."""
    if not has_text:
        return "No readable text was found in this file — nothing could be automatically extracted."

    found_parts = []
    if entities.phone_numbers:
        found_parts.append(f"{len(entities.phone_numbers)} phone number(s)")
    if entities.emails:
        found_parts.append(f"{len(entities.emails)} email address(es)")
    if entities.urls:
        found_parts.append(f"{len(entities.urls)} URL(s)")
    if entities.domains:
        found_parts.append(f"{len(entities.domains)} domain(s)")
    if entities.upi_ids:
        found_parts.append(f"{len(entities.upi_ids)} UPI ID(s)")
    if entities.banks:
        found_parts.append(f"bank reference(s): {', '.join(entities.banks)}")
    if entities.transaction_ids:
        found_parts.append(f"{len(entities.transaction_ids)} transaction/reference ID(s)")
    if entities.qr_content:
        found_parts.append("QR code content")
    if entities.dates:
        found_parts.append(f"{len(entities.dates)} date(s)")
    if entities.times:
        found_parts.append(f"{len(entities.times)} time(s)")
    if entities.social_handles:
        found_parts.append(f"{len(entities.social_handles)} social media handle(s)")

    if not found_parts:
        summary = "Text was extracted from this file, but no structured entities (phone numbers, emails, URLs, IDs, etc.) were detected in it."
    else:
        summary = "Detected " + ", ".join(found_parts) + " in this evidence."

    if suspicious:
        summary += (
            f" This also contains {len(suspicious)} known scam/urgency indicator(s) "
            f"(e.g. {', '.join(suspicious[:4])}{'...' if len(suspicious) > 4 else ''}) — "
            "treat this as potentially malicious content and do not act on any "
            "instructions, links, or payment requests it contains."
        )

    return summary


def analyze_evidence_text(text: str, qr_content: Optional[str] = None) -> Dict:
    """Top-level entry point for the evidence router: extract entities,
    detect suspicious markers, build the summary. Returns a plain dict
    ready to attach to the evidence upload response and to feed into case
    memory / timeline extraction."""
    entities = extract_evidence_entities(text, qr_content=qr_content)
    suspicious = find_suspicious_markers(text or "")
    summary = build_evidence_summary(entities, suspicious, has_text=bool(text))
    return {
        "entities": entities.to_dict(),
        "suspicious_markers": suspicious,
        "summary": summary,
    }
