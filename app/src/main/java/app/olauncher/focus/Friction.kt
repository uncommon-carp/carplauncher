package app.olauncher.focus

import java.util.Calendar
import java.util.TimeZone

data class FrictionSettings(
    val delaySeconds: Int = 10,
    val askWhy: Boolean = false,
    /** Successful launches allowed per local day; 0 means no cap. */
    val dailyCap: Int = 0,
)

sealed interface FrictionDecision {
    data object Allow : FrictionDecision

    /** Launch only after a [delaySeconds] countdown and, if [askWhy], a typed reason. */
    data class Challenge(val delaySeconds: Int, val askWhy: Boolean, val launchesLeftToday: Int?) : FrictionDecision

    data class Refuse(val dailyCap: Int) : FrictionDecision
}

/** What to do with a launch from the launcher, given how often the app was launched today. */
fun frictionDecision(isDistracting: Boolean, settings: FrictionSettings, launchesToday: Int): FrictionDecision {
    if (!isDistracting) return FrictionDecision.Allow
    val cap = settings.dailyCap.coerceAtLeast(0)
    if (cap > 0 && launchesToday >= cap) return FrictionDecision.Refuse(cap)
    val delay = settings.delaySeconds.coerceAtLeast(0)
    if (delay == 0 && !settings.askWhy) return FrictionDecision.Allow
    // Counting this launch: after it, cap - launchesToday - 1 remain
    val left = if (cap > 0) cap - launchesToday - 1 else null
    return FrictionDecision.Challenge(delay, settings.askWhy, left)
}

/** Epoch millis of the most recent local midnight at or before [epochMillis] (DST-safe). */
fun startOfLocalDay(epochMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    Calendar.getInstance(zone).apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** A distracting flag covers the app and any pinned shortcuts from the same package and profile. */
fun isDistracting(flagged: Set<String>, appPackage: String, user: String): Boolean =
    appKey(appPackage, user) in flagged
