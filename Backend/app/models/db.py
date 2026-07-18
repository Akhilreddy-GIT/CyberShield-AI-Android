"""
Database models — SQLite via SQLAlchemy.

Phase 13 spec: store Case ID, Anonymous User ID, Conversation, Evidence
metadata, Risk Level, Category, Timeline, Status.
Never store passwords or unrelated personal information — there IS no
user accounts table by design; identity is a random anonymous session id.
"""

import os
import uuid
from datetime import datetime, timezone
from sqlalchemy import create_engine, Column, String, DateTime, Integer, Text, ForeignKey, Boolean
from sqlalchemy.orm import declarative_base, relationship, sessionmaker

# ROOT CAUSE FIX ("Android receives a Case ID, then later gets 404 Case not
# found"): this used to be the cwd-relative path "sqlite:///./cybershield.db".
# SQLite resolves a relative path against the process's CURRENT WORKING
# DIRECTORY at connection time, not against this file's location. Any
# difference in how/where the server process is launched from run to run
# (a process manager with a different WorkingDirectory, `uvicorn --reload`'s
# subprocess, a container WORKDIR that doesn't match the repo checkout,
# running `python -m app.main` from the repo root one time and from inside
# `app/` another time, etc.) silently points the app at a DIFFERENT, empty
# SQLite file. The server still starts fine, still creates new cases fine —
# they just land in a different file than the one Android's earlier case ID
# was written to, so that same-looking backend now says "Case not found" for
# a real, previously-issued case ID. This is exactly the same absolute-path
# pattern already used correctly elsewhere in this codebase (see
# `vector_rag.py`'s `_BACKEND_ROOT`/`_CHROMA_DIR` and `evidence.py`'s
# `UPLOAD_DIR`) — db.py was the one inconsistent, cwd-dependent path.
#
# Fixed by anchoring to this file's location (stable regardless of cwd) and,
# for real deployments (e.g. a managed Postgres instance, or a persistent
# disk mount at a specific path), allowing a full override via the
# DATABASE_URL environment variable.
_BACKEND_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_DEFAULT_SQLITE_PATH = os.path.join(_BACKEND_ROOT, "cybershield.db")
DATABASE_URL = os.getenv("DATABASE_URL", f"sqlite:///{_DEFAULT_SQLITE_PATH}")

_connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}
engine = create_engine(DATABASE_URL, connect_args=_connect_args)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def gen_id(prefix: str) -> str:
    return f"{prefix}-{uuid.uuid4().hex[:10]}"


class User(Base):
    """
    Optional account layer. A user still gets an anon_user_id like any
    anonymous visitor — registering just lets that anon_user_id (and its
    cases) follow them across devices via login, instead of being tied to
    one browser's local storage. No email or phone required to register.
    """
    __tablename__ = "users"

    id = Column(String, primary_key=True, default=lambda: gen_id("USR"))
    username = Column(String, nullable=False, unique=True, index=True)
    password_hash = Column(String, nullable=False)
    anon_user_id = Column(String, nullable=False, unique=True)  # links to existing case data
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class Case(Base):
    __tablename__ = "cases"

    id = Column(String, primary_key=True, default=lambda: gen_id("CASE"))
    anon_user_id = Column(String, nullable=False, index=True)  # random token, not tied to identity
    category = Column(String, nullable=True)
    risk_level = Column(String, default="Low Risk")
    risk_score = Column(Integer, default=0)
    status = Column(String, default="open")  # open | under_review | escalated | closed
    facts_json = Column(Text, nullable=True)  # persisted case memory — see case_memory_service.py
    recovery_stage_override = Column(String, nullable=True)  # "resolved" | "closed" | None — see recovery_lifecycle_service.py; ONLY ever set by an explicit POST /api/cases/{id}/resolve or /close action, never inferred
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc))

    messages = relationship("Message", back_populates="case", cascade="all, delete-orphan")
    evidence_items = relationship("Evidence", back_populates="case", cascade="all, delete-orphan")
    timeline_events = relationship("TimelineEvent", back_populates="case", cascade="all, delete-orphan")


class Message(Base):
    __tablename__ = "messages"

    id = Column(String, primary_key=True, default=lambda: gen_id("MSG"))
    case_id = Column(String, ForeignKey("cases.id"), nullable=False)
    role = Column(String, nullable=False)  # user | assistant
    content = Column(Text, nullable=False)
    cited_sources = Column(Text, nullable=True)  # comma-separated titles
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    case = relationship("Case", back_populates="messages")


class Evidence(Base):
    __tablename__ = "evidence"

    id = Column(String, primary_key=True, default=lambda: gen_id("EVD"))
    case_id = Column(String, ForeignKey("cases.id"), nullable=False)
    filename = Column(String, nullable=False)
    stored_path = Column(String, nullable=False)
    file_type = Column(String, nullable=True)
    description = Column(Text, nullable=True)
    extracted_text = Column(Text, nullable=True)  # OCR output, if applicable
    uploaded_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    case = relationship("Case", back_populates="evidence_items")


