package com.nexus3d.diagnostics

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.launch

/**
 * Receives Supabase signup confirmation redirects from Gmail/browser and returns
 * the verified session to the existing encrypted auth store.
 */
class AuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val result = intent?.data?.let { uri ->
                SupabaseAccountRepository(applicationContext).handleAuthCallback(uri)
            }

            if (result.isNullOrBlank()) {
                Toast.makeText(
                    this@AuthCallbackActivity,
                    "Invalid Nexus3D Diagnostics authentication callback.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this@AuthCallbackActivity, result, Toast.LENGTH_LONG).show()
            }

            startActivity(
                Intent(this@AuthCallbackActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            finish()
        }
    }
}
