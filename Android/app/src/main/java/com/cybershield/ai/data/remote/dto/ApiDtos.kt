package com.cybershield.ai.data.remote.dto

import com.squareup.moshi.Json

data class ChatMessageInDto(
    @Json(name = "case_id") val case_id: String? = null,
    @Json(name = "anon_user_id") val anon_user_id: String,
    @Json(name = "message") val message: String,
)

data class ChatMessageOutDto(
    @Json(name = "case_id") val case_id: String?,
    @Json(name = "reply") val reply: String,
    @Json(name = "category") val category: String?,
    @Json(name = "is_critical") val is_critical: Boolean,
    // Explicit backend determination of whether this message represents a
    // real cyber incident. The client must never infer this from message
    // text, category strings, or message position — only this flag.
    @Json(name = "is_cyber_incident") val is_cyber_incident: Boolean = false,
    @Json(name = "cited_sources") val cited_sources: List<String>,
    @Json(name = "used_llm") val used_llm: Boolean,
    @Json(name = "helplines") val helplines: List<HelplineDto>? = null,
    // Present only when the backend has actually produced a threat
    // analysis for this message (i.e. is_cyber_incident == true). Null for
    // greetings, refusals, and any non-incident reply — the client must
    // treat null as "no analysis to show", never synthesize one.
    @Json(name = "analysis") val analysis: AnalysisDto? = null,
)

data class HelplineDto(
    @Json(name = "name") val name: String,
    @Json(name = "number") val number: String,
    @Json(name = "use_for") val use_for: String,
)

data class AnalysisDto(
    @Json(name = "title") val title: String,
    @Json(name = "risk_score") val risk_score: Int,
    @Json(name = "risk_level") val risk_level: String,
    @Json(name = "certainty") val certainty: Float,
    @Json(name = "target") val target: String,
    @Json(name = "recovery_actions") val recovery_actions: List<RecoveryActionDto>? = null,
)

data class RecoveryActionDto(
    @Json(name = "title") val title: String,
    @Json(name = "recommended") val recommended: Boolean = false,
    @Json(name = "optional") val optional: Boolean = false,
)

data class ChatHistoryItemDto(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String,
    @Json(name = "cited_sources") val cited_sources: List<String> = emptyList(),
    @Json(name = "created_at") val created_at: String? = null,
    @Json(name = "is_critical") val is_critical: Boolean = false,
    @Json(name = "is_cyber_incident") val is_cyber_incident: Boolean = false,
    @Json(name = "analysis") val analysis: AnalysisDto? = null,
)

data class CaseOutDto(
    @Json(name = "id") val id: String,
    @Json(name = "category") val category: String?,
    @Json(name = "risk_level") val risk_level: String,
    @Json(name = "risk_score") val risk_score: Int,
    @Json(name = "status") val status: String,
    @Json(name = "created_at") val created_at: String,
    // Backend-computed recovery lifecycle state (see recovery_lifecycle_service.py).
    // Never derive progress from risk_score on the client — the backend already
    // does this from actual case stage, and duplicating that logic client-side
    // is exactly the kind of fabricated value this app must avoid.
    @Json(name = "recovery_stage") val recovery_stage: String? = null,
    @Json(name = "recovery_stage_label") val recovery_stage_label: String? = null,
    @Json(name = "recovery_progress_percent") val recovery_progress_percent: Int? = null,
)

data class CaseListItemDto(
    @Json(name = "id") val id: String,
    @Json(name = "category") val category: String?,
    @Json(name = "risk_level") val risk_level: String,
    @Json(name = "status") val status: String,
    @Json(name = "created_at") val created_at: String,
    @Json(name = "recovery_stage") val recovery_stage: String? = null,
    @Json(name = "recovery_stage_label") val recovery_stage_label: String? = null,
    @Json(name = "recovery_progress_percent") val recovery_progress_percent: Int? = null,
)

data class RiskFactorsInDto(
    @Json(name = "case_id") val case_id: String,
    @Json(name = "is_repeated_incident") val is_repeated_incident: Boolean = false,
    @Json(name = "involves_threat_of_harm") val involves_threat_of_harm: Boolean = false,
    @Json(name = "involves_minor") val involves_minor: Boolean = false,
    @Json(name = "involves_financial_loss") val involves_financial_loss: Boolean = false,
    @Json(name = "involves_explicit_content") val involves_explicit_content: Boolean = false,
    @Json(name = "accused_knows_victim_location") val accused_knows_victim_location: Boolean = false,
    @Json(name = "ongoing_blackmail") val ongoing_blackmail: Boolean = false,
    @Json(name = "victim_reports_feeling_unsafe") val victim_reports_feeling_unsafe: Boolean = false,
)

