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
 * ### Why not `PsiFileFactory → PsiDirectory.add`?
 * That legacy path creates a detached in-memory PSI node.  When `add()` is
 * called, IntelliJ creates the physical file via VFS but does **not** copy the
 * in-memory text — the file lands on disk empty.
 *
 * ### Dual-write strategy
 * Every write goes through two phases:
 * 1. **VFS binary write** — `setBinaryContent` is a synchronous, platform-
 *    agnostic write that guarantees the bytes are on the physical file system
 *    before the action returns.  This is the source of truth.
 * 2. **Document-cache reconciliation** — `document.setText` + `commitDocument`
 *    brings the in-process PSI / editor layer into sync.  *No* call to
 *    `FileDocumentManager.saveDocument` is made here: the content is already
 *    on disk from phase 1.  On Android Studio, `saveDocument` inside a
 *    `WriteCommandAction` can be dispatched to an async save queue, which is
 *    exactly why the file can appear empty on first open.
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
     * Overwrites [barrelFile]'s content.
     * Phase 1 writes bytes to disk; phase 2 reconciles the document cache.
     */
    fun writeToExisting(
        barrelFile: PsiFile,
        content: String,
    ): PsiFile {
        val virtualFile = barrelFile.virtualFile ?: return barrelFile
        val bytes = content.toByteArray(Charsets.UTF_8)

        // Phase 1 — guaranteed on-disk write.
        virtualFile.setBinaryContent(bytes)
        virtualFile.refresh(false, false)

        // Phase 2 — reconcile the in-process document cache.
        val document = psiDocumentManager.getDocument(barrelFile)
            ?: fileDocumentManager.getDocument(virtualFile)

        if (document != null) {
            if (document.text != content) {
                document.setText(content)
            }
            psiDocumentManager.commitDocument(document)
        }

        return barrelFile
    }

    /**
     * Creates a new barrel file.
     * Phase 1 writes bytes to disk; phase 2 reconciles the document cache.
     *
     * `FileDocumentManager.saveDocument` is intentionally **not** called.
     * On Android Studio the call is async inside a `WriteCommandAction` and
     * the content never reaches disk before the file is opened, producing a
     * blank file on every first generation.  `setBinaryContent` is always
     * synchronous and is the only reliable disk-write API across all
     * IntelliJ-platform hosts (IDEA, Android Studio, Fleet, etc.).
     */
    fun createNew(
        directory: PsiDirectory,
        barrelFileName: String,
        content: String,
    ): PsiFile? {
        val virtualDir = directory.virtualFile

        return try {
            val bytes = content.toByteArray(Charsets.UTF_8)

            // Phase 1 — create the file and write bytes to disk immediately.
            val vFile = virtualDir.createChildData(this, barrelFileName)
            vFile.setBinaryContent(bytes)
            vFile.refresh(false, false)

            // Phase 2 — reconcile the in-process document / PSI cache.
            // commitDocument must be called unconditionally: even when
            // setBinaryContent already updated the document text (e.g. in the
            // light test VFS), the PSI tree is not re-parsed until a commit
            // is issued, so psiFile.text would still be stale.
            val psiFile = psiManager.findFile(vFile) ?: return null
            val document = psiDocumentManager.getDocument(psiFile)
                ?: fileDocumentManager.getDocument(vFile)

            if (document != null) {
                if (document.text != content) {
                    document.setText(content)
                }
                psiDocumentManager.commitDocument(document)
            }

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
