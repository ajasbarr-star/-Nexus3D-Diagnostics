package com.nexus3d.diagnostics

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class DiagnosticsStartupSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldStartShowsOwnerAdminLoginWithInPanelLogo() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("OWNER ADMIN LOGIN")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("OWNER ADMIN LOGIN").assertExists()
        composeRule.onNodeWithText("Nexus3D Diagnostics").assertExists()
        composeRule.onNodeWithContentDescription("Nexus3D Diagnostics logo").assertExists()
    }
}
