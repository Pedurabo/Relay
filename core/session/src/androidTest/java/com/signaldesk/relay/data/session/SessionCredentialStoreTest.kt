package com.signaldesk.relay.data.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionCredentialStoreTest {

    private lateinit var context:
        Context

    private lateinit var store:
        SessionCredentialStore

    private val prefsName =
        "relay_session_test"

    private val keyAlias =
        "relay_session_test_aes_v1"

    @Before
    fun setUp() {

        context =
            ApplicationProvider
                .getApplicationContext()

        context
            .getSharedPreferences(
                prefsName,
                Context.MODE_PRIVATE
            )
            .edit()
            .clear()
            .commit()

        store =
            SessionCredentialStore(
                context =
                    context,
                prefsName =
                    prefsName,
                keyAlias =
                    keyAlias
            )
    }

    @After
    fun tearDown() {

        context
            .getSharedPreferences(
                prefsName,
                Context.MODE_PRIVATE
            )
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun encryptedRoundTrip_doesNotPersistPlaintextCredentials() {

        val session =
            AuthenticatedSession(
                userId =
                    "user-secret-123",
                userName =
                    "Secret Operator",
                accessToken =
                    "access-secret-token",
                refreshToken =
                    "refresh-secret-token",
                accessTokenExpiresAt =
                    123456789L
            )

        store.save(
            session
        )

        val restored =
            store.read()

        assertEquals(
            session,
            restored
        )

        val preferences =
            context
                .getSharedPreferences(
                    prefsName,
                    Context.MODE_PRIVATE
                )

        assertNotNull(
            preferences.getString(
                SessionCredentialStore
                    .KEY_CIPHERTEXT,
                null
            )
        )

        assertNotNull(
            preferences.getString(
                SessionCredentialStore
                    .KEY_IV,
                null
            )
        )

        val raw =
            preferences
                .all
                .values
                .joinToString(
                    separator = "|"
                )

        assertFalse(
            raw.contains(
                session.userId
            )
        )

        assertFalse(
            raw.contains(
                session.userName
            )
        )

        assertFalse(
            raw.contains(
                session.accessToken
            )
        )

        assertFalse(
            raw.contains(
                session.refreshToken
            )
        )

        assertNull(
            preferences.getString(
                SessionCredentialStore
                    .LEGACY_KEY_ACCESS_TOKEN,
                null
            )
        )

        assertNull(
            preferences.getString(
                SessionCredentialStore
                    .LEGACY_KEY_REFRESH_TOKEN,
                null
            )
        )
    }

    @Test
    fun legacyPlaintextSession_migratesAndDeletesLegacyKeys() {

        val preferences =
            context
                .getSharedPreferences(
                    prefsName,
                    Context.MODE_PRIVATE
                )

        preferences
            .edit()
            .putString(
                SessionCredentialStore
                    .LEGACY_KEY_USER_ID,
                "legacy-user"
            )
            .putString(
                SessionCredentialStore
                    .LEGACY_KEY_USER_NAME,
                "Legacy Operator"
            )
            .putString(
                SessionCredentialStore
                    .LEGACY_KEY_ACCESS_TOKEN,
                "legacy-access"
            )
            .putString(
                SessionCredentialStore
                    .LEGACY_KEY_REFRESH_TOKEN,
                "legacy-refresh"
            )
            .putLong(
                SessionCredentialStore
                    .LEGACY_KEY_ACCESS_TOKEN_EXPIRES_AT,
                987654321L
            )
            .commit()

        val restored =
            store.read()

        assertEquals(
            "legacy-user",
            restored?.userId
        )

        assertEquals(
            "legacy-access",
            restored?.accessToken
        )

        assertNotNull(
            preferences.getString(
                SessionCredentialStore
                    .KEY_CIPHERTEXT,
                null
            )
        )

        assertNull(
            preferences.getString(
                SessionCredentialStore
                    .LEGACY_KEY_USER_ID,
                null
            )
        )

        assertNull(
            preferences.getString(
                SessionCredentialStore
                    .LEGACY_KEY_ACCESS_TOKEN,
                null
            )
        )

        assertNull(
            preferences.getString(
                SessionCredentialStore
                    .LEGACY_KEY_REFRESH_TOKEN,
                null
            )
        )
    }
}
