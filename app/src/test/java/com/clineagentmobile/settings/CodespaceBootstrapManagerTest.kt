package com.clineagentmobile.settings

import com.clineagentmobile.model.Codespace
import com.clineagentmobile.model.Repository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodespaceBootstrapManagerTest {

    @Test
    fun `prefers managed codespace over an older running codespace`() {
        val old = codespace(
            name = "old",
            displayName = "probable space xylophone",
            state = Codespace.State.RUNNING
        )
        val managed = codespace(
            name = "managed",
            displayName = "Cline Agent Mobile Bridge",
            state = Codespace.State.STOPPED
        )

        val selected = selectExistingCodespace(
            listOf(old, managed),
            "criscard90/endlessclimber"
        )

        assertEquals("managed", selected?.name)
    }

    @Test
    fun `prefers running codespace when no managed bridge exists`() {
        val stopped = codespace(name = "stopped", state = Codespace.State.STOPPED)
        val running = codespace(name = "running", state = Codespace.State.RUNNING)
        val otherRepo = codespace(
            name = "other",
            repo = "criscard90/another-repo",
            state = Codespace.State.RUNNING
        )

        val selected = selectExistingCodespace(
            listOf(stopped, otherRepo, running),
            "criscard90/endlessclimber"
        )

        assertEquals("running", selected?.name)
    }

    @Test
    fun `prefers the most recently updated managed bridge`() {
        val oldManaged = codespace(
            name = "old-managed",
            displayName = "Cline Agent Mobile Bridge",
            state = Codespace.State.RUNNING,
            updatedAt = "2026-10-14T15:00:00Z"
        )
        val replacement = codespace(
            name = "replacement",
            displayName = "Cline Agent Mobile Bridge",
            state = Codespace.State.RUNNING,
            updatedAt = "2026-10-14T16:00:00Z"
        )

        val selected = selectExistingCodespace(
            listOf(oldManaged, replacement),
            "criscard90/endlessclimber"
        )

        assertEquals("replacement", selected?.name)
    }

    @Test
    fun `ignores codespaces belonging to other repositories`() {
        val otherRepo = codespace(name = "other", repo = "criscard90/another-repo")

        val selected = selectExistingCodespace(
            listOf(otherRepo),
            "criscard90/endlessclimber"
        )

        assertNull(selected)
    }

    private fun codespace(
        name: String,
        repo: String = "criscard90/endlessclimber",
        displayName: String? = null,
        state: Codespace.State = Codespace.State.STOPPED,
        updatedAt: String? = null
    ) = Codespace(
        id = name,
        name = name,
        repository = Repository(fullName = repo),
        displayName = displayName,
        updatedAt = updatedAt,
        state = state
    )
}
