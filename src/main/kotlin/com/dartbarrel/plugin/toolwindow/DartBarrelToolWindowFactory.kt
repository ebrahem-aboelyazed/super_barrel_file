package com.dartbarrel.plugin.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class DartBarrelToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val barrelToolWindow = project.service<DartBarrelToolWindow>()
        val content = ContentFactory.getInstance().createContent(
            barrelToolWindow.getContent(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        val projectDir = project.guessProjectDir()
        return projectDir?.findChild("pubspec.yaml") != null
    }
}