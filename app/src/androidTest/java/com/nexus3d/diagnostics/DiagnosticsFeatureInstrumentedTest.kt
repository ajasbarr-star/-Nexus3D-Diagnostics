package com.nexus3d.diagnostics

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DiagnosticsFeatureInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun loginPanelShowsRememberAndPasswordRecoveryControls() {
        composeRule.onNodeWithText("REMEMBER MY LOGIN").assertIsDisplayed()
        composeRule.onNodeWithText("RESET PASSWORD").assertIsDisplayed()
    }

    @Test
    fun ownerAdminPinCanBeCreatedAndUniqueIdCanBeCopied() {
        context.deleteSharedPreferences("nexus3d_owner_admin_local")
        val repository = SupabaseAccountRepository(context)
        val owner = OwnerAdminStatus(
            registered = true,
            adminId = "TEST-OWNER-ADMIN-ID",
            role = "owner_admin",
            status = "active",
            eligible = true,
            emailVerified = true,
            consentAccepted = true
        )

        composeRule.setContent {
            MaterialTheme {
                OwnerAdminStatusbarPanel(owner, repository)
            }
        }

        composeRule.onNodeWithText("OWNER ADMIN STATUSBAR").assertIsDisplayed()
        composeRule.onNodeWithText("Security setup required").assertIsDisplayed()
        composeRule.onNodeWithText("SET PIN/PASS").performClick()
        composeRule.onNodeWithText("6–12 digit PIN").performTextInput("123456")
        composeRule.onNodeWithText("Confirm PIN / Password").performTextInput("123456")
        composeRule.onNodeWithText("Save Security").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("TEST-OWNER-ADMIN-ID").assertIsDisplayed()
        composeRule.onNodeWithText("SET PIN/PASS").assertIsDisplayed()
        composeRule.onNodeWithText("RESET PIN/PASS").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Copy Permanent Admin Unique ID").assertIsDisplayed()

        val store = OwnerAdminLocalStore(context)
        assertTrue(store.hasCredential())
        assertTrue(store.verifyCredential("123456"))
        assertFalse(store.verifyCredential("000000"))
        context.deleteSharedPreferences("nexus3d_owner_admin_local")
    }

    @Test
    fun appearanceControlsApplyAndPersistCustomGradient() {
        val store = DiagnosticsAppearanceStore(context)
        val initial = DiagnosticsAppearance.Default
        val applied = AtomicReference<DiagnosticsAppearance?>(null)

        composeRule.setContent {
            MaterialTheme {
                AppearanceSettingsPanel(initial) { applied.set(it) }
            }
        }
        composeRule.onNodeWithText("Gradient").performClick()
        composeRule.onNodeWithText("APPLY").performScrollTo().performClick()
        composeRule.waitForIdle()

        val result = applied.get()
        assertNotNull(result)
        assertEquals("Gradient", result!!.backgroundStyle)
        store.save(result)
        assertEquals(result, store.load())
        context.getSharedPreferences("nexus3d_diagnostics_appearance_v2", 0).edit().clear().apply()
    }
}
