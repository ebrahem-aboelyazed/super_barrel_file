package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
class DartBarrelService(private val project: Project) {

    private val settings = DartBarrelSettings.getInstance()

    fun generateBarrelFileAsync(directory: PsiDirectory): CompletableFuture<PsiFile?> {
        return CompletableFuture.supplyAsync({
            generateBarrelFile(directory)
        }, AppExecutorUtil.getAppExecutorService())
    }

    fun generateBarrelFile(directory: PsiDirectory): PsiFile? {
        val (dartFiles, barrelFileName, existingBarrel) = ReadAction.compute<Triple<List<PsiFile>, String, PsiFile?>, Exception> {
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
                    updateExistingBarrelFile(existingBarrel, content)
                    resultFile = existingBarrel
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
    ): CompletableFuture<PsiFile?> {
        return CompletableFuture.supplyAsync({
            if (selectedFiles.isEmpty()) return@supplyAsync null

            val existingBarrel = ReadAction.compute<PsiFile?, Exception> {
                directory.files.find { it.name == barrelFileName }
            }

            val content = buildBarrelContentWithRelativePaths(selectedFiles, directory)

            var resultFile: PsiFile? = null
            ApplicationManager.getApplication().invokeAndWait({
                ApplicationManager.getApplication().runWriteAction {
                    if (existingBarrel != null) {
                        updateExistingBarrelFile(existingBarrel, content)
                        resultFile = existingBarrel
                    } else {
                        resultFile = DartFileUtils.createDartFile(directory, barrelFileName, content)
                    }
                }
            }, ModalityState.defaultModalityState())

            resultFile
        }, AppExecutorUtil.getAppExecutorService())
    }


    private fun updateExistingBarrelFile(existingBarrel: PsiFile, content: String) {
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
            .getDocument(existingBarrel.virtualFile)
        if (document != null) {
            document.setText(content)
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveDocument(document)
        }
    }

    private fun buildBarrelContentWithRelativePaths(dartFiles: List<PsiFile>, rootDirectory: PsiDirectory): String {
        val barrelFileName = getBarrelFileName(rootDirectory)
        val rootPath = rootDirectory.virtualFile.toNioPath()

        val exports = dartFiles.asSequence()
            .filter { it.name != barrelFileName }
            .filter { !isPrivateFile(it) }
            .map { file ->
                val relativePath = rootPath
                    .relativize(file.virtualFile.toNioPath())
                    .toString()
                    .replace("\\", "/")
                "export './$relativePath';"
            }
            .filter { it.isNotBlank() }
            .sorted()
            .toList()

        return if (exports.isEmpty()) {
            ""
        } else {
            exports.joinToString("\n", postfix = "\n")
        }
    }

    fun getBarrelFileName(directory: PsiDirectory): String {
        return when (settings.barrelFileName) {
            "{folder_name}.dart" -> "${directory.name}.dart"
            "index.dart" -> "index.dart"
            else -> settings.barrelFileName
        }
    }

    fun isBarrelFile(virtualFile: VirtualFile): Boolean {
        // Quick checks first
        if (virtualFile.isDirectory || virtualFile.extension != "dart") return false

        // Size check - empty files or very large files are unlikely to be barrel files
        if (virtualFile.length == 0L || virtualFile.length > 10240) return false // 10KB limit

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return false

        val lines = psiFile.text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("/*") }
            .take(100) // Limit check to first 100 non-comment lines for performance
            .toList()

        return lines.isNotEmpty() && lines.all { it.startsWith("export ") && it.endsWith(";") }
    }

    fun needsRegeneration(barrelFile: PsiFile): Boolean {
        val directory = barrelFile.containingDirectory ?: return false
        val dartFiles = DartFileUtils.getAllDartFilesRecursively(directory)
            .filter { it.name != barrelFile.name }
        val expectedContent = buildBarrelContentWithRelativePaths(dartFiles, directory)

        // Normalize both contents by removing comments and empty lines for comparison
        val normalizedExpected = normalizeContentForComparison(expectedContent)
        val normalizedActual = normalizeContentForComparison(barrelFile.text)

        return normalizedExpected != normalizedActual
    }

    private fun normalizeContentForComparison(content: String): String {
        return content.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                        !line.startsWith("//") &&
                        !line.startsWith("/*") &&
                        !line.startsWith("*")
            }
            .joinToString("\n")
    }

    fun regenerateBarrelFileForFile(barrelFile: PsiFile) {
        val (directory, content) = ReadAction.compute<Pair<PsiDirectory?, String>, Exception> {
            val dir = barrelFile.containingDirectory
            val files = dir?.let {
                DartFileUtils.getAllDartFilesRecursively(it).filter { filter -> filter.name != barrelFile.name }
            } ?: emptyList()
            val cont = dir?.let { buildBarrelContentWithRelativePaths(files, it) } ?: ""
            Pair(dir, cont)
        }

        if (directory == null) return

        ApplicationManager.getApplication().invokeAndWait({
            ApplicationManager.getApplication().runWriteAction {
                updateExistingBarrelFile(barrelFile, content)
            }
        }, ModalityState.defaultModalityState())
    }

    fun regenerateBarrelFileAsync(barrelFile: PsiFile): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            regenerateBarrelFileForFile(barrelFile)
        }, AppExecutorUtil.getAppExecutorService())
    }

    fun batchGenerateBarrelFiles(directories: List<PsiDirectory>): CompletableFuture<List<PsiFile?>> {
        return CompletableFuture.supplyAsync({
            directories.chunked(5).flatMap { batch ->
                batch.map { directory ->
                    generateBarrelFile(directory)
                }
            }
        }, AppExecutorUtil.getAppExecutorService())
    }

    fun batchRegenerateBarrelFiles(barrelFiles: List<PsiFile>): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            barrelFiles.chunked(5).forEach { batch ->
                batch.forEach { barrelFile ->
                    regenerateBarrelFileForFile(barrelFile)
                }
            }
        }, AppExecutorUtil.getAppExecutorService())
    }

    private fun isPrivateFile(file: PsiFile): Boolean {
        return file.name.startsWith("_")
    }
}