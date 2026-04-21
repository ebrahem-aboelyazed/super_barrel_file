package com.dartbarrel.plugin.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile

/**
 * Stateless utilities for working with Dart source files inside an IntelliJ
 * Platform plugin.
 */
object DartFileUtils {

    /**
     * Returns `true` when [fileName] matches a code-generation suffix
     * (e.g. `.g.dart`, `.freezed.dart`).
     *
     * The list mirrors the default [com.dartbarrel.plugin.settings
     * .DartBarrelSettings.excludePatterns] but operates on raw suffix
     * matching for speed — no regex allocation.
     */
    fun isGeneratedFile(fileName: String): Boolean =
        fileName.endsWith(".g.dart") ||
                fileName.endsWith(".freezed.dart") ||
                fileName.endsWith(".gr.dart") ||
                fileName.endsWith(".config.dart") ||
                fileName.endsWith(".part.dart")

    /**
     * Returns `true` when [psiFile] is a `part of` file (i.e. not directly
     * importable as a library).
     */
    fun isPartFile(psiFile: PsiFile): Boolean =
        psiFile.text.lineSequence().any { line ->
            val trimmed = line.trim()
            trimmed.startsWith("part of ") && trimmed.endsWith(';')
        }
}