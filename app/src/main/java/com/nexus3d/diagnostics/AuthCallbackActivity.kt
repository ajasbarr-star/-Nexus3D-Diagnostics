package com.nexus3d.diagnostics

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Receives Supabase signup confirmation redirects from Gmail/browser and returns
 * the verified session to the existing encrypted auth store.
 */
class AuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = intent?.data?.let { uri ->
            SupabaseAccountRepository(applicationContext).handleAuthCallback(uri)
        }

        if (result.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Invalid Nexus3D Diagnostics authentication callback.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, result, Toast.LENGTH_LONG).show()
        }

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}
