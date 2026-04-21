// OutdatedBarrelInspection.kt
@file:Suppress("DialogTitleCapitalization")

package com.dartbarrel.plugin.inspections

import com.dartbarrel.plugin.services.DartBarrelService
import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.psi.DartFile

/**
 * Flags barrel files whose export list no longer matches the directory's
 * current Dart files.
 *
 * The inspection runs entirely on the background read thread: the
 * needsRegeneration check is a pure read-only diff under a `runReadAction`,
 * so there is no risk of blocking the EDT or acquiring the write lock
 * prematurely.
 *
 * The quick-fix [RegenerateBarrelQuickFix] runs a WriteCommandAction
 * internally through [DartBarrelService.regenerateBarrelFile], which is the
 * single canonical write path and applies the same empty-file-safe VFS write.
 */
class OutdatedBarrelInspection : LocalInspectionTool() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor = object : PsiElementVisitor() {

        override fun visitFile(file: PsiFile) {
            if (file !is DartFile) return
            if (!file.isValid) return
            if (file.containingDirectory == null) return

            val barrelService = file.project.service<DartBarrelService>()

            if (!barrelService.isBarrelFile(file)) return

            val outdated = runCatching { barrelService.needsRegeneration(file) }
                .onFailure { e ->
                    LOG.warn("Staleness check failed for ${file.name}", e)
                }
                .getOrDefault(false)

            if (outdated) {
                holder.registerProblem(
                    file,
                    "Barrel file is outdated — click to regenerate.",
                    ProblemHighlightType.WARNING,
                    RegenerateBarrelQuickFix(),
                )
            }
        }
    }

    private class RegenerateBarrelQuickFix : LocalQuickFix {

        override fun getName(): String = "Refresh Barrel File"

        override fun getFamilyName(): String = "Dart Barrel"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val file = descriptor.psiElement as? PsiFile ?: return
            if (!file.isValid) return
            val barrelService = project.service<DartBarrelService>()
            runCatching { barrelService.regenerateBarrelFile(file) }
                .onFailure { e ->
                    LOG.error("Quick-fix regeneration failed for ${file.name}", e)
                }
        }
    }

    private companion object {
        private val LOG = Logger.getInstance(OutdatedBarrelInspection::class.java)
    }
}