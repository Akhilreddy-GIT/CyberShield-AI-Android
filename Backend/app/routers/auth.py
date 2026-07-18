from fastapi import APIRouter, Depends, HTTPException, Header
from sqlalchemy.orm import Session
from pydantic import BaseModel
from typing import Optional

from app.models.db import get_db, User, gen_id
from app.services.auth_service import hash_password, verify_password, create_access_token, decode_access_token

router = APIRouter(prefix="/api/auth", tags=["auth"])


class RegisterIn(BaseModel):
    username: str
    password: str


class LoginIn(BaseModel):
    username: str
    password: str


@router.post("/register")
def register(payload: RegisterIn, db: Session = Depends(get_db)):
    if len(payload.username.strip()) < 3:
        raise HTTPException(400, "Username must be at least 3 characters")
    if len(payload.password) < 6:
        raise HTTPException(400, "Password must be at least 6 characters")

    existing = db.query(User).filter(User.username == payload.username).first()
    if existing:
        raise HTTPException(409, "Username already taken")

    anon_id = "anon-" + gen_id("")[:12]
    user = User(
        username=payload.username,
        password_hash=hash_password(payload.password),
        anon_user_id=anon_id,
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    token = create_access_token(user.id, user.username)
    return {"token": token, "anon_user_id": user.anon_user_id, "username": user.username}


@router.post("/login")
def login(payload: LoginIn, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.username == payload.username).first()
    if not user or not verify_password(payload.password, user.password_hash):
        raise HTTPException(401, "Invalid username or password")

    token = create_access_token(user.id, user.username)
    return {"token": token, "anon_user_id": user.anon_user_id, "username": user.username}


def get_current_user_optional(authorization: Optional[str] = Header(None), db: Session = Depends(get_db)) -> Optional[User]:
    """
    Used by endpoints that work fine anonymously but can personalize when
    a valid token is present. Never raises — a missing/invalid token just
    means "anonymous", not an error, preserving Phase 9's guarantee.
    """
    if not authorization or not authorization.startswith("Bearer "):
        return None
    token = authorization.removeprefix("Bearer ").strip()
    payload = decode_access_token(token)
    if not payload:
        return None
    return db.query(User).filter(User.id == payload["sub"]).first()
