package app.olauncher.ui

import android.content.Context
import android.os.CountDownTimer
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.databinding.DialogBaseBinding
import app.olauncher.focus.FrictionDecision
import app.olauncher.helper.OlDialog

/**
 * The pause before a flagged app opens: a countdown, optionally a typed reason, then "Open".
 * The reason is never stored; typing it is the point.
 */
object FrictionDialog {

    fun show(context: Context, app: AppModel, challenge: FrictionDecision.Challenge, onOpen: () -> Unit): OlDialog {
        val dialog = OlDialog(context)
        val binding = DialogBaseBinding.inflate(LayoutInflater.from(dialog.context))
        binding.tvTitle.text = context.getString(R.string.friction_title, app.appLabel)
        binding.tvAction.setText(R.string.friction_open)
        binding.tvMessage.isVisible = true

        val reason = if (challenge.askWhy) EditText(dialog.context).apply {
            hint = context.getString(R.string.friction_why)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 3
        } else null
        reason?.let {
            binding.contentContainer.addView(it)
            binding.contentContainer.isVisible = true
        }

        var secondsLeft = challenge.delaySeconds
        fun refresh() {
            val waiting = secondsLeft > 0
            val reasonMissing = reason != null && reason.text.isNullOrBlank()
            binding.tvMessage.text = buildString {
                append(
                    if (waiting) context.getString(R.string.friction_wait, secondsLeft)
                    else context.getString(R.string.friction_ready)
                )
                challenge.launchesLeftToday?.let {
                    append("\n")
                    append(context.resources.getQuantityString(R.plurals.friction_left_today, it, it))
                }
            }
            val enabled = !waiting && !reasonMissing
            binding.tvAction.isEnabled = enabled
            binding.tvAction.alpha = if (enabled) 1f else 0.35f
        }
        reason?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = refresh()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        val timer = object : CountDownTimer(challenge.delaySeconds * 1000L, 250L) {
            override fun onTick(millisUntilFinished: Long) {
                val next = ((millisUntilFinished + 999) / 1000).toInt()
                if (next != secondsLeft) {
                    secondsLeft = next
                    refresh()
                }
            }

            override fun onFinish() {
                secondsLeft = 0
                refresh()
            }
        }

        dialog.setView(binding.root)
        binding.ivClose.setOnClickListener { dialog.dismiss() }
        binding.tvAction.setOnClickListener {
            if (!binding.tvAction.isEnabled) return@setOnClickListener
            dialog.dismiss()
            onOpen()
        }
        // Leaving the dialog (back, outside tap, home) abandons the launch and the countdown
        dialog.setOnDismissListener { timer.cancel() }
        refresh()
        dialog.showRespectingStatusBar()
        if (challenge.delaySeconds > 0) timer.start()
        return dialog
    }
}
