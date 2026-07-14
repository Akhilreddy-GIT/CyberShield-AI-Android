"""
Database models — SQLite via SQLAlchemy.

Phase 13 spec: store Case ID, Anonymous User ID, Conversation, Evidence
metadata, Risk Level, Category, Timeline, Status.
Never store passwords or unrelated personal information — there IS no
user accounts table by design; identity is a random anonymous session id.
"""

import uuid
from datetime import datetime, timezone
from sqlalchemy import create_engine, Column, String, DateTime, Integer, Text, ForeignKey, Boolean
from sqlalchemy.orm import declarative_base, relationship, sessionmaker

DATABASE_URL = "sqlite:///./cybershield.db"
engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})
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


def init_db():
    Base.metadata.create_all(bind=engine)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
