package com.nexus3d.diagnostics

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsLoginInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun loginPanelShowsRememberAndPasswordRecoveryControls() {
        val database = Room.inMemoryDatabaseBuilder(
            context,
            DiagnosticsDatabase::class.java
        ).allowMainThreadQueries().build()
        val repository = SupabaseAccountRepository(context)
        repository.signOut()
        repository.setRememberLogin(false)

        composeRule.setContent {
            DiagnosticsStartupGate(database, repository)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("OWNER ADMIN LOGIN").assertExists()
        composeRule.onNodeWithText("Nexus3D Diagnostics").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Nexus3D Diagnostics logo").assertIsDisplayed()
        composeRule.onNodeWithText("REMEMBER MY LOGIN").assertIsDisplayed()
        composeRule.onNodeWithText("RESET PASSWORD").assertIsDisplayed()

        composeRule.onNodeWithTag("rememberMyLoginCheckbox").performScrollTo().performClick()
        assertTrue(repository.isRememberLoginEnabled())
        composeRule.onNodeWithTag("rememberMyLoginCheckbox").performClick()
        assertFalse(repository.isRememberLoginEnabled())

        database.close()
    }
}
