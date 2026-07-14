package com.cybershield.ai.data.remote

import com.cybershield.ai.data.remote.dto.AuthOutDto
import com.cybershield.ai.data.remote.dto.CaseListItemDto
import com.cybershield.ai.data.remote.dto.CaseOutDto
import com.cybershield.ai.data.remote.dto.ChatHistoryItemDto
import com.cybershield.ai.data.remote.dto.ChatMessageInDto
import com.cybershield.ai.data.remote.dto.ChatMessageOutDto
import com.cybershield.ai.data.remote.dto.DeleteStatusDto
import com.cybershield.ai.data.remote.dto.EvidenceItemDto
import com.cybershield.ai.data.remote.dto.EvidenceUploadOutDto
import com.cybershield.ai.data.remote.dto.HealthDto
import com.cybershield.ai.data.remote.dto.LoginInDto
import com.cybershield.ai.data.remote.dto.RegisterInDto
import com.cybershield.ai.data.remote.dto.ReportDto
import com.cybershield.ai.data.remote.dto.RiskAssessmentOutDto
import com.cybershield.ai.data.remote.dto.RiskFactorsInDto
import com.cybershield.ai.data.remote.dto.TimelineAddOutDto
import com.cybershield.ai.data.remote.dto.TimelineEventDto
import com.cybershield.ai.data.remote.dto.TimelineEventInDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

interface CyberShieldApi {

    @GET("api/health")
    suspend fun health(): HealthDto

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterInDto): AuthOutDto

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginInDto): AuthOutDto

    @POST("api/chat")
    suspend fun sendMessage(@Body body: ChatMessageInDto): ChatMessageOutDto

    @GET("api/chat/{case_id}/history")
    suspend fun chatHistory(@Path("case_id") caseId: String): List<ChatHistoryItemDto>

    @GET("api/cases/{case_id}")
    suspend fun getCase(@Path("case_id") caseId: String): CaseOutDto

    @GET("api/cases/by-user/{anon_user_id}")
    suspend fun listUserCases(@Path("anon_user_id") anonUserId: String): List<CaseListItemDto>

    @POST("api/cases/assess-risk")
    suspend fun assessRisk(@Body body: RiskFactorsInDto): RiskAssessmentOutDto

    @POST("api/cases/timeline")
    suspend fun addTimelineEvent(@Body body: TimelineEventInDto): TimelineAddOutDto

    @GET("api/cases/{case_id}/timeline")
    suspend fun getTimeline(@Path("case_id") caseId: String): List<TimelineEventDto>

    @Multipart
    @POST("api/evidence/upload")
    suspend fun uploadEvidence(
        @Part("case_id") caseId: RequestBody,
        @Part("description") description: RequestBody,
        @Part file: MultipartBody.Part,
    ): EvidenceUploadOutDto

    @GET("api/evidence/{case_id}")
    suspend fun listEvidence(@Path("case_id") caseId: String): List<EvidenceItemDto>

    @DELETE("api/evidence/{evidence_id}")
    suspend fun deleteEvidence(@Path("evidence_id") evidenceId: String): DeleteStatusDto

    @GET("api/report/{case_id}")
    suspend fun getReport(@Path("case_id") caseId: String): ReportDto

    @Streaming
    @GET("api/report/{case_id}/pdf")
    suspend fun getReportPdf(@Path("case_id") caseId: String): ResponseBody
}
