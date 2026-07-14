"""
JWT Authentication — optional accounts, anonymous reporting always preserved.

Design principle (per Phase 9 of the original spec): anonymous access must
never be degraded by adding accounts. So:
  - Every existing endpoint keeps working with just an anon_user_id, no
    token required — nothing here is a breaking change.
  - A NEW optional account layer lets someone register with just a
    username + password (no email/phone required) if they want their
    cases to follow them across devices.
  - A registered user's cases are still stored against an anon_user_id
    internally — accounts are an optional convenience layer on top of the
    same anonymous-first storage model, not a replacement for it.
"""

import os
import bcrypt
import jwt
from datetime import datetime, timedelta, timezone
from typing import Optional

JWT_SECRET = os.getenv("JWT_SECRET", "dev-secret-change-in-production-" + os.urandom(8).hex())
JWT_ALGORITHM = "HS256"
JWT_EXPIRY_HOURS = 24 * 7  # 7 days


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()


def verify_password(password: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode(), hashed.encode())
    except Exception:
        return False


def create_access_token(user_id: str, username: str) -> str:
    payload = {
        "sub": user_id,
        "username": username,
        "exp": datetime.now(timezone.utc) + timedelta(hours=JWT_EXPIRY_HOURS),
        "iat": datetime.now(timezone.utc),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)


def decode_access_token(token: str) -> Optional[dict]:
    try:
        return jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    except jwt.ExpiredSignatureError:
        return None
    except jwt.InvalidTokenError:
        return None
