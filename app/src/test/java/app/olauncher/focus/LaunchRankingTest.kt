package app.olauncher.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchRankingTest {
    private val now = 100 * DAY_MILLIS

    private fun launches(key: String, vararg daysAgo: Double) =
        daysAgo.map { LaunchRecord(key, now - (it * DAY_MILLIS).toLong()) }

    @Test
    fun `aggregate counts only launches inside the window`() {
        val records = launches("a", 1.0, 2.0, 8.0) + launches("b", 0.5)
        val stats = aggregateLaunches(records, windowStart(now, 7))
        assertEquals(2, stats.getValue("a").count)
        assertEquals(now - DAY_MILLIS, stats.getValue("a").lastLaunch)
        assertEquals(1, stats.getValue("b").count)
    }

    @Test
    fun `app with only old launches drops out of the window`() {
        val stats = aggregateLaunches(launches("old", 10.0, 12.0), windowStart(now, 7))
        assertTrue(stats.isEmpty())
    }

    @Test
    fun `ranks by count descending`() {
        val records = launches("a", 1.0) + launches("b", 1.0, 2.0, 3.0) + launches("c", 1.0, 2.0)
        val ranked = rankTopLaunched(aggregateLaunches(records, 0), limit = 3)
        assertEquals(listOf("b", "c", "a"), ranked)
    }

    @Test
    fun `ties break on most recent launch then key`() {
        val records = launches("a", 3.0) + launches("b", 1.0) + launches("c", 3.0)
        val ranked = rankTopLaunched(aggregateLaunches(records, 0), limit = 3)
        assertEquals(listOf("b", "a", "c"), ranked)
    }

    @Test
    fun `excluded apps are skipped and do not consume slots`() {
        val records = launches("fav", 1.0, 1.0, 1.0) + launches("a", 1.0, 2.0) + launches("b", 1.0)
        val ranked = rankTopLaunched(aggregateLaunches(records, 0), limit = 2, exclude = setOf("fav"))
        assertEquals(listOf("a", "b"), ranked)
    }

    @Test
    fun `limit caps the result and zero means off`() {
        val stats = aggregateLaunches(launches("a", 1.0) + launches("b", 1.0) + launches("c", 1.0), 0)
        assertEquals(2, rankTopLaunched(stats, limit = 2).size)
        assertTrue(rankTopLaunched(stats, limit = 0).isEmpty())
    }

    @Test
    fun `window is clamped to retention`() {
        assertEquals(now - LAUNCH_RETENTION_DAYS * DAY_MILLIS, windowStart(now, 365))
        assertEquals(now - DAY_MILLIS, windowStart(now, 0))
    }

    @Test
    fun `app key matches upstream hidden-apps format`() {
        assertEquals("com.example|UserHandle{0}", appKey("com.example", "UserHandle{0}"))
    }
}
