package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.jetbrains.lang.dart.DartFileType

class BarrelFileWriter(
    project: com.intellij.openapi.project.Project,
    private val fileDocumentManager: FileDocumentManager,
) {

    private val psiDocumentManager = PsiDocumentManager.getInstance(project)
    private val psiFileFactory = PsiFileFactory.getInstance(project)

    fun createOrUpdate(
        directory: PsiDirectory,
        barrelFileName: String,
        content: String,
    ): PsiFile? {
        val existing = directory.findFile(barrelFileName)
        return if (existing != null) {
            writeToExisting(existing, content)
        } else {
            createNew(directory, barrelFileName, content)
        }
    }

    fun writeToExisting(
        barrelFile: PsiFile,
        content: String,
    ): PsiFile {
        val virtualFile = barrelFile.virtualFile ?: return barrelFile
        val document =
            psiDocumentManager.getDocument(barrelFile)
                ?: fileDocumentManager.getDocument(virtualFile)

        if (document != null) {
            document.setText(content)
            psiDocumentManager.commitDocument(document)
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

    fun createNew(
        directory: PsiDirectory,
        barrelFileName: String,
        content: String,
    ): PsiFile? {
        val createdFile = runCatching {
            val file = psiFileFactory.createFileFromText(
                barrelFileName,
                DartFileType.INSTANCE,
                content,
            )
            directory.add(file) as? PsiFile
        }.getOrElse { error ->
            LOG.warn(
                "PSI file creation failed for '$barrelFileName', using fallback",
                error,
            )
            DartFileUtils.createDartFile(
                directory,
                barrelFileName,
                content,
            )
        }

        if (createdFile == null) {
            LOG.error(
                "DartFileUtils.createDartFile returned null " +
                    "for '$barrelFileName'"
            )
            return null
        }

        val document = psiDocumentManager.getDocument(createdFile)
        if (document != null) {
            psiDocumentManager.commitDocument(document)
            fileDocumentManager.saveDocument(document)
        }

        createdFile.virtualFile?.refresh(false, false)
        return createdFile
    }

    companion object {
        private val LOG = Logger.getInstance(
            BarrelFileWriter::class.java,
        )
    }
}

