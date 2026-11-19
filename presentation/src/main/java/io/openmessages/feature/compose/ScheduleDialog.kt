/*
 * Copyright (C) 2026 OpenMessages contributors
 *
 * This file is part of Open Messages.
 *
 * Open Messages is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open Messages is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open Messages.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.openmessages.feature.compose

import android.app.Activity
import android.text.format.DateFormat
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import io.openmessages.R
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.common.util.extensions.setTint
import io.openmessages.common.util.extensions.styleDialogActions
import io.openmessages.common.util.extensions.withAlpha
import io.openmessages.databinding.ScheduleDialogBinding
import io.openmessages.databinding.ScheduleOptionItemBinding
import io.openmessages.databinding.ScheduleTimeStepperBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Asks when a message should go, in [accent]: a few times at hand, or any day on a calendar and
 * any time to the five minutes. [onPicked] is told the time picked, in milliseconds.
 */
class ScheduleDialog(
    context: Activity,
    private val colors: Colors,
    private val accent: Int,
    private val onPicked: (Long) -> Unit
) : AlertDialog(context) {

    private val layout = ScheduleDialogBinding.inflate(LayoutInflater.from(context))
    private val is24Hour = DateFormat.is24HourFormat(context)
    private val timeFormat = DateFormat.getTimeFormat(context)
    private val monthFormat = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "LLLLyyyy"), Locale.getDefault())

    /** The time set on the calendar's page, its date aside */
    private var hour = 0
    private var minute = 0

    init {
        setView(layout.root)

        val now = Calendar.getInstance()
        val inAnHour = (now.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 1) }
        addOption(R.drawable.ic_av_timer_black_24dp, R.string.compose_schedule_in_hour, inAnHour)
        if (now.get(Calendar.HOUR_OF_DAY) < EVENING_HOUR - 1) {
            addOption(R.drawable.ic_schedule_black_24dp, R.string.compose_schedule_tonight, at(0, EVENING_HOUR))
        }
        addOption(R.drawable.ic_event_black_24dp, R.string.compose_schedule_tomorrow_morning, at(1, MORNING_HOUR))
        addOption(R.drawable.ic_event_black_24dp, R.string.compose_schedule_tomorrow_afternoon, at(1, AFTERNOON_HOUR))
        addOption(R.drawable.ic_calendar_event, R.string.compose_schedule_custom, null)

        // The calendar's page opens on the next five minutes past an hour from now
        inAnHour.add(Calendar.MINUTE, (MINUTE_STEP - inAnHour.get(Calendar.MINUTE) % MINUTE_STEP) % MINUTE_STEP)
        hour = inAnHour.get(Calendar.HOUR_OF_DAY)
        minute = inAnHour.get(Calendar.MINUTE)
        layout.calendar.picked.timeInMillis = inAnHour.timeInMillis
        layout.calendar.picked.set(Calendar.HOUR_OF_DAY, 0)
        layout.calendar.picked.set(Calendar.MINUTE, 0)
        layout.calendar.month.timeInMillis = layout.calendar.picked.timeInMillis
        layout.calendar.month.set(Calendar.DAY_OF_MONTH, 1)

        layout.calendar.onDayPicked = { updatePicker() }
        layout.previousMonth.setOnClickListener { layout.calendar.moveMonth(-1); updatePicker() }
        layout.nextMonth.setOnClickListener { layout.calendar.moveMonth(1); updatePicker() }
        bindStepper(layout.hour) { steps -> hour = Math.floorMod(hour + steps, 24) }
        bindStepper(layout.minute) { steps -> minute = Math.floorMod(minute + steps * MINUTE_STEP, 60) }
        layout.amPm.setOnClickListener { hour = (hour + 12) % 24; updatePicker() }

        layout.actions.delete.isVisible = false
        layout.actions.save.setText(R.string.compose_schedule_confirm)
        layout.actions.cancel.setOnClickListener { dismiss() }
        layout.actions.save.setOnClickListener { pick(pickerTime()) }
    }

    override fun onStart() {
        super.onStart()
        val readable = colors.readableOnBackground(context, accent)
        val boxes = accent.withAlpha(BOX_ALPHA)

        layout.options.let { options ->
            (0 until options.childCount).forEach { index ->
                ScheduleOptionItemBinding.bind(options.getChildAt(index)).icon.setTint(readable)
            }
        }
        layout.calendar.setTypeface(layout.month.typeface)
        layout.calendar.setAccent(accent, colors.contentColorOnTheme(accent))
        layout.previousMonth.setTint(readable)
        layout.nextMonth.setTint(readable)
        listOf(layout.hour, layout.minute).forEach { stepper ->
            stepper.up.setTint(readable)
            stepper.down.setTint(readable)
            stepper.value.setBackgroundTint(boxes)
        }
        layout.amPm.setBackgroundTint(boxes)
        layout.amPm.setTextColor(readable)
        colors.styleDialogActions(context, accent, layout.actions)
        updatePicker()
    }

    private fun addOption(@DrawableRes icon: Int, @StringRes label: Int, time: Calendar?) {
        val option = ScheduleOptionItemBinding.inflate(LayoutInflater.from(context), layout.options, true)
        option.icon.setImageResource(icon)
        option.label.setText(label)
        option.time.text = time?.let { timeFormat.format(it.time) }
        option.time.isVisible = time != null
        option.root.setOnClickListener {
            if (time != null) {
                pick(time.timeInMillis)
            } else {
                layout.presets.isVisible = false
                layout.picker.isVisible = true
            }
        }
    }

    private fun bindStepper(stepper: ScheduleTimeStepperBinding, step: (Int) -> Unit) {
        stepper.up.setOnClickListener { step(1); updatePicker() }
        stepper.down.setOnClickListener { step(-1); updatePicker() }
    }

    private fun updatePicker() {
        layout.month.text = monthFormat.format(layout.calendar.month.time)
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        layout.previousMonth.isEnabled = layout.calendar.canGoBack()
        layout.previousMonth.alpha = if (layout.calendar.canGoBack()) 1f else DISABLED_ALPHA

        val shownHour = if (is24Hour) hour else (hour + 11) % 12 + 1
        layout.hour.value.text = String.format(Locale.getDefault(), if (is24Hour) "%02d" else "%d", shownHour)
        layout.minute.value.text = String.format(Locale.getDefault(), "%02d", minute)
        layout.amPm.isVisible = !is24Hour
        layout.amPm.text = DateFormat.format("a", pickerTime())

        // Only a time still to come can be scheduled
        val future = pickerTime() > System.currentTimeMillis()
        layout.actions.save.isEnabled = future
        layout.actions.save.alpha = if (future) 1f else DISABLED_ALPHA
    }

    private fun pickerTime(): Long = (layout.calendar.picked.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }.timeInMillis

    private fun pick(time: Long) {
        onPicked(time)
        dismiss()
    }

    companion object {
        private const val MINUTE_STEP = 5
        private const val MORNING_HOUR = 8
        private const val AFTERNOON_HOUR = 14
        private const val EVENING_HOUR = 20
        private const val BOX_ALPHA = 0x24
        private const val DISABLED_ALPHA = 0.38f

        /** [days] from today, on the hour [hour] */
        private fun at(days: Int, hour: Int): Calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, days)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

}
