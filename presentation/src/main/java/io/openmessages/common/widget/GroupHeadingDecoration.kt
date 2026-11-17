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

import android.graphics.Canvas
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.openmessages.R
import io.openmessages.common.util.RowGroups
import io.openmessages.common.util.extensions.resolveThemeColor

/**
 * Leaves room above the first row of each group of the list's adapter, when it groups its rows (see
 * [RowGroups]), and writes the group's heading there. The headings are drawn rather than laid out
 * as rows, so the rows keep their positions, which selection, swipes and scrolling all go by. A
 * group labelled with nothing has no heading.
 *
 * [headingColor] writes the headings in that colour rather than the text's, and [sticky] keeps the
 * heading of the group at the top of the list there while its rows scroll by, as a contacts list does.
 */
class GroupHeadingDecoration(
    parent: RecyclerView,
    private val headingColor: Int? = null,
    private val sticky: Boolean = false
) : RecyclerView.ItemDecoration() {

    /** A heading laid out once per label, ready to be drawn wherever its group starts */
    private val headings = HashMap<String, View>()

    private val inflater = LayoutInflater.from(parent.context)

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val groups = (parent.adapter as? RowGroups.Adapter)?.rowGroups ?: return
        val label = groups.labelAt(parent.getChildAdapterPosition(view))?.takeIf(String::isNotEmpty) ?: return
        outRect.top = heading(label, parent).height
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val groups = (parent.adapter as? RowGroups.Adapter)?.rowGroups ?: return
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val label = groups.labelAt(parent.getChildAdapterPosition(child))?.takeIf(String::isNotEmpty) ?: continue
            val heading = heading(label, parent)
            canvas.save()
            // Follows the row through its animations, so the heading moves with its group
            canvas.translate(child.left.toFloat(), child.top + child.translationY - heading.height)
            canvas.clipRect(0, 0, heading.width, heading.height)
            heading.alpha = child.alpha
            heading.draw(canvas)
            canvas.restore()
        }
    }

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (!sticky) return
        val groups = (parent.adapter as? RowGroups.Adapter)?.rowGroups ?: return
        val first = parent.getChildAt(0) ?: return
        val position = parent.getChildAdapterPosition(first)
        val label = groups.groupLabel(position)?.takeIf(String::isNotEmpty) ?: return
        val heading = heading(label, parent)
        // While the group's own heading is still in view, it is the one shown
        if (groups.labelAt(position) != null && first.top - heading.height >= 0) return

        // The next group's heading pushes this one up as it arrives
        var top = 0f
        for (index in 1 until parent.childCount) {
            val child = parent.getChildAt(index)
            val next = groups.labelAt(parent.getChildAdapterPosition(child))?.takeIf(String::isNotEmpty) ?: continue
            val nextTop = child.top + child.translationY - heading(next, parent).height
            top = minOf(0f, nextTop - heading.height)
            break
        }

        canvas.save()
        canvas.translate(0f, top)
        canvas.clipRect(0, 0, parent.width, heading.height)
        canvas.drawColor(background)
        canvas.translate(first.left.toFloat(), 0f)
        heading.draw(canvas)
        canvas.restore()
    }

    /** What the sticky heading lies on, the list's own background, so the rows pass under it */
    private val background = parent.context.resolveThemeColor(android.R.attr.windowBackground)

    private fun heading(label: String, parent: RecyclerView): View {
        val width = parent.width - parent.paddingLeft - parent.paddingRight
        headings[label]?.takeIf { heading -> heading.width == width }?.let { return it }
        val heading = inflater.inflate(R.layout.conversation_group_header, parent, false)
        (heading as TextView).text = label
        headingColor?.let(heading::setTextColor)
        heading.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        heading.layout(0, 0, heading.measuredWidth, heading.measuredHeight)
        headings[label] = heading
        return heading
    }

}
