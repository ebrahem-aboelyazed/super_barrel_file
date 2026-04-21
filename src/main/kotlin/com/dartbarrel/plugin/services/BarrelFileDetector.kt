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
        val psiFile = ApplicationManager.getApplication()
            .runReadAction<PsiFile?> {
                psiManager.findFile(virtualFile)
            } ?: return false
        return hasBarrelContent(psiFile)
    }

    fun isBarrelFile(psiFile: PsiFile): Boolean {
        if (!psiFile.isValid) return false
        return hasBarrelContent(psiFile)
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

