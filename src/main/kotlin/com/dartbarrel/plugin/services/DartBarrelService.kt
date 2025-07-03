package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

@Service(Service.Level.PROJECT)
class DartBarrelService(private val project: Project) {

    private val settings = DartBarrelSettings.getInstance()

    fun generateBarrelFile(directory: PsiDirectory): PsiFile? {
        val (dartFiles, barrelFileName, existingBarrel) = ApplicationManager.getApplication()
            .runReadAction<Triple<List<PsiFile>, String, PsiFile?>> {
                val dartFiles = DartFileUtils.getAllDartFilesRecursively(directory)
                val barrelFileName = getBarrelFileName(directory)
                val existingBarrel = directory.files.find { it.name == barrelFileName }
                Triple(dartFiles, barrelFileName, existingBarrel)
            }
        if (dartFiles.isEmpty()) return null

        val content = buildBarrelContentWithRelativePaths(dartFiles, directory)

        var resultFile: PsiFile? = null
        ApplicationManager.getApplication().invokeAndWait({
            ApplicationManager.getApplication().runWriteAction {
                if (existingBarrel != null) {
                    val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                        .getDocument(existingBarrel.virtualFile)
                    if (document != null) {
                        document.setText(content)
                        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveDocument(document)
                        resultFile = existingBarrel
                    }
                } else {
                    resultFile = DartFileUtils.createDartFile(directory, barrelFileName, content)
                }
            }
        }, ModalityState.defaultModalityState())

        return resultFile
    }

    fun generateBarrelFileWithCustomSelection(
        directory: PsiDirectory,
        selectedFiles: List<PsiFile>,
        barrelFileName: String
    ): PsiFile? {
        if (selectedFiles.isEmpty()) return null

        val existingBarrel = ApplicationManager.getApplication().runReadAction<PsiFile?> {
            directory.files.find { it.name == barrelFileName }
        }
        val content = buildBarrelContentWithRelativePaths(selectedFiles, directory)

        var resultFile: PsiFile? = null
        ApplicationManager.getApplication().invokeAndWait({
            ApplicationManager.getApplication().runWriteAction {
                if (existingBarrel != null) {
                    val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                        .getDocument(existingBarrel.virtualFile)
                    if (document != null) {
                        document.setText(content)
                        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveDocument(document)
                        resultFile = existingBarrel
                    }
                } else {
                    resultFile = DartFileUtils.createDartFile(directory, barrelFileName, content)
                }
            }
        }, ModalityState.defaultModalityState())

        return resultFile
    }

    private fun buildBarrelContentWithRelativePaths(dartFiles: List<PsiFile>, rootDirectory: PsiDirectory): String {
        val barrelFileName = getBarrelFileName(rootDirectory)
        val exports = dartFiles
            .filter { it.name != barrelFileName }
            .map { file ->
                val relativePath = rootDirectory.virtualFile.toNioPath()
                    .relativize(file.virtualFile.toNioPath())
                    .toString()
                    .replace("\\", "/")
                "export '$relativePath';"
            }
            .filter { it.isNotBlank() }
            .sorted()
        return exports.joinToString("\n") + "\n"
    }

    fun getBarrelFileName(directory: PsiDirectory): String {
        return when (settings.barrelFileName) {
            "{folder_name}.dart" -> "${directory.name}.dart"
            "index.dart" -> "index.dart"
            else -> settings.barrelFileName
        }
    }

    fun isBarrelFile(virtualFile: VirtualFile): Boolean {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return false
        val lines = psiFile.text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("/*") }
        return lines.isNotEmpty() && lines.all { it.startsWith("export ") && it.endsWith(";") }
    }

    fun needsRegeneration(barrelFile: PsiFile): Boolean {
        val directory = barrelFile.containingDirectory ?: return false
        val dartFiles = DartFileUtils.getAllDartFilesRecursively(directory)
            .filter { it.name != barrelFile.name }
        val expectedContent = buildBarrelContentWithRelativePaths(dartFiles, directory)
        return expectedContent != barrelFile.text
    }

    fun regenerateBarrelFileForFile(barrelFile: PsiFile) {
        val (directory, dartFiles, content) = ApplicationManager.getApplication().runReadAction<Triple<PsiDirectory?, List<PsiFile>, String>> {
            val dir = barrelFile.containingDirectory
            val files = dir?.let { DartFileUtils.getAllDartFilesRecursively(it).filter { it.name != barrelFile.name } } ?: emptyList()
            val cont = dir?.let { buildBarrelContentWithRelativePaths(files, it) } ?: ""
            Triple(dir, files, cont)
        }
        if (directory == null) return

        ApplicationManager.getApplication().invokeAndWait({
            ApplicationManager.getApplication().runWriteAction {
                val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                    .getDocument(barrelFile.virtualFile)
                if (document != null) {
                    document.setText(content)
                    com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveDocument(document)
                }
            }
        }, ModalityState.defaultModalityState())
    }

    private fun isPrivateFile(file: PsiFile): Boolean {
        return file.name.startsWith("_")
    }
}