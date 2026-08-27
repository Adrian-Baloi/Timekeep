package com.example.timekeep_work

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timekeep_work.auth.SecureAuthStateStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScopeInstrumentedTest {
    @Test
    fun storedAuthorizationContainsManagementScopes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val authState = SecureAuthStateStore(context).read()
        assertNotNull("No signed-in Halo authorization state is stored.", authState)

        val grantedScopes = authState?.scopeSet.orEmpty()
        val requiredScopes = setOf("read:assets", "edit:assets", "read:customers")
        val missingScopes = requiredScopes - grantedScopes
        assertTrue(
            "Stored Halo authorization is missing scopes: ${missingScopes.sorted().joinToString()}",
            missingScopes.isEmpty(),
        )
    }
}
