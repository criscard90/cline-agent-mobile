package com.clineagentmobile.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.clineagentmobile.R
import com.clineagentmobile.bridge.BridgeService
import com.clineagentmobile.model.OAuthToken
import com.clineagentmobile.network.GitHubApiService
import com.clineagentmobile.network.RetrofitClient
import com.clineagentmobile.storage.EncryptedPrefsManager
import kotlinx.coroutines.launch

/**
 * Settings screen: GitHub Personal Access Token + bridge WebSocket URL.
 *
 * The PAT is verified against the GitHub API before being saved and is used for
 * two things:
 *  - direct Codespace management from the Dashboard (no bridge required)
 *  - authentication towards the bridge server (sent with the 'auth' message)
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var tokenInput: EditText
    private lateinit var tokenStatus: TextView
    private lateinit var bridgeInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        tokenInput = findViewById(R.id.settings_token_input)
        tokenStatus = findViewById(R.id.settings_token_status)
        bridgeInput = findViewById(R.id.settings_bridge_input)

        val prefs = try {
            EncryptedPrefsManager.getInstance(applicationContext)
        } catch (e: Exception) {
            android.util.Log.w("SettingsActivity", "Prefs unavailable", e)
            tokenStatus.text = getString(R.string.error_generic)
            return
        }

        val existingToken = prefs.getOAuthToken()?.accessToken
        if (!existingToken.isNullOrBlank()) {
            tokenInput.setText(existingToken)
            tokenStatus.text = getString(R.string.home_github_ok)
        }
        bridgeInput.setText(prefs.getBridgeUrl() ?: "")

        findViewById<Button>(R.id.settings_token_save).setOnClickListener { verifyAndSaveToken(prefs) }
        findViewById<Button>(R.id.settings_token_create).setOnClickListener { openTokenCreator() }
        findViewById<Button>(R.id.settings_bridge_save).setOnClickListener { saveBridgeUrl(prefs) }
    }

    private fun openTokenCreator() {
        val uri = Uri.parse(
            "https://github.com/settings/tokens/new?scopes=repo,codespace&description=Cline%20Agent%20Mobile"
        )
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
    }

    private fun verifyAndSaveToken(prefs: EncryptedPrefsManager) {
        val token = tokenInput.text.toString().trim()
        if (token.isEmpty()) {
            tokenStatus.text = getString(R.string.settings_token_missing)
            return
        }
        val button = findViewById<Button>(R.id.settings_token_save)
        button.isEnabled = false
        tokenStatus.text = getString(R.string.settings_token_validating)
        lifecycleScope.launch {
            runCatching {
                RetrofitClient.create(GitHubApiService::class.java).getUser("Bearer $token")
            }.onSuccess { user ->
                prefs.saveOAuthToken(
                    OAuthToken(accessToken = token, tokenType = "Bearer", scope = "")
                )
                tokenStatus.text = getString(R.string.settings_token_saved, user.login)
            }.onFailure { e ->
                tokenStatus.text =
                    getString(R.string.settings_token_invalid, e.localizedMessage ?: "error")
            }
            button.isEnabled = true
        }
    }

    private fun saveBridgeUrl(prefs: EncryptedPrefsManager) {
        val url = bridgeInput.text.toString().trim()
        prefs.saveBridgeUrl(url)
        if (url.isNotBlank()) {
            // Restart the service so an already-running client picks up the new URL
            val serviceIntent = Intent(this, BridgeService::class.java)
            stopService(serviceIntent)
            startService(serviceIntent)
        }
        Toast.makeText(this, R.string.settings_bridge_saved, Toast.LENGTH_SHORT).show()
    }
}
