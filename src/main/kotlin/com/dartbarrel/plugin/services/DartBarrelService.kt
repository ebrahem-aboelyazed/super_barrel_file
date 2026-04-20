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
    private val fileDocumentManager = FileDocumentManager.getInstance()
    private val psiManager = PsiManager.getInstance(project)

    /**
     * Generates a barrel file for all Dart files in the
     * given directory.
     */
    fun generateBarrelFile(directory: PsiDirectory): PsiFile? {
        val dartFiles = ApplicationManager.getApplication()
            .runReadAction<List<PsiFile>> {
                DartFileUtils.getAllDartFilesRecursively(directory)
            }

        if (dartFiles.isEmpty()) return null

        val barrelFileName = ApplicationManager.getApplication()
            .runReadAction<String> { getBarrelFileName(directory) }

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
        barrelFileName: String
    ): PsiFile? {
        if (selectedFiles.isEmpty()) {
            LOG.warn("No files selected for barrel generation")
            return null
        }

        val validFiles = selectedFiles.filter { it.isValid }
        if (validFiles.isEmpty()) {
            LOG.warn(
                "All selected PSI files are invalid"
            )
            return null
        }

        return try {
            WriteAction.compute<PsiFile?, Exception> {
                val content = buildBarrelContent(
                    validFiles,
                    directory,
                )

                if (content.isBlank()) {
                    LOG.warn(
                        "Built barrel content is blank for " +
                                "directory: ${directory.name}"
                    )
                    return@compute null
                }

                val existingBarrel = findExistingBarrelFile(
                    directory,
                    barrelFileName,
                )

                if (existingBarrel != null) {
                    updateExistingBarrelFile(existingBarrel, content)
                } else {
                    createNewBarrelFile(
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

        val directory = ApplicationManager.getApplication()
            .runReadAction<PsiDirectory?> {
                barrelFile.containingDirectory
            } ?: return

        val dartFiles = ApplicationManager.getApplication()
            .runReadAction<List<PsiFile>> {
                DartFileUtils.getAllDartFilesRecursively(directory)
                    .filter {
                        it.isValid && it.name != barrelFile.name
                    }
            }

        val content = ApplicationManager.getApplication()
            .runReadAction<String> {
                buildBarrelContent(dartFiles, directory)
            }

        WriteAction.run<Exception> {
            updateExistingBarrelFile(barrelFile, content)
        }
    }

    /**
     * Checks if a barrel file needs regeneration.
     */
    fun needsRegeneration(barrelFile: PsiFile): Boolean {
        if (!barrelFile.isValid) return false

        val directory = barrelFile.containingDirectory ?: return false

        return ApplicationManager.getApplication()
            .runReadAction<Boolean> {
                val dartFiles =
                    DartFileUtils.getAllDartFilesRecursively(directory)
                        .filter {
                            it.isValid &&
                                    it.name != barrelFile.name
                        }

                val expectedContent =
                    buildBarrelContent(dartFiles, directory)
                val actualContent =
                    normalizeContent(barrelFile.text)
                val expectedNormalized =
                    normalizeContent(expectedContent)

                expectedNormalized != actualContent
            }
    }

    /**
     * Checks if a file is a barrel file.
     */
    fun isBarrelFile(virtualFile: VirtualFile): Boolean {
        if (!virtualFile.isValid) return false
        if (isBarrelFile(virtualFile)) return true
        val psiFile = ApplicationManager.getApplication()
            .runReadAction<PsiFile?> {
                psiManager.findFile(virtualFile)
            } ?: return false
        return hasBarrelContent(psiFile)
    }

    /**
     * Checks if a PSI file is a barrel file.
     */
    fun isBarrelFile(psiFile: PsiFile): Boolean {
        if (!psiFile.isValid) return false
        val virtualFile = psiFile.virtualFile
        if (virtualFile != null && isBarrelFile(virtualFile)) {
            return true
        }
        return hasBarrelContent(psiFile)
    }

    private fun hasBarrelContent(psiFile: PsiFile): Boolean {
        return ApplicationManager.getApplication()
            .runReadAction<Boolean> {
                val text =
                    psiFile.text ?: return@runReadAction false
                val lines = text.lines()
                    .map { it.trim() }
                    .filter {
                        it.isNotEmpty() &&
                                !it.startsWith("//") &&
                                !it.startsWith("/*")
                    }

                lines.isNotEmpty() && lines.all {
                    it.startsWith("export ") &&
                            it.endsWith(";")
                }
            }
    }

    /**
     * Gets the barrel file name for a directory.
     */
    fun getBarrelFileName(directory: PsiDirectory): String {
        return when (settings.barrelFileName) {
            "{folder_name}.dart" -> "${directory.name}.dart"
            "index.dart" -> "index.dart"
            else -> settings.barrelFileName
        }
    }

    /**
     * Builds the barrel file content with smart
     * sub-barrel awareness.
     *
     * When a subdirectory already has its own barrel file,
     * that barrel is exported instead of individual files.
     * Files with `part of` directives are always excluded.
     */
    private fun buildBarrelContent(
        dartFiles: List<PsiFile>,
        rootDirectory: PsiDirectory,
    ): String {
        val barrelFileName = getBarrelFileName(rootDirectory)

        val subBarrels = findSubBarrelFiles(rootDirectory)

        val coveredPaths = subBarrels.flatMap { barrel ->
            getFilesCoveredByBarrel(barrel)
        }.toSet()

        val exports = dartFiles
            .filter { it.isValid && it.name != barrelFileName }
            .filter { !DartFileUtils.isPartFile(it) }
            .filter { file ->
                val path = file.virtualFile?.path ?: ""
                path !in coveredPaths
            }
            .mapNotNull { file ->
                buildExportStatement(rootDirectory, file)
            }

        val subBarrelExports = subBarrels.mapNotNull { barrel ->
            buildExportStatement(rootDirectory, barrel)
        }

        val allExports = (exports + subBarrelExports)
            .filter { it.isNotBlank() }
            .sorted()
            .distinct()

        return if (allExports.isNotEmpty()) {
            allExports.joinToString("\n") + "\n"
        } else {
            ""
        }
    }

    private fun findSubBarrelFiles(
        rootDirectory: PsiDirectory,
    ): List<PsiFile> {
        val barrels = mutableListOf<PsiFile>()

        fun collectBarrels(dir: PsiDirectory) {
            val barrelName = getBarrelFileName(dir)
            dir.files.find { it.name == barrelName }
                ?.let { barrels.add(it) }

            dir.subdirectories.forEach { collectBarrels(it) }
        }

        rootDirectory.subdirectories.forEach {
            collectBarrels(it)
        }
        return barrels
    }

    private fun getFilesCoveredByBarrel(
        barrelFile: PsiFile,
    ): Set<String> {
        val dir = barrelFile.containingDirectory ?: return emptySet()
        val dirPath = dir.virtualFile.path
        val text = barrelFile.text ?: return emptySet()

        return text.lines()
            .map { it.trim() }
            .filter {
                it.startsWith("export '") && it.endsWith("';")
            }
            .mapNotNull { line ->
                val path = line
                    .removePrefix("export '")
                    .removeSuffix("';")
                val resolved = java.io.File(dirPath, path)
                    .canonicalPath
                resolved
            }
            .toSet()
    }

    private fun buildExportStatement(
        rootDirectory: PsiDirectory,
        file: PsiFile,
    ): String? {
        val virtualFile = file.virtualFile
        if (virtualFile == null || !virtualFile.isValid) {
            LOG.warn(
                "Skipping file with invalid " +
                        "VirtualFile: ${file.name}"
            )
            return null
        }
        return try {
            val relativePath = calculateRelativePath(
                rootDirectory,
                file,
            )
            if (relativePath.isBlank()) {
                LOG.warn(
                    "Empty relative path for: ${file.name}"
                )
                null
            } else {
                "export './$relativePath';"
            }
        } catch (e: Exception) {
            LOG.warn(
                "Failed to calculate relative path " +
                        "for: ${file.name}",
                e,
            )
            null
        }
    }

    /**
     * Calculates the relative path between directory and file.
     */
    private fun calculateRelativePath(
        rootDirectory: PsiDirectory,
        file: PsiFile,
    ): String {
        val rootVf = rootDirectory.virtualFile
        val fileVf = file.virtualFile

        requireNotNull(fileVf) {
            "File VirtualFile is null for ${file.name}"
        }

        val rootPath = rootVf.path
        val filePath = fileVf.path

        if (!filePath.startsWith(rootPath)) {
            LOG.warn(
                "File '$filePath' is not under " +
                        "root '$rootPath'"
            )
            return fileVf.name
        }

        val relative = filePath
            .removePrefix(rootPath)
            .removePrefix("/")

        return relative.replace("\\", "/")
    }

    /**
     * Finds the existing barrel file in directory.
     */
    private fun findExistingBarrelFile(
        directory: PsiDirectory,
        barrelFileName: String,
    ): PsiFile? {
        return directory.files.find { it.name == barrelFileName }
    }

    /**
     * Updates existing barrel file content.
     */
    private fun updateExistingBarrelFile(
        barrelFile: PsiFile,
        content: String,
    ): PsiFile {
        val virtualFile = barrelFile.virtualFile
        val document =
            fileDocumentManager.getDocument(virtualFile)

        if (document != null) {
            document.setText(content)
            fileDocumentManager.saveDocument(document)
        } else {
            LOG.warn(
                "Could not get document for: " +
                        "${barrelFile.name}, writing via VFS"
            )
            virtualFile.setBinaryContent(
                content.toByteArray(Charsets.UTF_8)
            )
        }

        virtualFile.refresh(false, false)
        return barrelFile
    }

    /**
     * Creates the new barrel file.
     */
    private fun createNewBarrelFile(
        directory: PsiDirectory,
        barrelFileName: String,
        content: String
    ): PsiFile? {
        val createdFile = DartFileUtils.createDartFile(
            directory,
            barrelFileName,
            content,
        )

        if (createdFile == null) {
            LOG.error(
                "DartFileUtils.createDartFile returned null " +
                        "for '$barrelFileName'"
            )
            return null
        }

        createdFile.virtualFile?.refresh(false, false)
        return createdFile
    }

    /**
     * Normalizes content for comparison by removing
     * comments and empty lines.
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

    companion object {
        private val LOG = Logger.getInstance(
            DartBarrelService::class.java,
        )
    }
}