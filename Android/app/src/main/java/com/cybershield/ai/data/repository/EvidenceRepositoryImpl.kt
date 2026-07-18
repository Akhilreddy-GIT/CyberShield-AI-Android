package com.cybershield.ai.data.repository

import com.cybershield.ai.data.local.db.CachedEvidenceEntity
import com.cybershield.ai.data.local.db.EvidenceCacheDao
import com.cybershield.ai.data.remote.CyberShieldApi
import com.cybershield.ai.data.remote.dto.EvidenceItemDto
import com.cybershield.ai.data.remote.dto.EvidenceUploadOutDto
import com.cybershield.ai.domain.repository.EvidenceRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvidenceRepositoryImpl @Inject constructor(
    private val api: CyberShieldApi,
    private val evidenceCacheDao: EvidenceCacheDao,
) : EvidenceRepository {

    override suspend fun upload(
        caseId: String,
        description: String,
        file: File,
        mimeType: String,
    ): EvidenceUploadOutDto {
        val caseIdBody = caseId.toRequestBody("text/plain".toMediaTypeOrNull())
        val descriptionBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
        val fileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", file.name, fileBody)
        val result = api.uploadEvidence(caseIdBody, descriptionBody, part)
        evidenceCacheDao.upsertEvidence(
            listOf(
                CachedEvidenceEntity(
                    id = result.id,
                    caseId = caseId,
                    filename = result.filename,
                    fileType = result.file_type,
                    description = description,
                    extractedText = result.extracted_text,
                    uploadedAt = result.uploaded_at,
                )
            )
        )
        return result
    }

    override suspend fun list(caseId: String): List<EvidenceItemDto> {
        return try {
            val remote = api.listEvidence(caseId)
            evidenceCacheDao.clearForCase(caseId)
            evidenceCacheDao.upsertEvidence(
                remote.map {
                    CachedEvidenceEntity(
                        id = it.id,
                        caseId = caseId,
                        filename = it.filename,
                        fileType = it.file_type,
                        description = it.description,
                        extractedText = it.extracted_text,
                        uploadedAt = it.uploaded_at,
                    )
                }
            )
            remote
        } catch (e: Exception) {
            evidenceCacheDao.getEvidence(caseId).map {
                EvidenceItemDto(
                    id = it.id,
                    filename = it.filename,
                    file_type = it.fileType,
                    description = it.description,
                    extracted_text = it.extractedText,
                    uploaded_at = it.uploadedAt,
                    // Reconstruct the deterministic relative URL from the cached ids so
                    // evidence viewing still works while offline.  The backend-provided
                    // file_url is preferred whenever a live network response is available;
                    // this fallback is only used when the cache is the sole data source.
                    file_url = "/api/evidence/file/${it.caseId}/${it.id}",
                )
            }.ifEmpty { throw e }
        }
    }

    override suspend fun delete(evidenceId: String) {
        api.deleteEvidence(evidenceId)
        evidenceCacheDao.deleteById(evidenceId)
    }
}
