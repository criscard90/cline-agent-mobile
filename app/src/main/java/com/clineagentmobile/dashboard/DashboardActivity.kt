package com.clineagentmobile.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.clineagentmobile.R
import com.clineagentmobile.model.Codespace
import com.clineagentmobile.network.GitHubApiService
import com.clineagentmobile.network.RetrofitClient
import com.clineagentmobile.settings.SettingsActivity
import com.clineagentmobile.storage.EncryptedPrefsManager
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Codespace dashboard: lists the user's Codespaces via the GitHub REST API
 * (works even before the bridge is configured) and allows create/start/stop/delete.
 */
class DashboardActivity : AppCompatActivity() {

    private val api: GitHubApiService by lazy { RetrofitClient.create(GitHubApiService::class.java) }
    private lateinit var adapter: CodespaceAdapter
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var emptyState: View
    private lateinit var emptyText: TextView
    private lateinit var openSettingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        progress = findViewById(R.id.dashboard_progress)
        errorText = findViewById(R.id.dashboard_error)
        emptyState = findViewById(R.id.dashboard_empty_state)
        emptyText = findViewById(R.id.dashboard_empty)
        openSettingsButton = findViewById(R.id.dashboard_open_settings)

        adapter = CodespaceAdapter(
            onOpen = { openInBrowser(it) },
            onStart = { performAction(it, ACTION_START) },
            onStop = { performAction(it, ACTION_STOP) },
            onDelete = { confirmDelete(it) }
        )
        val recyclerView = findViewById<RecyclerView>(R.id.dashboard_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.dashboard_create).setOnClickListener { showCreateDialog() }
        findViewById<Button>(R.id.dashboard_refresh).setOnClickListener { load() }
        openSettingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun token(): String? = try {
        EncryptedPrefsManager.getInstance(applicationContext).getOAuthToken()?.accessToken
    } catch (e: Exception) {
        android.util.Log.w("DashboardActivity", "Cannot read token", e)
        null
    }

    private fun load() {
        val token = token()
        if (token.isNullOrBlank()) {
            setLoading(false)
            adapter.submit(emptyList())
            errorText.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            emptyText.text = getString(R.string.dashboard_no_token)
            openSettingsButton.visibility = View.VISIBLE
            return
        }
        openSettingsButton.visibility = View.GONE
        emptyText.text = getString(R.string.dashboard_no_codespaces)
        setLoading(true)
        lifecycleScope.launch {
            runCatching { api.listMyCodespaces("Bearer $token") }
                .onSuccess { showList(it.codespaces) }
                .onFailure { showError(it) }
            setLoading(false)
        }
    }

    private fun showList(codespaces: List<Codespace>) {
        adapter.submit(codespaces)
        errorText.visibility = View.GONE
        emptyState.visibility = if (codespaces.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showCreateDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.dashboard_create_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dashboard_create_title)
            .setView(input)
            .setPositiveButton(R.string.dashboard_create) { _, _ ->
                createCodespace(input.text.toString().trim())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createCodespace(repository: String) {
        val token = token() ?: return
        val parts = repository.trim().split("/")
        if (parts.size != 2 || parts.any { it.isBlank() }) {
            errorText.text = getString(R.string.dashboard_create_invalid_repo)
            errorText.visibility = View.VISIBLE
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            runCatching {
                api.createCodespaceInRepo("Bearer $token", parts[0], parts[1])
            }.onSuccess {
                Toast.makeText(this@DashboardActivity, R.string.dashboard_action_done, Toast.LENGTH_SHORT).show()
                load()
            }.onFailure { e ->
                setLoading(false)
                showError(e)
            }
        }
    }

    private fun confirmDelete(codespace: Codespace) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dashboard_delete)
            .setMessage(getString(R.string.dashboard_delete_confirm, codespace.name))
            .setPositiveButton(R.string.delete) { _, _ -> performAction(codespace, ACTION_DELETE) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performAction(codespace: Codespace, action: String) {
        val token = token() ?: return
        setLoading(true)
        lifecycleScope.launch {
            runCatching {
                when (action) {
                    ACTION_START -> api.startCodespace("Bearer $token", codespace.name)
                    ACTION_STOP -> api.stopCodespace("Bearer $token", codespace.name)
                    else -> api.deleteCodespace("Bearer $token", codespace.name)
                }
            }.onSuccess {
                Toast.makeText(this@DashboardActivity, R.string.dashboard_action_done, Toast.LENGTH_SHORT).show()
                load()
            }.onFailure { e ->
                setLoading(false)
                showError(e)
            }
        }
    }

    private fun openInBrowser(codespace: Codespace) {
        val url = codespace.webUrl ?: codespace.url ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showError(e: Throwable) {
        val message = if (e is HttpException) {
            val detail = e.response()?.errorBody()?.string()?.let { parseGithubError(it) }
            "HTTP ${e.code()}: ${detail ?: e.message()}"
        } else {
            e.localizedMessage ?: e.javaClass.simpleName
        }
        errorText.text = getString(R.string.dashboard_action_error, message)
        errorText.visibility = View.VISIBLE
    }

    /** Extracts the human-readable "message" (and "errors") from a GitHub API error body. */
    private fun parseGithubError(body: String): String? = try {
        val json = org.json.JSONObject(body)
        val msg = json.optString("message").takeIf { it.isNotBlank() }
        val errors = json.optJSONArray("errors")
            ?.let { arr -> (0 until arr.length()).joinToString("; ") { i -> arr.optString(i) } }
            ?.takeIf { it.isNotBlank() }
        listOfNotNull(msg, errors).joinToString(" — ").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val ACTION_DELETE = "delete"
    }
}

