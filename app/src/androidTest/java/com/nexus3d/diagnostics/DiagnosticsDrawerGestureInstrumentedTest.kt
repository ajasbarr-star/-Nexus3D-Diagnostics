package com.nexus3d.diagnostics

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsDrawerGestureInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setDashboardContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            DiagnosticsDatabase::class.java
        ).allowMainThreadQueries().build()
        val repository = SupabaseAccountRepository(context)
        val owner = OwnerAdminStatus(
            registered = true,
            adminId = "GESTURE-TEST-ADMIN",
            role = "owner_admin",
            status = "active",
            eligible = true,
            emailVerified = true,
            consentAccepted = true
        )
        composeRule.setContent {
            MaterialTheme {
                DiagnosticsHome(
                    database = database,
                    cloudAccountRepository = repository,
                    ownerAdminStatus = owner,
                    appearance = DiagnosticsAppearance.Default,
                    onAppearanceChange = {},
                    onSignOut = {}
                )
            }
        }
    }

    @Test
    fun swipeInFromLeftEdgeOpensLeftDrawer() {
        setDashboardContent()
        composeRule.onRoot().performTouchInput {
            val y = center.y
            down(Offset(1f, y))
            moveTo(Offset(160f, y), 350)
            up()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Device Inventory List").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Device Inventory List").assertCountEquals(1)
        composeRule.onRoot().performTouchInput {
            val y = center.y
            down(Offset(160f, y))
            moveTo(Offset(1f, y), 350)
            up()
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Device Inventory List").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithText("Device Inventory List").assertCountEquals(0)
    }

    @Test
    fun swipeInFromRightEdgeOpensRightDrawer() {
        setDashboardContent()
        composeRule.waitUntil(5_000) { composeRule.onRoot().fetchSemanticsNode().size.width > 0 }
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().size.width.toFloat()
        composeRule.onRoot().performTouchInput {
            val y = center.y
            down(Offset(rootWidth - 1f, y))
            moveTo(Offset(rootWidth - 160f, y), 350)
            up()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Appearance & Settings").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Appearance & Settings").assertCountEquals(1)
        composeRule.onRoot().performTouchInput {
            val y = center.y
            down(Offset(rootWidth - 160f, y))
            moveTo(Offset(rootWidth - 1f, y), 350)
            up()
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Appearance & Settings").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithText("Appearance & Settings").assertCountEquals(0)
    }
}
