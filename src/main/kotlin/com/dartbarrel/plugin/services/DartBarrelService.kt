package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.model.BarrelGenerationPlan
import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/**
 * Central service that orchestrates barrel-file generation, regeneration, and
 * staleness detection for a single [Project].
 *
 * ### Threading contract
 * All scanning helpers are run inside `runReadAction` so they are safe to
 * call from any thread.  Write operations are wrapped in
 * [WriteCommandAction.runWriteCommandAction] and must therefore be dispatched
 * to the EDT/write-thread by the caller when needed.
 *
 * ### Empty-file fix
 * File *creation* is delegated exclusively to [BarrelFileWriter.createNew],
 * which writes bytes directly to the VFS.  The previous `PsiFileFactory →
 * PsiDirectory.add` path was dropped because it left the physical file empty
 * on first generation.
 *
 * ### No double-scan
 * [generateBarrelFile] no longer re-scans the directory. It receives the
 * [BarrelGenerationPlan] that was already produced for the dialog, filters it
 * to the user's selection, and writes.  This prevents subtle path-mismatch
 * issues that would silently produce an empty candidates list.
 */
@Service(Service.Level.PROJECT)
class DartBarrelService(private val project: Project) {

    private val settings = DartBarrelSettings.getInstance()
    private val psiManager = PsiManager.getInstance(project)
    private val psiDocumentManager = PsiDocumentManager.getInstance(project)

    private val detector = BarrelFileDetector(psiManager, settings)
    private val snapshotScanner = BarrelSnapshotScanner(settings)
    private val contentBuilder = BarrelContentBuilder(settings)
    private val writer = BarrelFileWriter(project, FileDocumentManager.getInstance())
    private val recentGenerationTracker = RecentGenerationTracker()

    /**
     * Builds an immutable [BarrelGenerationPlan] for [directory] by scanning
     * the file system.  Safe to call from a background thread.
     */
    fun prepareGeneration(directory: PsiDirectory): BarrelGenerationPlan {
        commitAllDocuments()
        return readAction { snapshotScanner.createPlan(directory) }
    }

    /**
     * Writes (or overwrites) the barrel file described by [plan], exporting
     * only the paths listed in [selectedRelativePaths].
     *
     * The [plan] **must** be the one produced by [prepareGeneration] so that
     * path strings are consistent and no extra file-system scan is needed.
     *
     * @return the written [PsiFile], or `null` if nothing was written.
     */
    fun generateBarrelFile(
        directory: PsiDirectory,
        plan: BarrelGenerationPlan,
        selectedRelativePaths: Set<String>,
        barrelFileName: String,
    ): PsiFile? {
        val sanitizedFileName = sanitizeBarrelFileName(directory, barrelFileName)

        val selectedCandidates = plan.candidates.filter { candidate ->
            candidate.relativePath in selectedRelativePaths
        }

        if (selectedCandidates.isEmpty()) {
            LOG.warn(
                "No matching candidates for the selected paths in " +
                    directory.virtualFile.path,
            )
            return null
        }

        val content = contentBuilder.build(selectedCandidates)
        if (content.isBlank()) {
            LOG.warn("Content builder produced blank output for ${directory.name}")
            return null
        }

        return writeBarrelFile(
            directory = directory,
            barrelFileName = sanitizedFileName,
            content = content,
            commandName = "Generate Barrel File",
        )
    }

    /**
     * Regenerates [barrelFile] from a fresh directory snapshot.
     * No-op when the content is already up to date.
     */
    fun regenerateBarrelFile(barrelFile: PsiFile) {
        if (!barrelFile.isValid) {
            LOG.warn("Cannot regenerate invalid file: ${barrelFile.name}")
            return
        }

        val directory = readAction { barrelFile.containingDirectory } ?: return
        val plan = prepareGeneration(directory)
        val content = contentBuilder.build(plan.candidates)

        if (!hasContentChanged(barrelFile, content)) return

        writeBarrelFile(
            directory = directory,
            barrelFileName = barrelFile.name,
            content = content,
            commandName = "Regenerate Barrel File",
        )
    }

    /**
     * Returns `true` when the current barrel content differs from what a
     * fresh scan would produce, meaning the file needs regeneration.
     */
    fun needsRegeneration(barrelFile: PsiFile): Boolean {
        if (!barrelFile.isValid) return false
        val directory = barrelFile.containingDirectory ?: return false
        return readAction {
            val expected = contentBuilder.build(
                snapshotScanner.createPlan(directory).candidates,
            )
            normalizeContent(expected) != normalizeContent(barrelFile.text)
        }
    }

