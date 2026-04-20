package com.dartbarrel.plugin.listeners

import com.dartbarrel.plugin.services.DartBarrelService
import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.jetbrains.lang.dart.DartFileType

class DartFileListener(
    private val project: Project,
) : BulkFileListener {

    private val settings = DartBarrelSettings.getInstance()

    override fun after(events: MutableList<out VFileEvent>) {
        if (!settings.autoGenerate) return
        if (project.isDisposed) return

        val dartEvents = events.filter(::isRelevantDartEvent)
        if (dartEvents.isEmpty()) return

        dartEvents.forEach(::handleDartFileChange)
    }

    private fun isRelevantDartEvent(
        event: VFileEvent,
    ): Boolean {
        val file = event.file ?: return false
        val barrelService = project.service<DartBarrelService>()
        return file.fileType == DartFileType.INSTANCE &&
            !file.name.startsWith("_") &&
            !DartFileUtils.isGeneratedFile(file.name) &&
            !barrelService.isBarrelFile(file)
    }

    private fun handleDartFileChange(event: VFileEvent) {
        if (project.isDisposed) return

        val barrelService = project.service<DartBarrelService>()
        val affectedDirs = mutableSetOf<VirtualFile>()

        when (event) {
            is VFileCreateEvent,
            is VFileDeleteEvent,
            is VFileContentChangeEvent -> {
                event.file?.parent?.let {
                    affectedDirs.add(it)
                }
            }
            is VFileMoveEvent -> {
                affectedDirs.add(event.newParent)
                affectedDirs.add(event.oldParent)
            }
        }

        for (dir in affectedDirs) {
            if (!dir.isValid) continue

            try {
                val barrelFile = ApplicationManager
                    .getApplication()
                    .runReadAction<PsiFile?> {
                        val psiDir = PsiManager
                            .getInstance(project)
                            .findDirectory(dir)
                            ?: return@runReadAction null

                        psiDir.files.firstOrNull {
                            it.isValid &&
                                barrelService
                                    .isBarrelFile(it)
                        }
                    } ?: continue

                if (barrelService
                        .needsRegeneration(barrelFile)
                ) {
                    barrelService
                        .regenerateBarrelFile(barrelFile)
                }
            } catch (e: Exception) {
                LOG.warn(
                    "Error handling dart file change " +
                        "in ${dir.path}",
                    e,
                )
            }
        }
    }


    companion object {
        private val LOG = Logger.getInstance(
            DartFileListener::class.java,
        )
    }
}
