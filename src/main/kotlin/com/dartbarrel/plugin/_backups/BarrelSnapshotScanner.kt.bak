package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.model.BarrelExportCandidate
import com.dartbarrel.plugin.model.BarrelGenerationPlan
import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory

/**
 * Builds a stable snapshot of the files that can participate in a barrel.
 */
class BarrelSnapshotScanner(
    private val settings: DartBarrelSettings,
) {

    /**
     * Creates a generation plan for the given directory.
     */
    fun createPlan(directory: PsiDirectory): BarrelGenerationPlan {
        val root = directory.virtualFile
        val barrelFileName = resolveBarrelFileName(directory.name)
        val rootBarrelPath = root.resolveChildPath(barrelFileName)
        val nestedBarrels = findNestedBarrels(root)
        val nestedBarrelPaths = nestedBarrels.mapTo(linkedSetOf()) { it.path }
        val coveredPaths = nestedBarrels.flatMapTo(linkedSetOf()) {
            resolveCoveredPaths(it)
        }

        val directCandidates = collectDartFiles(root)
            .asSequence()
            .filter { it.path != rootBarrelPath }
            .filter { it.path !in nestedBarrelPaths }
            .filter { it.path !in coveredPaths }
            .mapNotNull { file ->
                toCandidate(root, file, isNestedBarrel = false)
            }
            .toList()

        val nestedBarrelCandidates = nestedBarrels.mapNotNull { barrel ->
            toCandidate(root, barrel, isNestedBarrel = true)
        }

        return BarrelGenerationPlan(
            barrelFileName = barrelFileName,
            candidates = (directCandidates + nestedBarrelCandidates)
                .sortedBy { it.relativePath },
        )
    }

    /**
     * Resolves the configured barrel file name for a directory.
     */
    fun resolveBarrelFileName(directoryName: String): String {
        return when (val configuredName = settings.barrelFileName) {
            FOLDER_NAME_PATTERN -> "$directoryName.dart"
            else -> configuredName
        }
    }

    private fun findNestedBarrels(root: VirtualFile): List<VirtualFile> {
        val barrels = mutableListOf<VirtualFile>()
        val stack = ArrayDeque<VirtualFile>()
        root.children
            .filter(VirtualFile::isDirectory)
            .sortedBy(VirtualFile::getPath)
            .forEach(stack::addLast)

        while (stack.isNotEmpty()) {
            val directory = stack.removeFirst()
            if (!directory.isValid) {
                continue
            }

            directory.children
                .filter(VirtualFile::isDirectory)
                .sortedBy(VirtualFile::getPath)
                .forEach(stack::addLast)

            directory.findChild(resolveBarrelFileName(directory.name))
                ?.takeIf(::isBarrelCandidate)
                ?.let(barrels::add)
        }

        return barrels
    }

    private fun collectDartFiles(root: VirtualFile): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val stack = ArrayDeque<VirtualFile>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (!current.isValid) {
                continue
            }

            current.children
                .sortedBy(VirtualFile::getPath)
                .forEach { child ->
                    when {
                        !child.isValid -> Unit
                        child.isDirectory -> stack.addLast(child)
                        isExportableDartFile(child) -> files.add(child)
                    }
                }
        }

        return files
    }

    private fun resolveCoveredPaths(barrelFile: VirtualFile): Set<String> {
        val parent = barrelFile.parent ?: return emptySet()
        val text = safeText(barrelFile)

        return EXPORT_REGEX.findAll(text)
            .mapNotNull { match ->
                val relativePath = match.groupValues[2]
                    .removePrefix("./")
                VfsUtilCore.findRelativeFile(relativePath, parent)
                    ?.path
            }
            .toSet()
    }

    private fun toCandidate(
        root: VirtualFile,
        file: VirtualFile,
        isNestedBarrel: Boolean,
    ): BarrelExportCandidate? {
        val relativePath = VfsUtilCore.getRelativePath(file, root, '/')
        if (relativePath.isNullOrBlank()) {
            LOG.warn("Failed to resolve relative path for ${file.path}")
            return null
        }

        return BarrelExportCandidate(
            relativePath = relativePath,
            isNestedBarrel = isNestedBarrel,
        )
    }

    private fun isExportableDartFile(file: VirtualFile): Boolean {
        if (file.extension != DART_EXTENSION) {
            return false
        }

        return !file.name.startsWith('.') &&
            !file.name.startsWith('_') &&
            !DartFileUtils.isGeneratedFile(file.name) &&
            !matchesExcludePattern(file.name) &&
            !isPartFile(file)
    }

    private fun isBarrelCandidate(file: VirtualFile): Boolean {
        return file.isValid &&
            !file.isDirectory &&
            file.extension == DART_EXTENSION
    }

    private fun isPartFile(file: VirtualFile): Boolean {
        return safeText(file)
            .lineSequence()
            .map(String::trim)
            .any { line ->
                line.startsWith("part of ") && line.endsWith(';')
            }
    }

    private fun matchesExcludePattern(fileName: String): Boolean {
        return settings.excludePatterns.any { pattern ->
            runCatching { Regex(pattern) }
                .onFailure { error ->
                    LOG.warn("Ignoring invalid exclude pattern: $pattern", error)
                }
                .getOrNull()
                ?.matches(fileName)
                ?: false
        }
    }

    private fun safeText(file: VirtualFile): String {
        return runCatching { VfsUtilCore.loadText(file) }
            .getOrElse { error ->
                LOG.warn("Failed to read ${file.path}", error)
                ""
            }
    }

    private fun VirtualFile.resolveChildPath(fileName: String): String =
        "$path/$fileName"

    private companion object {
        private const val DART_EXTENSION = "dart"
        private const val FOLDER_NAME_PATTERN = "{folder_name}.dart"
        private val LOG = Logger.getInstance(BarrelSnapshotScanner::class.java)
        private val EXPORT_REGEX =
            Regex("^export\\s+(['\"])([^'\"]+)\\1;", RegexOption.MULTILINE)
    }
}

