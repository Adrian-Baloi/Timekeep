package com.example.timekeep_work.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.openid.appauth.AuthState

/** Persistence boundary for AppAuth's serializable session state. */
interface AuthStateStore {
    fun read(): AuthState?
    fun write(state: AuthState)
    fun clear()
}

/**
 * Stores OAuth state encrypted with an AES-GCM key held by Android Keystore.
 *
 * SharedPreferences contains only the encrypted blob; the encryption key cannot be exported
 * from the keystore. Corrupt or obsolete state is discarded so callers fall back to sign-in.
 */
class SecureAuthStateStore(context: Context) : AuthStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(): AuthState? {
        val encoded = preferences.getString(KEY_AUTH_STATE, null) ?: return null
        return runCatching {
            // Binary layout: format version, IV length, IV bytes, then ciphertext and GCM tag.
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(blob)
            val version = buffer.get().toInt()
            require(version == STORAGE_VERSION) { "Unsupported auth-state storage version." }
            val ivLength = buffer.get().toInt() and 0xff
            require(ivLength in 12..16 && buffer.remaining() > ivLength) { "Invalid auth-state blob." }
            val iv = ByteArray(ivLength).also(buffer::get)
            val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
            val plaintext = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
            AuthState.jsonDeserialize(plaintext)
        }.getOrElse {
            clear()
            null
        }
    }

    override fun write(state: AuthState) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val ciphertext = cipher.doFinal(state.jsonSerializeString().toByteArray(Charsets.UTF_8))
        val blob = ByteBuffer.allocate(2 + cipher.iv.size + ciphertext.size)
            .put(STORAGE_VERSION.toByte())
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        preferences.edit()
            .putString(KEY_AUTH_STATE, Base64.encodeToString(blob, Base64.NO_WRAP))
            .commit()
    }

    override fun clear() {
        preferences.edit().remove(KEY_AUTH_STATE).commit()
    }

    /** Returns the existing non-exportable key or creates it on first use. */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "halo_secure_auth_state"
        const val KEY_AUTH_STATE = "encrypted_appauth_state"
        const val KEY_ALIAS = "timekeep_halo_auth_state_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val STORAGE_VERSION = 1
    }
}
