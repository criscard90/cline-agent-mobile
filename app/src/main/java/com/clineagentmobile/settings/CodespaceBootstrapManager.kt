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
        val api = RetrofitClient.create(GitHubApiService::class.java)
        val auth = "Bearer $token"
        val repo = repoFull.trim()

        validateRepoFull(repo)

        onStep("Ricerca codespace in $repo")
        val cs = findExisting(api, auth, repo)
            ?: createCodespace(api, auth, repo)
                .also { onStep("Codespace creato: " + it.name) }

        if (cs.isStopped) {
            onStep("Avvio codespace " + cs.name)
            api.startCodespace(auth, cs.name)
            onStep("Codespace avviato: " + cs.name)
        }

        onStep("Attesa codespace in esecuzione (max 300s)...")
        val running = pollUntilRunning(api, auth, cs.name)
        onStep("Codespace pronto: " + running.name)

        val portPublished = publishBridgePort(api, auth, cs.name)
        val wssUrl = "wss://" + cs.name + "-" + BRIDGE_PORT + ".app.github.dev"
        if (!portPublished) {
            onStep("Attenzione: porta $BRIDGE_PORT non pubblicata. Aprite Impostazioni codespace e esponete 3000 in modalità public.")
            onComplete("Bridge URL: $wssUrl (porta 3000 non pubblicata: esporla nelle Impostazioni del codespace)")
        } else {
            onComplete("Bridge URL: $wssUrl")
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
