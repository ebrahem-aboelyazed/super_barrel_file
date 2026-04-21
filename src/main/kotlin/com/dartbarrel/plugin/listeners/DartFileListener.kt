package com.dartbarrel.plugin.listeners

import com.dartbarrel.plugin.services.DartBarrelService
import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
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

/**
 * Listens for VFS changes in Dart files and queues the affected directories
 * for barrel re-synchronisation.
 *
 * ### Debounce strategy
 * Events are batched during a [DEBOUNCE_MILLIS] window; a monotonically
 * increasing sequence number ensures that only the last scheduled flush
 * actually runs, preventing duplicate regenerations when multiple files are
 * saved together (e.g. after a formatter run or a Flutter build).
 *
 * ### Barrel-file events are ignored
 * Changes to barrel files themselves are filtered out at [isRelevantEvent] to
 * break the feedback loop: writing a barrel must not trigger another
 * regeneration. The [com.dartbarrel.plugin.services.RecentGenerationTracker]
 * provides an additional quiet-period guard inside the service.
 */
class DartFileListener(
    private val project: Project,
) : BulkFileListener {

    private val settings = DartBarrelSettings.getInstance()
    private val pendingPaths = ConcurrentHashMap.newKeySet<String>()
    private val sequence = AtomicLong()

    override fun after(events: MutableList<out VFileEvent>) {
        if (!settings.autoGenerate) return
        if (project.isDisposed) return

        val affectedPaths = events.asSequence()
            .filter(::isRelevantEvent)
            .flatMap(::affectedDirectories)
            .filter { it.isValid && it.isDirectory }
            .map(VirtualFile::getPath)
            .toList()

        if (affectedPaths.isEmpty()) return

        pendingPaths.addAll(affectedPaths)

        val token = sequence.incrementAndGet()
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            { flushIfCurrent(token) },
            DEBOUNCE_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun flushIfCurrent(token: Long) {
        if (sequence.get() != token || project.isDisposed) return

        val paths = pendingPaths.toList().also { pendingPaths.clear() }
        if (paths.isEmpty()) return

        DumbService.getInstance(project).smartInvokeLater {
            if (project.isDisposed) return@smartInvokeLater
            val barrelService = project.service<DartBarrelService>()
            paths.forEach { path ->
                val dir = LocalFileSystem.getInstance()
                    .findFileByPath(path) ?: return@forEach
                runCatching { barrelService.synchronizeExistingBarrel(dir) }
                    .onFailure { e ->
                        LOG.warn("Failed to synchronize barrel for $path", e)
                    }
            }
        }
    }

    private fun isRelevantEvent(event: VFileEvent): Boolean {
        val file = event.file ?: return false
        if (file.fileType != DartFileType.INSTANCE) return false
        if (file.name.startsWith('_')) return false
        if (DartFileUtils.isGeneratedFile(file.name)) return false
        if (project.isDisposed) return false
        return !project.service<DartBarrelService>().isBarrelFile(file)
    }

    private fun affectedDirectories(event: VFileEvent): Sequence<VirtualFile> =
        when (event) {
            is VFileCreateEvent,
            is VFileDeleteEvent,
            is VFileContentChangeEvent,
            -> listOfNotNull(event.file?.parent).asSequence()

            is VFileMoveEvent -> sequenceOf(event.oldParent, event.newParent)
            else -> emptySequence()
        }

    private companion object {
        private const val DEBOUNCE_MILLIS = 500L
        private val LOG = Logger.getInstance(DartFileListener::class.java)
    }
}
