package com.cybershield.ai.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_cases")
data class CachedCaseEntity(
    @PrimaryKey val id: String,
    val category: String?,
    val riskLevel: String,
    val status: String,
    val createdAt: String,
    val anonUserId: String,
)

@Entity(tableName = "cached_messages")
data class CachedMessageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val caseId: String,
    val role: String,
    val content: String,
    val citedSources: String,
    val createdAt: String?,
)

@Entity(tableName = "cached_evidence")
data class CachedEvidenceEntity(
    @PrimaryKey val id: String,
    val caseId: String,
    val filename: String,
    val fileType: String?,
    val description: String?,
    val extractedText: String?,
    val uploadedAt: String,
)
