from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime


class ChatMessageIn(BaseModel):
    case_id: Optional[str] = None
    anon_user_id: str
    message: str


class ChatMessageOut(BaseModel):
    case_id: str
    reply: str
    category: Optional[str]
    is_critical: bool
    cited_sources: List[str]
    used_llm: bool
    helplines: Optional[list] = None


class RiskFactorsIn(BaseModel):
    case_id: str
    is_repeated_incident: bool = False
    involves_threat_of_harm: bool = False
    involves_minor: bool = False
    involves_financial_loss: bool = False
    involves_explicit_content: bool = False
    accused_knows_victim_location: bool = False
    ongoing_blackmail: bool = False
    victim_reports_feeling_unsafe: bool = False


class TimelineEventIn(BaseModel):
    case_id: str
    description: str
    event_time: Optional[str] = None


class CaseOut(BaseModel):
    id: str
    category: Optional[str]
    risk_level: str
    risk_score: int
    status: str
    created_at: datetime

    class Config:
        from_attributes = True
