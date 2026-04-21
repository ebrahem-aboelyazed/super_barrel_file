package com.dartbarrel.plugin.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentGenerationTrackerTest {

    @Test
    fun marksPathOnlyWithinQuietPeriod() {
        var now = 1_000L
        val tracker = RecentGenerationTracker(
            quietPeriodMillis = 500L,
            currentTimeMillis = { now },
        )

        tracker.mark("/tmp/lib/feature")

        assertTrue(tracker.wasRecentlyGenerated("/tmp/lib/feature"))

        now = 1_501L

        assertFalse(tracker.wasRecentlyGenerated("/tmp/lib/feature"))
    }
}

