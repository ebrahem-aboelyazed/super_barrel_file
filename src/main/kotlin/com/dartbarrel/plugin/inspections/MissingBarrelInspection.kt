package com.dartbarrel.plugin.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.psi.DartFile
import com.dartbarrel.plugin.services.DartBarrelService
import com.dartbarrel.plugin.utils.DartFileUtils

class MissingBarrelInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file !is DartFile) return

                val directory = file.containingDirectory ?: return
                val barrelService = file.project.service<DartBarrelService>()

                // Check if this directory should have a barrel file
                val dartFiles = DartFileUtils.getDartFiles(directory)
                if (dartFiles.size <= 1) return // No need for barrel if only one file or no files

                // Check if barrel file exists
                val hasBarrelFile = directory.files.any {
                    barrelService.isBarrelFile(it.virtualFile)
                }

                if (!hasBarrelFile) {
                    val fix = GenerateBarrelQuickFix()
                    holder.registerProblem(
                        file,
                        "Directory contains multiple Dart files but no barrel file",
                        ProblemHighlightType.WEAK_WARNING,
                        fix
                    )
                }
            }
        }
    }

    private class GenerateBarrelQuickFix : LocalQuickFix {
        override fun getName(): String = "Generate barrel file"

        override fun getFamilyName(): String = "Dart Barrel"

        override fun applyFix(project: com.intellij.openapi.project.Project, descriptor: ProblemDescriptor) {
            val file = descriptor.psiElement as? PsiFile ?: return
            val directory = file.containingDirectory ?: return
            val barrelService = project.service<DartBarrelService>()

            barrelService.generateBarrelFile(directory)
        }
    }
}