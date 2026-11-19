/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
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
package io.openmessages.feature.conversationmedia

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.openmessages.common.util.extensions.dpToPx

/** Even gutters between the cells of the media grid, none against its edges */
class GridSpacingItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val spacing = 2.dpToPx(context)

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)

        // A cell on its way out of the grid has no position in it any more
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val column = position % SPAN_COUNT
        outRect.top = if (position < SPAN_COUNT) 0 else spacing
        outRect.left = column * spacing / SPAN_COUNT
        outRect.right = spacing - (column + 1) * spacing / SPAN_COUNT
    }

    companion object {
        const val SPAN_COUNT = 3
    }

}
