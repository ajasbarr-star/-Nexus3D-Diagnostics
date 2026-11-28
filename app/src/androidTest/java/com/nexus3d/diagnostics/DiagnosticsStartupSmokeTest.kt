package com.nexus3d.diagnostics

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsStartupSmokeTest {
    @Test
    fun mainActivityStartsInImmersiveFullscreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SupabaseAccountRepository(context).signOut()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                assertNotNull(insets)
                assertFalse(insets!!.isVisible(WindowInsetsCompat.Type.systemBars()))
            }
        }
    }
}
