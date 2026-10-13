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
 *  1. cerca un codespace per il repo della app (eseguendolo)
 *  2. lo crea se manca
 *  3. lo avvia se è fermo
 *  4. aspetta che sia davvero in esecuzione (poll)
 *  5. pubblica la porta 3000 (bridge) sulla rete pubblica
 *  6. restituisce l'URL wss da salvare in Prefs e dal quale il bridge si collega
 */
object CodespaceBootstrapManager {
    // Repository hosting this app (devcontainer target). GitHub codespaces
    // injecta lo stesso valore come variabile d'ambiente standard GITHUB_REPOSITORY.
    // Se il repo GitHub reale è diverso, modifica qui.
    private const val PROJECT_OWNER = "criscard90"
    private const val PROJECT_REPO = "cline-agent-mobile"
    private val PROJECT_FULL = "$PROJECT_OWNER/$PROJECT_REPO"
    private const val BRIDGE_PORT = 3000
    private const val POLL_INTERVAL_MS = 2_000L
    private const val POLL_TIMEOUT_MS = 120_000L

    suspend fun bootstrap(
        token: String,
        onStep: (String) -> Unit = {},
        onComplete: (String) -> Unit = {}
    ): String {
        val api = RetrofitClient.create(GitHubApiService::class.java)
        val auth = "Bearer $token"

        onStep("Ricerca codespace in " + PROJECT_FULL)
        val cs = findExisting(api, auth, PROJECT_FULL)
            ?: createCodespace(api, auth).also { onStep("Codespace creato: " + it.name) }

        if (cs.isStopped) {
            onStep("Avvio codespace " + cs.name)
            api.startCodespace(auth, cs.name)   // REST suspend -> dichiara il coroutine
            onStep("Codespace avviato: " + cs.name)
        }

        onStep("Attesa codespace in esecuzione (max " + POLL_TIMEOUT_MS / 1000 + "s)...")
        val running = pollUntilRunning(api, auth, cs.name)
        onStep("Codespace pronto: " + running.name)

        onStep("Pubblicazione porta " + BRIDGE_PORT + " su public...")
        publishBridgePort(api, auth, cs.name)

        val wssUrl = "wss://" + cs.name + "-" + BRIDGE_PORT + ".app.github.dev"
        onComplete("Bridge URL: " + wssUrl)
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

    private suspend fun createCodespace(api: GitHubApiService, auth: String): Codespace {
        return try {
            api.createCodespaceInRepo(auth, PROJECT_OWNER, PROJECT_REPO, CodespaceCreateRequest())
        } catch (e: HttpException) {
            throw IllegalStateException("Impossibile creare i codespace (codice " + e.code() + "): " + e.message, e)
        }
    }

    private suspend fun pollUntilRunning(
        api: GitHubApiService,
        auth: String,
        name: String
    ): Codespace {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < POLL_TIMEOUT_MS) {
            val cs = api.getMyCodespace(auth, name)
            if (cs.isRunning) {
                return cs
            }
            kotlinx.coroutines.delay(POLL_INTERVAL_MS)
        }
        throw IllegalStateException("Timeout: codespace $name non è riuscito a partire entro ${POLL_TIMEOUT_MS / 1000}s.")
    }

    private suspend fun publishBridgePort(api: GitHubApiService, auth: String, name: String) {
        try {
            api.updateCodespacePort(auth, name, BRIDGE_PORT, CodespacePortUpdate("public"))
        } catch (e: HttpException) {
            val code = e.code()
            val msg = "Endpoint publish port non disponibile (codice $code); " + "pubblica manualmente la porta nelle Impostazioni."
            android.util.Log.w("CodespaceBootstrap", msg, e)
            throw IllegalStateException(msg, e)
        }
    }
}
