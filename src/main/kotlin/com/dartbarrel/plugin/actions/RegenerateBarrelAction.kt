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
import com.intellij.psi.PsiManager

class RegenerateBarrelAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (virtualFile.isDirectory) return

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return
        val barrelService = project.service<DartBarrelService>()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Regenerating barrel file", true) {
            override fun run(indicator: ProgressIndicator) {
                barrelService.regenerateBarrelFileForFile(psiFile)
                NotificationUtils.showInfo(
                    project,
                    "Barrel File Regenerated",
                    "Regenerated ${psiFile.name}"
                )
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val isBarrel = project != null &&
                virtualFile != null &&
                !virtualFile.isDirectory &&
                virtualFile.fileType.name == "Dart" &&
                project.service<DartBarrelService>().isBarrelFile(virtualFile)

        e.presentation.isEnabledAndVisible = isBarrel
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}