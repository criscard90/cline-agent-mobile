package com.clineagentmobile

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.clineagentmobile.bridge.BridgeService
import com.clineagentmobile.chat.ChatActivity
import com.clineagentmobile.dashboard.DashboardActivity
import com.clineagentmobile.settings.SettingsActivity
import com.clineagentmobile.storage.EncryptedPrefsManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.home_open_dashboard).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        findViewById<Button>(R.id.home_open_chat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        findViewById<Button>(R.id.home_open_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val prefs = try {
            EncryptedPrefsManager.getInstance(applicationContext)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Prefs unavailable", e)
            return
        }
        val bridgeUrl = try {
            prefs.getBridgeUrl()
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Cannot read bridge url", e)
            null
        }
        val token = try {
            prefs.getOAuthToken()?.accessToken
        } catch (e: Exception) {
            null
        }
        findViewById<TextView>(R.id.home_bridge_text).text =
            if (bridgeUrl.isNullOrBlank()) getString(R.string.home_bridge_not_set)
            else getString(R.string.home_bridge_set, bridgeUrl)
        if (!bridgeUrl.isNullOrBlank()) {
            // Auto-start the bridge service as soon as an URL is configured
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    this, Intent(this, BridgeService::class.java)
                )
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Cannot start BridgeService", e)
            }
        }
        findViewById<TextView>(R.id.home_status_text).text =
            if (token.isNullOrBlank()) getString(R.string.home_github_missing)
            else getString(R.string.home_github_ok)
    }
}
