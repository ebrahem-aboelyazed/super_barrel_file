package com.dartbarrel.plugin.services

import com.dartbarrel.plugin.settings.DartBarrelSettings
import com.dartbarrel.plugin.utils.DartFileUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile

class BarrelContentBuilder(
    private val settings: DartBarrelSettings,
) {

    data class ExportableItem(
        val file: PsiFile,
        val isSubBarrel: Boolean,
    )

    fun resolveExportableItems(
        rootDirectory: PsiDirectory,
    ): List<ExportableItem> {
        val barrelFileName = resolveBarrelFileName(rootDirectory)
        val subBarrels = findSubBarrelFiles(rootDirectory)
        val coveredPaths = collectCoveredPaths(subBarrels)
        val rootBarrelPath = buildRootBarrelPath(
            rootDirectory, barrelFileName,
        )

        val allDartFiles =
            DartFileUtils.getAllDartFilesRecursively(rootDirectory)

        val subBarrelPaths = subBarrels.mapNotNull {
            it.virtualFile?.path
        }.toSet()

        val directFiles = allDartFiles
            .asSequence()
            .filter { it.isValid }
            .filter { (it.virtualFile?.path ?: "") != rootBarrelPath }
            .filter { !DartFileUtils.isPartFile(it) }
            .filter { file ->
                val path = file.virtualFile?.path ?: ""
                path !in coveredPaths && path !in subBarrelPaths
            }
            .map { ExportableItem(it, isSubBarrel = false) }
            .toList()

        val barrelItems = subBarrels.map {
            ExportableItem(it, isSubBarrel = true)
        }

        return (directFiles + barrelItems).sortedBy {
            it.file.virtualFile?.path ?: ""
        }
    }

    fun build(
        dartFiles: List<PsiFile>,
        rootDirectory: PsiDirectory,
    ): String {
        val barrelFileName = resolveBarrelFileName(rootDirectory)
        val subBarrels = findSubBarrelFiles(rootDirectory)
        val coveredPaths = collectCoveredPaths(subBarrels)
        val rootBarrelPath = buildRootBarrelPath(
            rootDirectory, barrelFileName,
        )

        val fileExports = dartFiles
            .asSequence()
            .filter { it.isValid }
            .filter { (it.virtualFile?.path ?: "") != rootBarrelPath }
            .filter { !DartFileUtils.isPartFile(it) }
            .filter { file ->
                val path = file.virtualFile?.path ?: ""
                path !in coveredPaths
            }
            .mapNotNull { buildExportStatement(rootDirectory, it) }
            .toList()

        val subBarrelExports = subBarrels.mapNotNull {
            buildExportStatement(rootDirectory, it)
        }

        val allExports = (fileExports + subBarrelExports)
            .filter { it.isNotBlank() }
            .sorted()
            .distinct()

        return if (allExports.isNotEmpty()) {
            allExports.joinToString("\n") + "\n"
        } else {
            ""
        }
    }

    fun buildFromSelection(
        selectedFiles: List<PsiFile>,
        rootDirectory: PsiDirectory,
    ): String {
        val exports = selectedFiles
            .filter { it.isValid }
            .mapNotNull { buildExportStatement(rootDirectory, it) }
            .filter { it.isNotBlank() }
            .sorted()
            .distinct()

        return if (exports.isNotEmpty()) {
            exports.joinToString("\n") + "\n"
        } else {
            ""
        }
    }

    fun resolveBarrelFileName(
        directory: PsiDirectory,
    ): String {
        return when (settings.barrelFileName) {
            "{folder_name}.dart" -> "${directory.name}.dart"
            "index.dart" -> "index.dart"
            else -> settings.barrelFileName
        }
    }

    private fun findSubBarrelFiles(
        rootDirectory: PsiDirectory,
    ): List<PsiFile> {
        val barrels = mutableListOf<PsiFile>()

        fun collectBarrels(dir: PsiDirectory) {
            val barrelName = resolveBarrelFileName(dir)
            dir.files
                .find { it.name == barrelName }
                ?.let { barrels.add(it) }
            dir.subdirectories.forEach { collectBarrels(it) }
        }

        rootDirectory.subdirectories.forEach {
            collectBarrels(it)
        }
        return barrels
    }

    private fun collectCoveredPaths(
        subBarrels: List<PsiFile>,
    ): Set<String> {
        return subBarrels.flatMap { barrel ->
            parseCoveredFiles(barrel)
        }.toSet()
    }

    private fun parseCoveredFiles(
        barrelFile: PsiFile,
    ): Set<String> {
        val dir =
            barrelFile.containingDirectory ?: return emptySet()
        val dirPath = dir.virtualFile.path
        val text = barrelFile.text ?: return emptySet()

        return text.lines()
            .asSequence()
            .map { it.trim() }
            .filter {
                it.startsWith("export '") && it.endsWith("';")
            }
            .mapNotNull { line ->
                val relativePath = line
                    .removePrefix("export '")
                    .removeSuffix("';")
                java.io.File(dirPath, relativePath)
                    .canonicalPath
            }
            .toSet()
    }

    private fun buildExportStatement(
        rootDirectory: PsiDirectory,
        file: PsiFile,
    ): String? {
        val virtualFile = file.virtualFile
        if (virtualFile == null || !virtualFile.isValid) {
            LOG.warn(
                "Skipping file with invalid " +
                    "VirtualFile: ${file.name}"
            )
            return null
        }
        return try {
            val relativePath =
                calculateRelativePath(rootDirectory, file)
            if (relativePath.isBlank()) {
                LOG.warn(
                    "Empty relative path for: ${file.name}"
                )
                null
            } else {
                "export '$relativePath';"
            }
        } catch (e: Exception) {
            LOG.warn(
                "Failed to calculate relative path " +
                    "for: ${file.name}",
                e,
            )
            null
        }
    }

    private fun calculateRelativePath(
        rootDirectory: PsiDirectory,
        file: PsiFile,
    ): String {
        val rootVf = rootDirectory.virtualFile
        val fileVf = file.virtualFile

        requireNotNull(fileVf) {
            "File VirtualFile is null for ${file.name}"
        }

        val rootPath = rootVf.path
        val filePath = fileVf.path

        if (!filePath.startsWith(rootPath)) {
            LOG.warn(
                "File '$filePath' is not under " +
                    "root '$rootPath'"
            )
            return fileVf.name
        }

        return filePath
            .removePrefix(rootPath)
            .removePrefix("/")
            .replace("\\", "/")
    }

    private fun buildRootBarrelPath(
        rootDirectory: PsiDirectory,
        barrelFileName: String,
    ): String {
        return "${rootDirectory.virtualFile.path}/$barrelFileName"
    }

    companion object {
        private val LOG = Logger.getInstance(
            BarrelContentBuilder::class.java,
        )
    }
}

