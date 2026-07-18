"""
Institution Emergency Routing Engine.

Decides whether an incident should surface institution contacts (from an
InstitutionProfile) ahead of government guidance, or fall back to
government-only guidance. Government helplines (legal_kb.HELPLINES) are
reused as-is — this module does not duplicate that list, it just decides
ordering/inclusion.

Routing rule (per spec):
  IF an institution is selected AND the incident category is one of:
    cyberbullying, blackmail, stalking, ragging, harassment,
    women safety, student safety
  THEN recommend the institution profile first, then government guidance.
  OTHERWISE recommend government guidance only.

"Ragging" and "student safety" aren't existing CyberCategory values, so
this module also does a lightweight text check for those specific terms
(the intent classifier has no such category) alongside the categories it
already produces.
"""

import re
from dataclasses import dataclass, field
from typing import List, Optional

from app.knowledge_base.institution_profiles import InstitutionProfile, ContactPoint, get_institution_profile
from app.knowledge_base.legal_kb import get_relevant_helplines
from app.services.intent_classifier import CyberCategory

# Cyber categories that qualify for institution-first routing.
_INSTITUTION_ELIGIBLE_CATEGORIES = {
    CyberCategory.CYBERBULLYING.value,
    CyberCategory.ONLINE_HARASSMENT.value,
    CyberCategory.BLACKMAIL.value,
    CyberCategory.STALKING.value,
    CyberCategory.SOCIAL_MEDIA_ABUSE.value,
}

# "Ragging" and generic "student/women safety" phrasing aren't a distinct
# CyberCategory, so also check the raw incident text directly.
_RAGGING_RE = re.compile(r"\bragging\b|\branged\b|\bsenior(?:s)? (?:harass|torment|bull)", re.IGNORECASE)
_WOMEN_SAFETY_RE = re.compile(r"\bwomen'?s? safety\b|\beve.?teasing\b|\bmolest\w*\b|\bharass\w* (?:me|her) (?:on|at) campus\b", re.IGNORECASE)
_STUDENT_SAFETY_RE = re.compile(r"\bstudent safety\b|\bcampus safety\b|\bhostel\b.{0,20}\b(?:unsafe|threat|harass)", re.IGNORECASE)


def _is_institution_eligible(category_value: Optional[str], incident_text: str) -> bool:
    if category_value in _INSTITUTION_ELIGIBLE_CATEGORIES:
        return True
    text = incident_text or ""
    if _RAGGING_RE.search(text) or _WOMEN_SAFETY_RE.search(text) or _STUDENT_SAFETY_RE.search(text):
        return True
    return False


@dataclass
class InstitutionRoutingResult:
    institution_recommended: bool
    institution_profile: Optional[InstitutionProfile]
    institution_contacts: List[dict] = field(default_factory=list)   # only non-empty contacts
    government_contacts: List[dict] = field(default_factory=list)
    routing_reason: str = ""


def route(
    institution_id: Optional[str],
    category_value: Optional[str],
    incident_text: str = "",
) -> InstitutionRoutingResult:
    """
    Core routing decision. Never raises — an unknown/missing institution_id
    or ineligible category simply falls back to government-only guidance,
    which is always safe to show.
    """
    government_contacts = [
        {"name": h["name"], "contact": h["number"], "use_for": h["use_for"]}
        for h in get_relevant_helplines(category=category_value)
    ]

    profile = get_institution_profile(institution_id)

    if profile is None:
        return InstitutionRoutingResult(
            institution_recommended=False,
            institution_profile=None,
            institution_contacts=[],
            government_contacts=government_contacts,
            routing_reason=(
                "No institution selected or institution not recognized — showing government "
                "guidance only."
            ),
        )

    eligible = _is_institution_eligible(category_value, incident_text)

    if not eligible:
        return InstitutionRoutingResult(
            institution_recommended=False,
            institution_profile=profile,
            institution_contacts=[],
            government_contacts=government_contacts,
            routing_reason=(
                f"Institution '{profile.institution_name}' is configured, but this incident "
                f"category isn't one routed to institutional contacts (cyberbullying, "
                f"harassment, blackmail, stalking, ragging, women/student safety) — showing "
                f"government guidance only."
            ),
        )

    institution_contacts = [
        {"label": cp.label, "contact": cp.contact, "notes": cp.notes}
        for cp in profile.contact_points()
        if cp.contact.strip()
    ]

    reason = (
        f"Incident occurred at / involves '{profile.institution_name}' and matches an "
        f"institution-eligible category — institutional contacts are recommended first, "
        f"followed by government guidance."
    )
    if not institution_contacts:
        reason += (
            " Note: this institution's contact details have not been configured yet in the "
            "backend, so no specific institutional numbers can be shown yet — government "
            "guidance is provided in the meantime."
        )

    return InstitutionRoutingResult(
        institution_recommended=True,
        institution_profile=profile,
        institution_contacts=institution_contacts,
        government_contacts=government_contacts,
        routing_reason=reason,
    )
