package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

@Service(Service.Level.PROJECT)
class DartBarrelService(project: Project) {

    private val settings = DartBarrelSettings.getInstance()
    private val fileDocumentManager = FileDocumentManager.getInstance()
    private val psiManager = PsiManager.getInstance(project)

    /**
     * Generates a barrel file for all Dart files in the given directory
     */
    fun generateBarrelFile(directory: PsiDirectory): PsiFile? {
        return ApplicationManager.getApplication().runReadAction<PsiFile?> {
            val dartFiles = DartFileUtils.getAllDartFilesRecursively(directory)
            if (dartFiles.isEmpty()) return@runReadAction null

            val barrelFileName = getBarrelFileName(directory)
            generateBarrelFileWithCustomSelection(directory, dartFiles, barrelFileName)
        }
    }

    /**
     * Generates a barrel file with custom file selection
     */
    fun generateBarrelFileWithCustomSelection(
        directory: PsiDirectory,
        selectedFiles: List<PsiFile>,
        barrelFileName: String
    ): PsiFile? {
        if (selectedFiles.isEmpty()) return null

        return try {
            WriteAction.compute<PsiFile?, Exception> {
                val content = buildBarrelContent(selectedFiles, directory)
                val existingBarrel = findExistingBarrelFile(directory, barrelFileName)

                if (existingBarrel != null) {
                    updateExistingBarrelFile(existingBarrel, content)
                } else {
                    createNewBarrelFile(directory, barrelFileName, content)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Regenerates an existing barrel file
     */
    fun regenerateBarrelFile(barrelFile: PsiFile) {
        val directory = barrelFile.containingDirectory ?: return

        WriteAction.run<Exception> {
            val dartFiles = ApplicationManager.getApplication().runReadAction<List<PsiFile>> {
                DartFileUtils.getAllDartFilesRecursively(directory)
                    .filter { it.name != barrelFile.name }
            }

            val content = buildBarrelContent(dartFiles, directory)
            updateExistingBarrelFile(barrelFile, content)
        }
    }

    /**
     * Checks if a barrel file needs regeneration
     */
    fun needsRegeneration(barrelFile: PsiFile): Boolean {
        val directory = barrelFile.containingDirectory ?: return false

        return ApplicationManager.getApplication().runReadAction<Boolean> {
            val dartFiles = DartFileUtils.getAllDartFilesRecursively(directory)
                .filter { it.name != barrelFile.name }

            val expectedContent = buildBarrelContent(dartFiles, directory)
            val actualContent = normalizeContent(barrelFile.text)
            val expectedNormalized = normalizeContent(expectedContent)

            expectedNormalized != actualContent
        }
    }

    /**
     * Checks if a file is a barrel file
     */
    fun isBarrelFile(virtualFile: VirtualFile): Boolean {
        val psiFile = psiManager.findFile(virtualFile) ?: return false
        return isBarrelFile(psiFile)
    }

    /**
     * Checks if a PSI file is a barrel file
     */
    fun isBarrelFile(psiFile: PsiFile): Boolean {
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            val lines = psiFile.text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("/*") }

            lines.isNotEmpty() && lines.all {
                it.startsWith("export ") && it.endsWith(";")
            }
        }
    }

    /**
     * Gets the barrel file name for a directory
     */
    fun getBarrelFileName(directory: PsiDirectory): String {
        return when (settings.barrelFileName) {
            "{folder_name}.dart" -> "${directory.name}.dart"
            "index.dart" -> "index.dart"
            else -> settings.barrelFileName
        }
    }

    /**
     * Builds the barrel file content
     */
    private fun buildBarrelContent(
        dartFiles: List<PsiFile>,
        rootDirectory: PsiDirectory,
    ): String {
        val barrelFileName = getBarrelFileName(rootDirectory)
        val exports = dartFiles
            .filter { it.name != barrelFileName }
            .mapNotNull { file ->
                try {
                    val relativePath = calculateRelativePath(rootDirectory, file)
                    "export './$relativePath';"
                } catch (_: Exception) {
                    null
                }
            }
            .filter { it.isNotBlank() }
            .sorted()
            .distinct()

        return if (exports.isNotEmpty()) {
            exports.joinToString("\n") + "\n"
        } else {
            ""
        }
    }

    /**
     * Calculates the relative path between directory and file
     */
    private fun calculateRelativePath(rootDirectory: PsiDirectory, file: PsiFile): String {
        val rootPath = rootDirectory.virtualFile.toNioPath()
        val filePath = file.virtualFile.toNioPath()
        return rootPath.relativize(filePath).toString().replace("\\", "/")
    }

    /**
     * Finds the existing barrel file in directory
     */
    private fun findExistingBarrelFile(directory: PsiDirectory, barrelFileName: String): PsiFile? {
        return directory.files.find { it.name == barrelFileName }
    }

    /**
     * Updates existing barrel file content
     */
    private fun updateExistingBarrelFile(barrelFile: PsiFile, content: String): PsiFile {
        val virtualFile = barrelFile.virtualFile
        val document = fileDocumentManager.getDocument(virtualFile)

        if (document != null) {
            document.setText(content)
            fileDocumentManager.saveDocument(document)
        }

        // Refresh the virtual file to ensure the IDE sees the changes
        virtualFile.refresh(false, false)

        return barrelFile
    }

    /**
     * Creates the new barrel file
     */
    private fun createNewBarrelFile(
        directory: PsiDirectory,
        barrelFileName: String,
        content: String
    ): PsiFile? {
        val createdFile = DartFileUtils.createDartFile(directory, barrelFileName, content)

        // Ensure the file is properly refreshed and visible to the IDE
        createdFile?.virtualFile?.refresh(false, false)

        return createdFile
    }

    /**
     * Normalizes content for comparison by removing comments and empty lines
     */
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
}