package com.clineagentmobile.settings

import android.content.Context
import com.clineagentmobile.network.CodespaceCreateRequest
import com.clineagentmobile.network.CodespacePortUpdate
import com.clineagentmobile.network.GitHubApiService
import com.clineagentmobile.network.RetrofitClient
import com.clineagentmobile.model.Codespace
import retrofit2.HttpException

/**
 * Bootstrap automatico a solo PAT. Ogni passaggio è idempotente:
 *  0. self-provisioning: commit devcontainer+bridge nella repo scelta (via Contents API)
 *  1. cerca un codespace per il repo scelto (eseguendolo)
 *  2. lo crea se manca
 *  3. lo avvia se è fermo
 *  4. aspetta che sia davvero in esecuzione (poll)
 *  5. pubblica la porta 3000 (bridge) sulla rete pubblica
 *  6. restituisce l'URL wss da salvare in Prefs e dal quale il bridge si collega
 *
 * @param repoFull repository GitHub in formato owner/repo (es. octocat/hello-world);
 *                 il codespace viene cercato/creato su questo repo.
 */
object CodespaceBootstrapManager {
    private val MAIN = android.os.Handler(android.os.Looper.getMainLooper())
    // Se il repo GitHub reale è diverso, modificalo qui o selezionalo nelle Impostazioni.
    private const val BRIDGE_PORT = 3000
    private const val POLL_INTERVAL_MS = 2_000L
    private const val POLL_TIMEOUT_MS = 300_000L

    suspend fun bootstrap(
        token: String,
        repoFull: String,
        onStep: (String) -> Unit = {},
        onComplete: (String) -> Unit = {}
    ): String {
        // Retrofit sposta le suspend su thread IO: gli onStep arrivano da worker thread.
        // Normalizziamo qui dentro cosi' nessun chiamante puo' dimenticare il main thread.
        val mainStep: (String) -> Unit = { msg ->
            android.util.Log.i("CodespaceBootstrap", "STEP: $msg")
            try {
                MAIN.post { onStep(msg) }
            } catch (e: Exception) {
                android.util.Log.w("CodespaceBootstrap", "onStep fallito: $msg", e)
            }
        }
        val mainComplete: (String) -> Unit = { msg ->
            android.util.Log.i("CodespaceBootstrap", "COMPLETE: $msg")
            try {
                MAIN.post { onComplete(msg) }
            } catch (e: Exception) {
                android.util.Log.w("CodespaceBootstrap", "onComplete fallito: $msg", e)
            }
        }
        val api = RetrofitClient.create(GitHubApiService::class.java)
        val auth = "Bearer $token"
        val repo = repoFull.trim()

        validateRepoFull(repo)
        val (owner, repoName) = repo.split('/', limit = 2)

        mainStep("Provisioning bridge in $repo (auto-commit devcontainer)")
        BridgeProvisioner.ensureBridgeFiles(api, auth, owner, repoName, mainStep)

        mainStep("Ricerca codespace in $repo")
        val cs = findExisting(api, auth, repo)
            ?: createCodespace(api, auth, repo)
                .also { mainStep("Codespace creato: " + it.name) }

        if (cs.isStopped) {
            mainStep("Avvio codespace " + cs.name)
            api.startCodespace(auth, cs.name)
            mainStep("Codespace avviato: " + cs.name)
        }

        mainStep("Attesa codespace in esecuzione (max 300s)...")
        val running = pollUntilRunning(api, auth, cs.name, mainStep)
        mainStep("Codespace pronto: " + running.name)

        mainStep("Verifica bridge server sulla porta $BRIDGE_PORT...")
        val bridgeReady = waitForBridgeServer(api, auth, cs.name, mainStep)
        if (!bridgeReady) {
            mainStep(
                "Bridge server non risponde sulla porta $BRIDGE_PORT: " +
                    "apri il codespace, avvia 'node bridge/server.js' e riprova."
            )
        }

        val portPublished = publishBridgePort(api, auth, cs.name)
        val wssUrl = "wss://" + cs.name + "-" + BRIDGE_PORT + ".app.github.dev"
        if (!portPublished) {
            mainStep("Attenzione: porta $BRIDGE_PORT non pubblicata. Aprite Impostazioni codespace e esponete 3000 in modalità public.")
            mainComplete("Bridge URL: $wssUrl (porta 3000 non pubblicata: esporla nelle Impostazioni del codespace)")
        } else {
            mainComplete("Bridge URL: $wssUrl")
        }
        return wssUrl
    }

