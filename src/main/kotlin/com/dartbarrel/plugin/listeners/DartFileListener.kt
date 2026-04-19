package com.dartbarrel.plugin.listeners

import com.dartbarrel.plugin.services.DartBarrelService
import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.jetbrains.lang.dart.DartFileType

class DartFileListener : AsyncFileListener {

    private val settings = DartBarrelSettings.getInstance()

    override fun prepareChange(
        events: MutableList<out VFileEvent>,
    ): AsyncFileListener.ChangeApplier? {
        if (!settings.autoGenerate) return null

        val dartEvents = events.filter(::isRelevantDartEvent)
        if (dartEvents.isEmpty()) return null

        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                dartEvents.forEach(::handleDartFileChange)
            }
        }
    }

    private fun isRelevantDartEvent(
        event: VFileEvent,
    ): Boolean {
        val file = event.file ?: return false
        return file.fileType == DartFileType.INSTANCE &&
            !file.name.startsWith("_") &&
            !DartFileUtils.isGeneratedFile(file.name) &&
            !isBarrelFile(file)
    }

    private fun handleDartFileChange(event: VFileEvent) {
        val projects = ProjectManager.getInstance().openProjects
        if (projects.isEmpty()) return

        for (project in projects) {
            if (project.isDisposed) continue

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
    }

    private fun isBarrelFile(file: VirtualFile): Boolean {
        val fileName = file.name
        val defaultName = settings.barrelFileName

        return when {
            fileName == "index.dart" -> true
            fileName == defaultName -> true
            defaultName.contains("{folder_name}") -> {
                val parentName =
                    file.parent?.name ?: return false
                fileName == "$parentName.dart"
            }
            else -> false
        }
    }

    companion object {
        private val LOG = Logger.getInstance(
            DartFileListener::class.java,
        )
    }
}
