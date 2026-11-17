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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import io.openmessages.common.util.extensions.resolveThemeColor

/**
 * The initials of a list down its side, as in AukDialer: the one in view is set in a disc of
 * [accent], and touching or dragging along the rail reports the initial under the finger.
 */
class LetterRailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var letters: List<String> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    /** The initial of what is in view */
    var selected: String? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var accent: Int = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    /** The colour of the selected initial, on [accent] */
    var onAccent: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    /** Told the initial under the finger whenever it changes, then null once the finger lifts */
    var onLetterTouched: ((String?) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val maxCell = 18 * density
    private val padding = 8 * density

    private val textColor = ColorUtils.setAlphaComponent(
        context.resolveThemeColor(android.R.attr.textColorSecondary), 0xCC)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10 * resources.displayMetrics.scaledDensity
    }

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var touched: String? = null

    /** The height of an initial's cell: [maxCell], tighter when the rail has less room than that */
    private val cell: Float
        get() = if (letters.isEmpty()) maxCell else minOf(maxCell, (height - 2 * padding) / letters.size)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (24 * density).toInt()
        val height = (letters.size * maxCell + 2 * padding).toInt()
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val top = (height - letters.size * cell) / 2
        letters.forEachIndexed { index, letter ->
            val centreX = width / 2f
            val centreY = top + index * cell + cell / 2
            val isSelected = letter == selected
            if (isSelected) {
                discPaint.color = accent
                canvas.drawCircle(centreX, centreY, minOf(cell, maxCell) / 2, discPaint)
            }
            textPaint.color = if (isSelected) onAccent else textColor
            textPaint.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(letter, centreX, centreY - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (letters.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val top = (height - letters.size * cell) / 2
                val letter = letters[((event.y - top) / cell).toInt().coerceIn(0, letters.lastIndex)]
                if (letter != touched) {
                    touched = letter
                    onLetterTouched?.invoke(letter)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touched = null
                onLetterTouched?.invoke(null)
            }
        }
        return true
    }

}
