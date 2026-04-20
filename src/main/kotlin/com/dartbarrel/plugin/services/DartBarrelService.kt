package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

@Service(Service.Level.PROJECT)
class DartBarrelService(project: Project) {

    private val settings = DartBarrelSettings.getInstance()
    private val psiManager = PsiManager.getInstance(project)

    private val detector = BarrelFileDetector(
        psiManager,
        settings,
    )
    private val contentBuilder = BarrelContentBuilder(settings)
    private val writer = BarrelFileWriter(
        FileDocumentManager.getInstance(),
    )

    /**
     * Generates a barrel file for all Dart files in the
     * given directory.
     */
    fun generateBarrelFile(
        directory: PsiDirectory,
    ): PsiFile? {
        val dartFiles = readAction {
            DartFileUtils.getAllDartFilesRecursively(directory)
        }

        if (dartFiles.isEmpty()) return null

        val barrelFileName = readAction {
            contentBuilder.resolveBarrelFileName(directory)
        }

        return generateBarrelFileWithCustomSelection(
            directory,
            dartFiles,
            barrelFileName,
        )
    }

    /**
     * Generates a barrel file with custom file selection.
     */
    fun generateBarrelFileWithCustomSelection(
        directory: PsiDirectory,
        selectedFiles: List<PsiFile>,
        barrelFileName: String,
    ): PsiFile? {
        val validFiles = selectedFiles.filter { it.isValid }
        if (validFiles.isEmpty()) {
            LOG.warn("No valid files for barrel generation")
            return null
        }

        return try {
            WriteAction.compute<PsiFile?, Exception> {
                val content = contentBuilder.build(
                    validFiles,
                    directory,
                )

                if (content.isBlank()) {
                    LOG.warn(
                        "Barrel content is blank for " +
                            "directory: ${directory.name}"
                    )
                    return@compute null
                }

                val existing = findExistingBarrelFile(
                    directory,
                    barrelFileName,
                )

                if (existing != null) {
                    writer.writeToExisting(existing, content)
                } else {
                    writer.createNew(
                        directory,
                        barrelFileName,
                        content,
                    )
                }
            }
        } catch (e: Exception) {
            LOG.error(
                "Failed to generate barrel file " +
                    "'$barrelFileName' in '${directory.name}'",
                e,
            )
            null
        }
    }

    /**
     * Regenerates an existing barrel file.
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

        val barrelPath = barrelFile.virtualFile?.path

        val dartFiles = readAction {
            DartFileUtils.getAllDartFilesRecursively(directory)
                .filter {
                    it.isValid &&
                        it.virtualFile?.path != barrelPath
                }
        }

        val content = readAction {
            contentBuilder.build(dartFiles, directory)
        }

        WriteAction.run<Exception> {
            writer.writeToExisting(barrelFile, content)
        }
    }

    /**
     * Checks if a barrel file needs regeneration.
     */
    fun needsRegeneration(barrelFile: PsiFile): Boolean {
        if (!barrelFile.isValid) return false

        val directory =
            barrelFile.containingDirectory ?: return false
        val barrelPath = barrelFile.virtualFile?.path

        return readAction {
            val dartFiles =
                DartFileUtils.getAllDartFilesRecursively(
                    directory,
                ).filter {
                    it.isValid &&
                        it.virtualFile?.path != barrelPath
                }

            val expected = contentBuilder.build(
                dartFiles,
                directory,
            )

            normalizeContent(expected) !=
                normalizeContent(barrelFile.text)
        }
    }

    /**
     * Checks if a file is a barrel file.
     */
    fun isBarrelFile(virtualFile: VirtualFile): Boolean =
        detector.isBarrelFile(virtualFile)

    /**
     * Checks if a PSI file is a barrel file.
     */
    fun isBarrelFile(psiFile: PsiFile): Boolean =
        detector.isBarrelFile(psiFile)

    /**
     * Gets the barrel file name for a directory.
     */
    fun getBarrelFileName(directory: PsiDirectory): String =
        contentBuilder.resolveBarrelFileName(directory)

    fun buildPreviewContent(
        files: List<PsiFile>,
        directory: PsiDirectory,
    ): String = readAction {
        contentBuilder.buildFromSelection(files, directory)
    }

    fun resolveExportableItems(
        directory: PsiDirectory,
    ): List<BarrelContentBuilder.ExportableItem> = readAction {
        contentBuilder.resolveExportableItems(directory)
    }

    private fun findExistingBarrelFile(
        directory: PsiDirectory,
        barrelFileName: String,
    ): PsiFile? =
        directory.files.find { it.name == barrelFileName }

    private fun normalizeContent(content: String): String {
        return content.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    !line.startsWith("//") &&
                    !line.startsWith("/*") &&
                    !line.startsWith("*")
            }
            .joinToString("\n")
    }

    companion object {
        private val LOG = Logger.getInstance(
            DartBarrelService::class.java,
        )

        private fun <T> readAction(action: () -> T): T =
            ApplicationManager.getApplication()
                .runReadAction<T> { action() }
    }
}