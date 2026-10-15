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
import com.clineagentmobile.ai.OpenRouterCatalog
import com.clineagentmobile.bridge.BridgeService
import com.clineagentmobile.model.OAuthToken
import com.clineagentmobile.model.Repository
import com.clineagentmobile.network.GitHubApiService
import com.clineagentmobile.network.RetrofitClient
import com.clineagentmobile.storage.EncryptedPrefsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException

class SettingsActivity : AppCompatActivity() {

    private var bootstrapJob: Job? = null

    private lateinit var tokenInput: EditText
    private lateinit var tokenStatus: TextView
    private lateinit var bootstrapStatus: TextView
    private lateinit var bridgeAutoUrl: TextView
    private lateinit var repoInput: EditText
    private lateinit var repoStatus: TextView
    private lateinit var repoLoadButton: Button
    private lateinit var openRouterKeyInput: EditText
    private lateinit var openRouterStatus: TextView
    private lateinit var openRouterSaveButton: Button

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
        openRouterKeyInput = findViewById(R.id.settings_openrouter_key)
        openRouterStatus = findViewById(R.id.settings_openrouter_status)
        openRouterSaveButton = findViewById(R.id.settings_openrouter_save)

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
        openRouterStatus.setText(
            if (prefs.getOpenRouterKey().isNullOrBlank()) {
                R.string.settings_openrouter_key_missing
            } else {
                R.string.settings_openrouter_key_configured
            }
        )
        bridgeAutoUrl.text = prefs.getBridgeUrl()?.takeIf { it.isNotBlank() }
                ?.let { getString(R.string.settings_bridge_auto_url, it) }
                ?: getString(R.string.settings_bridge_auto_url_hint)
        // Ripristina l'ultimo step reale del bootstrap (se l'activity e' stata
        // riaperta, il default del layout "Token verificato, avvio provisioning…"
        // non deve restare li' a ingannare).
        CodespaceBootstrapManager.lastStep()?.let { bootstrapStatus.text = it }

        findViewById<Button>(R.id.settings_token_save).setOnClickListener {
            verifyAndSaveToken(prefs)
        }
        findViewById<Button>(R.id.settings_token_create).setOnClickListener {
            openTokenCreator()
        }
        openRouterSaveButton.setOnClickListener { verifyAndSaveOpenRouterKey(prefs) }
        findViewById<Button>(R.id.settings_openrouter_create).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/keys")))
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
        }
        repoInput.setText(prefs.getRepository()?.takeIf { it.isNotBlank() } ?: defaultRepo)
        updateRepoStatus(prefs.getRepository()?.takeIf { it.isNotBlank() } ?: defaultRepo)
        repoLoadButton.setOnClickListener {
            loadRepoPicker(prefs)
        }
        findViewById<Button>(R.id.settings_bootstrap_start).setOnClickListener {
            val btn = it as Button
            if (bootstrapJob?.isActive == true) {
                bootstrapJob?.cancel()
                bootstrapStatus.text = getString(R.string.settings_bootstrap_cancelled)
                btn.text = getString(R.string.settings_bootstrap_start)
                btn.isEnabled = true
                return@setOnClickListener
            }
            btn.isEnabled = false
            btn.text = getString(R.string.settings_bootstrap_cancel)
            bootstrapJob = lifecycleScope.launch {
                try {
                    bootstrapFlow(prefs)
                } finally {
                    runOnUiThread {
                        btn.isEnabled = true
                        btn.text = getString(R.string.settings_bootstrap_start)
                    }
                }
            }
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
                // 'SCOPE_CODESPACES_MANCANTE' viaggia in IllegalStateException.message.
                // Per gli altri errori mostriamo il messaggio leggibile.
                val missingScope = e.message == "SCOPE_CODESPACES_MANCANTE" ||
                    (e is IllegalStateException && e.cause?.message == "SCOPE_CODESPACES_MANCANTE")
                tokenStatus.text = if (missingScope) {
                    getString(R.string.settings_token_scope_missing)
                } else {
                    getString(R.string.settings_token_invalid, e.localizedMessage ?: "error")
                }
            }
            button.isEnabled = true
        }
    }

    private fun verifyAndSaveOpenRouterKey(prefs: EncryptedPrefsManager) {
        val key = openRouterKeyInput.text.toString().trim()
        if (key.isEmpty()) {
            openRouterStatus.setText(R.string.settings_openrouter_key_missing)
            return
        }
        openRouterSaveButton.isEnabled = false
        openRouterStatus.setText(R.string.settings_openrouter_key_validating)
        lifecycleScope.launch {
            try {
                OpenRouterCatalog.verifyApiKey(key)
                prefs.saveOpenRouterKey(key)
                openRouterKeyInput.text.clear()
                openRouterStatus.setText(R.string.settings_openrouter_key_configured)
            } catch (e: Exception) {
                openRouterStatus.text = getString(
                    R.string.settings_openrouter_key_error,
                    e.localizedMessage ?: e.javaClass.simpleName
                )
            } finally {
                openRouterSaveButton.isEnabled = true
            }
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
            val msg = scopeError.localizedMessage ?: "Unknown error"
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
                applicationContext.assets.open("bridge-server.js")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() },
                onStep = { step -> runOnUiThread { bootstrapStatus.text = step } },
                onComplete = { msg ->
                    runOnUiThread { bootstrapStatus.text = msg }
                }
            )
            prefs.saveBridgeUrl(bridgeUrl)
            // Salva anche l'URL tunnel diretto per la connessione WebSocket
            CodespaceBootstrapManager.lastTunnelUrl()?.let { tunnelUrl ->
                prefs.saveBridgeTunnelUrl(tunnelUrl)
            }
            bridgeAutoUrl.text = getString(R.string.settings_bridge_auto_url, bridgeUrl)
            bootstrapStatus.text = getString(R.string.settings_bridge_auto_url, bridgeUrl)
            prefs.saveOAuthToken(OAuthToken(accessToken = authToken, tokenType = "Bearer", scope = ""))
            tokenStatus.text = getString(R.string.settings_token_saved, authToken.take(4) + "…")
            android.util.Log.i("SettingsActivity", "Bootstrap OK, bridgeUrl=$bridgeUrl, avvio BridgeService")
            restartBridgeService()
        } catch (e: Exception) {
            tokenStatus.text = getString(R.string.settings_bridge_error, e.localizedMessage ?: "Unknown error")
            bootstrapStatus.text = getString(R.string.settings_bridge_error, e.localizedMessage ?: "Unknown error")
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
            // Foreground start: evita "Background start not allowed" su Android 12+
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            android.util.Log.w("SettingsActivity", "Impossibile riavviare il bridge service", e)
        }
    }
}
