// OutdatedBarrelInspection.kt
@file:Suppress("DialogTitleCapitalization")

package com.dartbarrel.plugin.inspections

import com.dartbarrel.plugin.services.DartBarrelService
import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.psi.DartFile

class OutdatedBarrelInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file !is DartFile) return

                val barrelService = file.project.service<DartBarrelService>()
                if (!barrelService.isBarrelFile(file.virtualFile)) return

                file.containingDirectory ?: return

                if (barrelService.needsRegeneration(file)) {
                    val fix = RegenerateBarrelQuickFix()
                    holder.registerProblem(
                        file,
                        "Barrel file is outdated, click to regenerate.",
                        ProblemHighlightType.WARNING,
                        fix
                    )
                }
            }
        }
    }

    private class RegenerateBarrelQuickFix : LocalQuickFix {
        override fun getName(): String = "Refresh Barrel File"

        override fun getFamilyName(): String = "Dart Barrel"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val file = descriptor.psiElement as? PsiFile ?: return
            val barrelService = project.service<DartBarrelService>()
            barrelService.regenerateBarrelFileForFile(file)
        }
    }
}