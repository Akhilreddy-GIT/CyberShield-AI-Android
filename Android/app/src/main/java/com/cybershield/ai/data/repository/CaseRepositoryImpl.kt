package com.cybershield.ai.data.repository

import com.cybershield.ai.data.local.datastore.SessionPreferences
import com.cybershield.ai.data.local.db.CachedCaseEntity
import com.cybershield.ai.data.local.db.CaseCacheDao
import com.cybershield.ai.data.remote.CyberShieldApi
import com.cybershield.ai.data.remote.dto.CaseListItemDto
import com.cybershield.ai.data.remote.dto.CaseOutDto
import com.cybershield.ai.data.remote.dto.CaseWsUpdateDto
import com.cybershield.ai.data.remote.dto.RiskAssessmentOutDto
import com.cybershield.ai.data.remote.dto.RiskFactorsInDto
import com.cybershield.ai.data.remote.dto.TimelineAddOutDto
import com.cybershield.ai.data.remote.dto.TimelineEventDto
import com.cybershield.ai.data.remote.dto.TimelineEventInDto
import com.cybershield.ai.data.remote.websocket.CaseWebSocketClient
import com.cybershield.ai.domain.repository.CaseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaseRepositoryImpl @Inject constructor(
    private val api: CyberShieldApi,
    private val caseCacheDao: CaseCacheDao,
    private val session: SessionPreferences,
    private val webSocketClient: CaseWebSocketClient,
) : CaseRepository {

    override suspend fun getCase(caseId: String): CaseOutDto = api.getCase(caseId)

    override suspend fun listUserCases(anonUserId: String): List<CaseListItemDto> {
        return try {
            val remote = api.listUserCases(anonUserId)
            caseCacheDao.clearForUser(anonUserId)
            caseCacheDao.upsertCases(
                remote.map {
                    CachedCaseEntity(
                        id = it.id,
                        category = it.category,
                        riskLevel = it.risk_level,
                        status = it.status,
                        createdAt = it.created_at,
                        anonUserId = anonUserId,
                    )
                }
            )
            remote
        } catch (e: Exception) {
            caseCacheDao.getCasesForUser(anonUserId).map {
                CaseListItemDto(
                    id = it.id,
                    category = it.category,
                    risk_level = it.riskLevel,
                    status = it.status,
                    created_at = it.createdAt,
                )
            }.ifEmpty { throw e }
        }
    }

    override suspend fun assessRisk(payload: RiskFactorsInDto): RiskAssessmentOutDto =
        api.assessRisk(payload)

    override suspend fun addTimelineEvent(
        caseId: String,
        description: String,
        eventTime: String?,
    ): TimelineAddOutDto = api.addTimelineEvent(
        TimelineEventInDto(
            case_id = caseId,
            description = description,
            event_time = eventTime,
        )
    )

    override suspend fun getTimeline(caseId: String): List<TimelineEventDto> =
        api.getTimeline(caseId)

    override fun observeCaseUpdates(caseId: String): Flow<CaseWsUpdateDto> =
        webSocketClient.observeCase(caseId)

    override suspend fun setActiveCaseId(caseId: String?) = session.setActiveCaseId(caseId)

    override fun observeActiveCaseId(): Flow<String?> = session.activeCaseId
}
