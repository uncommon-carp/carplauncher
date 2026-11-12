package app.olauncher.focus

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.data.Prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings for the focus features. Same pattern as upstream [Prefs], kept in its own
 * file so upstream changes to Prefs merge cleanly.
 */
class FocusPrefs(context: Context) {
    private val PREFS_FILENAME = "app.olauncher.focus"

    private val FREQUENT_APPS_NUM = "FREQUENT_APPS_NUM"
    private val FREQUENT_APPS_WINDOW_DAYS = "FREQUENT_APPS_WINDOW_DAYS"
    private val AUTO_LAUNCH_SINGLE_MATCH = "AUTO_LAUNCH_SINGLE_MATCH"
    private val MODES = "MODES"
    private val ACTIVE_MODE_ID = "ACTIVE_MODE_ID"
    private val MODE_OVERRIDE_ID = "MODE_OVERRIDE_ID"
    private val MODE_OVERRIDE_AT = "MODE_OVERRIDE_AT"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)

    var frequentAppsNum: Int
        get() = prefs.getInt(FREQUENT_APPS_NUM, 4)
        set(value) = prefs.edit { putInt(FREQUENT_APPS_NUM, value) }

    var frequentAppsWindowDays: Int
        get() = prefs.getInt(FREQUENT_APPS_WINDOW_DAYS, 7)
        set(value) = prefs.edit { putInt(FREQUENT_APPS_WINDOW_DAYS, value) }

    /** Upstream always auto-launched a single search match; default stays on. */
    var autoLaunchSingleMatch: Boolean
        get() = prefs.getBoolean(AUTO_LAUNCH_SINGLE_MATCH, true)
        set(value) = prefs.edit { putBoolean(AUTO_LAUNCH_SINGLE_MATCH, value) }

    /** Always starts with the default mode, which holds the pre-existing favorites. */
    var modes: List<FocusMode>
        get() {
            val stored = try {
                decodeModes(prefs.getString(MODES, null))
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
            val default = stored.firstOrNull { it.id == DEFAULT_MODE_ID } ?: FocusMode(DEFAULT_MODE_ID, "Default")
            return listOf(default) + stored.filter { it.id != DEFAULT_MODE_ID }
        }
        set(value) = prefs.edit { putString(MODES, encodeModes(value)) }

    /** The mode whose favorites are currently in upstream's home slots. */
    var activeModeId: String
        get() = prefs.getString(ACTIVE_MODE_ID, DEFAULT_MODE_ID) ?: DEFAULT_MODE_ID
        set(value) = prefs.edit { putString(ACTIVE_MODE_ID, value) }

    var modeOverride: ModeOverride?
        get() = prefs.getString(MODE_OVERRIDE_ID, null)?.let { ModeOverride(it, prefs.getLong(MODE_OVERRIDE_AT, 0L)) }
        set(value) = prefs.edit {
            if (value == null) {
                remove(MODE_OVERRIDE_ID)
                remove(MODE_OVERRIDE_AT)
            } else {
                putString(MODE_OVERRIDE_ID, value.modeId)
                putLong(MODE_OVERRIDE_AT, value.setAtLocalMinutes)
            }
        }

    private fun encodeModes(modes: List<FocusMode>): String = JSONArray().apply {
        modes.forEach { mode ->
            put(JSONObject().apply {
                put("id", mode.id)
                put("name", mode.name)
                mode.schedule?.let {
                    put("schedule", JSONObject().apply {
                        put("days", JSONArray(it.days.sorted()))
                        put("start", it.startMinute)
                        put("end", it.endMinute)
                    })
                }
            })
        }
    }.toString()

    private fun decodeModes(json: String?): List<FocusMode> {
        if (json.isNullOrBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val schedule = o.optJSONObject("schedule")?.let { s ->
                val days = s.getJSONArray("days")
                ModeSchedule(
                    days = (0 until days.length()).map { days.getInt(it) }.toSet(),
                    startMinute = s.getInt("start"),
                    endMinute = s.getInt("end"),
                )
            }
            FocusMode(o.getString("id"), o.getString("name"), schedule)
        }
    }
}

/** Keys of the visible home favorites (apps only; pinned shortcuts aren't ranked). */
fun Prefs.homeAppKeys(): Set<String> = (1..homeAppsNum)
    .filter { !getIsShortcut(it) && getAppPackage(it).isNotEmpty() }
    .map { appKey(getAppPackage(it), getAppUser(it)) }
    .toSet()
