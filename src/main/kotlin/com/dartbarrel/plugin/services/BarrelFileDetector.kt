package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.jetbrains.lang.dart.DartFileType

/**
 * Determines whether a given file is a Dart barrel file.
 *
 * A file is considered a barrel when **every** non-blank, non-comment line
 * matches the pattern `export '…';`.  A file with zero qualifying lines is
 * *not* a barrel — this prevents a brand-new, still-empty file from being
 * mis-classified as a barrel before its content has been written.
 */
class BarrelFileDetector(
    private val psiManager: PsiManager,
    @Suppress("UNUSED_PARAMETER") settings: DartBarrelSettings,
) {

    /**
     * Returns `true` when [virtualFile] is a valid Dart barrel file.
     * Safe to call from any thread.
     */
    fun isBarrelFile(virtualFile: VirtualFile): Boolean {
        if (!virtualFile.isValid || virtualFile.isDirectory) return false
        if (virtualFile.fileType != DartFileType.INSTANCE &&
            virtualFile.extension != DART_EXT
        ) return false

        return ApplicationManager.getApplication().runReadAction<Boolean> {
            val psiFile = psiManager.findFile(virtualFile) ?: return@runReadAction false
            hasBarrelContent(psiFile)
        }
    }

    /**
     * Returns `true` when [psiFile] is a valid Dart barrel file.
     * Must be called inside a read action (or on the EDT).
     */
    fun isBarrelFile(psiFile: PsiFile): Boolean {
        if (!psiFile.isValid) return false
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            hasBarrelContent(psiFile)
        }
    }

    private fun hasBarrelContent(psiFile: PsiFile): Boolean {
        if (psiFile.fileType != DartFileType.INSTANCE) return false

        val lines = psiFile.text
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("/*") }
            .toList()

        if (lines.isEmpty()) return false

        return lines.all { line ->
            line.startsWith("export ") && line.endsWith(";")
        }
    }

    private companion object {
        private const val DART_EXT = "dart"
    }
}
