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

/**
 * Leaves room above the first row of each group of the list's adapter, when it groups its rows (see
 * [RowGroups]), and writes the group's heading there. The headings are drawn rather than laid out
 * as rows, so the rows keep their positions, which selection, swipes and scrolling all go by.
 */
class GroupHeadingDecoration(parent: RecyclerView) : RecyclerView.ItemDecoration() {

    /** A heading laid out once per label, ready to be drawn wherever its group starts */
    private val headings = HashMap<String, View>()

    private val inflater = LayoutInflater.from(parent.context)

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val groups = (parent.adapter as? RowGroups.Adapter)?.rowGroups ?: return
        val label = groups.labelAt(parent.getChildAdapterPosition(view)) ?: return
        outRect.top = heading(label, parent).height
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val groups = (parent.adapter as? RowGroups.Adapter)?.rowGroups ?: return
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val label = groups.labelAt(parent.getChildAdapterPosition(child)) ?: continue
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

    private fun heading(label: String, parent: RecyclerView): View {
        val width = parent.width - parent.paddingLeft - parent.paddingRight
        headings[label]?.takeIf { heading -> heading.width == width }?.let { return it }
        val heading = inflater.inflate(R.layout.conversation_group_header, parent, false)
        (heading as TextView).text = label
        heading.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        heading.layout(0, 0, heading.measuredWidth, heading.measuredHeight)
        headings[label] = heading
        return heading
    }

}
