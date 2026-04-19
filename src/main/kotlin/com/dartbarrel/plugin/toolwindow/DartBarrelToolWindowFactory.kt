package com.dartbarrel.plugin.toolwindow

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.function.Supplier

private const val TOOL_WINDOW_ID = "Dart Barrel"

class DartBarrelToolWindowRegistrar : ProjectActivity {

    override suspend fun execute(project: Project) {
        val projectDir =
            project.guessProjectDir() ?: return
        if (projectDir.findChild("pubspec.yaml") == null) {
            return
        }

        withContext(Dispatchers.EDT) {
            if (project.isDisposed) return@withContext

            val toolWindow = ToolWindowManager
                .getInstance(project)
                .registerToolWindow(TOOL_WINDOW_ID) {
                    anchor = ToolWindowAnchor.RIGHT
                    icon = IconLoader.getIcon(
                        "/icons/dartBarrelToolWindow.svg",
                        DartBarrelToolWindowRegistrar::class.java,
                    )
                    stripeTitle = Supplier { TOOL_WINDOW_ID }
                    canCloseContent = false
                }

            val barrelToolWindow =
                project.service<DartBarrelToolWindow>()
            val content = ContentFactory.getInstance()
                .createContent(
                    barrelToolWindow.getContent(),
                    "",
                    false,
                )
            toolWindow.contentManager.addContent(content)
        }
    }
}