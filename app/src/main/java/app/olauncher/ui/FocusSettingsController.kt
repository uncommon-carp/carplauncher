package app.olauncher.ui

import android.app.TimePickerDialog
import android.text.InputType
import android.text.format.DateFormat
import android.view.Menu
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import app.olauncher.R
import app.olauncher.databinding.SettingsFocusBinding
import app.olauncher.focus.DEFAULT_MODE_ID
import app.olauncher.focus.FocusMode
import app.olauncher.focus.FocusPrefs
import app.olauncher.focus.LAUNCH_RETENTION_DAYS
import app.olauncher.focus.ModeManager
import app.olauncher.focus.ModeSchedule
import app.olauncher.helper.dpToPx
import java.text.DateFormatSymbols
import java.util.Calendar
import app.olauncher.helper.showPopupMenu

/** Binds the Focus settings card, keeping SettingsFragment's diff against upstream minimal. */
class FocusSettingsController(
    private val binding: SettingsFocusBinding,
    private val focusPrefs: FocusPrefs,
    private val modeManager: ModeManager,
) {
    private val context get() = binding.root.context

    fun bind() {
        binding.frequentAppsNum.setOnClickListener { showFrequentAppsNumMenu(it) }
        binding.frequentAppsWindow.setOnClickListener { showFrequentAppsWindowMenu(it) }
        binding.modes.setOnClickListener { showModesMenu(it) }
        binding.distractingApps.setOnClickListener { showDistractingApps() }
        binding.frictionDelay.setOnClickListener { anchor ->
            showChoiceMenu(anchor, listOf(0, 5, 10, 20, 30, 60), ::formatSeconds) { focusPrefs.frictionDelaySeconds = it }
        }
        binding.frictionAskWhy.setOnClickListener {
            focusPrefs.frictionAskWhy = !focusPrefs.frictionAskWhy
            populate()
        }
        binding.frictionDailyCap.setOnClickListener { anchor ->
            showChoiceMenu(anchor, listOf(0, 1, 2, 3, 5, 10), ::formatCap) { focusPrefs.frictionDailyCap = it }
        }
        binding.autoLaunchSingleMatch.setOnClickListener {
            focusPrefs.autoLaunchSingleMatch = !focusPrefs.autoLaunchSingleMatch
            populate()
        }
        populate()
    }

    private fun populate() {
        binding.frequentAppsNum.text = focusPrefs.frequentAppsNum.toString()
        binding.modes.text = modeManager.activeMode.name
        binding.distractingApps.text = focusPrefs.distractingApps.size.toString()
        binding.frictionDelay.text = formatSeconds(focusPrefs.frictionDelaySeconds)
        binding.frictionAskWhy.text = onOff(focusPrefs.frictionAskWhy)
        binding.frictionDailyCap.text = formatCap(focusPrefs.frictionDailyCap)
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

    // Friction

    private fun showChoiceMenu(anchor: View, options: List<Int>, format: (Int) -> String, onPick: (Int) -> Unit) {
        anchor.showPopupMenu(configure = { menu ->
            options.forEachIndexed { i, value -> menu.add(Menu.NONE, i, i, format(value)) }
        }) { item ->
            onPick(options[item.itemId])
            populate()
        }
    }

    private fun formatSeconds(seconds: Int): String =
        if (seconds == 0) context.getString(R.string.off) else context.getString(R.string.n_seconds, seconds)

    private fun formatCap(cap: Int): String = if (cap == 0) context.getString(R.string.off) else cap.toString()

    /** Flagged apps with checkboxes; unticking unflags. Flagging happens from the drawer. */
    private fun showDistractingApps() {
        val keys = focusPrefs.distractingApps.sorted()
        if (keys.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle(R.string.distracting_apps)
                .setMessage(R.string.none_flagged)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val labels = keys.map(::labelFor).toTypedArray()
        val keep = BooleanArray(keys.size) { true }
        AlertDialog.Builder(context)
            .setTitle(R.string.distracting_apps)
            .setMultiChoiceItems(labels, keep) { _, i, on -> keep[i] = on }
            .setPositiveButton(R.string.save) { _, _ ->
                focusPrefs.distractingApps = keys.filterIndexed { i, _ -> keep[i] }.toSet()
                populate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun labelFor(key: String): String {
        val appPackage = key.substringBefore('|')
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(appPackage, 0)).toString()
        } catch (e: Exception) {
            appPackage
        }
    }

    // Modes

    private fun showModesMenu(anchor: View) {
        val modes = modeManager.modes
        val activeId = modeManager.activeMode.id
        anchor.showPopupMenu(configure = { menu ->
            modes.forEachIndexed { i, mode ->
                menu.add(Menu.NONE, i, i, if (mode.id == activeId) "• ${mode.name}" else mode.name)
            }
            menu.add(Menu.NONE, ADD_MODE, modes.size, context.getString(R.string.add_mode))
        }) { item ->
            if (item.itemId == ADD_MODE) promptName(R.string.add_mode, "") { modeManager.addMode(it); populate() }
            else modes.getOrNull(item.itemId)?.let { showModeActions(anchor, it) }
        }
    }

    private fun showModeActions(anchor: View, mode: FocusMode) {
        anchor.showPopupMenu(configure = { menu ->
            menu.add(Menu.NONE, 0, 0, describe(mode)).isEnabled = false
            menu.add(Menu.NONE, 1, 1, context.getString(R.string.switch_to_mode))
            menu.add(Menu.NONE, 2, 2, context.getString(R.string.rename))
            menu.add(Menu.NONE, 3, 3, context.getString(R.string.schedule))
            if (mode.id != DEFAULT_MODE_ID) menu.add(Menu.NONE, 4, 4, context.getString(R.string.delete_mode))
        }) { item ->
            when (item.itemId) {
                1 -> modeManager.switchManually(mode.id)
                2 -> promptName(R.string.rename, mode.name) { modeManager.renameMode(mode.id, it) }
                3 -> editSchedule(mode)
                4 -> modeManager.deleteMode(mode.id)
            }
            populate()
        }
    }

    private fun promptName(title: Int, initial: String, onName: (String) -> Unit) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint = context.getString(R.string.mode_name)
            setText(initial)
            setSelectAllOnFocus(true)
        }
        val container = FrameLayout(context).apply {
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 0)
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(onName)
                populate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Weekdays, then start time, then end time. "No schedule" clears it. */
    private fun editSchedule(mode: FocusMode) {
        val current = mode.schedule
        val isoDays = (1..7).toList()
        val checked = BooleanArray(7) { current?.days?.contains(isoDays[it]) ?: (it < 5) }
        AlertDialog.Builder(context)
            .setTitle(R.string.schedule_days)
            .setMultiChoiceItems(isoDays.map(::weekdayName).toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton(R.string.next) { _, _ ->
                val days = isoDays.filterIndexed { i, _ -> checked[i] }.toSet()
                pickTime(R.string.schedule_start, current?.startMinute ?: (9 * 60)) { start ->
                    pickTime(R.string.schedule_end, current?.endMinute ?: (17 * 60)) { end ->
                        modeManager.setSchedule(mode.id, ModeSchedule(days, start, end))
                        modeManager.applySchedule()
                        populate()
                    }
                }
            }
            .setNeutralButton(R.string.no_schedule) { _, _ ->
                modeManager.setSchedule(mode.id, null)
                populate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickTime(title: Int, initialMinute: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initialMinute / 60,
            initialMinute % 60,
            DateFormat.is24HourFormat(context),
        ).apply { setTitle(title) }.show()
    }

    private fun describe(mode: FocusMode): String {
        val s = mode.schedule ?: return context.getString(R.string.mode_schedule_none, mode.name)
        val days = (1..7).filter { it in s.days }.joinToString(" ") { weekdayName(it).take(2) }
        return context.getString(R.string.mode_schedule_summary, mode.name, days, formatMinute(s.startMinute), formatMinute(s.endMinute))
    }

    private fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    /** ISO weekday (1 = Monday) to the locale's short name. */
    private fun weekdayName(isoDay: Int): String {
        val calendarDay = if (isoDay == 7) Calendar.SUNDAY else isoDay + 1
        return DateFormatSymbols.getInstance().shortWeekdays[calendarDay]
    }

    private fun onOff(value: Boolean): String = context.getString(if (value) R.string.on else R.string.off)

    private fun formatDays(days: Int): String =
        if (days == 1) context.getString(R.string.one_day) else context.getString(R.string.n_days, days)

    private companion object {
        const val ADD_MODE = 1000
    }
}
