package com.cybershield.ai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CaseCacheDao {
    @Query("SELECT * FROM cached_cases WHERE anonUserId = :anonUserId ORDER BY createdAt DESC")
    suspend fun getCasesForUser(anonUserId: String): List<CachedCaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCases(cases: List<CachedCaseEntity>)

    @Query("DELETE FROM cached_cases WHERE anonUserId = :anonUserId")
    suspend fun clearForUser(anonUserId: String)
}

@Dao
interface MessageCacheDao {
    @Query("SELECT * FROM cached_messages WHERE caseId = :caseId ORDER BY localId ASC")
    suspend fun getMessages(caseId: String): List<CachedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<CachedMessageEntity>)

    @Query("DELETE FROM cached_messages WHERE caseId = :caseId")
    suspend fun clearForCase(caseId: String)
}

@Dao
interface EvidenceCacheDao {
    @Query("SELECT * FROM cached_evidence WHERE caseId = :caseId ORDER BY uploadedAt ASC")
    suspend fun getEvidence(caseId: String): List<CachedEvidenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvidence(items: List<CachedEvidenceEntity>)

    @Query("DELETE FROM cached_evidence WHERE caseId = :caseId")
    suspend fun clearForCase(caseId: String)

    @Query("DELETE FROM cached_evidence WHERE id = :id")
    suspend fun deleteById(id: String)
}
