package com.clineagentmobile.settings

import com.clineagentmobile.network.CodespaceCreateRequest
import com.clineagentmobile.network.GitHubApiService
import com.clineagentmobile.network.RetrofitClient
import com.clineagentmobile.model.Codespace
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import retrofit2.HttpException

private const val BRIDGE_CODESPACE_DISPLAY_NAME = "Cline Agent Mobile Bridge"

internal fun selectExistingCodespace(
    codespaces: List<Codespace>,
    repoFull: String
): Codespace? {
    val candidates = codespaces.filter { it.repository.fullName == repoFull }
    val managed = candidates.filter { it.displayName == BRIDGE_CODESPACE_DISPLAY_NAME }
    return managed.maxByOrNull { codespace ->
        try {
            OffsetDateTime.parse(codespace.updatedAt).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            Long.MIN_VALUE
        }
    } ?: candidates.firstOrNull {
        it.isRunning
    } ?: candidates.firstOrNull()
}

/**
 * Bootstrap automatico a solo PAT. Ogni passaggio è idempotente:
 *  0. self-provisioning: commit devcontainer+bridge nella repo scelta (via Contents API)
 *  1. cerca un codespace per il repo scelto
 *  2. ne crea uno se manca o se la configurazione del devcontainer è cambiata
 *  3. lo avvia se è fermo
 *  4. aspetta che sia davvero in esecuzione (poll)
 *  5. il lifecycle del devcontainer pubblica la porta con GitHub CLI
 *  6. verifica il bridge prima di restituire l'URL wss
 *  7. dopo una sostituzione riuscita, ferma il codespace precedente senza eliminarlo
 *
 * @param repoFull repository GitHub in formato owner/repo (es. octocat/hello-world);
 *                 il codespace viene cercato/creato su questo repo.
 */
object CodespaceBootstrapManager {
    private val MAIN = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var latestStep: String? = null
    @Volatile private var latestTunnelUrl: String? = null
    // Se il repo GitHub reale è diverso, modificalo qui o selezionalo nelle Impostazioni.
    private const val BRIDGE_PORT = 3000
    private const val POLL_INTERVAL_MS = 2_000L
    private const val POLL_TIMEOUT_MS = 300_000L

    fun lastStep(): String? = latestStep

    fun lastTunnelUrl(): String? = latestTunnelUrl