    private suspend fun findExisting(
        api: GitHubApiService,
        auth: String,
        repoFull: String
    ): Codespace? {
        val csList = api.listMyCodespaces(auth)
        val candidate = csList.codespaces.firstOrNull {
            it.repository.fullName == repoFull && it.state == Codespace.State.RUNNING
        } ?: csList.codespaces.firstOrNull {
            it.repository.fullName == repoFull
        }
        return candidate
    }

    private fun validateRepoFull(repoFull: String) {
        val parts = repoFull.split('/', limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw IllegalArgumentException("Repository non valido: $repoFull (usa owner/nome-repo, es. octocat/hello-world)")
        }
    }

    private suspend fun createCodespace(api: GitHubApiService, auth: String, repoFull: String): Codespace {
        val (owner, repo) = repoFull.split('/', limit = 2)
        return try {
            api.createCodespaceInRepo(auth, owner, repo, CodespaceCreateRequest())
        } catch (e: HttpException) {
            throw IllegalStateException("Impossibile creare i codespace su $repoFull (codice ${e.code()}): ${e.message}", e)
        }
    }

    private suspend fun pollUntilRunning(
        api: GitHubApiService,
        auth: String,
        name: String,
        onStep: (String) -> Unit = {}
    ): Codespace {
        val start = System.currentTimeMillis()
        var lastState = ""
        while (System.currentTimeMillis() - start < POLL_TIMEOUT_MS) {
            val cs = api.getMyCodespace(auth, name)
            val state = cs.state.name
            if (state != lastState) {
                lastState = state
                onStep("Stato codespace: $state")
            }
            if (cs.isRunning) {
                return cs
            }
            kotlinx.coroutines.delay(POLL_INTERVAL_MS)
        }
        throw IllegalStateException("Timeout: codespace $name non è riuscito a partire entro ${POLL_TIMEOUT_MS / 1000}s.")
    }

    private suspend fun waitForBridgeServer(
        api: GitHubApiService,
        auth: String,
        name: String,
        onStep: (String) -> Unit = {}
    ): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            repeat(6) { attempt ->
                try {
                    val cs = api.getMyCodespace(auth, name)
                    val httpUrl = "https://" + cs.name + "-" + BRIDGE_PORT + ".app.github.dev"
                    val req = okhttp3.Request.Builder().url(httpUrl).get().build()
                    client.newCall(req).execute().use { resp ->
                        onStep("Bridge check ${attempt + 1}/6: HTTP ${resp.code}")
                        if (resp.isSuccessful) return@withContext true
                    }
                } catch (e: Exception) {
                    onStep("Bridge non ancora raggiungibile (tentativo ${attempt + 1}/6)")
                    android.util.Log.d(
                        "CodespaceBootstrap",
                        "Bridge non ancora raggiungibile (tentativo ${attempt + 1}/6): ${e.message}"
                    )
                }
                kotlinx.coroutines.delay(5_000L)
            }
            false
        }
    }

    private suspend fun publishBridgePort(api: GitHubApiService, auth: String, name: String): Boolean {
        return try {
            api.updateCodespacePort(auth, name, BRIDGE_PORT, CodespacePortUpdate("public"))
            true
        } catch (e: HttpException) {
            android.util.Log.w(
                "CodespaceBootstrap",
                "Pubblicazione porta $BRIDGE_PORT non riuscita (codice ${e.code()}); risultato: pubblicare manualmente la porta 3000 nelle Impostazioni del codespace.",
                e
            )
            false
        }
    }
}
