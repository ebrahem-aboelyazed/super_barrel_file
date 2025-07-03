package com.dartbarrel.plugin.actions

import com.dartbarrel.plugin.services.DartBarrelService
import com.dartbarrel.plugin.utils.NotificationUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager

class RegenerateBarrelAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (!virtualFile.isDirectory) return

        val psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating barrel files", true) {
            override fun run(indicator: ProgressIndicator) {
                val count = generateBarrelFilesRecursively(project, psiDirectory, indicator)
                NotificationUtils.showInfo(
                    project,
                    "Bulk Generation Complete",
                    "Generated $count barrel file(s) in ${psiDirectory.name} and subdirectories"
                )
            }
        })
    }

    // Recursively generate for all subdirectories
    private fun generateBarrelFilesRecursively(
        project: Project,
        directory: PsiDirectory,
        indicator: ProgressIndicator
    ): Int {
        val barrelService = project.service<DartBarrelService>()
        var count = 0

        indicator.text = "Generating barrel file in ${directory.name}..."
        indicator.isIndeterminate = false

        if (barrelService.generateBarrelFile(directory) != null) {
            count++
        }
        directory.subdirectories.forEach { subdir ->
            count += generateBarrelFilesRecursively(project, subdir, indicator)
        }
        return count
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        e.presentation.isEnabledAndVisible = project != null &&
                virtualFile != null &&
                virtualFile.isDirectory &&
                isDartProject(project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    private fun isDartProject(project: Project): Boolean {
        val basePath = project.basePath
        val dir = basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        return dir?.findChild("pubspec.yaml") != null
    }
}