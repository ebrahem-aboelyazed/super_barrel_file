package com.dartbarrel.plugin.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.jetbrains.lang.dart.DartFileType

object DartFileUtils {

    private val LOG = Logger.getInstance(DartFileUtils::class.java)

    /**
     * Gets all Dart files recursively in a directory
     */
    fun getAllDartFilesRecursively(directory: PsiDirectory): List<PsiFile> {
        return ApplicationManager.getApplication().runReadAction<List<PsiFile>> {
            val dartFiles = mutableListOf<PsiFile>()

            fun collectDartFiles(dir: PsiDirectory) {
                dartFiles.addAll(dir.files.filter { isDartFile(it) })
                dir.subdirectories.forEach { subdir ->
                    collectDartFiles(subdir)
                }
            }

            collectDartFiles(directory)
            dartFiles
        }
    }

    /**
     * Creates a new Dart file with the given content
     */
    fun createDartFile(
        directory: PsiDirectory,
        fileName: String,
        content: String,
    ): PsiFile? {
        return try {
            val psiFileFactory = PsiFileFactory.getInstance(
                directory.project,
            )
            val file = psiFileFactory.createFileFromText(
                fileName,
                DartFileType.INSTANCE,
                content,
            )

            val addedFile = directory.add(file) as? PsiFile

            if (addedFile != null) {
                addedFile.virtualFile?.refresh(false, false)
                return addedFile
            }

            LOG.warn(
                "PSI add returned null, falling back to VFS"
            )
            createDartFileViaVfs(directory, fileName, content)
        } catch (e: Exception) {
            LOG.error(
                "Failed to create dart file '$fileName' " +
                        "via PSI, trying VFS fallback",
                e,
            )
            try {
                createDartFileViaVfs(
                    directory,
                    fileName,
                    content,
                )
            } catch (e2: Exception) {
                LOG.error(
                    "VFS fallback also failed for " +
                            "'$fileName'",
                    e2,
                )
                null
            }
        }
    }

    private fun createDartFileViaVfs(
        directory: PsiDirectory,
        fileName: String,
        content: String,
    ): PsiFile? {
        val vDir = directory.virtualFile
        val vFile = vDir.createChildData(this, fileName)
        vFile.setBinaryContent(
            content.toByteArray(Charsets.UTF_8),
        )
        vFile.refresh(false, false)
        return PsiManager.getInstance(directory.project)
            .findFile(vFile)
    }

    /**
     * Checks if a file is a valid Dart file (not generated, not private, etc.)
     */
    fun isDartFile(file: PsiFile): Boolean {
        return file.fileType == DartFileType.INSTANCE &&
                !file.name.startsWith(".") &&
                !file.name.startsWith("_") &&
                !isGeneratedFile(file.name)
    }

    /**
     * Checks if a file is generated (like .g.dart, .freezed.dart, etc.)
     */
    fun isGeneratedFile(fileName: String): Boolean {
        return fileName.endsWith(".g.dart") ||
                fileName.endsWith(".freezed.dart") ||
                fileName.endsWith(".gr.dart") ||
                fileName.endsWith(".config.dart") ||
                fileName.endsWith(".part.dart")
    }


    fun isPartFile(file: PsiFile): Boolean {
        return ApplicationManager.getApplication()
            .runReadAction<Boolean> {
                val text = file.text ?: return@runReadAction false
                text.lines().any { line ->
                    val trimmed = line.trim()
                    trimmed.startsWith("part of ") &&
                            trimmed.endsWith(";")
                }
            }
    }
}