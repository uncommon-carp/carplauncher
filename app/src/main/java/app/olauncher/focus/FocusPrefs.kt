package app.olauncher.focus

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.data.Prefs

/**
 * Settings for the focus features. Same pattern as upstream [Prefs], kept in its own
 * file so upstream changes to Prefs merge cleanly.
 */
class FocusPrefs(context: Context) {
    private val PREFS_FILENAME = "app.olauncher.focus"

    private val FREQUENT_APPS_NUM = "FREQUENT_APPS_NUM"
    private val FREQUENT_APPS_WINDOW_DAYS = "FREQUENT_APPS_WINDOW_DAYS"
    private val AUTO_LAUNCH_SINGLE_MATCH = "AUTO_LAUNCH_SINGLE_MATCH"

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
}

/** Keys of the visible home favorites (apps only; pinned shortcuts aren't ranked). */
fun Prefs.homeAppKeys(): Set<String> = (1..homeAppsNum)
    .filter { !getIsShortcut(it) && getAppPackage(it).isNotEmpty() }
    .map { appKey(getAppPackage(it), getAppUser(it)) }
    .toSet()
