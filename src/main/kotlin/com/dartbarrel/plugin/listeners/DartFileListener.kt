package com.dartbarrel.plugin.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.jetbrains.lang.dart.DartFileType
import com.dartbarrel.plugin.services.DartBarrelService
import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.dartbarrel.plugin.utils.DartFileUtils

class DartFileListener : AsyncFileListener {

    private val settings = DartBarrelSettings.getInstance()

    override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
        if (!settings.autoGenerate) return null

        val dartEvents = events.filter { event ->
            val file = event.file
            file != null &&
                    file.fileType == DartFileType.INSTANCE &&
                    !file.name.startsWith("_") &&
                    !DartFileUtils.isGeneratedFile(file.name) &&
                    !isBarrelFile(file.name)
        }

        if (dartEvents.isEmpty()) return null

        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                dartEvents.forEach { event ->
                    handleDartFileChange(event)
                }
            }
        }
    }

    private fun handleDartFileChange(event: VFileEvent) {
        val openProjects = ProjectManager.getInstance().openProjects
        if (openProjects.isEmpty()) return

        openProjects.forEach { project ->
            val barrelService = project.service<DartBarrelService>()

            when (event) {
                is VFileCreateEvent, is VFileDeleteEvent, is VFileContentChangeEvent, is VFileMoveEvent -> {
                    val targetDir = when (event) {
                        is VFileMoveEvent -> event.newParent
                        else -> event.file?.parent
                    }

                    targetDir?.let { parentDir ->
                        val psiDirectory = com.intellij.psi.PsiManager.getInstance(project)
                            .findDirectory(parentDir)

                        psiDirectory?.let { directory ->
                            if (barrelService.needsRegeneration(directory)) {
                                barrelService.generateBarrelFile(directory)
                            }
                        }
                    }

                    // Also handle old directory for move events
                    if (event is VFileMoveEvent) {
                        event.oldParent?.let { oldParent ->
                            val psiDirectory = com.intellij.psi.PsiManager.getInstance(project)
                                .findDirectory(oldParent)

                            psiDirectory?.let { directory ->
                                if (barrelService.needsRegeneration(directory)) {
                                    barrelService.generateBarrelFile(directory)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isBarrelFile(fileName: String): Boolean {
        val settings = DartBarrelSettings.getInstance()
        return fileName == "index.dart" ||
                fileName == settings.barrelFileName ||
                (settings.barrelFileName == "{folder_name}.dart" && fileName.endsWith(".dart"))
    }
}