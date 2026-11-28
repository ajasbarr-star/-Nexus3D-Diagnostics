package com.nexus3d.diagnostics

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    val composeRule = createAndroidComposeRule<MainActivity>()

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
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Device Inventory List").assertIsDisplayed()
    }

    @Test
    fun swipeInFromRightEdgeOpensRightDrawer() {
        setDashboardContent()
        composeRule.waitForIdle()
        val rootWidth = composeRule.activity.window.decorView.width.toFloat()
        composeRule.onRoot().performTouchInput {
            val y = center.y
            down(Offset(rootWidth - 1f, y))
            moveTo(Offset(rootWidth - 160f, y), 350)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Appearance & Settings").assertIsDisplayed()
    }
}
