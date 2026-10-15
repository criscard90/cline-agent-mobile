package com.clineagentmobile.settings

import android.util.Base64
import android.util.Log
import com.clineagentmobile.network.ContentFileResponse
import com.clineagentmobile.network.ContentPutRequest
import com.clineagentmobile.network.GitHubApiService
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import retrofit2.HttpException

/**
 * Self-provisioning del bridge (solo PAT, zero operazioni manuali sul codespace).
 *
 * Prima del bootstrap scrive (o aggiorna) nella repo scelta, via Contents API:
 *  - `.devcontainer/devcontainer.json` con postStartCommand che avvia il bridge
 *  - `bridge/package.json` (dipendenza ws)
 *  - `bridge/server.js` con il bridge WebSocket e l'integrazione con Cline CLI
 */
object BridgeProvisioner {

    suspend fun ensureBridgeFiles(
        api: GitHubApiService,
        auth: String,
        owner: String,
        repo: String,
        bridgeServerScript: String,
        onStep: (String) -> Unit = {}
    ): Boolean {
        val info = try {
            api.getRepo(auth, owner, repo)
        } catch (e: HttpException) {
            throw IllegalStateException(
                "Repository $owner/$repo is inaccessible (HTTP ${e.code()}): check the name and token permissions",
                e
            )
        }
        val branch = info.defaultBranch.ifBlank { "main" }

        onStep("Checking bridge files in $owner/$repo")
        val devcontainerChanged = putIfNeeded(
            api, auth, owner, repo, branch, ".devcontainer/devcontainer.json", DEVCONTAINER_JSON
        )
        val portScriptChanged = putIfNeeded(
            api, auth, owner, repo, branch, "bridge/configure-public-port.sh", CONFIGURE_PUBLIC_PORT_SCRIPT
        )
        putIfNeeded(api, auth, owner, repo, branch, "bridge/package.json", BRIDGE_PACKAGE_JSON)
        val bridgeServerChanged = putIfNeeded(
            api, auth, owner, repo, branch, "bridge/server.js", bridgeServerScript
        )
        putIfNeeded(api, auth, owner, repo, branch, "bridge/.gitignore", BRIDGE_GITIGNORE)
        return devcontainerChanged || portScriptChanged || bridgeServerChanged
    }

