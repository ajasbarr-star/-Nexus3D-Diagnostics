package com.nexus3d.diagnostics

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
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
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rememberMyLoginPreferencePersistsInEncryptedLocalSettings() {
        val repository = SupabaseAccountRepository(context)
        repository.setRememberLogin(true)
        assertTrue(repository.isRememberLoginEnabled())
        repository.setRememberLogin(false)
        assertFalse(repository.isRememberLoginEnabled())
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
        composeRule.onNodeWithContentDescription("Copy Permanent Admin Unique ID").performClick()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("TEST-OWNER-ADMIN-ID", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        val store = OwnerAdminLocalStore(context)
        assertTrue(store.hasCredential())
        assertTrue(store.verifyCredential("123456"))
        assertFalse(store.verifyCredential("000000"))

        composeRule.onNodeWithText("RESET PIN/PASS").performClick()
        composeRule.onNodeWithText("Current PIN / Password").performTextInput("000000")
        composeRule.onNodeWithText("New PIN / Password").performTextInput("654321")
        composeRule.onNodeWithText("Confirm new PIN / Password").performTextInput("654321")
        composeRule.onNodeWithText("Save Changes").performClick()
        composeRule.onNodeWithText("Current PIN / Password is incorrect.").assertIsDisplayed()
        composeRule.onNodeWithText("Current PIN / Password").performTextClearance()
        composeRule.onNodeWithText("Current PIN / Password").performTextInput("123456")
        composeRule.onNodeWithText("Save Changes").performClick()
        composeRule.waitForIdle()
        assertTrue(store.verifyCredential("654321"))
        assertFalse(store.verifyCredential("123456"))

        composeRule.setContent {
            MaterialTheme {
                OwnerAdminStatusbarPanel(owner, repository)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Locked • verification required").assertIsDisplayed()
        composeRule.onAllNodesWithText("TEST-OWNER-ADMIN-ID").assertCountEquals(0)
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
