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
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager

class GenerateBarrelAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (!virtualFile.isDirectory) {
            NotificationUtils.showWarning(project, "Invalid Selection", "Please select a directory")
            return
        }

        val psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile)
        if (psiDirectory == null) {
            NotificationUtils.showError(project, "Error", "Could not access directory")
            return
        }

        // Get all Dart files in the directory
        val dartFiles = DartFileUtils.getAllDartFilesRecursively(psiDirectory)
        if (dartFiles.isEmpty()) {
            NotificationUtils.showWarning(
                project,
                "No Dart Files",
                "No Dart files found in the selected directory and its subdirectories."
            )
            return
        }

        val barrelService = project.service<DartBarrelService>()

        // Show dialog for file selection
        val dialog = GenerateBarrelDialog(project, psiDirectory, dartFiles, barrelService)
        if (!dialog.showAndGet()) return

        val selectedFileNames = dialog.getSelectedFiles()
        val barrelFileName = dialog.getSelectedFileName()

        val freshDartFiles = DartFileUtils
            .getAllDartFilesRecursively(psiDirectory)
        val selectedFiles = freshDartFiles.filter {
            it.name in selectedFileNames && it.isValid
        }

        if (selectedFiles.isEmpty()) {
            NotificationUtils.showWarning(project, "No Files Selected", "Please select at least one file")
            return
        }

        // Generate the barrel file
        try {
            val generatedFile = barrelService.generateBarrelFileWithCustomSelection(
                psiDirectory,
                selectedFiles,
                barrelFileName
            )

            if (generatedFile != null) {
                NotificationUtils.showInfo(
                    project,
                    "Success",
                    "Generated barrel file '$barrelFileName' with ${selectedFiles.size} exports in '${psiDirectory.name}'"
                )
            } else {
                NotificationUtils.showError(
                    project,
                    "Generation Failed",
                    "Failed to generate barrel file. Please check the logs for more details."
                )
            }
        } catch (e: Exception) {
            NotificationUtils.showError(
                project,
                "Generation Error",
                "Error generating barrel file: ${e.message}"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val isEnabled = project != null &&
                virtualFile != null &&
                virtualFile.isDirectory &&
                isDartProject(project)

        e.presentation.isEnabledAndVisible = isEnabled

        // Update the action text based on context
        if (isEnabled) {
            e.presentation.text = "Generate Barrel File"
            e.presentation.description = "Generate a barrel file for exporting Dart files"
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    /**
     * Checks if the project is a Dart project by looking for pubspec.yaml
     */
    private fun isDartProject(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return false
        return projectRoot.findChild("pubspec.yaml") != null
    }
}