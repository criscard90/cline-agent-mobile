package com.clineagentmobile.settings

import android.util.Base64
import android.util.Log
import com.clineagentmobile.network.ContentFileResponse
import com.clineagentmobile.network.ContentPutRequest
import com.clineagentmobile.network.GitHubApiService
import retrofit2.HttpException

/**
 * Self-provisioning del bridge (solo PAT, zero operazioni manuali sul codespace).
 *
 * Prima del bootstrap scrive (o aggiorna) nella repo scelta, via Contents API:
 *  - `.devcontainer/devcontainer.json` con postStartCommand che avvia il bridge
 *  - `bridge/package.json` (dipendenza ws)
 *  - `bridge/server.js` minimale compatibile col protocollo app
 *    (auth -> auth_success, ping -> pong, chat_message -> echo, HTTP 200 per health-check)
 */
object BridgeProvisioner {

    suspend fun ensureBridgeFiles(
        api: GitHubApiService,
        auth: String,
        owner: String,
        repo: String,
        onStep: (String) -> Unit = {}
    ) {
        val info = try {
            api.getRepo(auth, owner, repo)
        } catch (e: HttpException) {
            throw IllegalStateException(
                "Repo $owner/$repo non accessibile (codice ${e.code()}): verifica nome e permessi del token",
                e
            )
        }
        val branch = info.defaultBranch.ifBlank { "main" }

        onStep("Verifica file bridge in $owner/$repo")
        putIfNeeded(api, auth, owner, repo, branch, ".devcontainer/devcontainer.json", DEVCONTAINER_JSON)
        putIfNeeded(api, auth, owner, repo, branch, "bridge/package.json", BRIDGE_PACKAGE_JSON)
        putIfNeeded(api, auth, owner, repo, branch, "bridge/server.js", BRIDGE_SERVER_JS)
        putIfNeeded(api, auth, owner, repo, branch, "bridge/.gitignore", BRIDGE_GITIGNORE)
    }

    private suspend fun putIfNeeded(
        api: GitHubApiService,
        auth: String,
        owner: String,
        repo: String,
        branch: String,
        path: String,
        desired: String
    ) {
        // Se e' una directory, la Contents API restituisce un ARRAY (non un file):
        // getContentFile lancia JsonSyntaxException ("Expected BEGIN_OBJECT but was BEGIN_ARRAY"):
        // la trattiamo come "assente" (poi la PUT crea il file).
        // 404 = file mancante -> PUT senza sha.
        val existing: ContentFileResponse? = try {
            Log.d("BridgeProvisioner", "GET $path (branch=$branch)")
            api.getContentFile(auth, owner, repo, path, branch)
        } catch (e: HttpException) {
            if (e.code() == 404) {
                Log.d("BridgeProvisioner", "$path assente (404), verra' creato")
                null
            } else throw IllegalStateException(
                "Lettura $path fallita (codice ${e.code()}): ${e.message}", e
            )
        } catch (e: com.google.gson.JsonSyntaxException) {
            Log.d("BridgeProvisioner", "$path e' una directory, verra' creato il file dentro")
            null
        }
        val existingText = existing?.content
            ?.takeIf { existing.encoding.equals("base64", ignoreCase = true) }
            ?.let { decodeBase64(it) }
        if (existingText != null && normalize(existingText) == normalize(desired)) return

        val body = ContentPutRequest(
            message = "chore(bridge): self-provisioning Cline Agent Mobile",
            content = encodeBase64(desired),
            sha = existing?.sha,
            branch = branch
        )
        try {
            api.putContent(auth, owner, repo, path, body)
            Log.i("BridgeProvisioner", "PUT $path OK")
        } catch (e: HttpException) {
            throw IllegalStateException(
                "Scrittura $path fallita (codice ${e.code()}): serve scope repo con permessi di scrittura",
                e
            )
        }
    }

    private fun encodeBase64(text: String): String =
        Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decodeBase64(b64: String): String {
        val cleaned = b64.replace("\\s".toRegex(), "")
        return String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun normalize(text: String): String = text.replace("\r\n", "\n").trim()
    private const val DEVCONTAINER_JSON = """{
  "name": "Cline Agent Mobile - Bridge",
  "image": "mcr.microsoft.com/devcontainers/javascript-node:1-18-bullseye",
  "forwardPorts": [3000],
  "portsAttributes": {
    "3000": {
      "label": "Cline Bridge",
      "onAutoForward": "notify"
    }
  },
  "postCreateCommand": "cd bridge && npm install",
  "postStartCommand": "cd bridge && (npm install --no-audit --no-fund || true) && nohup node server.js > /tmp/cline-bridge.log 2>&1 &"
}
""";
    private const val BRIDGE_PACKAGE_JSON = """{
  "name": "cline-agent-bridge",
  "version": "1.0.0",
  "private": true,
  "description": "Bridge server auto-provisioned by Cline Agent Mobile",
  "main": "server.js",
  "scripts": {
    "start": "node server.js"
  },
  "engines": {
    "node": ">=18.0.0"
  },
  "license": "MIT",
  "dependencies": {
    "ws": "^8.16.0"
  }
}
""";
    private const val BRIDGE_GITIGNORE = "node_modules/\n";
    // Bridge minimale: HTTP 200 su / (health-check), WS con auth/ping/chat-echo.
    private const val BRIDGE_SERVER_JS = """const http = require('http');
const WebSocket = require('ws');
const PORT = process.env.PORT || 3000;
function send(ws, type, payload) {
  ws.send(JSON.stringify({ type: type, payload: payload || {} }));
}
const server = http.createServer(function (req, res) {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('Cline Agent Mobile Bridge Server');
});
const wss = new WebSocket.Server({ server });
wss.on('connection', function (ws) {
  send(ws, 'status', { bridgeState: { connected: true, status: 'connected' } });
  ws.on('message', function (raw) {
    var msg;
    try { msg = JSON.parse(raw.toString()); } catch (e) { return; }
    var payload = msg.payload || {};
    if (msg.type === 'auth') {
      send(ws, 'auth_success', { bridgeState: { connected: true, authenticated: true, status: 'authenticated' } });
    } else if (msg.type === 'ping') {
      send(ws, 'pong', {});
    } else if (msg.type === 'chat_message') {
      var text = (payload.message && payload.message.content) || payload.text || '';
      send(ws, 'chat_message', { message: { role: 'assistant', content: 'Bridge auto-provisionato. Echo: ' + text } });
    }
  });
});
server.listen(PORT, function () {
  console.log('Bridge server listening on port ' + PORT);
});
""";
}

