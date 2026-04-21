package com.dartbarrel.plugin.listeners

import com.dartbarrel.plugin.services.DartBarrelService
import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.project.DumbService
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
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.lang.dart.DartFileType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class DartFileListener(
    private val project: Project,
) : BulkFileListener {

    private val settings = DartBarrelSettings.getInstance()
    private val pendingDirectoryPaths = ConcurrentHashMap.newKeySet<String>()
    private val flushSequence = AtomicLong()

    override fun after(events: MutableList<out VFileEvent>) {
        if (!settings.autoGenerate) return
        if (project.isDisposed) return

        events.asSequence()
            .filter(::isRelevantDartEvent)
            .flatMap(::resolveAffectedDirectories)
            .filter(VirtualFile::isValid)
            .map(VirtualFile::getPath)
            .forEach(pendingDirectoryPaths::add)

        if (pendingDirectoryPaths.isEmpty()) {
            return
        }

        val currentSequence = flushSequence.incrementAndGet()
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            {
                if (flushSequence.get() != currentSequence || project.isDisposed) {
                    return@schedule
                }
                flushPendingDirectories()
            },
            REGENERATION_DELAY_MILLIS,
            TimeUnit.MILLISECONDS,
        )
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

    private fun resolveAffectedDirectories(
        event: VFileEvent,
    ): Sequence<VirtualFile> {
        return when (event) {
            is VFileCreateEvent,
            is VFileDeleteEvent,
            is VFileContentChangeEvent -> listOfNotNull(event.file?.parent)
                .asSequence()
            is VFileMoveEvent -> sequenceOf(
                event.oldParent,
                event.newParent,
            )
            else -> emptySequence()
        }
    }

    private fun flushPendingDirectories() {
        if (project.isDisposed) {
            return
        }

        val paths = pendingDirectoryPaths.toList()
        pendingDirectoryPaths.clear()
        if (paths.isEmpty()) {
            return
        }

        DumbService.getInstance(project).smartInvokeLater {
            if (project.isDisposed) {
                return@smartInvokeLater
            }

            val barrelService = project.service<DartBarrelService>()
            paths.forEach { path ->
                val directory = com.intellij.openapi.vfs.LocalFileSystem
                    .getInstance()
                    .findFileByPath(path)
                    ?: return@forEach

                runCatching {
                    barrelService.synchronizeExistingBarrel(directory)
                }.onFailure { error ->
                    LOG.warn("Failed to synchronize barrel for $path", error)
                }
            }
        }
    }



    companion object {
        private const val REGENERATION_DELAY_MILLIS = 400L
        private val LOG = Logger.getInstance(
            DartFileListener::class.java,
        )
    }
}
