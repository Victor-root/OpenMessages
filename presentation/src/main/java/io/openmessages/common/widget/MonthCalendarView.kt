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
package io.openmessages.common.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import io.openmessages.common.util.extensions.dpToPx
import io.openmessages.common.util.extensions.resolveThemeColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * A month of days to pick one from, drawn in a colour given at run time, which the platform's and
 * Material's calendars can only take from a theme. Days before [firstDay] are greyed and can't be
 * picked; today is ringed and the picked day filled.
 */
class MonthCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var accent = 0
    private var onAccent = 0

    /** Told the day picked, as a calendar at midnight that day */
    var onDayPicked: ((Calendar) -> Unit)? = null

    /** The first of the month shown */
    val month: Calendar = startOfDay(Calendar.getInstance()).apply { set(Calendar.DAY_OF_MONTH, 1) }

    /** The day picked, at midnight */
    val picked: Calendar = startOfDay(Calendar.getInstance())

    /** The first day that can be picked, at midnight: today */
    private val firstDay: Calendar = startOfDay(Calendar.getInstance())

    private val firstDayOfWeek = Calendar.getInstance().firstDayOfWeek
    private val cell = 40.dpToPx(context)
    private val headerHeight = 32.dpToPx(context)

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)
    }
    private val headerPaint = Paint(dayPaint).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }
    private var regular: Typeface = Typeface.DEFAULT
    private var bold: Typeface = Typeface.DEFAULT_BOLD
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }

    private val textPrimary = context.resolveThemeColor(android.R.attr.textColorPrimary)
    private val textSecondary = context.resolveThemeColor(android.R.attr.textColorSecondary)
    private val textTertiary = context.resolveThemeColor(android.R.attr.textColorTertiary)

    /** The weekdays' initials, in the order the locale starts its weeks */
    private val weekdayInitials: List<String> = SimpleDateFormat("EEEEE", Locale.getDefault()).let { format ->
        val day = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }
        (0 until 7).map { format.format(day.time).also { day.add(Calendar.DAY_OF_YEAR, 1) } }
    }

    /** The face of the days and weekdays, the app's text one as given by a text beside them */
    fun setTypeface(typeface: Typeface?) {
        regular = Typeface.create(typeface, Typeface.NORMAL)
        bold = Typeface.create(typeface, Typeface.BOLD)
        headerPaint.typeface = regular
        invalidate()
    }

    /** Fills the picked day and rings today in [accent], the picked day's number in [onAccent] */
    fun setAccent(accent: Int, onAccent: Int) {
        this.accent = accent
        this.onAccent = onAccent
        invalidate()
    }

    /** Shows the month [months] away from the one shown, never one before the first day */
    fun moveMonth(months: Int) {
        val moved = (month.clone() as Calendar).apply { add(Calendar.MONTH, months) }
        val firstMonth = (firstDay.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        if (moved.before(firstMonth)) return
        month.timeInMillis = moved.timeInMillis
        invalidate()
    }

    /** Whether there is a month to go back to */
    fun canGoBack(): Boolean = month.get(Calendar.YEAR) * 12 + month.get(Calendar.MONTH) >
            firstDay.get(Calendar.YEAR) * 12 + firstDay.get(Calendar.MONTH)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // Always six weeks tall, so the dialog doesn't change height from one month to the next
        setMeasuredDimension(width, headerHeight + WEEKS * cell)
    }

    override fun onDraw(canvas: Canvas) {
        val columnWidth = width / 7f
        val metrics = dayPaint.fontMetrics
        val textOffset = -(metrics.ascent + metrics.descent) / 2

        headerPaint.color = textSecondary
        weekdayInitials.forEachIndexed { column, initial ->
            canvas.drawText(initial, columnWidth * (column + 0.5f), headerHeight / 2f + textOffset, headerPaint)
        }

        val day = (month.clone() as Calendar)
        val lead = (day.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        val days = day.getActualMaximum(Calendar.DAY_OF_MONTH)
        val radius = cell * 0.42f

        for (date in 1..days) {
            day.set(Calendar.DAY_OF_MONTH, date)
            val index = lead + date - 1
            val cx = columnWidth * (index % 7 + 0.5f)
            val cy = headerHeight + cell * (index / 7 + 0.5f)

            val isPicked = sameDay(day, picked)
            val isToday = sameDay(day, firstDay)
            when {
                isPicked -> {
                    fillPaint.color = accent
                    canvas.drawCircle(cx, cy, radius, fillPaint)
                }
                isToday -> {
                    ringPaint.color = accent
                    canvas.drawCircle(cx, cy, radius, ringPaint)
                }
            }

            dayPaint.typeface = if (isPicked || isToday) bold else regular
            dayPaint.color = when {
                isPicked -> onAccent
                day.before(firstDay) -> textTertiary
                else -> textPrimary
            }
            canvas.drawText(date.toString(), cx, cy + textOffset, dayPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) return true
        if (event.action != MotionEvent.ACTION_UP) return super.onTouchEvent(event)

        val row = ((event.y - headerHeight) / cell).toInt()
        val column = (event.x / (width / 7f)).toInt().coerceIn(0, 6)
        if (event.y < headerHeight || row !in 0 until WEEKS) return true

        val lead = (month.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        val date = row * 7 + column - lead + 1
        if (date !in 1..month.getActualMaximum(Calendar.DAY_OF_MONTH)) return true

        val day = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, date) }
        if (day.before(firstDay)) return true

        picked.timeInMillis = day.timeInMillis
        invalidate()
        performClick()
        onDayPicked?.invoke(day)
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    companion object {
        private const val WEEKS = 6

        private fun startOfDay(calendar: Calendar) = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

}
