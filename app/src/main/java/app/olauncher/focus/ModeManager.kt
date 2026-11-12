package app.olauncher.focus

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.TimeZone

fun localMinutes(epochMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    Math.floorDiv(epochMillis + zone.getOffset(epochMillis), 60_000L)

/**
 * Contextual favorites. Upstream's 8 home slots always hold the *active* mode's favorites, so the
 * home screen and app picker keep working unchanged; switching modes snapshots the live slots
 * under the outgoing mode and restores the incoming mode's snapshot.
 */
class ModeManager(context: Context) {
    private val focusPrefs = FocusPrefs(context)
    private val upstream: SharedPreferences = context.getSharedPreferences(UPSTREAM_PREFS, 0)
    private val snapshots: SharedPreferences = context.getSharedPreferences(SNAPSHOT_PREFS, 0)

    val modes: List<FocusMode> get() = focusPrefs.modes

    val activeMode: FocusMode
        get() = modes.firstOrNull { it.id == focusPrefs.activeModeId } ?: modes.first()

    /** Applies the schedule or a still-valid manual override. Returns true if favorites changed. */
    fun applySchedule(now: Long = System.currentTimeMillis()): Boolean {
        val override = focusPrefs.modeOverride
        val resolution = resolveMode(modes, localMinutes(now), override)
        if (override != null && !resolution.overrideActive) focusPrefs.modeOverride = null
        return activate(resolution.modeId)
    }

    fun switchManually(modeId: String, now: Long = System.currentTimeMillis()): Boolean {
        focusPrefs.modeOverride = ModeOverride(modeId, localMinutes(now))
        return activate(modeId)
    }

    /** New modes start as a copy of the current favorites. */
    fun addMode(name: String): FocusMode {
        val mode = FocusMode("m${System.currentTimeMillis()}", name)
        saveSlots(mode.id)
        focusPrefs.modes = modes + mode
        return mode
    }

    fun renameMode(modeId: String, name: String) {
        focusPrefs.modes = modes.map { if (it.id == modeId) it.copy(name = name) else it }
    }

    fun setSchedule(modeId: String, schedule: ModeSchedule?) {
        focusPrefs.modes = modes.map { if (it.id == modeId) it.copy(schedule = schedule) else it }
    }

    fun deleteMode(modeId: String) {
        if (modeId == DEFAULT_MODE_ID) return
        if (focusPrefs.activeModeId == modeId) {
            focusPrefs.modeOverride = null
            activate(DEFAULT_MODE_ID)
        }
        snapshots.edit { SLOT_KEYS.forEach { remove(snapshotKey(modeId, it)) } }
        focusPrefs.modes = modes.filterNot { it.id == modeId }
    }

    private fun activate(modeId: String): Boolean {
        val current = focusPrefs.activeModeId
        if (current == modeId) return false
        saveSlots(current)
        loadSlots(modeId)
        focusPrefs.activeModeId = modeId
        return true
    }

    private fun saveSlots(modeId: String) {
        val live = upstream.all
        snapshots.edit {
            for (key in SLOT_KEYS) {
                val snapshotKey = snapshotKey(modeId, key)
                when (val value = live[key]) {
                    is String -> putString(snapshotKey, value)
                    is Boolean -> putBoolean(snapshotKey, value)
                    is Int -> putInt(snapshotKey, value)
                    else -> remove(snapshotKey)
                }
            }
        }
    }

    private fun loadSlots(modeId: String) {
        val saved = snapshots.all
        // A mode never saved before (e.g. Default on first switch) keeps the live favorites
        if (SLOT_KEYS.none { snapshotKey(modeId, it) in saved }) return
        upstream.edit {
            for (key in SLOT_KEYS) {
                when (val value = saved[snapshotKey(modeId, key)]) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    else -> remove(key)
                }
            }
        }
    }

    private fun snapshotKey(modeId: String, key: String) = "MODE_${modeId}_$key"

    companion object {
        private const val UPSTREAM_PREFS = "app.olauncher"
        private const val SNAPSHOT_PREFS = "app.olauncher.focus.modes"

        /** Upstream Prefs keys that make up the home favorites. Must track upstream renames. */
        private val SLOT_KEYS: List<String> = listOf("HOME_APPS_NUM") + (1..8).flatMap { i ->
            listOf(
                "APP_NAME_$i", "APP_PACKAGE_$i", "APP_ACTIVITY_CLASS_NAME_$i",
                "APP_USER_$i", "IS_SHORTCUT_$i", "SHORTCUT_ID_$i",
            )
        }
    }
}
