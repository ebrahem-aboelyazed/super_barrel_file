package com.dartbarrel.plugin.actions

import com.dartbarrel.plugin.services.DartBarrelService
import com.dartbarrel.plugin.ui.GenerateBarrelDialog
import com.dartbarrel.plugin.utils.DartFileUtils
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
import com.intellij.psi.PsiManager

class GenerateBarrelAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!virtualFile.isDirectory) return
        val psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile) ?: return

        val dartFiles = DartFileUtils.getAllDartFilesRecursively(psiDirectory)
        if (dartFiles.isEmpty()) {
            NotificationUtils.showWarning(project, "No Dart Files", "No Dart files found in the selected directory.")
            return
        }

        val barrelService = project.service<DartBarrelService>()
        val dialog = GenerateBarrelDialog(project, psiDirectory, dartFiles, barrelService)
        if (!dialog.showAndGet()) return

        val selectedFileNames = dialog.getSelectedFiles()
        val selectedFiles = dartFiles.filter { it.name in selectedFileNames }
        val barrelFileName = dialog.getSelectedFileName()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating barrel file", true) {
            override fun run(indicator: ProgressIndicator) {
                // Do NOT wrap this in runReadAction!
                barrelService.generateBarrelFileWithCustomSelection(psiDirectory, selectedFiles, barrelFileName)
                NotificationUtils.showInfo(
                    project,
                    "Barrel File Generated",
                    "Generated $barrelFileName in ${psiDirectory.name}"
                )
            }
        })
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