    private suspend fun putIfNeeded(
        api: GitHubApiService,
        auth: String,
        owner: String,
        repo: String,
        branch: String,
        path: String,
        desired: String
    ): Boolean {
        // The Contents API returns an array for directories; a 404 indicates a missing file.
        val existing: ContentFileResponse? = try {
            Log.d("BridgeProvisioner", "GET $path (branch=$branch)")
            api.getContentFile(auth, owner, repo, path, branch)
        } catch (e: HttpException) {
            if (e.code() == 404) {
                Log.d("BridgeProvisioner", "$path is missing (404), it will be created")
                null
            } else throw IllegalStateException(
                "Failed to read $path (HTTP ${e.code()}): ${e.message}", e
            )
        } catch (e: com.google.gson.JsonSyntaxException) {
            Log.d("BridgeProvisioner", "$path is a directory; the file will be created inside it")
            null
        } catch (e: Exception) {
            // Qualsiasi altro errore di rete/parsing (es. timeout, DNS, SSL):
            // non deve mai lasciare la UI ferma sul testo iniziale senza motivo.
            throw IllegalStateException("Failed to read $path (${e.javaClass.simpleName}): ${e.message}", e)
        }
        val existingText = existing?.content
            ?.takeIf { existing.encoding.equals("base64", ignoreCase = true) }
            ?.let { decodeBase64(it) }
        val content = (if (path == DEVCONTAINER_PATH) {
            mergeDevcontainerConfig(existingText, desired)
        } else {
            desired
        }).replace("\r\n", "\n")
        if (existingText != null && normalize(existingText) == normalize(content)) return false

        val body = ContentPutRequest(
            message = "chore(bridge): self-provisioning Cline Agent Mobile",
            content = encodeBase64(content),
            sha = existing?.sha,
            branch = branch
        )
        try {
            api.putContent(auth, owner, repo, path, body)
            Log.i("BridgeProvisioner", "PUT $path OK")
            return true
        } catch (e: HttpException) {
            throw IllegalStateException(
                "Failed to write $path (HTTP ${e.code()}): the token needs the repo scope with write access",
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

    internal fun mergeDevcontainerConfig(existingText: String?, desiredText: String): String {
        if (existingText.isNullOrBlank()) return desiredText

        val existing = JsonParser.parseString(existingText).asJsonObject
        val desired = JsonParser.parseString(desiredText).asJsonObject

        if (!existing.has("image") && !existing.has("dockerFile") && !existing.has("build")) {
            desired.get("image")?.let { existing.add("image", it.deepCopy()) }
        }

        val forwardedPorts = getOrCreateArray(existing, "forwardPorts")
        desired.getAsJsonArray("forwardPorts").forEach { desiredPort ->
            if (forwardedPorts.none { it == desiredPort }) forwardedPorts.add(desiredPort.deepCopy())
        }

        val portsAttributes = getOrCreateObject(existing, "portsAttributes")
        val bridgeAttributes = portsAttributes.get(BRIDGE_PORT_TEXT)?.let {
            if (!it.isJsonObject) {
                throw IllegalStateException("portsAttributes.$BRIDGE_PORT_TEXT deve essere un oggetto")
            }
            it.asJsonObject
        } ?: JsonObject().also { portsAttributes.add(BRIDGE_PORT_TEXT, it) }
        bridgeAttributes.addProperty("label", "Cline Bridge")
        bridgeAttributes.addProperty("onAutoForward", "silent")
        bridgeAttributes.remove("visibility")

        ensureGitHubCliFeature(existing, desired)
        existing.getAsJsonObject("features")?.remove(LEGACY_DIAGNOSTIC_SSH_FEATURE)
        ensureBridgeStartup(existing, desired)
        return existing.toString()
    }

    private fun ensureGitHubCliFeature(existing: JsonObject, desired: JsonObject) {
        val desiredFeatures = desired.getAsJsonObject("features")
        val existingFeatures = getOrCreateObject(existing, "features")
        desiredFeatures.entrySet().forEach { (feature, config) ->
            if (!existingFeatures.has(feature)) existingFeatures.add(feature, config.deepCopy())
        }
    }

    private fun getOrCreateArray(config: JsonObject, key: String): JsonArray {
        val value = config.get(key)
        if (value == null || value.isJsonNull) return JsonArray().also { config.add(key, it) }
        if (!value.isJsonArray) throw IllegalStateException("$key nel devcontainer deve essere un array")
        return value.asJsonArray
    }

    private fun getOrCreateObject(config: JsonObject, key: String): JsonObject {
        val value = config.get(key)
        if (value == null || value.isJsonNull) return JsonObject().also { config.add(key, it) }
        if (!value.isJsonObject) throw IllegalStateException("$key nel devcontainer deve essere un oggetto")
        return value.asJsonObject
    }

    private fun ensureBridgeStartup(existing: JsonObject, desired: JsonObject) {
        val current = existing.get("postStartCommand")
        val bridgeCommand = desired.get("postStartCommand")
        if (current == null || current.isJsonNull) {
            existing.add("postStartCommand", bridgeCommand.deepCopy())
            return
        }

        when {
            current.isJsonPrimitive && current.asJsonPrimitive.isString -> {
                val currentCommand = current.asString
                val updatedLegacyCommand = removeLegacyBridgeStartup(
                    currentCommand,
                    bridgeCommand.asString
                )
                if (updatedLegacyCommand != null) {
                    existing.addProperty("postStartCommand", updatedLegacyCommand)
                    return
                }
                if (currentCommand.contains(bridgeCommand.asString)) return
                existing.addProperty(
                    "postStartCommand",
                    currentCommand.trimEnd() +
                        (if (currentCommand.trimEnd().endsWith("&")) " " else "; ") +
                        bridgeCommand.asString
                )
            }
            current.isJsonArray -> {
                if (current.asJsonArray.none { it.toString().contains(bridgeCommand.asString) }) {
                    current.asJsonArray.add(bridgeCommand.asString)
                }
            }
            current.isJsonObject -> {
                val commands = current.asJsonObject
                if (commands.entrySet().any { it.value.toString().contains(bridgeCommand.asString) }) {
                    return
                }
                var key = "clineAgentMobileBridge"
                var suffix = 1
                while (commands.has(key)) key = "clineAgentMobileBridge${suffix++}"
                commands.addProperty(key, bridgeCommand.asString)
            }
            else -> throw IllegalStateException("Unsupported postStartCommand format in devcontainer")
        }
    }

    private fun removeLegacyBridgeStartup(command: String, bridgeCommand: String): String? {
        val bridgeCommandIndex = command.indexOf(bridgeCommand)
        if (bridgeCommandIndex < 0) return null

        val legacyStart = command.lastIndexOf("cd bridge &&", bridgeCommandIndex)
        if (legacyStart < 0 ||
            !command.substring(legacyStart, bridgeCommandIndex).contains("nohup node server.js")
        ) {
            return null
        }

        val before = command.substring(0, legacyStart)
            .trim()
            .removeSuffix("&&")
            .trim()
            .trimEnd(';')
            .trim()
        val after = command.substring(bridgeCommandIndex + bridgeCommand.length)
            .trim()
            .trimStart(';')
            .trim()
        return listOf(before, bridgeCommand, after)
            .filter { it.isNotBlank() }
            .joinToString("; ")
    }

    private fun normalize(text: String): String = text.replace("\r\n", "\n").trim()
    private const val DEVCONTAINER_PATH = ".devcontainer/devcontainer.json"
    private const val BRIDGE_PORT_TEXT = "3000"
    private const val LEGACY_DIAGNOSTIC_SSH_FEATURE = "ghcr.io/devcontainers/features/sshd:1"
    private const val DEVCONTAINER_JSON = """{
  "features": {
    "ghcr.io/devcontainers/features/github-cli:1": {}
  },
  "forwardPorts": [3000],
  "portsAttributes": {
    "3000": {
      "label": "Cline Bridge",
      "onAutoForward": "silent"
    }
  },
  "postStartCommand": "sh bridge/configure-public-port.sh"
}
""";
    internal const val CONFIGURE_PUBLIC_PORT_SCRIPT = """#!/bin/sh
set -u

cd "$(dirname "$0")"
exec >> /tmp/cline-port-visibility.log 2>&1

if [ ! -d node_modules/ws ]; then
  if ! npm install --no-audit --no-fund; then
    echo "ERROR: bridge dependency installation failed; bridge not started." >&2
    exit 1
  fi
fi

if ! command -v cline >/dev/null 2>&1; then
  if ! command -v npm >/dev/null 2>&1; then
    echo "ERROR: npm is unavailable; cannot install the Cline CLI." >&2
    exit 1
  fi
  if ! npm install --global --no-audit --no-fund cline; then
    echo "ERROR: Cline CLI installation failed; bridge not started." >&2
    exit 1
  fi
fi
if ! command -v cline >/dev/null 2>&1; then
  echo "ERROR: Cline CLI was not found in PATH after installation." >&2
  exit 1
fi

if ! pgrep -f '[n]ode .*server\.js' >/dev/null; then
  if ! command -v setsid >/dev/null 2>&1; then
    echo "ERROR: setsid is unavailable; bridge not started." >&2
    exit 1
  fi
  setsid -f node server.js > /tmp/cline-bridge.log 2>&1 < /dev/null
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is unavailable; cannot verify the bridge." >&2
  exit 1
fi

attempt=1
while [ "${'$'}attempt" -le 15 ]; do
  if curl -fsS --max-time 2 http://127.0.0.1:$BRIDGE_PORT_TEXT/ 2>/dev/null |
    grep -Fxq "Cline Agent Mobile Bridge Server"; then
    echo "Bridge server pronto sulla porta $BRIDGE_PORT_TEXT."
    break
  fi
  if [ "${'$'}attempt" -eq 15 ]; then
    echo "ERROR: bridge is not responding on port $BRIDGE_PORT_TEXT; recent logs:" >&2
    tail -n 20 /tmp/cline-bridge.log >&2 || true
    exit 1
  fi
  sleep 2
  attempt=${'$'}((attempt + 1))
done

if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: GitHub CLI is unavailable; bridge started but port $BRIDGE_PORT_TEXT was not published." >&2
  exit 1
fi
if [ -z "${'$'}{CODESPACE_NAME:-}" ]; then
  echo "ERROR: CODESPACE_NAME is not set; bridge started but port $BRIDGE_PORT_TEXT was not published." >&2
  exit 1
fi

attempt=1
while [ "${'$'}attempt" -le 12 ]; do
  if gh codespace ports visibility "$BRIDGE_PORT_TEXT:private" --codespace "${'$'}CODESPACE_NAME" &&
    gh codespace ports visibility "$BRIDGE_PORT_TEXT:public" --codespace "${'$'}CODESPACE_NAME"; then
    echo "Port $BRIDGE_PORT_TEXT published automatically."
    exit 0
  fi
  echo "Port publication failed (attempt ${'$'}attempt/12)." >&2
  sleep 5
  attempt=${'$'}((attempt + 1))
done

echo "ERROR: unable to publish port $BRIDGE_PORT_TEXT; bridge remains running for diagnostics." >&2
exit 1
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
}
