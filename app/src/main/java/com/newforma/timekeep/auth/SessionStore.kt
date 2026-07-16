package com.newforma.timekeep.auth

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.newforma.timekeep.model.AuthSession
import com.newforma.timekeep.model.PendingAuthorizationRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface SessionStore {
    fun readSession(): AuthSession?
    fun writeSession(session: AuthSession)
    fun clearSession()
    fun readPendingAuthorization(): PendingAuthorizationRequest?
    fun writePendingAuthorization(request: PendingAuthorizationRequest)
    fun clearPendingAuthorization()
    fun clearAll()
}

class SecureSessionStore(
    context: Context,
    private val json: Json,
) : SessionStore {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun readSession(): AuthSession? = decode(KEY_SESSION)

    override fun writeSession(session: AuthSession) {
        preferences.edit {
            putString(KEY_SESSION, json.encodeToString(session))
        }
    }

    override fun clearSession() {
        preferences.edit {
            remove(KEY_SESSION)
        }
    }

    override fun readPendingAuthorization(): PendingAuthorizationRequest? = decode(KEY_PENDING_AUTH)

    override fun writePendingAuthorization(request: PendingAuthorizationRequest) {
        preferences.edit {
            putString(KEY_PENDING_AUTH, json.encodeToString(request))
        }
    }

    override fun clearPendingAuthorization() {
        preferences.edit {
            remove(KEY_PENDING_AUTH)
        }
    }

    override fun clearAll() {
        preferences.edit {
            remove(KEY_SESSION)
            remove(KEY_PENDING_AUTH)
        }
    }

    private inline fun <reified T> decode(key: String): T? {
        val raw = preferences.getString(key, null) ?: return null
        return runCatching {
            json.decodeFromString<T>(raw)
        }.getOrNull()
    }

    private companion object {
        const val PREFS_NAME = "timekeep_secure_session"
        const val KEY_SESSION = "auth_session"
        const val KEY_PENDING_AUTH = "pending_authorization"
    }
}
