package com.dartbarrel.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

class SettingsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            "com.dartbarrel.plugin.settings.DartBarrelConfigurable"
        )
    }
}