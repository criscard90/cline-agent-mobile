package com.clineagentmobile.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a file diff in the diff viewer.
 * Supports unified diff format with added, removed, and context lines.
 */
data class DiffFile(
    @SerializedName("file") val filePath: String,
    @SerializedName("diff") val lines: List<DiffLine> = emptyList(),
    @SerializedName("stat") val stat: FileStat? = null,
    @SerializedName("isBinary") val isBinary: Boolean = false,
    @SerializedName("isDeleted") val isDeleted: Boolean = false,
    @SerializedName("isRenamed") val isRenamed: Boolean = false,
    @SerializedName("fromPath") val fromPath: String? = null,
    @SerializedName("toPath") val toPath: String? = null,
    @SerializedName("summary") val summary: String? = null
) {
    enum class ChangeType {
        @SerializedName("added") ADDED,
        @SerializedName("removed") REMOVED,
        @SerializedName("modified") MODIFIED,
        @SerializedName("context") CONTEXT,
        @SerializedName("header") HEADER,
        @SerializedName("metadata") METADATA
    }

    companion object {
        fun fromUnifiedDiff(filePath: String, diffContent: String): DiffFile {
            val lines = mutableListOf<DiffLine>()
            var currentType = ChangeType.CONTEXT

            diffContent.lineSequence()
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    if (line.startsWith("diff --git ")) {
                        currentType = ChangeType.HEADER
                        if (lines.isNotEmpty()) {
                            // Finalize current hunk
                            // This is simplified - real implementation parses hunks properly
                        }
                    } else if (line.startsWith("+++ ") || line.startsWith("--- ")) {
                        currentType = ChangeType.HEADER
                    } else if (line.startsWith("@")) {
                        currentType = ChangeType.HEADER
                    } else if (line.startsWith("+") && !line.startsWith("+++")) {
                        if (currentType != ChangeType.REMOVED) {
                            lines.add(DiffLine(line.removePrefix("+"), ChangeType.ADDED, true))
                        }
                    } else if (line.startsWith("-") && !line.startsWith("---")) {
                        if (currentType != ChangeType.ADDED) {
                            lines.add(DiffLine(line.removePrefix("-"), ChangeType.REMOVED, true))
                        }
                    } else if (line.startsWith(" ")) {
                        lines.add(DiffLine(line.removePrefix(" "), ChangeType.CONTEXT, false))
                    } else {
                        currentType = ChangeType.CONTEXT
                    }
                }

            return DiffFile(filePath = filePath, lines = lines)
        }
    }
}

/**
 * A single line in a diff file.
 */
data class DiffLine(
    @SerializedName("content") val content: String,
    @SerializedName("type") val type: DiffFile.ChangeType,
    @SerializedName("isFuzzy") val isFuzzy: Boolean = false,
    @SerializedName("oldStart") val oldStart: Int? = null,
    @SerializedName("oldEnd") val oldEnd: Int? = null,
    @SerializedName("newStart") val newStart: Int? = null,
    @SerializedName("newEnd") val newEnd: Int? = null
)

/**
 * File statistics for the diff viewer.
 */
data class FileStat(
    @SerializedName("oldFileName") val oldFileName: String? = null,
    @SerializedName("newFileName") val newFileName: String? = null,
    @SerializedName("nFiles") val nFiles: Int = 0,
    @SerializedName("nInsertions") val nInsertions: Int = 0,
    @SerializedName("nDeletions") val nDeletions: Int = 0,
    @SerializedName("nLines") val nLines: Int = 0
) {
    val totalLines: Int = nInsertions + nDeletions
    val isEmpty: Boolean = totalLines == 0
}
