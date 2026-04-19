package com.dartbarrel.plugin.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.jetbrains.lang.dart.DartFileType
import com.jetbrains.lang.dart.psi.DartClass
import com.jetbrains.lang.dart.psi.DartExtensionDeclaration
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartFunctionDeclarationWithBodyOrNative

object DartFileUtils {

    private val LOG = Logger.getInstance(
        DartFileUtils::class.java,
    )

    /**
     * Gets all direct Dart files in a directory (non-recursive)
     */
    fun getDartFiles(directory: PsiDirectory): List<PsiFile> {
        return ApplicationManager.getApplication().runReadAction<List<PsiFile>> {
            directory.files.filter { isDartFile(it) }
        }
    }

    /**
     * Gets all Dart files recursively in a directory
     */
    fun getAllDartFilesRecursively(directory: PsiDirectory): List<PsiFile> {
        return ApplicationManager.getApplication().runReadAction<List<PsiFile>> {
            val dartFiles = mutableListOf<PsiFile>()

            fun collectDartFiles(dir: PsiDirectory) {
                // Add Dart files from current directory
                dartFiles.addAll(dir.files.filter { isDartFile(it) })

                // Recursively process subdirectories
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
     * Checks if a virtual file is a valid Dart file
     */
    fun isDartFile(virtualFile: VirtualFile): Boolean {
        return virtualFile.fileType == DartFileType.INSTANCE &&
                !virtualFile.name.startsWith(".") &&
                !virtualFile.name.startsWith("_") &&
                !isGeneratedFile(virtualFile.name)
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

    /**
     * Checks if a Dart file has public declarations
     */
    fun hasPublicDeclarations(file: PsiFile): Boolean {
        if (file !is DartFile) return false

        return ApplicationManager.getApplication().runReadAction<Boolean> {
            file.children.any { element ->
                when (element) {
                    is DartClass -> isPublicDeclaration(element.name)
                    is DartFunctionDeclarationWithBodyOrNative -> isPublicDeclaration(element.name)
                    is DartExtensionDeclaration -> isPublicDeclaration(element.name)
                    else -> false
                }
            }
        }
    }

    /**
     * Gets all public element names from a Dart file
     */
    fun getPublicElements(file: PsiFile): List<String> {
        if (file !is DartFile) return emptyList()

        return ApplicationManager.getApplication().runReadAction<List<String>> {
            val elements = mutableListOf<String>()

            file.children.forEach { element ->
                when (element) {
                    is DartClass -> {
                        element.name?.let { name ->
                            if (isPublicDeclaration(name)) elements.add(name)
                        }
                    }

                    is DartFunctionDeclarationWithBodyOrNative -> {
                        element.name?.let { name ->
                            if (isPublicDeclaration(name)) elements.add(name)
                        }
                    }

                    is DartExtensionDeclaration -> {
                        element.name?.let { name ->
                            if (isPublicDeclaration(name)) elements.add(name)
                        }
                    }
                }
            }

            elements
        }
    }

    /**
     * Gets all private element names from a Dart file
     */
    fun getPrivateElements(file: PsiFile): List<String> {
        if (file !is DartFile) return emptyList()

        return ApplicationManager.getApplication().runReadAction<List<String>> {
            val elements = mutableListOf<String>()

            file.children.forEach { element ->
                when (element) {
                    is DartClass -> {
                        element.name?.let { name ->
                            if (isPrivateDeclaration(name)) elements.add(name)
                        }
                    }

                    is DartFunctionDeclarationWithBodyOrNative -> {
                        element.name?.let { name ->
                            if (isPrivateDeclaration(name)) elements.add(name)
                        }
                    }

                    is DartExtensionDeclaration -> {
                        element.name?.let { name ->
                            if (isPrivateDeclaration(name)) elements.add(name)
                        }
                    }
                }
            }

            elements
        }
    }

    /**
     * Gets all element names (public and private) from a Dart file
     */
    fun getAllElementNames(file: PsiFile): List<String> {
        return getPublicElements(file) + getPrivateElements(file)
    }

    /**
     * Checks if a declaration name is public (doesn't start with underscore)
     */
    private fun isPublicDeclaration(name: String?): Boolean {
        return name != null && !name.startsWith("_")
    }

    /**
     * Checks if a declaration name is private (starts with underscore)
     */
    private fun isPrivateDeclaration(name: String?): Boolean {
        return name != null && name.startsWith("_")
    }

    /**
     * Checks if a file is a private Dart file (name starts with underscore)
     */
    fun isPrivateFile(file: PsiFile): Boolean {
        return file.name.startsWith("_")
    }
}