package com.signaldesk.relay.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SessionCredentialStore(
    context: Context
) {

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

    fun read():
        AuthenticatedSession? {

        val encrypted =
            readEncrypted()

        if (
            encrypted != null
        ) {
            return encrypted
        }

        val legacy =
            readLegacy()
                ?: return null

        save(
            legacy
        )

        return legacy
    }

    fun save(
        session:
            AuthenticatedSession
    ) {

        val payload =
            JSONObject()
                .put(
                    "userId",
                    session.userId
                )
                .put(
                    "userName",
                    session.userName
                )
                .put(
                    "accessToken",
                    session.accessToken
                )
                .put(
                    "refreshToken",
                    session.refreshToken
                )
                .put(
                    "accessTokenExpiresAt",
                    session.accessTokenExpiresAt
                )
                .toString()

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateKey()
        )

        val ciphertext =
            cipher.doFinal(
                payload.toByteArray(
                    Charsets.UTF_8
                )
            )

        preferences
            .edit()
            .clear()
            .putString(
                KEY_CIPHERTEXT,
                Base64.encodeToString(
                    ciphertext,
                    Base64.NO_WRAP
                )
            )
            .putString(
                KEY_IV,
                Base64.encodeToString(
                    cipher.iv,
                    Base64.NO_WRAP
                )
            )
            .commit()
    }

    fun clear() {

        preferences
            .edit()
            .clear()
            .commit()
    }

    private fun readEncrypted():
        AuthenticatedSession? {

        val ciphertextText =
            preferences.getString(
                KEY_CIPHERTEXT,
                null
            )
                ?: return null

        val ivText =
            preferences.getString(
                KEY_IV,
                null
            )
                ?: return null

        return runCatching {

            val cipher =
                Cipher.getInstance(
                    TRANSFORMATION
                )

            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(
                    GCM_TAG_BITS,
                    Base64.decode(
                        ivText,
                        Base64.NO_WRAP
                    )
                )
            )

            val plaintext =
                cipher.doFinal(
                    Base64.decode(
                        ciphertextText,
                        Base64.NO_WRAP
                    )
                )
                    .toString(
                        Charsets.UTF_8
                    )

            val json =
                JSONObject(
                    plaintext
                )

            AuthenticatedSession(
                userId =
                    json.getString(
                        "userId"
                    ),
                userName =
                    json.getString(
                        "userName"
                    ),
                accessToken =
                    json.getString(
                        "accessToken"
                    ),
                refreshToken =
                    json.getString(
                        "refreshToken"
                    ),
                accessTokenExpiresAt =
                    json.getLong(
                        "accessTokenExpiresAt"
                    )
            )

        }.getOrNull()
    }

    private fun readLegacy():
        AuthenticatedSession? {

        val userId =
            preferences.getString(
                LEGACY_KEY_USER_ID,
                null
            )

        val userName =
            preferences.getString(
                LEGACY_KEY_USER_NAME,
                null
            )

        val accessToken =
            preferences.getString(
                LEGACY_KEY_ACCESS_TOKEN,
                null
            )

        val refreshToken =
            preferences.getString(
                LEGACY_KEY_REFRESH_TOKEN,
                null
            )

        val accessTokenExpiresAt =
            preferences.getLong(
                LEGACY_KEY_ACCESS_TOKEN_EXPIRES_AT,
                0L
            )

        if (
            userId.isNullOrBlank() ||
            userName.isNullOrBlank() ||
            accessToken.isNullOrBlank() ||
            refreshToken.isNullOrBlank() ||
            accessTokenExpiresAt <=
                0L
        ) {
            return null
        }

        return AuthenticatedSession(
            userId = userId,
            userName = userName,
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt =
                accessTokenExpiresAt
        )
    }

    private fun getOrCreateKey():
        SecretKey {

        val keyStore =
            KeyStore.getInstance(
                KEYSTORE
            )

        keyStore.load(
            null
        )

        val existing =
            keyStore.getKey(
                KEY_ALIAS,
                null
            )

        if (
            existing is
            SecretKey
        ) {
            return existing
        }

        val generator =
            KeyGenerator.getInstance(
                KeyProperties
                    .KEY_ALGORITHM_AES,
                KEYSTORE
            )

        generator.init(
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties
                        .PURPOSE_ENCRYPT or
                        KeyProperties
                            .PURPOSE_DECRYPT
                )
                .setBlockModes(
                    KeyProperties
                        .BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    KeyProperties
                        .ENCRYPTION_PADDING_NONE
                )
                .setKeySize(
                    256
                )
                .build()
        )

        return generator
            .generateKey()
    }

    companion object {

        internal const val PREFS_NAME =
            "relay_session"

        internal const val KEY_CIPHERTEXT =
            "encrypted_session"

        internal const val KEY_IV =
            "encrypted_session_iv"

        internal const val LEGACY_KEY_USER_ID =
            "user_id"

        internal const val LEGACY_KEY_USER_NAME =
            "user_name"

        internal const val LEGACY_KEY_ACCESS_TOKEN =
            "access_token"

        internal const val LEGACY_KEY_REFRESH_TOKEN =
            "refresh_token"

        internal const val LEGACY_KEY_ACCESS_TOKEN_EXPIRES_AT =
            "access_token_expires_at"

        private const val KEYSTORE =
            "AndroidKeyStore"

        private const val KEY_ALIAS =
            "relay_session_aes_v1"

        private const val TRANSFORMATION =
            "AES/GCM/NoPadding"

        private const val GCM_TAG_BITS =
            128
    }
}
