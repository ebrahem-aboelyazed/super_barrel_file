package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.model.BarrelExportCandidate
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

@Service(Service.Level.PROJECT)
class DartBarrelService(private val project: Project) {

    private val settings = DartBarrelSettings.getInstance()
    private val psiManager = PsiManager.getInstance(project)
    private val psiDocumentManager = PsiDocumentManager.getInstance(project)

    private val detector = BarrelFileDetector(
        psiManager,
        settings,
    )
    private val snapshotScanner = BarrelSnapshotScanner(settings)
    private val contentBuilder = BarrelContentBuilder(settings)
    private val writer = BarrelFileWriter(
        project,
        FileDocumentManager.getInstance(),
    )
    private val recentGenerationTracker = RecentGenerationTracker()

    /**
     * Creates the immutable snapshot consumed by the generation dialog.
     */
    fun prepareGeneration(directory: PsiDirectory): BarrelGenerationPlan {
        commitAllDocuments()
        return readAction {
            snapshotScanner.createPlan(directory)
        }
    }

    /**
     * Generates a barrel file from the selected snapshot paths.
     */
    fun generateBarrelFile(
        directory: PsiDirectory,
        selectedRelativePaths: Set<String>,
        barrelFileName: String,
    ): PsiFile? {
        val sanitizedFileName = sanitizeBarrelFileName(
            directory,
            barrelFileName,
        )
        val plan = prepareGeneration(directory)
        val selectedCandidates = resolveSelectedCandidates(
            plan,
            selectedRelativePaths,
        )

        if (selectedCandidates.isEmpty()) {
            LOG.warn(
                "No export candidates selected for ${directory.virtualFile.path}",
            )
            return null
        }

        val content = contentBuilder.build(selectedCandidates)

        return writeBarrelFile(
            directory = directory,
            barrelFileName = sanitizedFileName,
            content = content,
            commandName = "Generate Barrel File",
        )
    }

    /**
     * Regenerates an existing barrel file from a fresh snapshot.
     */
    fun regenerateBarrelFile(barrelFile: PsiFile) {
        if (!barrelFile.isValid) {
            LOG.warn(
                "Cannot regenerate invalid barrel file: " +
                        barrelFile.name
            )
            return
        }

        val directory = readAction {
            barrelFile.containingDirectory
        } ?: return

        val plan = prepareGeneration(directory)
        val content = contentBuilder.build(plan.candidates)
        if (!shouldWriteUpdatedContent(barrelFile, content)) {
            return
        }

        writeBarrelFile(
            directory = directory,
            barrelFileName = barrelFile.name,
            content = content,
            commandName = "Regenerate Barrel File",
        )
    }

    /**
     * Checks if a barrel file needs regeneration.
     */
    fun needsRegeneration(barrelFile: PsiFile): Boolean {
        if (!barrelFile.isValid) return false

        val directory = barrelFile.containingDirectory ?: return false
        val expected = prepareGeneration(directory)
            .let { plan -> contentBuilder.build(plan.candidates) }

        return readAction {
            normalizeContent(expected) !=
                    normalizeContent(barrelFile.text)
        }
    }

    /**
     * Regenerates an existing barrel after the directory settles.
     */
    fun synchronizeExistingBarrel(directory: VirtualFile) {
        if (!directory.isValid || recentGenerationTracker.wasRecentlyGenerated(directory.path)) {
            return
        }

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

    /**
     * Checks if a file is a barrel file.
     */
    fun isBarrelFile(virtualFile: VirtualFile): Boolean =
        detector.isBarrelFile(virtualFile)

    /**
     * Gets the barrel file name for a directory.
     */
    fun getBarrelFileName(directory: PsiDirectory): String =
        snapshotScanner.resolveBarrelFileName(directory.name)

    fun buildPreviewContent(
        plan: BarrelGenerationPlan,
        selectedRelativePaths: Set<String>,
    ): String = contentBuilder.build(
        resolveSelectedCandidates(plan, selectedRelativePaths),
    )

    private fun findExistingBarrelFile(
        directory: PsiDirectory,
        preferredFileName: String? = null,
    ): PsiFile? {
        val preferred = preferredFileName
            ?.let(directory::findFile)
            ?.takeIf(PsiFile::isValid)
        if (preferred != null) {
            return preferred
        }

        val defaultBarrel = directory.findFile(getBarrelFileName(directory))
            ?.takeIf(PsiFile::isValid)
        if (defaultBarrel != null) {
            return defaultBarrel
        }

        return directory.files.firstOrNull { file ->
            file.isValid && detector.isBarrelFile(file)
        }
    }

    private fun resolveSelectedCandidates(
        plan: BarrelGenerationPlan,
        selectedRelativePaths: Set<String>,
    ): List<BarrelExportCandidate> {
        return plan.candidates.filter { candidate ->
            candidate.relativePath in selectedRelativePaths
        }
    }

    private fun shouldWriteUpdatedContent(
        barrelFile: PsiFile,
        content: String,
    ): Boolean {
        return readAction {
            normalizeContent(barrelFile.text) != normalizeContent(content)
        }
    }

    private fun writeBarrelFile(
        directory: PsiDirectory,
        barrelFileName: String,
        content: String,
        commandName: String,
    ): PsiFile? {
        var writtenFile: PsiFile? = null

        try {
            WriteCommandAction.runWriteCommandAction(
                project,
                commandName,
                null,
                {
                    writtenFile = writer.createOrUpdate(
                        directory = directory,
                        barrelFileName = barrelFileName,
                        content = content,
                    )
                },
            )
        } catch (exception: Exception) {
            LOG.error(
                "Failed to write barrel file '$barrelFileName' in ${directory.virtualFile.path}",
                exception,
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
        val trimmedName = barrelFileName.trim()
        if (trimmedName.isBlank()) {
            return getBarrelFileName(directory)
        }

        return when {
            trimmedName.endsWith(".dart") -> trimmedName
            else -> "$trimmedName.dart"
        }
    }

    private fun commitAllDocuments() {
        psiDocumentManager.commitAllDocuments()
    }

    private fun normalizeContent(content: String): String {
        return content.lines()
            .map { it.trim() }
            .map { normalizeExportDirective(it) }
            .filter { line ->
                line.isNotEmpty() &&
                        !line.startsWith("//") &&
                        !line.startsWith("/*") &&
                        !line.startsWith("*")
            }
            .joinToString("\n")
    }

    private fun normalizeExportDirective(line: String): String {
        val match = EXPORT_DIRECTIVE_REGEX.matchEntire(line) ?: return line
        val quote = match.groupValues[1]
        val rawPath = match.groupValues[2]
        val normalizedPath = rawPath.removePrefix("./")
        return "export $quote$normalizedPath$quote;"
    }

    companion object {
        private val LOG = Logger.getInstance(
            DartBarrelService::class.java,
        )
        private val EXPORT_DIRECTIVE_REGEX =
            Regex("^export\\s+(['\"])([^'\"]+)\\1;")

        private fun <T> readAction(action: () -> T): T =
            ApplicationManager.getApplication()
                .runReadAction<T> { action() }
    }
}