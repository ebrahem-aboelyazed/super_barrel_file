package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile

class BarrelFileWriter(
    private val fileDocumentManager: FileDocumentManager,
) {

    fun writeToExisting(
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

    fun createNew(
        directory: PsiDirectory,
        barrelFileName: String,
        content: String,
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

    companion object {
        private val LOG = Logger.getInstance(
            BarrelFileWriter::class.java,
        )
    }
}

