package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.model.BarrelExportCandidate
import com.dartbarrel.plugin.settings.DartBarrelSettings

class BarrelContentBuilder(
    private val settings: DartBarrelSettings,
) {

    /**
     * Builds barrel content from the provided candidates.
     */
    fun build(candidates: List<BarrelExportCandidate>): String {
        val exportLines = normalizeExportLines(
            candidates.map { candidate ->
                "export '${candidate.relativePath}';"
            },
        )

        if (exportLines.isEmpty()) {
            return ""
        }

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

    private fun normalizeExportLines(lines: List<String>): List<String> {
        val distinctLines = LinkedHashSet(lines.filter(String::isNotBlank))
        return distinctLines
            .toList()
            .let { exports ->
                if (settings.sortExports) {
                    exports.sorted()
                } else {
                    exports
                }
            }
    }
}
