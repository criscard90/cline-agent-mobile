package com.clineagentmobile.settings

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeProvisionerTest {

    @Test
    fun `merge enables silent bridge forwarding and preserves project config`() {
        val existing = """
            {
              "name": "Existing project",
              "features": {
                "example.org/features/project-tool:1": {},
                "ghcr.io/devcontainers/features/sshd:1": { "version": "latest" }
              },
              "forwardPorts": [11434],
              "portsAttributes": {
                "11434": { "label": "Ollama" },
                "3000": { "customAttribute": "keep" }
              },
              "postCreateCommand": "npm install -g cline",
              "postStartCommand": "echo project startup"
            }
        """.trimIndent()
            val desired = """
            {
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
            """.trimIndent()

        val merged = BridgeProvisioner.mergeDevcontainerConfig(existing, desired)
        val config = JsonParser.parseString(merged).asJsonObject
        val attributes = config.getAsJsonObject("portsAttributes")

        assertEquals("Existing project", config.get("name").asString)
        assertTrue(config.has("features"))
        assertEquals("npm install -g cline", config.get("postCreateCommand").asString)
        assertEquals(listOf(11434, 3000), config.getAsJsonArray("forwardPorts").map { it.asInt })
        assertEquals("Ollama", attributes.getAsJsonObject("11434").get("label").asString)
        assertEquals("keep", attributes.getAsJsonObject("3000").get("customAttribute").asString)
        assertEquals("silent", attributes.getAsJsonObject("3000").get("onAutoForward").asString)
        assertFalse(attributes.getAsJsonObject("3000").has("visibility"))
        assertTrue(
            config.getAsJsonObject("features")
                .has("ghcr.io/devcontainers/features/github-cli:1")
        )
        assertFalse(
            config.getAsJsonObject("features")
                .has("ghcr.io/devcontainers/features/sshd:1")
        )
        assertTrue(config.getAsJsonObject("features").has("example.org/features/project-tool:1"))
        assertTrue(config.get("postStartCommand").asString.contains("echo project startup"))
        assertTrue(config.get("postStartCommand").asString.contains("configure-public-port.sh"))
    }

    @Test
    fun `merge is idempotent`() {
        val desired = """
            {
              "features": {
                "ghcr.io/devcontainers/features/github-cli:1": {}
              },
              "forwardPorts": [3000],
              "portsAttributes": { "3000": { "onAutoForward": "silent" } },
              "postStartCommand": "sh bridge/configure-public-port.sh"
            }
        """.trimIndent()

        val once = BridgeProvisioner.mergeDevcontainerConfig(
            """{"forwardPorts":[3000],"features":{"ghcr.io/devcontainers/features/sshd:1":{"version":"latest"}}}""",
            desired
        )
        val twice = BridgeProvisioner.mergeDevcontainerConfig(once, desired)

        assertEquals(once, twice)
        assertTrue(
            JsonParser.parseString(once).asJsonObject.getAsJsonObject("features")
                .has("ghcr.io/devcontainers/features/github-cli:1")
        )
        assertFalse(
            JsonParser.parseString(once).asJsonObject.getAsJsonObject("features")
                .has("ghcr.io/devcontainers/features/sshd:1")
        )
    }

    @Test
    fun `legacy bridge startup is replaced to avoid matching its background shell`() {
        val desired = """
            {
              "features": {
                "ghcr.io/devcontainers/features/github-cli:1": {}
              },
              "forwardPorts": [3000],
              "portsAttributes": { "3000": { "onAutoForward": "silent" } },
              "postStartCommand": "sh bridge/configure-public-port.sh"
            }
        """.trimIndent()

        val merged = BridgeProvisioner.mergeDevcontainerConfig(
            """{"postStartCommand":"npm install -g cline && cd bridge && (npm install --no-audit --no-fund || true) && nohup node server.js > /tmp/cline-bridge.log 2>&1 & sh bridge/configure-public-port.sh"}""",
            desired
        )
        val command = JsonParser.parseString(merged).asJsonObject
            .get("postStartCommand").asString

        assertEquals("npm install -g cline; sh bridge/configure-public-port.sh", command)
        assertFalse(command.contains("nohup node server.js"))
    }

    @Test
    fun `startup script detaches bridge and verifies local health before publishing`() {
        val script = BridgeProvisioner.CONFIGURE_PUBLIC_PORT_SCRIPT

        assertTrue(script.contains("npm install --global --no-audit --no-fund cline"))
        assertTrue(script.contains("command -v cline"))
        assertTrue(
            script.indexOf("npm install --global") <
                script.indexOf("setsid -f node server.js")
        )
        assertTrue(script.contains("setsid -f node server.js"))
        assertTrue(script.contains("http://127.0.0.1:3000/"))
        assertTrue(
            script.indexOf("http://127.0.0.1:") <
                script.indexOf("gh codespace ports visibility")
        )
    }
}