    suspend fun bootstrap(
        token: String,
        repoFull: String,
        bridgeServerScript: String,
        onStep: (String) -> Unit = {},
        onComplete: (String) -> Unit = {}
    ): String {
        // Retrofit sposta le suspend su thread IO: gli onStep arrivano da worker thread.
        // Normalizziamo qui dentro cosi' nessun chiamante puo' dimenticare il main thread.
        val mainStep: (String) -> Unit = { msg ->
            latestStep = msg
            android.util.Log.i("CodespaceBootstrap", "STEP: $msg")
            try {
                MAIN.post { onStep(msg) }
            } catch (e: Exception) {
                android.util.Log.w("CodespaceBootstrap", "onStep fallito: $msg", e)
            }
        }
        val mainComplete: (String) -> Unit = { msg ->
            latestStep = msg
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
        val bridgeStartupChanged =
            BridgeProvisioner.ensureBridgeFiles(
                api, auth, owner, repoName, bridgeServerScript, mainStep
            )

        mainStep("Searching for a Codespace in $repo")
        val existingCodespace = findExisting(api, auth, repo)
        val replacingExisting = existingCodespace != null && bridgeStartupChanged
        val cs = when {
            replacingExisting -> {
                mainStep("Configuration updated: creating a replacement Codespace.")
                createCodespace(api, auth, repo)
                    .also { mainStep("Replacement Codespace created: " + it.name) }
            }
            existingCodespace != null -> existingCodespace
            else -> createCodespace(api, auth, repo)
                .also { mainStep("Codespace created: " + it.name) }
        }

        if (cs.isStopped) {
            mainStep("Starting Codespace " + cs.name)
            api.startCodespace(auth, cs.name)
            mainStep("Codespace started: " + cs.name)
        }

        mainStep("Waiting for Codespace to start (max 300s)…")
        val running = pollUntilRunning(api, auth, cs.name, mainStep)
        mainStep("Codespace ready: " + running.name)

        mainStep("Waiting for bridge and automatic publication of port $BRIDGE_PORT…")
        if (!waitForBridgeServer(api, auth, cs.name, mainStep)) {
            throw IllegalStateException(
                "The bridge did not become publicly accessible on port $BRIDGE_PORT. " +
                    "Check /tmp/cline-bridge.log and /tmp/cline-port-visibility.log in the Codespace."
            )
        }

        if (replacingExisting && existingCodespace?.isRunning == true) {
            mainStep("Bridge ready; stopping previous Codespace " + existingCodespace.name)
            try {
                api.stopCodespace(auth, existingCodespace.name)
            } catch (e: HttpException) {
                throw IllegalStateException(
                    "Bridge is active in new Codespace ${cs.name}, but could not stop " +
                        "previous Codespace ${existingCodespace.name} (HTTP ${e.code()}).",
                    e
                )
            }
        }

        val wssUrl = "wss://" + running.name + "-" + BRIDGE_PORT + ".app.github.dev"
        latestTunnelUrl = wssUrl
        mainComplete("Bridge URL: $wssUrl")
        return wssUrl
    }

    private suspend fun findExisting(
        api: GitHubApiService,
        auth: String,
        repoFull: String
    ): Codespace? {
        val csList = api.listMyCodespaces(auth)
        return selectExistingCodespace(csList.codespaces, repoFull)
    }

    private fun validateRepoFull(repoFull: String) {
        val parts = repoFull.split('/', limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw IllegalArgumentException("Invalid repository: $repoFull (use owner/repository, e.g. octocat/hello-world)")
        }
    }

    private suspend fun createCodespace(
        api: GitHubApiService,
        auth: String,
        repoFull: String
    ): Codespace {
        val (owner, repo) = repoFull.split('/', limit = 2)
        return try {
            api.createCodespaceInRepo(
                auth,
                owner,
                repo,
                CodespaceCreateRequest(displayName = BRIDGE_CODESPACE_DISPLAY_NAME)
            )
        } catch (e: HttpException) {
            throw IllegalStateException("Unable to create a Codespace for $repoFull (HTTP ${e.code()}): ${e.message}", e)
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
                onStep("Codespace state: $state")
            }
            if (cs.isRunning) {
                return cs
            }
            kotlinx.coroutines.delay(POLL_INTERVAL_MS)
        }
        throw IllegalStateException("Timeout: Codespace $name did not start within ${POLL_TIMEOUT_MS / 1000}s.")
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
            repeat(BRIDGE_CHECK_ATTEMPTS) { attempt ->
                try {
                    val cs = api.getMyCodespace(auth, name)
                    val httpUrl = "https://" + cs.name + "-" + BRIDGE_PORT + ".app.github.dev"
                    val req = okhttp3.Request.Builder().url(httpUrl).get().build()
                    client.newCall(req).execute().use { resp ->
                        val body = resp.peekBody(256).string().trim()
                        if (resp.isSuccessful && body == "Cline Agent Mobile Bridge Server") {
                            onStep("Bridge verificato (HTTP ${resp.code})")
                            return@withContext true
                        }
                        onStep(
                            "Bridge/port check ${attempt + 1}/$BRIDGE_CHECK_ATTEMPTS: " +
                                "not accessible yet (HTTP ${resp.code})"
                        )
                    }
                } catch (e: Exception) {
                    onStep(
                        "Bridge not reachable yet " +
                            "(attempt ${attempt + 1}/$BRIDGE_CHECK_ATTEMPTS)"
                    )
                    android.util.Log.d(
                        "CodespaceBootstrap",
                        "Bridge non ancora raggiungibile " +
                            "(tentativo ${attempt + 1}/$BRIDGE_CHECK_ATTEMPTS): ${e.message}"
                    )
                }
                kotlinx.coroutines.delay(BRIDGE_CHECK_INTERVAL_MS)
            }
            false
        }
    }

    private const val BRIDGE_CHECK_ATTEMPTS = 24
    private const val BRIDGE_CHECK_INTERVAL_MS = 5_000L
}
