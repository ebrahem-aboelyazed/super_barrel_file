package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

class BarrelFileDetector(
    private val psiManager: PsiManager,
    private val settings: DartBarrelSettings,
) {

    fun isBarrelFile(virtualFile: VirtualFile): Boolean {
        if (!virtualFile.isValid) return false
        if (matchesBarrelFileName(virtualFile)) return true
        val psiFile = ApplicationManager.getApplication()
            .runReadAction<PsiFile?> {
                psiManager.findFile(virtualFile)
            } ?: return false
        return hasBarrelContent(psiFile)
    }

    fun isBarrelFile(psiFile: PsiFile): Boolean {
        if (!psiFile.isValid) return false
        val virtualFile = psiFile.virtualFile
        if (virtualFile != null &&
            matchesBarrelFileName(virtualFile)
        ) {
            return true
        }
        return hasBarrelContent(psiFile)
    }

    private fun matchesBarrelFileName(
        virtualFile: VirtualFile,
    ): Boolean {
        val fileName = virtualFile.name
        val defaultName = settings.barrelFileName
        val parentName = virtualFile.parent?.name ?: return false

        return when {
            fileName == "index.dart" -> true
            defaultName.contains("{folder_name}") -> {
                fileName == "$parentName.dart"
            }
            else -> fileName == defaultName
        }
    }

    private fun hasBarrelContent(psiFile: PsiFile): Boolean {
        return ApplicationManager.getApplication()
            .runReadAction<Boolean> {
                val text =
                    psiFile.text ?: return@runReadAction false
                val lines = text.lines()
                    .map { it.trim() }
                    .filter {
                        it.isNotEmpty() &&
                            !it.startsWith("//") &&
                            !it.startsWith("/*")
                    }

                lines.isNotEmpty() && lines.all {
                    it.startsWith("export ") &&
                        it.endsWith(";")
                }
            }
    }
}

