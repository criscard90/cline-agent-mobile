package com.clineagentmobile.model

import com.clineagentmobile.network.CodespaceCreateRequest
import com.clineagentmobile.network.CodespaceListResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies parsing of real GitHub Codespaces API responses against the
 * official OpenAPI schema: the {total_count, codespaces} list wrapper,
 * numeric id, repository as an object, ISO-8601 date strings and the
 * official state enum values (with lenient fallback for unknown ones).
 */
class CodespaceParsingTest {

    private val gson = Gson()

    @Test
    fun `list response with wrapper and full codespace parses`() {
        val json = """
            {
              "total_count": 1,
              "codespaces": [
                {
                  "id": 42,
                  "name": "fluent-mango-abc123",
                  "display_name": "My codespace",
                  "owner": { "login": "criscard90" },
                  "billable_owner": { "login": "criscard90" },
                  "repository": {
                    "id": 815933240,
                    "name": "cline-agent-mobile",
                    "full_name": "criscard90/cline-agent-mobile",
                    "owner": { "login": "criscard90" },
                    "private": true,
                    "html_url": "https://github.com/criscard90/cline-agent-mobile"
                  },
                  "machine": { "name": "standardLinux32gb", "display_name": "4 cores, 16 GB RAM" },
                  "created_at": "2026-10-01T10:00:00Z",
                  "updated_at": "2026-10-02T12:34:56Z",
                  "last_used_at": "2026-10-02T12:00:00Z",
                  "state": "available",
                  "url": "https://api.github.com/user/codespaces/fluent-mango-abc123",
                  "web_url": "https://github.com/codespaces/fluent-mango-abc123",
                  "location": "WestUs",
                  "recent_folders": ["/workspaces/cline-agent-mobile"]
                }
              ]
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, CodespaceListResponse::class.java)

        assertEquals(1, parsed.totalCount)
        assertEquals(1, parsed.codespaces.size)
        val cs = parsed.codespaces[0]
        assertEquals("42", cs.id)
        assertEquals("fluent-mango-abc123", cs.name)
        assertEquals("My codespace", cs.displayName)
        assertEquals("criscard90/cline-agent-mobile", cs.repository.fullName)
        assertEquals(Codespace.State.RUNNING, cs.state)
        assertEquals("2026-10-02T12:34:56Z", cs.updatedAt)
        assertEquals("https://github.com/codespaces/fluent-mango-abc123", cs.webUrl)
        assertTrue(cs.isRunning)
        // Not part of the current API schema: must stay null instead of crashing
        assertNull(cs.status)
    }

    @Test
    fun `empty list parses`() {
        val parsed = gson.fromJson(
            """{"total_count": 0, "codespaces": []}""",
            CodespaceListResponse::class.java
        )
        assertEquals(0, parsed.totalCount)
        assertTrue(parsed.codespaces.isEmpty())
    }

    @Test
    fun `official state values map to local states`() {
        assertEquals(Codespace.State.RUNNING, Codespace.State.fromApi("available"))
        assertEquals(Codespace.State.STOPPED, Codespace.State.fromApi("stopped"))
        assertEquals(Codespace.State.STOPPED, Codespace.State.fromApi("shutting_down"))
        assertEquals(Codespace.State.STOPPED, Codespace.State.fromApi("archived"))
        assertEquals(Codespace.State.CREATING, Codespace.State.fromApi("provisioning"))
        assertEquals(Codespace.State.CREATING, Codespace.State.fromApi("starting"))
        assertEquals(Codespace.State.CREATING, Codespace.State.fromApi("rebuilding"))
        assertEquals(Codespace.State.DELETING, Codespace.State.fromApi("deleting"))
        assertEquals(Codespace.State.PAUSED, Codespace.State.fromApi("awaiting"))
    }

    @Test
    fun `unknown or missing state does not fail the parse`() {
        val withUnknown = gson.fromJson(
            """{"id":1,"name":"n","repository":{"full_name":"o/r"},"state":"brand_new_state"}""",
            Codespace::class.java
        )
        assertEquals(Codespace.State.UNKNOWN, withUnknown.state)

        val withoutState = gson.fromJson(
            """{"id":1,"name":"n","repository":{"full_name":"o/r"}}""",
            Codespace::class.java
        )
        assertEquals(Codespace.State.UNKNOWN, Codespace.State.fromApi(null))
        assertTrue(withoutState.repository.fullName.isNotEmpty())
    }

    @Test
    fun `state and status serialize back to api values`() {
        assertEquals("\"available\"", gson.toJson(Codespace.State.RUNNING))
        assertEquals("\"stopped\"", gson.toJson(Codespace.State.STOPPED))
        assertEquals("\"ready\"", gson.toJson(Codespace.Status.READY))
    }

    @Test
    fun `create request sends only explicitly set fields`() {
        // The repository comes from the URL path: an empty body is valid and
        // must not serialize any (previously invalid) repository property.
        assertEquals("{}", gson.toJson(CodespaceCreateRequest()))
        val withName = gson.toJson(CodespaceCreateRequest(displayName = "test"))
        assertTrue(withName.contains("\"display_name\":\"test\""))
        assertTrue(!withName.contains("\"repository\""))
    }
}
