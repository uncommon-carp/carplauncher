package app.olauncher.focus

/** Launch events older than this are pruned; it also caps the configurable frequency window. */
const val LAUNCH_RETENTION_DAYS = 30
const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** Same "package|user" format the upstream hidden-apps set uses. */
fun appKey(appPackage: String, user: String): String = "$appPackage|$user"

data class LaunchRecord(val appKey: String, val timestamp: Long)

data class LaunchStats(val count: Int, val lastLaunch: Long)

fun windowStart(now: Long, days: Int): Long = now - days.coerceIn(1, LAUNCH_RETENTION_DAYS) * DAY_MILLIS

/** Counts launches per app at or after [since]. */
fun aggregateLaunches(records: List<LaunchRecord>, since: Long): Map<String, LaunchStats> =
    records.asSequence()
        .filter { it.timestamp >= since }
        .groupBy { it.appKey }
        .mapValues { (_, launches) -> LaunchStats(launches.size, launches.maxOf { it.timestamp }) }

/**
 * App keys ordered most-launched first. Ties go to the most recently launched app,
 * then to key order so the result is stable. Keys in [exclude] are skipped.
 */
fun rankTopLaunched(
    stats: Map<String, LaunchStats>,
    limit: Int,
    exclude: Set<String> = emptySet(),
): List<String> {
    if (limit <= 0) return emptyList()
    return stats.entries
        .filter { it.key !in exclude }
        .sortedWith(
            compareByDescending<Map.Entry<String, LaunchStats>> { it.value.count }
                .thenByDescending { it.value.lastLaunch }
                .thenBy { it.key }
        )
        .take(limit)
        .map { it.key }
}
