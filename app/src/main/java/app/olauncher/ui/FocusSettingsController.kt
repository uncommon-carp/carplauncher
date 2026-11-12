package app.olauncher.ui

import android.view.Menu
import android.view.View
import app.olauncher.R
import app.olauncher.databinding.SettingsFocusBinding
import app.olauncher.focus.FocusPrefs
import app.olauncher.focus.LAUNCH_RETENTION_DAYS
import app.olauncher.helper.showPopupMenu

/** Binds the Focus settings card, keeping SettingsFragment's diff against upstream minimal. */
class FocusSettingsController(
    private val binding: SettingsFocusBinding,
    private val focusPrefs: FocusPrefs,
) {
    private val context get() = binding.root.context

    fun bind() {
        binding.frequentAppsNum.setOnClickListener { showFrequentAppsNumMenu(it) }
        binding.frequentAppsWindow.setOnClickListener { showFrequentAppsWindowMenu(it) }
        binding.autoLaunchSingleMatch.setOnClickListener {
            focusPrefs.autoLaunchSingleMatch = !focusPrefs.autoLaunchSingleMatch
            populate()
        }
        populate()
    }

    private fun populate() {
        binding.frequentAppsNum.text = focusPrefs.frequentAppsNum.toString()
        binding.frequentAppsWindow.text = formatDays(focusPrefs.frequentAppsWindowDays)
        binding.autoLaunchSingleMatch.text = onOff(focusPrefs.autoLaunchSingleMatch)
    }

    private fun showFrequentAppsNumMenu(anchor: View) {
        anchor.showPopupMenu(configure = { menu ->
            for (num in 0..8) menu.add(Menu.NONE, num, num, num.toString())
        }) { item ->
            focusPrefs.frequentAppsNum = item.itemId
            populate()
        }
    }

    private fun showFrequentAppsWindowMenu(anchor: View) {
        val options = listOf(1, 3, 7, 14, LAUNCH_RETENTION_DAYS)
        anchor.showPopupMenu(configure = { menu ->
            options.forEachIndexed { i, days -> menu.add(Menu.NONE, days, i, formatDays(days)) }
        }) { item ->
            focusPrefs.frequentAppsWindowDays = item.itemId
            populate()
        }
    }

    private fun onOff(value: Boolean): String = context.getString(if (value) R.string.on else R.string.off)

    private fun formatDays(days: Int): String =
        if (days == 1) context.getString(R.string.one_day) else context.getString(R.string.n_days, days)
}
