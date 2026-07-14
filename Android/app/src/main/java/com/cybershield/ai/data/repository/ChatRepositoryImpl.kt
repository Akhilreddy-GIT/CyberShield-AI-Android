package com.cybershield.ai.data.repository

import com.cybershield.ai.data.local.db.CachedMessageEntity
import com.cybershield.ai.data.local.db.MessageCacheDao
import com.cybershield.ai.data.remote.CyberShieldApi
import com.cybershield.ai.data.remote.dto.ChatHistoryItemDto
import com.cybershield.ai.data.remote.dto.ChatMessageInDto
import com.cybershield.ai.data.remote.dto.ChatMessageOutDto
import com.cybershield.ai.domain.repository.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val api: CyberShieldApi,
    private val messageCacheDao: MessageCacheDao,
) : ChatRepository {

    override suspend fun sendMessage(
        caseId: String?,
        anonUserId: String,
        message: String,
    ): ChatMessageOutDto {
        return api.sendMessage(
            ChatMessageInDto(
                case_id = caseId,
                anon_user_id = anonUserId,
                message = message,
            )
        )
    }

    override suspend fun getHistory(caseId: String): List<ChatHistoryItemDto> {
        return try {
            val remote = api.chatHistory(caseId)
            messageCacheDao.clearForCase(caseId)
            messageCacheDao.upsertMessages(
                remote.map {
                    CachedMessageEntity(
                        caseId = caseId,
                        role = it.role,
                        content = it.content,
                        citedSources = it.cited_sources.joinToString(","),
                        createdAt = it.created_at,
                    )
                }
            )
            remote
        } catch (e: Exception) {
            messageCacheDao.getMessages(caseId).map {
                ChatHistoryItemDto(
                    role = it.role,
                    content = it.content,
                    cited_sources = if (it.citedSources.isBlank()) emptyList() else it.citedSources.split(","),
                    created_at = it.createdAt,
                )
            }.ifEmpty { throw e }
        }
    }
}
