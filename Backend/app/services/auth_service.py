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

JWT_ALGORITHM = "HS256"
JWT_EXPIRY_HOURS = 24 * 7  # 7 days


def _load_or_create_dev_secret() -> str:
    """
    ROOT CAUSE FIX (session-expiration / random-logout bug): this used to be
    `os.getenv("JWT_SECRET", "dev-secret-" + os.urandom(8).hex())`, i.e. a
    brand-new random secret generated every time the module was imported.
    That meant:
      1. Every server restart silently invalidated every previously issued
         token — every logged-in user was force-logged-out on any deploy or
         crash-restart, with no error other than a mysteriously-failing
         auth check.
      2. Under multiple worker processes (gunicorn -w N, or any multi-worker
         ASGI setup), each worker generated its OWN random secret, so a
         token issued by whichever worker handled /login would silently
         fail to validate on any other worker handling a later request —
         intermittent, worker-dependent auth failures.
    Fixed by persisting the generated fallback secret to a file next to the
    database (stable across restarts, shared across workers on the same
    host/volume) instead of regenerating it in memory on every import. Real
    deployments should still set the JWT_SECRET env var explicitly; this
    fallback only covers local/dev runs where it isn't set.
    """
    env_secret = os.getenv("JWT_SECRET")
    if env_secret:
        return env_secret
    backend_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    secret_path = os.path.join(backend_root, ".jwt_secret")
    try:
        if os.path.exists(secret_path):
            with open(secret_path, "r") as f:
                existing = f.read().strip()
                if existing:
                    return existing
        new_secret = os.urandom(32).hex()
        with open(secret_path, "w") as f:
            f.write(new_secret)
        return new_secret
    except OSError:
        # Read-only filesystem or similar — fall back to an in-process
        # secret rather than crashing startup. Real deployments should set
        # JWT_SECRET explicitly to avoid ever hitting this branch.
        return "dev-secret-change-in-production-" + os.urandom(8).hex()


JWT_SECRET = _load_or_create_dev_secret()


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
