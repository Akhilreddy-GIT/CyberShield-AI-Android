package com.cybershield.ai.data.remote.dto

import com.squareup.moshi.Json

data class ChatMessageInDto(
    @Json(name = "case_id") val case_id: String? = null,
    @Json(name = "anon_user_id") val anon_user_id: String,
    @Json(name = "message") val message: String,
)

data class ChatMessageOutDto(
    @Json(name = "case_id") val case_id: String,
    @Json(name = "reply") val reply: String,
    @Json(name = "category") val category: String?,
    @Json(name = "is_critical") val is_critical: Boolean,
    @Json(name = "cited_sources") val cited_sources: List<String>,
    @Json(name = "used_llm") val used_llm: Boolean,
    @Json(name = "helplines") val helplines: List<HelplineDto>? = null,
)

data class HelplineDto(
    @Json(name = "name") val name: String,
    @Json(name = "number") val number: String,
    @Json(name = "use_for") val use_for: String,
)

data class ChatHistoryItemDto(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String,
    @Json(name = "cited_sources") val cited_sources: List<String> = emptyList(),
    @Json(name = "created_at") val created_at: String? = null,
)

data class CaseOutDto(
    @Json(name = "id") val id: String,
    @Json(name = "category") val category: String?,
    @Json(name = "risk_level") val risk_level: String,
    @Json(name = "risk_score") val risk_score: Int,
    @Json(name = "status") val status: String,
    @Json(name = "created_at") val created_at: String,
)

data class CaseListItemDto(
    @Json(name = "id") val id: String,
    @Json(name = "category") val category: String?,
    @Json(name = "risk_level") val risk_level: String,
    @Json(name = "status") val status: String,
    @Json(name = "created_at") val created_at: String,
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
)

data class EvidenceUploadOutDto(
    @Json(name = "id") val id: String,
    @Json(name = "filename") val filename: String,
    @Json(name = "file_type") val file_type: String?,
    @Json(name = "uploaded_at") val uploaded_at: String,
    @Json(name = "extracted_text") val extracted_text: String?,
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
    @Json(name = "status") val status: String,
    @Json(name = "risk_level") val risk_level: String,
    @Json(name = "risk_score") val risk_score: Int,
)
