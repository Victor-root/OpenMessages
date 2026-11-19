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
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import androidx.core.graphics.drawable.DrawableCompat
import io.openmessages.common.util.extensions.dpToPx
import kotlin.math.ceil
import kotlin.math.max

/**
 * A message bubble's text with its time, and for a sent message an icon for where it stands, in
 * the bubble's bottom corner the way Signal sets them: at the end of the last line when they fit
 * beside it, on a line of their own below the text otherwise.
 *
 * Drawn by the text itself rather than laid out as views next to it, so the bubble keeps its
 * shape, its grouping, its links and its text selection exactly as before.
 */
class MessageBodyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TightTextView(context, attrs) {

    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, FOOTER_TEXT_SP, resources.displayMetrics)
    }
    private val gap = 8.dpToPx(context)
    private val iconGap = 3.dpToPx(context)
    private val iconSize = 15.dpToPx(context)

    private var time = ""
    private var icon: Drawable? = null

    /** The room added under the text when the footer takes a line of its own, else 0 */
    private var ownLine = 0

    private val footerWidth: Int
        get() = ceil(footerPaint.measureText(time)).toInt() + (icon?.let { iconGap + iconSize } ?: 0)

    private val footerHeight: Int
        get() = max(ceil(footerPaint.fontMetrics.run { descent - ascent }).toInt(), iconSize)

    /** Shows [time] in the corner, followed by [icon] when given, both in [color] */
    fun setFooter(time: String, icon: Drawable?, color: Int) {
        this.time = time
        this.icon = icon?.let { drawable -> DrawableCompat.wrap(drawable.mutate()).also { it.setTint(color) } }
        footerPaint.color = color
        requestLayout()
        invalidate()
    }

    override fun getCompoundPaddingBottom(): Int = super.getCompoundPaddingBottom() + ownLine

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        ownLine = 0
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (time.isEmpty()) return

        val layout = layout ?: return
        val horizontalPadding = compoundPaddingLeft + compoundPaddingRight
        val maxWidth = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> Int.MAX_VALUE
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }

        val lastLine = layout.lineCount - 1
        val inline = horizontalPadding + ceil(layout.getLineWidth(lastLine)).toInt() + gap + footerWidth
        val leftToRight = layout.getParagraphDirection(lastLine) == Layout.DIR_LEFT_TO_RIGHT

        if (leftToRight && inline <= maxWidth) {
            setMeasuredDimension(max(measuredWidth, inline), measuredHeight)
        } else {
            ownLine = footerHeight
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(max(measuredWidth, horizontalPadding + footerWidth), measuredHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (time.isEmpty()) return

        val right = width - paddingRight
        val bottom = height - paddingBottom
        val icon = icon
        if (icon != null) {
            val iconTop = bottom - (footerHeight + iconSize) / 2
            icon.setBounds(right - iconSize, iconTop, right, iconTop + iconSize)
            icon.draw(canvas)
        }

        val textRight = right - (icon?.let { iconGap + iconSize } ?: 0)
        val metrics = footerPaint.fontMetrics
        val baseline = bottom - (footerHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.descent
        canvas.drawText(time, textRight - footerPaint.measureText(time), baseline, footerPaint)
    }

    companion object {
        private const val FOOTER_TEXT_SP = 12f
    }

}
