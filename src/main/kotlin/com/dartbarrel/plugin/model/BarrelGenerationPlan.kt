package com.dartbarrel.plugin.model

/**
 * Immutable snapshot used by the generation dialog and write pipeline.
 */
data class BarrelGenerationPlan(
    val barrelFileName: String,
    val candidates: List<BarrelExportCandidate>,
)

/**
 * Export candidate resolved from the current directory snapshot.
 */
data class BarrelExportCandidate(
    val relativePath: String,
    val isNestedBarrel: Boolean,
)

