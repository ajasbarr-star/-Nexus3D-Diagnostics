package com.nexus3d.diagnostics

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class DiagnosticsStartupSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityStartsInImmersiveFullscreen() {
        composeRule.waitForIdle()
        val insets = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
        assertNotNull(insets)
        assertFalse(insets!!.isVisible(WindowInsetsCompat.Type.systemBars()))
    }
}
