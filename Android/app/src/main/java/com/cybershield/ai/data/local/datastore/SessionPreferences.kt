package com.cybershield.ai.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "cybershield_session")

@Singleton
class SessionPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ANON_USER_ID = stringPreferencesKey("anon_user_id")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USERNAME = stringPreferencesKey("username")
        val ACTIVE_CASE_ID = stringPreferencesKey("active_case_id")
    }

    val anonUserId: Flow<String> = context.sessionDataStore.data.map { prefs ->
        prefs[Keys.ANON_USER_ID] ?: ""
    }

    val authToken: Flow<String?> = context.sessionDataStore.data.map { prefs ->
        prefs[Keys.AUTH_TOKEN]
    }

    val username: Flow<String?> = context.sessionDataStore.data.map { prefs ->
        prefs[Keys.USERNAME]
    }

    val activeCaseId: Flow<String?> = context.sessionDataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_CASE_ID]
    }

    suspend fun ensureAnonUserId(): String {
        var id = ""
        context.sessionDataStore.edit { prefs ->
            val existing = prefs[Keys.ANON_USER_ID]
            if (existing.isNullOrBlank()) {
                val generated = "anon-" + UUID.randomUUID().toString().replace("-", "").take(12)
                prefs[Keys.ANON_USER_ID] = generated
                id = generated
            } else {
                id = existing
            }
        }
        return id
    }

    suspend fun setAuth(token: String, anonUserId: String, username: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.AUTH_TOKEN] = token
            prefs[Keys.ANON_USER_ID] = anonUserId
            prefs[Keys.USERNAME] = username
        }
    }

    suspend fun clearAuth() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(Keys.AUTH_TOKEN)
            prefs.remove(Keys.USERNAME)
            // Also drop the anon_user_id: while logged in it was overwritten
            // with the server-assigned, account-linked id (see setAuth). If
            // we left it in place, ensureAnonUserId() would keep reusing
            // that same account-linked id after logout, silently attributing
            // all further "anonymous" activity to the account the user just
            // signed out of instead of starting a genuinely fresh session.
            prefs.remove(Keys.ANON_USER_ID)
            // The active case belonged to the logged-in session too — carrying
            // it forward would let a fresh anonymous session land straight
            // back into a previous account's case.
            prefs.remove(Keys.ACTIVE_CASE_ID)
        }
    }

    suspend fun setActiveCaseId(caseId: String?) {
        context.sessionDataStore.edit { prefs ->
            if (caseId.isNullOrBlank()) {
                prefs.remove(Keys.ACTIVE_CASE_ID)
            } else {
                prefs[Keys.ACTIVE_CASE_ID] = caseId
            }
        }
    }
}
