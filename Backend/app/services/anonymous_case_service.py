"""
Anonymous Crisis Mode.

The existing database is ALREADY anonymous-by-design: Case rows are keyed
by anon_user_id (a random token), and there is no identity field anywhere
in the schema (see models/db.py's module docstring). This module doesn't
change that — it adds a short, memorable, shareable code (e.g.
"CS-9F31ABX") on top of the existing Case.id / anon_user_id pair, purely
for the user's own reference (e.g. to read back to a counsellor or write
down), without exposing the raw internal id format.

Linking an anonymous case to a registered account already works via the
existing auth flow: registering/logging in returns an anon_user_id, and if
that value matches the anon_user_id the anonymous case was created under,
the case is already "theirs" — no separate data migration needed. This
module exposes that explicitly as `link_anonymous_case_to_account` for
clarity from the client side.
"""

import re
import secrets
import string
from dataclasses import dataclass
from typing import Optional

from sqlalchemy.orm import Session

from app.models.db import Case, gen_id

_CODE_ALPHABET = string.ascii_uppercase + string.digits
_CODE_RE = re.compile(r"^CS-[A-Z0-9]{7}$")


def generate_case_code() -> str:
    """Generates a short public-facing code like 'CS-9F31ABX'. Purely a
    display/reference alias — the database's real primary key is still
    Case.id (e.g. 'CASE-...'), unchanged."""
    return "CS-" + "".join(secrets.choice(_CODE_ALPHABET) for _ in range(7))


def is_valid_case_code(code: str) -> bool:
    return bool(_CODE_RE.match((code or "").strip().upper()))


@dataclass
class AnonymousCaseHandle:
    case_id: str            # internal id (CASE-...), used by existing endpoints
    anon_user_id: str       # existing anonymous identity token
    case_code: str          # new short public code, e.g. CS-9F31ABX


def start_anonymous_case(db: Session, category: Optional[str] = None) -> AnonymousCaseHandle:
    """
    Creates a brand-new anonymous identity + case with zero personal
    information collected — no name, no email, no phone. Returns a handle
    the client stores locally (e.g. secure device storage) to continue the
    conversation later. Reuses the exact same Case model that every other
    part of the app (Guardian, Timeline, Evidence, Case Memory) already
    writes to, so nothing downstream needs special-casing for "anonymous
    crisis" cases — they're just ordinary cases under a fresh anon id.
    """
    anon_user_id = "anon-" + gen_id("")[:12]
    case = Case(anon_user_id=anon_user_id, category=category)
    db.add(case)
    db.commit()
    db.refresh(case)

    return AnonymousCaseHandle(
        case_id=case.id,
        anon_user_id=anon_user_id,
        case_code=generate_case_code(),
    )


def link_anonymous_case_to_account(db: Session, case_id: str, account_anon_user_id: str) -> bool:
    """
    Links an existing anonymous case to a registered account, by
    re-pointing the case's anon_user_id to the account's anon_user_id
    (obtained from /api/auth/register or /api/auth/login). This is an
    explicit, opt-in action the user must request — nothing links
    automatically. Returns False if the case doesn't exist so callers can
    404 appropriately.
    """
    case = db.query(Case).filter(Case.id == case_id).first()
    if not case:
        return False
    case.anon_user_id = account_anon_user_id
    db.commit()
    return True
