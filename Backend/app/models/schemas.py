from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime


class ChatMessageIn(BaseModel):
    case_id: Optional[str] = None
    anon_user_id: str
    message: str = Field(..., max_length=8000)


class ChatMessageOut(BaseModel):
    case_id: Optional[str] = None  # null for pure small-talk/off-topic replies with no case
    reply: str
    category: Optional[str]
    is_critical: bool
    cited_sources: List[str]
    used_llm: bool
    helplines: Optional[list] = None
    # Additive — backend-driven recovery lifecycle (see
    # recovery_lifecycle_service.py). None for turns with no case (pure
    # small talk / standalone educational Q&A) rather than a fabricated
    # default, since there's no case to report progress on.
    recovery_stage: Optional[str] = None
    recovery_progress_percent: Optional[int] = None


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
    # Additive fields — backend-driven recovery lifecycle. New Android
    # builds can bind "Recovery Progress" / stage label directly to these
    # instead of inventing a client-side value; existing clients that only
    # read the fields above are unaffected since these are additive.
    recovery_stage: str = "open"
    recovery_stage_label: str = "Open"
    recovery_progress_percent: int = 0

    class Config:
        from_attributes = True


# ---------------------------------------------------------------------------
# Guardian schemas (Smart Notification Guardian, Call Guardian, Event Engine)
# ---------------------------------------------------------------------------

class NotificationScanIn(BaseModel):
    anon_user_id: str
    case_id: Optional[str] = None
    app_name: Optional[str] = None
    notification_title: Optional[str] = None
    notification_text: Optional[str] = None
    notification_category: Optional[str] = None  # client-provided OS category, informational only
    timestamp: Optional[str] = None
    conversation_context: Optional[str] = None


class NotificationScanOut(BaseModel):
    scan_id: str
    notification_category: str
    threat_type: Optional[str]
    risk_score: int
    confidence_score: int
    severity: str
    explanation: str
    immediate_recommendation: str
    is_interaction_dangerous: bool
    recommended_next_action: str
    triggered_signals: List[str]
    proactive_warning: Optional[str] = None


class CallAnalysisIn(BaseModel):
    anon_user_id: str
    case_id: Optional[str] = None
    caller_number: Optional[str] = None
    contact_name: Optional[str] = None
    call_duration_seconds: Optional[int] = None
    timestamp: Optional[str] = None
    user_notes: Optional[str] = None
    conversation_summary: Optional[str] = None
    follow_up_answers: Optional[List[str]] = None


class CallAnalysisOut(BaseModel):
    call_id: str
    scam_category: Optional[str]
    threat_type: Optional[str]
    risk_score: int
    confidence_score: int
    urgency_level: str
    is_unknown_number: bool
    possible_impersonation: Optional[str]
    manipulation_tactics: List[str]
    explanation: str
    recovery_recommendations: List[str]
    evidence_recommendations: List[str]
    authorities_to_contact: List[str]
    triggered_signals: List[str]
    proactive_warning: Optional[str] = None


# ---------------------------------------------------------------------------
# Crisis Assistance Engine / Institution Routing / Anonymous Crisis Mode
# ---------------------------------------------------------------------------

class CrisisAssessIn(BaseModel):
    message: str
    case_id: Optional[str] = None          # existing case to continue, if any
    anon_user_id: Optional[str] = None     # required only if case_id is absent and persistence is desired
    institution_id: Optional[str] = None   # e.g. "gvp" — omit for no institution routing


class ContactPointOut(BaseModel):
    label: str
    contact: str
    notes: str = ""


class CrisisAssessOut(BaseModel):
    case_id: Optional[str] = None
    case_code: Optional[str] = None
    emergency_level: str
    human_assistance_recommended: bool
    category: Optional[str]
    emergency_type: Optional[str]
    situation_summary: str
    current_risk: str
    immediate_safety_advice: List[str]
    evidence_to_preserve: List[str]
    recommended_human_assistance: List[str]
    institution_recommended: bool = False
    institution_name: Optional[str] = None
    institution_routing_reason: str = ""
    institution_support: List[dict]
    government_support: List[dict]
    recovery_steps: List[str]
    next_question: Optional[str] = None
    guidance_text: str
    cited_sources: List[str]
    used_llm: bool
    risk_score: int
    triggered_factors: List[str]


class AnonymousCaseStartIn(BaseModel):
    category: Optional[str] = None


class AnonymousCaseStartOut(BaseModel):
    case_id: str
    anon_user_id: str
    case_code: str


class AnonymousCaseLinkIn(BaseModel):
    case_id: str
    account_anon_user_id: str


class InstitutionProfileOut(BaseModel):
    institution_id: str
    institution_name: str


class GuardianEventOutSchema(BaseModel):
    id: str
    event_type: str
    severity: str
    risk_score: int
    summary: str
    explanation: Optional[str]
    related_case_id: Optional[str]
    related_evidence_id: Optional[str]
    source: str
    created_at: datetime

    class Config:
        from_attributes = True
