package com.dartbarrel.plugin.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/**
 * Handles all physical write operations for barrel files.
 *
 * New files are always created via VFS createChildData + setBinaryContent
 * to guarantee non-empty content on first generation. The old approach of
 * `PsiFileFactory.createFileFromText` followed by `PsiDirectory.add` was
 * unreliable: the in-memory PSI content was not guaranteed to be flushed to
 * the physical file, which caused blank barrel files on first creation.
 *
 * Existing files are updated through the document layer so that IntelliJ's
 * undo-manager, unsaved-changes indicators, and live templates stay in sync.
 */
class BarrelFileWriter(
    project: Project,
    private val fileDocumentManager: FileDocumentManager,
) {

    private val psiDocumentManager = PsiDocumentManager.getInstance(project)
    private val psiManager = PsiManager.getInstance(project)

    /**
     * Creates a new barrel file, or overwrites an existing one, with
     * [content]. Must be called inside a [com.intellij.openapi.command
     * .WriteCommandAction].
     */
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

    /**
     * Overwrites [barrelFile]'s content via the document layer so that
     * IntelliJ stays consistent with the underlying VFS.
     */
    fun writeToExisting(
        barrelFile: PsiFile,
        content: String,
    ): PsiFile {
        val virtualFile = barrelFile.virtualFile ?: return barrelFile
        val document = psiDocumentManager.getDocument(barrelFile)
            ?: fileDocumentManager.getDocument(virtualFile)

        if (document != null) {
            document.setText(content)
            psiDocumentManager.commitDocument(document)
            fileDocumentManager.saveDocument(document)
        } else {
            LOG.warn(
                "No document for '${barrelFile.name}'; " +
                    "falling back to VFS binary write.",
            )
            virtualFile.setBinaryContent(content.toByteArray(Charsets.UTF_8))
        }

        virtualFile.refresh(false, false)
        return barrelFile
    }

    /**
     * Creates a new barrel file with [content].
     *
     * ### Why not `PsiFileFactory → PsiDirectory.add`?
     * That legacy path creates a detached in-memory PSI node and hands it to
     * the directory's `add` method.  IntelliJ creates the physical file via
     * VFS but does **not** copy the in-memory text, leaving it empty on disk.
     *
     * ### Two-phase write (VFS + document layer)
     * 1. `createChildData` — creates an empty physical file in the VFS.
     * 2. `getDocument → setText` — writes the content through the IntelliJ
     *    document/commit pipeline so both the on-disk file and the in-process
     *    PSI document cache reflect the correct content immediately, in both
     *    production and the light test VFS.
     */
    fun createNew(
        directory: PsiDirectory,
        barrelFileName: String,
        content: String,
    ): PsiFile? {
        val virtualDir = directory.virtualFile

        return try {
            val vFile = virtualDir.createChildData(this, barrelFileName)
            vFile.refresh(false, false)

            val psiFile = psiManager.findFile(vFile) ?: return null

            val document = psiDocumentManager.getDocument(psiFile)
                ?: fileDocumentManager.getDocument(vFile)

            if (document != null) {
                document.setText(content)
                psiDocumentManager.commitDocument(document)
                fileDocumentManager.saveDocument(document)
            } else {
                LOG.warn(
                    "No document for new file '$barrelFileName'; " +
                        "falling back to VFS binary write.",
                )
                vFile.setBinaryContent(content.toByteArray(Charsets.UTF_8))
            }

            vFile.refresh(false, false)
            psiFile
        } catch (e: Exception) {
            LOG.error(
                "Failed to create barrel file '$barrelFileName' " +
                    "in ${virtualDir.path}",
                e,
            )
            null
        }
    }

    private companion object {
        private val LOG = Logger.getInstance(BarrelFileWriter::class.java)
    }
}
