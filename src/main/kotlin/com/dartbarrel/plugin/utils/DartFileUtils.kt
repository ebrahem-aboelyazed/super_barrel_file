package com.dartbarrel.plugin.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.jetbrains.lang.dart.DartFileType
import com.jetbrains.lang.dart.psi.DartClass
import com.jetbrains.lang.dart.psi.DartExtensionDeclaration
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartFunctionDeclarationWithBodyOrNative

object DartFileUtils {

    fun getDartFiles(directory: PsiDirectory): List<PsiFile> {
        return ApplicationManager.getApplication().runReadAction<List<PsiFile>> {
            directory.files.filter {
                it.fileType == DartFileType.INSTANCE &&
                        !it.name.startsWith(".") &&
                        !it.name.endsWith(".g.dart") &&
                        !it.name.endsWith(".freezed.dart")
            }
        }
    }

    // DartFileUtils.kt
    fun getAllDartFilesRecursively(directory: PsiDirectory): List<PsiFile> {
        val dartFiles = mutableListOf<PsiFile>()
        ApplicationManager.getApplication().runReadAction {
            fun collect(dir: PsiDirectory) {
                dartFiles += getDartFiles(dir)
                dir.subdirectories.forEach { collect(it) }
            }
            collect(directory)
        }
        return dartFiles
    }

    fun createDartFile(directory: PsiDirectory, fileName: String, content: String): PsiFile? {
        return try {
            val psiFileFactory = PsiFileFactory.getInstance(directory.project)
            val file = psiFileFactory.createFileFromText(
                fileName,
                DartFileType.INSTANCE,
                content
            )
            directory.add(file) as PsiFile
        } catch (_: Exception) {
            null
        }
    }

    fun hasPublicDeclarations(file: PsiFile): Boolean {
        if (file !is DartFile) return false

        return file.children.any { element ->
            when (element) {
                is DartClass -> element.name?.let { !it.startsWith("_") } ?: false
                is DartFunctionDeclarationWithBodyOrNative -> element.name?.let { !it.startsWith("_") } ?: false
                is DartExtensionDeclaration -> element.name?.let { !it.startsWith("_") } ?: false
                else -> false
            }
        }
    }

    fun getPublicElements(file: PsiFile): List<String> {
        if (file !is DartFile) return emptyList()

        val elements = mutableListOf<String>()

        file.children.forEach { element ->
            when (element) {
                is DartClass -> {
                    element.name?.let { name ->
                        if (!name.startsWith("_")) elements.add(name)
                    }
                }

                is DartExtensionDeclaration -> {
                    element.name?.let { name ->
                        if (!name.startsWith("_")) elements.add(name)
                    }
                }
            }
        }

        return elements
    }

    fun getPrivateElements(file: PsiFile): List<String> {
        if (file !is DartFile) return emptyList()

        val elements = mutableListOf<String>()

        file.children.forEach { element ->
            when (element) {
                is DartClass -> {
                    element.name?.let { name ->
                        if (name.startsWith("_")) elements.add(name)
                    }
                }

                is DartExtensionDeclaration -> {
                    element.name?.let { name ->
                        if (name.startsWith("_")) elements.add(name)
                    }
                }
            }
        }

        return elements
    }

    fun getAllElementNames(file: PsiFile): List<String> {
        return getPublicElements(file) + getPrivateElements(file)
    }

    fun isGeneratedFile(fileName: String): Boolean {
        return fileName.endsWith(".g.dart") ||
                fileName.endsWith(".freezed.dart") ||
                fileName.endsWith(".gr.dart") ||
                fileName.endsWith(".config.dart")
    }
}