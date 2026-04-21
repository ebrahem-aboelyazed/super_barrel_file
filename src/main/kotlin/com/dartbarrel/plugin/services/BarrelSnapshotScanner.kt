package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.model.BarrelExportCandidate
import com.dartbarrel.plugin.model.BarrelGenerationPlan
import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * Produces a stable, deterministic [BarrelGenerationPlan] by walking the VFS
 * tree synchronously inside a `runReadAction`.
 *
 * ### Algorithm
 * 1. Collect all `.dart` files under [root] recursively (BFS, sorted by path).
 * 2. Build a "nested barrel" list: any subdirectory whose canonical barrel
 *    file already exists is exposed as a single re-export entry rather than
 *    having all its children listed individually.
 * 3. Exclude: the root barrel file itself, files already covered by a nested
 *    barrel's export list, generated files, `part of` files, hidden/private
 *    files, and user-configured regex patterns.
 */
class BarrelSnapshotScanner(
    private val settings: DartBarrelSettings,
) {

    /**
     * Creates a [BarrelGenerationPlan] for [directory].
     * Must be called inside a read action.
     */
    fun createPlan(
        directory: com.intellij.psi.PsiDirectory,
    ): BarrelGenerationPlan {
        val root = directory.virtualFile
        return createPlanFromVirtualFile(root)
    }

    /**
     * Creates a [BarrelGenerationPlan] from a [VirtualFile] root.
     * Can be called directly when only a VirtualFile is available.
     */
    fun createPlanFromVirtualFile(root: VirtualFile): BarrelGenerationPlan {
        val barrelFileName = resolveBarrelFileName(root.name)
        val rootBarrelPath = "${root.path}/$barrelFileName"

        val nestedBarrels = findNestedBarrels(root)
        val nestedBarrelPaths = nestedBarrels.mapTo(LinkedHashSet()) { it.path }
        val coveredPaths = nestedBarrels.flatMapTo(LinkedHashSet()) {
            resolveCoveredPaths(it)
        }

        val directCandidates = collectDartFiles(root)
            .asSequence()
            .filter { it.path != rootBarrelPath }
            .filter { it.path !in nestedBarrelPaths }
            .filter { it.path !in coveredPaths }
            .mapNotNull { toCandidate(root, it, isNestedBarrel = false) }
            .toList()

        val nestedCandidates = nestedBarrels.mapNotNull { barrel ->
            toCandidate(root, barrel, isNestedBarrel = true)
        }

        return BarrelGenerationPlan(
            barrelFileName = barrelFileName,
            candidates = (directCandidates + nestedCandidates)
                .sortedBy { it.relativePath },
        )
    }

    /**
     * Resolves the barrel file name for the given [directoryName] using the
     * current settings.
     */
    fun resolveBarrelFileName(directoryName: String): String =
        when (val configured = settings.barrelFileName) {
            FOLDER_NAME_PATTERN -> "$directoryName.dart"
            else -> configured
        }

    private fun findNestedBarrels(root: VirtualFile): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val queue = ArrayDeque<VirtualFile>()

        root.children
            .filter { it.isValid && it.isDirectory }
            .sortedBy(VirtualFile::getPath)
            .forEach(queue::addLast)

        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            if (!dir.isValid) continue

            dir.children
                .filter { it.isValid && it.isDirectory }
                .sortedBy(VirtualFile::getPath)
                .forEach(queue::addLast)

            dir.findChild(resolveBarrelFileName(dir.name))
                ?.takeIf { it.isValid && !it.isDirectory && it.extension == DART_EXT }
                ?.let(result::add)
        }
        return result
    }

    private fun collectDartFiles(root: VirtualFile): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val stack = ArrayDeque<VirtualFile>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (!current.isValid) continue

            current.children
                .filter(VirtualFile::isValid)
                .sortedBy(VirtualFile::getPath)
                .forEach { child ->
                    when {
                        child.isDirectory -> stack.addLast(child)
                        isExportableDartFile(child) -> result.add(child)
                    }
                }
        }
        return result
    }

    private fun resolveCoveredPaths(barrelFile: VirtualFile): Set<String> {
        val parent = barrelFile.parent ?: return emptySet()
        val text = safeReadText(barrelFile)

        return EXPORT_REGEX.findAll(text)
            .mapNotNull { match ->
                val relativePath = match.groupValues[2].removePrefix("./")
                VfsUtilCore.findRelativeFile(relativePath, parent)?.path
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
            LOG.warn("Could not resolve relative path for ${file.path}")
            return null
        }
        return BarrelExportCandidate(relativePath, isNestedBarrel)
    }

    private fun isExportableDartFile(file: VirtualFile): Boolean {
        if (file.extension != DART_EXT) return false
        val name = file.name
        return !name.startsWith('.') &&
            !name.startsWith('_') &&
            !DartFileUtils.isGeneratedFile(name) &&
            !matchesExcludePattern(name) &&
            !isPartFile(file)
    }

    private fun isPartFile(file: VirtualFile): Boolean =
        safeReadText(file)
            .lineSequence()
            .map(String::trim)
            .any { it.startsWith("part of ") && it.endsWith(';') }

    private fun matchesExcludePattern(fileName: String): Boolean =
        settings.excludePatterns.any { pattern ->
            runCatching { Regex(pattern) }
                .onFailure { e ->
                    LOG.warn("Ignoring invalid exclude pattern '$pattern'", e)
                }
                .getOrNull()
                ?.matches(fileName)
                ?: false
        }

    private fun safeReadText(file: VirtualFile): String =
        runCatching { VfsUtilCore.loadText(file) }
            .onFailure { e -> LOG.warn("Could not read ${file.path}", e) }
            .getOrDefault("")

    private companion object {
        private const val DART_EXT = "dart"
        private const val FOLDER_NAME_PATTERN = "{folder_name}.dart"
        private val LOG = Logger.getInstance(BarrelSnapshotScanner::class.java)
        private val EXPORT_REGEX =
            Regex("""^export\s+(['"])([^'"]+)\1;""", RegexOption.MULTILINE)
    }
}