data class RiskAssessmentOutDto(
    @Json(name = "level") val level: String,
    @Json(name = "score") val score: Int,
    @Json(name = "triggered_factors") val triggered_factors: List<String>,
    @Json(name = "explanation") val explanation: String,
)

data class TimelineEventInDto(
    @Json(name = "case_id") val case_id: String,
    @Json(name = "description") val description: String,
    @Json(name = "event_time") val event_time: String? = null,
)

data class TimelineAddOutDto(
    @Json(name = "status") val status: String,
    @Json(name = "event_id") val event_id: String,
)

data class TimelineEventDto(
    @Json(name = "id") val id: String,
    @Json(name = "description") val description: String,
    @Json(name = "event_time") val event_time: String?,
    @Json(name = "created_at") val created_at: String,
)

data class EvidenceItemDto(
    @Json(name = "id") val id: String,
    @Json(name = "filename") val filename: String,
    @Json(name = "file_type") val file_type: String?,
    @Json(name = "description") val description: String? = null,
    @Json(name = "extracted_text") val extracted_text: String? = null,
    @Json(name = "uploaded_at") val uploaded_at: String,
    // New backend fields (additive): relative URL to view/download the file.
    // Null only for items returned by very old server versions; use fallback URL in that case.
    @Json(name = "file_url") val file_url: String? = null,
    @Json(name = "download_url") val download_url: String? = null,
)

data class EvidenceUploadOutDto(
    @Json(name = "id") val id: String,
    @Json(name = "filename") val filename: String,
    @Json(name = "file_type") val file_type: String?,
    @Json(name = "uploaded_at") val uploaded_at: String,
    @Json(name = "extracted_text") val extracted_text: String?,
    // New backend fields (additive): relative URL to view/download the uploaded file.
    @Json(name = "file_url") val file_url: String? = null,
    @Json(name = "download_url") val download_url: String? = null,
)

data class DeleteStatusDto(
    @Json(name = "status") val status: String,
)

data class ReportDto(
    @Json(name = "case_id") val case_id: String,
    @Json(name = "category") val category: String?,
    @Json(name = "risk_level") val risk_level: String,
    @Json(name = "risk_score") val risk_score: Int,
    @Json(name = "status") val status: String,
    @Json(name = "created_at") val created_at: String,
    @Json(name = "incident_summary") val incident_summary: String,
    @Json(name = "timeline") val timeline: List<ReportTimelineItemDto>,
    @Json(name = "evidence") val evidence: List<ReportEvidenceItemDto>,
    @Json(name = "legal_references") val legal_references: List<LegalReferenceDto>,
    @Json(name = "recommended_actions") val recommended_actions: List<String>,
)

data class ReportTimelineItemDto(
    @Json(name = "description") val description: String,
    @Json(name = "event_time") val event_time: String?,
)

data class ReportEvidenceItemDto(
    @Json(name = "filename") val filename: String,
    @Json(name = "file_type") val file_type: String?,
    @Json(name = "description") val description: String?,
)

data class LegalReferenceDto(
    @Json(name = "title") val title: String,
    @Json(name = "summary") val summary: String,
    @Json(name = "source") val source: String,
)

data class RegisterInDto(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String,
)

data class LoginInDto(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String,
)

data class AuthOutDto(
    @Json(name = "token") val token: String,
    @Json(name = "anon_user_id") val anon_user_id: String,
    @Json(name = "username") val username: String,
)

data class HealthDto(
    @Json(name = "status") val status: String,
    @Json(name = "llm_configured") val llm_configured: Boolean,
)

data class CaseWsUpdateDto(
    @Json(name = "case_id") val case_id: String,
    @Json(name = "status") val status: String? = null,
    @Json(name = "risk_level") val risk_level: String? = null,
    @Json(name = "risk_score") val risk_score: Int? = null,
    @Json(name = "timeline_events_added") val timeline_events_added: List<TimelineEventDto>? = null,
    @Json(name = "recovery_stage") val recovery_stage: String? = null,
    @Json(name = "recovery_progress_percent") val recovery_progress_percent: Int? = null,
)
