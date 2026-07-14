package com.cybershield.ai.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedCaseEntity::class,
        CachedMessageEntity::class,
        CachedEvidenceEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class CyberShieldDatabase : RoomDatabase() {
    abstract fun caseCacheDao(): CaseCacheDao
    abstract fun messageCacheDao(): MessageCacheDao
    abstract fun evidenceCacheDao(): EvidenceCacheDao
}
