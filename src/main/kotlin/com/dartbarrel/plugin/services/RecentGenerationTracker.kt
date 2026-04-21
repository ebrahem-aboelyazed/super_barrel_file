package com.dartbarrel.plugin.services

import java.util.concurrent.ConcurrentHashMap

/**
 * Suppresses immediate re-synchronisation of a directory right after a
 * manual write so that the DartFileListener VFS
 * event triggered by write does not re-enter the generation pipeline.
 *
 * All methods are thread-safe.
 */
class RecentGenerationTracker(
    private val quietPeriodMillis: Long = DEFAULT_QUIET_PERIOD_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val timestamps = ConcurrentHashMap<String, Long>()

    /** Records that [directoryPath] was just generated. */
    fun mark(directoryPath: String) {
        timestamps[directoryPath] = clock()
    }

    /**
     * Returns `true` when [directoryPath] was generated within the quiet
     * period.  Expired entries are pruned lazily on every check.
     */
    fun wasRecentlyGenerated(directoryPath: String): Boolean {
        val now = clock()
        pruneExpired(now)
        val ts = timestamps[directoryPath] ?: return false
        return now - ts <= quietPeriodMillis
    }

    private fun pruneExpired(now: Long) {
        timestamps.entries.removeIf { (_, ts) -> now - ts > quietPeriodMillis }
    }

    private companion object {
        private const val DEFAULT_QUIET_PERIOD_MILLIS = 2_000L
    }
}
