package com.cybershield.ai.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(name = "cybershield_privacy")

@Singleton
class PrivacyPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val SHARE_TELEMETRY = booleanPreferencesKey("share_telemetry")
        val CRASH_REPORTS = booleanPreferencesKey("crash_reports")
    }

    val shareTelemetry: Flow<Boolean> = context.privacyDataStore.data.map { prefs ->
        prefs[Keys.SHARE_TELEMETRY] ?: false
    }

    val crashReports: Flow<Boolean> = context.privacyDataStore.data.map { prefs ->
        prefs[Keys.CRASH_REPORTS] ?: true
    }

    suspend fun setShareTelemetry(enabled: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[Keys.SHARE_TELEMETRY] = enabled
        }
    }

    suspend fun setCrashReports(enabled: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[Keys.CRASH_REPORTS] = enabled
        }
    }
}
