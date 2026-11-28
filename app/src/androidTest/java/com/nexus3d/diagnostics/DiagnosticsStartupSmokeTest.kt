package com.nexus3d.diagnostics

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class DiagnosticsStartupSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityStartsInImmersiveFullscreen() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val insets = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
        assertNotNull(insets)
        assertFalse(insets!!.isVisible(WindowInsetsCompat.Type.systemBars()))
    }
}
