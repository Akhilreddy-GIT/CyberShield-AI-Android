package com.cybershield.ai.data.repository

import com.cybershield.ai.data.remote.CyberShieldApi
import com.cybershield.ai.data.remote.dto.HealthDto
import com.cybershield.ai.data.remote.dto.ReportDto
import com.cybershield.ai.domain.repository.HealthRepository
import com.cybershield.ai.domain.repository.ReportRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val api: CyberShieldApi,
) : ReportRepository {

    override suspend fun getReport(caseId: String): ReportDto = api.getReport(caseId)

    override suspend fun downloadPdf(caseId: String, destination: File): File {
        val body = api.getReportPdf(caseId)
        destination.parentFile?.mkdirs()
        body.byteStream().use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destination
    }
}

@Singleton
class HealthRepositoryImpl @Inject constructor(
    private val api: CyberShieldApi,
) : HealthRepository {
    override suspend fun health(): HealthDto = api.health()
}