class TimelineEvent(Base):
    __tablename__ = "timeline_events"

    id = Column(String, primary_key=True, default=lambda: gen_id("TL"))
    case_id = Column(String, ForeignKey("cases.id"), nullable=False)
    description = Column(Text, nullable=False)
    event_time = Column(String, nullable=True)  # user-reported, may be approximate
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    case = relationship("Case", back_populates="timeline_events")


class GuardianEvent(Base):
    """
    Every Guardian activity (notification scanned, call analyzed, evidence
    correlated, risk changed, etc) becomes one row here. This is what the
    Guardian Timeline is built from. anon_user_id is always set (Guardian
    can run before any Case exists — e.g. a scanned notification with no
    case yet); case_id is optional and only set once a case is opened or
    correlated to an existing one.
    """
    __tablename__ = "guardian_events"

    id = Column(String, primary_key=True, default=lambda: gen_id("GEVT"))
    anon_user_id = Column(String, nullable=False, index=True)
    case_id = Column(String, ForeignKey("cases.id"), nullable=True, index=True)
    event_type = Column(String, nullable=False)  # see guardian_event_service.EventType
    severity = Column(String, nullable=False, default="info")  # info | low | medium | high | critical
    risk_score = Column(Integer, default=0)
    summary = Column(Text, nullable=False)
    explanation = Column(Text, nullable=True)
    related_evidence_id = Column(String, nullable=True)
    source = Column(String, nullable=False, default="system")  # notification | call | evidence | system
    extra_json = Column(Text, nullable=True)  # category, confidence, raw signal labels, etc
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), index=True)

    case = relationship("Case")


class NotificationScan(Base):
    """One analyzed Android notification. Kept even when not scam-flagged
    (LOW/benign) so Guardian Intelligence has full signal history to
    correlate against later, per Feature 4."""
    __tablename__ = "notification_scans"

    id = Column(String, primary_key=True, default=lambda: gen_id("NSCAN"))
    anon_user_id = Column(String, nullable=False, index=True)
    case_id = Column(String, ForeignKey("cases.id"), nullable=True, index=True)
    app_name = Column(String, nullable=True)
    notification_title = Column(Text, nullable=True)
    notification_text = Column(Text, nullable=True)
    notification_category = Column(String, nullable=True)  # OTP scam, fake bank alert, etc
    threat_type = Column(String, nullable=True)
    risk_score = Column(Integer, default=0)
    confidence_score = Column(Integer, default=0)
    severity = Column(String, nullable=False, default="info")
    is_interaction_dangerous = Column(Boolean, default=False)
    explanation = Column(Text, nullable=True)
    recommendation = Column(Text, nullable=True)
    triggered_signals_json = Column(Text, nullable=True)
    reported_at = Column(String, nullable=True)  # client-supplied notification timestamp, as sent
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), index=True)


class CallAnalysis(Base):
    """One analyzed call report. caller_number is stored as given by the
    client (unknown numbers are legitimately allowed and NOT auto-flagged
    as scam — see call_guardian_service)."""
    __tablename__ = "call_analyses"

    id = Column(String, primary_key=True, default=lambda: gen_id("CALL"))
    anon_user_id = Column(String, nullable=False, index=True)
    case_id = Column(String, ForeignKey("cases.id"), nullable=True, index=True)
    caller_number = Column(String, nullable=True)
    contact_name = Column(String, nullable=True)
    call_duration_seconds = Column(Integer, nullable=True)
    conversation_summary = Column(Text, nullable=True)
    user_notes = Column(Text, nullable=True)
    scam_category = Column(String, nullable=True)
    is_unknown_number = Column(Boolean, default=True)
    risk_score = Column(Integer, default=0)
    confidence_score = Column(Integer, default=0)
    urgency_level = Column(String, nullable=True)  # low | medium | high
    manipulation_tactics_json = Column(Text, nullable=True)
    possible_impersonation = Column(String, nullable=True)
    explanation = Column(Text, nullable=True)
    recovery_recommendations_json = Column(Text, nullable=True)
    authorities_to_contact_json = Column(Text, nullable=True)
    triggered_signals_json = Column(Text, nullable=True)
    reported_at = Column(String, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), index=True)


def init_db():
    Base.metadata.create_all(bind=engine)
    _ensure_column_exists("cases", "facts_json", "TEXT")
    _ensure_column_exists("cases", "recovery_stage_override", "TEXT")


def _ensure_column_exists(table: str, column: str, sql_type: str) -> None:
    """SQLAlchemy's create_all() only creates missing TABLES, not missing
    COLUMNS on tables that already exist. Since this project stores data in
    a local SQLite file that may already exist from a previous run (before
    facts_json was added), do a minimal manual ALTER TABLE so existing
    databases upgrade in place instead of crashing on first query."""
    with engine.connect() as conn:
        existing_cols = {row[1] for row in conn.exec_driver_sql(f"PRAGMA table_info({table})")}
        if column not in existing_cols:
            conn.exec_driver_sql(f"ALTER TABLE {table} ADD COLUMN {column} {sql_type}")
            conn.commit()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
