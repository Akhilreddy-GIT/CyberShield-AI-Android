"""
Institution Emergency Routing — Profile Configuration.

Generic, data-driven institution profiles. Each profile describes the
institutional contact points relevant to campus/college safety incidents
(women's cell, anti-ragging committee, student welfare, campus security,
emergency faculty, college counselling, local police liaison) — NOT the
national government helplines, which stay in legal_kb.HELPLINES and are
always available as a fallback/complement regardless of institution.

Design notes:
- Contact values are intentionally left empty ("") for profiles that
  haven't been populated with real official numbers yet. The routing
  engine treats an empty contact as "not yet configured" and skips it in
  the rendered response rather than showing a blank or fabricated number
  — this backend must never invent a phone number.
- New institutions are added by appending a new INSTITUTION_PROFILES
  entry — no other code changes required (routing logic is generic).
- institution_id is the stable key used by the API (query param / request
  body) to select a profile; institution_name is the display name.
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional


@dataclass
class ContactPoint:
    label: str
    contact: str = ""          # phone number / email / URL — left empty until supplied
    notes: str = ""            # e.g. "Available 9am-5pm, Mon-Fri"


@dataclass
class InstitutionProfile:
    institution_id: str
    institution_name: str
    emergency_head: ContactPoint
    womens_cell: ContactPoint
    anti_ragging_committee: ContactPoint
    security_office: ContactPoint
    dean: ContactPoint
    principal: ContactPoint
    student_welfare: ContactPoint
    college_counselling: ContactPoint
    local_police: ContactPoint
    notes: str = ""

    def has_any_contact_configured(self) -> bool:
        return any(cp.contact.strip() for cp in self.contact_points())

    def contact_points(self) -> List[ContactPoint]:
        """Ordered list of contact points, most safety-critical / fastest
        first. New institutions can add or omit fields freely — the
        routing engine only ever renders contacts with a non-empty
        `contact` value, so an unconfigured field is simply skipped."""
        return [
            self.emergency_head,
            self.security_office,
            self.womens_cell,
            self.anti_ragging_committee,
            self.dean,
            self.principal,
            self.student_welfare,
            self.college_counselling,
            self.local_police,
        ]


# ---------------------------------------------------------------------------
# Registered institution profiles.
# Contact values are left empty ("") for GVP by design — to be filled in
# with the college's actual official numbers/emails later. Do NOT invent
# placeholder numbers here.
# ---------------------------------------------------------------------------
INSTITUTION_PROFILES: Dict[str, InstitutionProfile] = {
    "gvp": InstitutionProfile(
        institution_id="gvp",
        institution_name="Gayatri Vidya Parishad College of Engineering (GVPCE)",
        emergency_head=ContactPoint(label="Emergency Head"),
        womens_cell=ContactPoint(label="Women's Cell"),
        anti_ragging_committee=ContactPoint(label="Anti-Ragging Committee"),
        security_office=ContactPoint(label="Security Office"),
        dean=ContactPoint(label="Dean"),
        principal=ContactPoint(label="Principal"),
        student_welfare=ContactPoint(label="Student Welfare"),
        college_counselling=ContactPoint(label="College Counselling Cell"),
        local_police=ContactPoint(label="Local Police Station (jurisdiction over campus)"),
        notes=(
            "Contact details not yet configured — add the official GVP number/email to "
            "each ContactPoint's `contact` field below once available (e.g. "
            "emergency_head=ContactPoint(label=\"Emergency Head\", contact=\"+91XXXXXXXXXX\")). "
            "The routing engine automatically starts including a contact as soon as its "
            "`contact` value is non-empty — no other code changes needed."
        ),
    ),
}


def get_institution_profile(institution_id: Optional[str]) -> Optional[InstitutionProfile]:
    """Looks up a profile by id. Returns None for missing/unknown ids so
    callers can cleanly fall back to government-only guidance."""
    if not institution_id:
        return None
    return INSTITUTION_PROFILES.get(institution_id.strip().lower())


def list_institution_profiles() -> List[Dict]:
    """Lightweight listing for a picker UI — id + display name only."""
    return [
        {"institution_id": p.institution_id, "institution_name": p.institution_name}
        for p in INSTITUTION_PROFILES.values()
    ]
