package com.cybershield.ai.domain.repository

import com.cybershield.ai.data.remote.dto.AuthOutDto
import com.cybershield.ai.data.remote.dto.CaseListItemDto
import com.cybershield.ai.data.remote.dto.CaseOutDto
import com.cybershield.ai.data.remote.dto.CaseWsUpdateDto
import com.cybershield.ai.data.remote.dto.ChatHistoryItemDto
import com.cybershield.ai.data.remote.dto.ChatMessageOutDto
import com.cybershield.ai.data.remote.dto.EvidenceItemDto
import com.cybershield.ai.data.remote.dto.EvidenceUploadOutDto
import com.cybershield.ai.data.remote.dto.HealthDto
import com.cybershield.ai.data.remote.dto.ReportDto
import com.cybershield.ai.data.remote.dto.RiskAssessmentOutDto
import com.cybershield.ai.data.remote.dto.RiskFactorsInDto
import com.cybershield.ai.data.remote.dto.TimelineAddOutDto
import com.cybershield.ai.data.remote.dto.TimelineEventDto
import kotlinx.coroutines.flow.Flow
import java.io.File

interface AuthRepository {
    suspend fun ensureAnonUserId(): String
    fun observeAnonUserId(): Flow<String>
    fun observeUsername(): Flow<String?>
    fun observeAuthToken(): Flow<String?>
    suspend fun register(username: String, password: String): AuthOutDto
    suspend fun login(username: String, password: String): AuthOutDto
    suspend fun logout()
}

interface ChatRepository {
    suspend fun sendMessage(caseId: String?, anonUserId: String, message: String): ChatMessageOutDto
    suspend fun getHistory(caseId: String): List<ChatHistoryItemDto>
}

interface CaseRepository {
    suspend fun getCase(caseId: String): CaseOutDto
    suspend fun listUserCases(anonUserId: String): List<CaseListItemDto>
    suspend fun assessRisk(payload: RiskFactorsInDto): RiskAssessmentOutDto
    suspend fun addTimelineEvent(caseId: String, description: String, eventTime: String?): TimelineAddOutDto
    suspend fun getTimeline(caseId: String): List<TimelineEventDto>
    fun observeCaseUpdates(caseId: String): Flow<CaseWsUpdateDto>
    suspend fun setActiveCaseId(caseId: String?)
    fun observeActiveCaseId(): Flow<String?>
}

interface EvidenceRepository {
    suspend fun upload(caseId: String, description: String, file: File, mimeType: String): EvidenceUploadOutDto
    suspend fun list(caseId: String): List<EvidenceItemDto>
    suspend fun delete(evidenceId: String)
}

interface ReportRepository {
    suspend fun getReport(caseId: String): ReportDto
    suspend fun downloadPdf(caseId: String, destination: File): File
}

interface HealthRepository {
    suspend fun health(): HealthDto
}
