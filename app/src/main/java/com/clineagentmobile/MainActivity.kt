package com.clineagentmobile

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.clineagentmobile.chat.ChatActivity
import com.clineagentmobile.dashboard.DashboardActivity
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
            showBridgeDialog()
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
        findViewById<TextView>(R.id.home_status_text).text =
            if (token.isNullOrBlank()) getString(R.string.home_github_missing)
            else getString(R.string.home_github_ok)
    }

    private fun showBridgeDialog() {
        val prefs = EncryptedPrefsManager.getInstance(applicationContext)
        val input = EditText(this).apply {
            hint = getString(R.string.home_bridge_dialog_hint)
            setText(prefs.getBridgeUrl() ?: "")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.home_bridge_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                prefs.saveBridgeUrl(input.text.toString().trim())
                refreshStatus()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