    /**
     * Synchronises an existing barrel file in [directory] after the VFS
     * settles. Skips directories that were generated very recently to avoid
     * ping-pong regeneration loops.
     */
    fun synchronizeExistingBarrel(directory: VirtualFile) {
        if (!directory.isValid) return
        if (recentGenerationTracker.wasRecentlyGenerated(directory.path)) return

        commitAllDocuments()
        val barrelFile = readAction {
            val psiDirectory = psiManager.findDirectory(directory)
                ?: return@readAction null
            findExistingBarrelFile(psiDirectory)
        } ?: return

        if (needsRegeneration(barrelFile)) {
            regenerateBarrelFile(barrelFile)
        }
    }

    /** Returns `true` when [virtualFile] is a barrel file. */
    fun isBarrelFile(virtualFile: VirtualFile): Boolean =
        detector.isBarrelFile(virtualFile)

    /** Returns `true` when [psiFile] is a barrel file. */
    fun isBarrelFile(psiFile: PsiFile): Boolean =
        detector.isBarrelFile(psiFile)

    /** Resolves the configured barrel file name for [directory]. */
    fun getBarrelFileName(directory: PsiDirectory): String =
        snapshotScanner.resolveBarrelFileName(directory.name)

    /**
     * Builds a live preview of barrel content for the generation dialog.
     * No VFS access — purely in-memory.
     */
    fun buildPreviewContent(
        plan: BarrelGenerationPlan,
        selectedRelativePaths: Set<String>,
    ): String = contentBuilder.build(
        plan.candidates.filter { it.relativePath in selectedRelativePaths },
    )

    private fun findExistingBarrelFile(
        directory: PsiDirectory,
        preferredFileName: String? = null,
    ): PsiFile? {
        preferredFileName
            ?.let(directory::findFile)
            ?.takeIf(PsiFile::isValid)
            ?.let { return it }

        directory.findFile(getBarrelFileName(directory))
            ?.takeIf(PsiFile::isValid)
            ?.let { return it }

        return directory.files.firstOrNull { file ->
            file.isValid && detector.isBarrelFile(file)
        }
    }

    private fun hasContentChanged(barrelFile: PsiFile, content: String): Boolean =
        readAction {
            normalizeContent(barrelFile.text) != normalizeContent(content)
        }

    private fun writeBarrelFile(
        directory: PsiDirectory,
        barrelFileName: String,
        content: String,
        commandName: String,
    ): PsiFile? {
        var writtenFile: PsiFile? = null
        try {
            WriteCommandAction.runWriteCommandAction(project, commandName, null, {
                writtenFile = writer.createOrUpdate(
                    directory = directory,
                    barrelFileName = barrelFileName,
                    content = content,
                )
            })
        } catch (e: Exception) {
            LOG.error(
                "Failed to write '$barrelFileName' in ${directory.virtualFile.path}",
                e,
            )
            return null
        }

        if (writtenFile != null) {
            recentGenerationTracker.mark(directory.virtualFile.path)
        }
        return writtenFile
    }

    private fun sanitizeBarrelFileName(
        directory: PsiDirectory,
        barrelFileName: String,
    ): String {
        val trimmed = barrelFileName.trim()
        if (trimmed.isBlank()) return getBarrelFileName(directory)
        return if (trimmed.endsWith(".dart")) trimmed else "$trimmed.dart"
    }

    private fun commitAllDocuments() {
        psiDocumentManager.commitAllDocuments()
    }

    private fun normalizeContent(content: String): String =
        content.lineSequence()
            .map(String::trim)
            .map(::normalizeExportDirective)
            .filter { line ->
                line.isNotEmpty() &&
                    !line.startsWith("//") &&
                    !line.startsWith("/*") &&
                    !line.startsWith("*")
            }
            .joinToString("\n")

    private fun normalizeExportDirective(line: String): String {
        val match = EXPORT_DIRECTIVE_REGEX.matchEntire(line) ?: return line
        val quote = match.groupValues[1]
        val path = match.groupValues[2].removePrefix("./")
        return "export $quote$path$quote;"
    }

    private companion object {
        private val LOG = Logger.getInstance(DartBarrelService::class.java)
        private val EXPORT_DIRECTIVE_REGEX =
            Regex("""^export\s+(['"])([^'"]+)\1;""")

        private fun <T> readAction(action: () -> T): T =
            ApplicationManager.getApplication().runReadAction<T> { action() }
    }
}