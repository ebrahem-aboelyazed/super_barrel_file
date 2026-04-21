package com.dartbarrel.plugin.services

import java.util.concurrent.ConcurrentHashMap

/**
 * Suppresses immediate reprocessing of a directory right after a manual write.
 */
class RecentGenerationTracker(
    private val quietPeriodMillis: Long = DEFAULT_QUIET_PERIOD_MILLIS,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {

    private val generatedAtByPath = ConcurrentHashMap<String, Long>()

    /**
     * Marks the given directory path as freshly generated.
     */
    fun mark(path: String) {
        generatedAtByPath[path] = currentTimeMillis()
    }

    /**
     * Returns whether the path was generated within the quiet period.
     */
    fun wasRecentlyGenerated(path: String): Boolean {
        val now = currentTimeMillis()
        pruneExpired(now)
        val generatedAt = generatedAtByPath[path] ?: return false
        return now - generatedAt <= quietPeriodMillis
    }

    private fun pruneExpired(now: Long) {
        generatedAtByPath.entries.removeIf { (_, generatedAt) ->
            now - generatedAt > quietPeriodMillis
        }
    }

    private companion object {
        private const val DEFAULT_QUIET_PERIOD_MILLIS = 1500L
    }
}

