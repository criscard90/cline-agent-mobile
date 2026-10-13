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
import com.clineagentmobile.settings.CodespaceBootstrapManager
import com.clineagentmobile.storage.EncryptedPrefsManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var tokenInput: EditText
    private lateinit var tokenStatus: TextView
    private lateinit var bootstrapStatus: TextView
    private lateinit var bridgeAutoUrl: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        tokenInput = findViewById(R.id.settings_token_input)
        tokenStatus = findViewById(R.id.settings_token_status)
        bootstrapStatus = findViewById(R.id.settings_bootstrap_status)
        bridgeAutoUrl = findViewById(R.id.settings_bridge_auto_url)

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
        bridgeAutoUrl.text = prefs.getBridgeUrl()?.takeIf { it.isNotBlank() }
                ?.let { getString(R.string.settings_bridge_auto_url, it) }
                ?: getString(R.string.settings_bridge_auto_url_hint)

        findViewById<Button>(R.id.settings_token_save).setOnClickListener {
            verifyAndSaveToken(prefs)
        }
        findViewById<Button>(R.id.settings_token_create).setOnClickListener {
            openTokenCreator()
        }
        findViewById<Button>(R.id.settings_bootstrap_start).setOnClickListener {
            lifecycleScope.launch { bootstrapFlow(prefs) }
        }
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
                prefs.saveOAuthToken(OAuthToken(accessToken = token, tokenType = "Bearer", scope = ""))
                tokenStatus.text = getString(R.string.settings_token_saved, user.login)
            }.onFailure { e ->
                tokenStatus.text =
                    getString(R.string.settings_token_invalid, e.localizedMessage ?: "error")
            }
            button.isEnabled = true
        }
    }

    suspend fun bootstrapFlow(prefs: EncryptedPrefsManager) {
        val authToken = prefs.getOAuthToken()?.accessToken ?: tokenInput.text.toString().trim()
        if (authToken.isEmpty()) {
            tokenStatus.text = getString(R.string.settings_token_missing)
            return
        }

        bootstrapStatus.text = getString(R.string.settings_bootstrap_status_initial)
        try {
            val bridgeUrl = CodespaceBootstrapManager.bootstrap(
                authToken,
                onStep = { step -> bootstrapStatus.text = step },
                onComplete = { msg ->
                    bootstrapStatus.text = msg
                    bridgeAutoUrl.text = getString(R.string.settings_bridge_auto_url, msg)
                }
            )
            prefs.saveBridgeUrl(bridgeUrl)
            prefs.saveOAuthToken(OAuthToken(accessToken = authToken, tokenType = "Bearer", scope = ""))
            restartBridgeService()
            tokenStatus.text = getString(R.string.settings_token_saved, authToken.take(4) + "…")
        } catch (e: Exception) {
            tokenStatus.text = getString(R.string.settings_bridge_error, e.localizedMessage ?: "errore")
            android.util.Log.e("SettingsActivity", "Bootstrap fallito", e)
        }
    }

    private fun restartBridgeService() {
        try {
            val intent = Intent(this, BridgeService::class.java)
            stopService(intent)
            startService(intent)
        } catch (e: Exception) {
            android.util.Log.w("SettingsActivity", "Impossibile riavviare il bridge service", e)
        }
    }
}
