package com.dartbarrel.plugin.utils

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object NotificationUtils {

    private const val NOTIFICATION_GROUP_ID = "Dart Barrel Plugin"

    fun showInfo(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }

    fun showWarning(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.WARNING)
            .notify(project)
    }

    fun showError(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.ERROR)
            .notify(project)
    }

    fun showSuccess(project: Project, title: String, content: String) {
        showInfo(project, title, content)
    }
}