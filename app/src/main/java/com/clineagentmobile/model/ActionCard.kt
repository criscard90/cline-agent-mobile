package com.clineagentmobile.model

import com.google.gson.annotations.SerializedName

/**
 * Action card representing a user action (approve/deny) for commands, file edits, etc.
 */
data class ActionCard(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: ActionType,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("command") val command: String? = null,
    @SerializedName("files") val files: List<DiffFile>? = null,
    @SerializedName("confirmLabel") val confirmLabel: String = "Approva",
    @SerializedName("denyLabel") val denyLabel: String = "Rifiuta",
    @SerializedName("requiresConfirmation") val requiresConfirmation: Boolean = true,
    @SerializedName("metadata") val metadata: Map<String, String> = emptyMap()
) {
    enum class ActionType {
        @SerializedName("terminal_command") TERMINAL_COMMAND,
        @SerializedName("file_edit") FILE_EDIT,
        @SerializedName("code_edit") CODE_EDIT,
        @SerializedName("merge_request") MERGE_REQUEST,
        @SerializedName("web_request") WEB_REQUEST,
        @SerializedName("dangerous") DANGEROUS,
        @SerializedName("custom") CUSTOM
    }

    companion object {
        fun terminal(command: String, description: String = "", requiresConfirmation: Boolean = true) =
            ActionCard(
                id = java.util.UUID.randomUUID().toString(),
                type = ActionType.TERMINAL_COMMAND,
                title = "Esegui comando",
                description = description,
                command = command,
                requiresConfirmation = requiresConfirmation,
                confirmLabel = "Esegui",
                denyLabel = "Annulla"
            )

        fun fileEdit(
            files: List<DiffFile>,
            description: String = "Modifica file"
        ) = ActionCard(
            id = java.util.UUID.randomUUID().toString(),
            type = ActionType.FILE_EDIT,
            title = "Modifica file",
            description = description,
            files = files,
            requiresConfirmation = true,
            confirmLabel = "Applica",
            denyLabel = "Ignora"
        )
    }
}
