package com.clineagentmobile.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.clineagentmobile.R
import com.clineagentmobile.bridge.BridgeService
import com.clineagentmobile.model.OAuthToken
import com.clineagentmobile.model.Repository
import com.clineagentmobile.network.GitHubApiService
import com.clineagentmobile.network.RetrofitClient
import com.clineagentmobile.storage.EncryptedPrefsManager
import kotlinx.coroutines.launch
import retrofit2.HttpException

class SettingsActivity : AppCompatActivity() {

    private lateinit var tokenInput: EditText
    private lateinit var tokenStatus: TextView
    private lateinit var bootstrapStatus: TextView
    private lateinit var bridgeAutoUrl: TextView
    private lateinit var repoInput: EditText
    private lateinit var repoStatus: TextView
    private lateinit var repoLoadButton: Button

    private val defaultRepo = "criscard90/cline-agent-mobile"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        tokenInput = findViewById(R.id.settings_token_input)
        tokenStatus = findViewById(R.id.settings_token_status)
        bootstrapStatus = findViewById(R.id.settings_bootstrap_status)
        bridgeAutoUrl = findViewById(R.id.settings_bridge_auto_url)
        repoInput = findViewById(R.id.settings_repo_input)
        repoStatus = findViewById(R.id.settings_repo_status)
        repoLoadButton = findViewById(R.id.settings_repo_load)

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
        repoInput.setText(prefs.getRepository()?.takeIf { it.isNotBlank() } ?: defaultRepo)
        updateRepoStatus(prefs.getRepository()?.takeIf { it.isNotBlank() } ?: defaultRepo)
        repoLoadButton.setOnClickListener {
            loadRepoPicker(prefs)
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
                val api = RetrofitClient.create(GitHubApiService::class.java)
                val auth = "Bearer $token"
                val user = api.getUser(auth)
                try {
                    api.listMyCodespaces(auth)
                } catch (scopeError: HttpException) {
                    if (scopeError.code() == 401 || scopeError.code() == 403) {
                        throw IllegalStateException("SCOPE_CODESPACES_MANCANTE")
                    }
                    throw scopeError
                }
                user
            }.onSuccess { user ->
                prefs.saveOAuthToken(OAuthToken(accessToken = token, tokenType = "Bearer", scope = ""))
                tokenStatus.text = getString(R.string.settings_token_saved, user.login)
            }.onFailure { e ->
                tokenStatus.text = if (e.message == "SCOPE_CODESPACES_MANCANTE") {
                    getString(R.string.settings_token_scope_missing)
                } else {
                    getString(R.string.settings_token_invalid, e.localizedMessage ?: "error")
                }
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

        val repoFull = repoInput.text.toString().trim().ifEmpty {
            prefs.getRepository()?.takeIf { it.isNotBlank() } ?: defaultRepo
        }
        if (!repoFull.contains('/') || repoFull.startsWith('/') || repoFull.endsWith('/')) {
            bootstrapStatus.text = getString(R.string.settings_repo_invalid)
            return
        }
        prefs.saveRepository(repoFull)
        updateRepoStatus(repoFull)

        bootstrapStatus.text = getString(R.string.settings_bootstrap_status_initial)
        android.util.Log.i("SettingsActivity", "Bootstrap start su $repoFull")
        val tokenForBootstrap = try {
            preflightCodespaceScope(authToken)
        } catch (scopeError: Exception) {
            val msg = scopeError.localizedMessage ?: "errore"
            val label = if (msg == "SCOPE_CODESPACES_MANCANTE") {
                getString(R.string.settings_token_scope_missing)
            } else {
                getString(R.string.settings_bridge_error, msg)
            }
            tokenStatus.text = label
            bootstrapStatus.text = label
            android.util.Log.e("SettingsActivity", "Bootstrap preflight fallito", scopeError)
            return
        }
        try {
            // Nota: CodespaceBootstrapManager invoca onStep/onComplete gia' sul main thread.
            val bridgeUrl = CodespaceBootstrapManager.bootstrap(
                tokenForBootstrap,
                repoFull,
                onStep = { step -> runOnUiThread { bootstrapStatus.text = step } },
                onComplete = { msg ->
                    runOnUiThread { bootstrapStatus.text = msg }
                }
            )
            prefs.saveBridgeUrl(bridgeUrl)
            bridgeAutoUrl.text = getString(R.string.settings_bridge_auto_url, bridgeUrl)
            bootstrapStatus.text = getString(R.string.settings_bridge_auto_url, bridgeUrl)
            prefs.saveOAuthToken(OAuthToken(accessToken = authToken, tokenType = "Bearer", scope = ""))
            tokenStatus.text = getString(R.string.settings_token_saved, authToken.take(4) + "…")
            android.util.Log.i("SettingsActivity", "Bootstrap OK, bridgeUrl=$bridgeUrl, avvio BridgeService")
            restartBridgeService()
        } catch (e: Exception) {
            tokenStatus.text = getString(R.string.settings_bridge_error, e.localizedMessage ?: "errore")
            bootstrapStatus.text = getString(R.string.settings_bridge_error, e.localizedMessage ?: "errore")
            android.util.Log.e("SettingsActivity", "Bootstrap fallito", e)
        }
    }

    private fun updateRepoStatus(repoFull: String) {
        repoStatus.text = getString(R.string.settings_repo_status, repoFull)
    }

    private fun loadRepoPicker(prefs: EncryptedPrefsManager) {
        val authToken = prefs.getOAuthToken()?.accessToken ?: tokenInput.text.toString().trim()
        if (authToken.isEmpty()) {
            Toast.makeText(this, R.string.settings_repo_no_token, Toast.LENGTH_SHORT).show()
            return
        }
        repoLoadButton.isEnabled = false
        repoStatus.text = getString(R.string.settings_repo_loading)
        lifecycleScope.launch {
            runCatching {
                RetrofitClient.create(GitHubApiService::class.java)
                    .getRepositories("Bearer $authToken", perPage = 100)
            }.onSuccess { repos ->
                showRepoDialog(prefs, repos)
            }.onFailure { e ->
                repoStatus.text = getString(
                    R.string.settings_token_invalid,
                    e.localizedMessage ?: "error"
                )
            }
            repoLoadButton.isEnabled = true
        }
    }

    private fun showRepoDialog(prefs: EncryptedPrefsManager, repos: List<Repository>) {
        val names = repos.map { it.fullName.ifBlank { it.name } }.filter { it.isNotBlank() }
        if (names.isEmpty()) {
            repoStatus.text = getString(R.string.settings_repo_empty)
            return
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_repo_pick_title)
            .setAdapter(adapter) { _, which ->
                val picked = names[which]
                repoInput.setText(picked)
                prefs.saveRepository(picked)
                updateRepoStatus(picked)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private suspend fun preflightCodespaceScope(authToken: String): String {
        val api = RetrofitClient.create(GitHubApiService::class.java)
        val auth = "Bearer $authToken"
        try {
            api.listMyCodespaces(auth)
        } catch (scopeError: HttpException) {
            if (scopeError.code() == 401 || scopeError.code() == 403) {
                throw IllegalStateException("SCOPE_CODESPACES_MANCANTE")
            }
            throw scopeError
        }
        return authToken
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
