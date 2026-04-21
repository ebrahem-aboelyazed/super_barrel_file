package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.model.BarrelExportCandidate
import com.dartbarrel.plugin.settings.DartBarrelSettings

/**
 * Builds the final text content of a barrel file from a list of
 * [BarrelExportCandidate] entries.
 *
 * The output is always terminated with a trailing newline and contains no
 * blank lines between export directives.  When the candidates list is empty
 * an empty string is returned so callers can detect a no-op early.
 */
class BarrelContentBuilder(
    private val settings: DartBarrelSettings,
) {

    /**
     * Builds the barrel file content from [candidates].
     * Returns an empty string when [candidates] is empty.
     */
    fun build(candidates: List<BarrelExportCandidate>): String {
        val exportLines = buildExportLines(candidates)
        if (exportLines.isEmpty()) return ""

        val header = settings.headerComment
            .takeIf { settings.includeHeader && it.isNotBlank() }

        return buildString {
            if (header != null) {
                appendLine(header)
                appendLine()
            }
            append(exportLines.joinToString("\n"))
            append('\n')
        }
    }

    private fun buildExportLines(
        candidates: List<BarrelExportCandidate>,
    ): List<String> {
        val lines = candidates
            .map { "export '${it.relativePath}';" }
            .filter(String::isNotBlank)
            .distinct()

        return if (settings.sortExports) lines.sorted() else lines
    }
}
