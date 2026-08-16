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
package io.openmessages.feature.conversationinfo

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.openmessages.common.util.extensions.dpToPx
import io.openmessages.feature.conversationinfo.ConversationInfoItem.ConversationInfoMedia
import io.openmessages.feature.conversationinfo.ConversationInfoItem.ConversationInfoRecipient

class GridSpacingItemDecoration(
    private val adapter: ConversationInfoAdapter,
    private val context: Context
) : RecyclerView.ItemDecoration() {

    private val spanCount = 3
    private val spacing = 2.dpToPx(context)

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)

        // A row on its way out of the list has no position in it any more, and a row at the end has
        // nothing after it. Both were asked of the list all the same, and the list answered by
        // bringing the screen down mid-layout.
        val position = parent.getChildAdapterPosition(view)
        val item = adapter.data.getOrNull(position) ?: return

        if (item is ConversationInfoRecipient &&
            adapter.data.getOrNull(position + 1) !is ConversationInfoRecipient
        ) {
            outRect.bottom = 8.dpToPx(context)
        } else if (item is ConversationInfoMedia) {
            val firstPartIndex = adapter.data.indexOfFirst { it is ConversationInfoMedia }
            val localPartIndex = position - firstPartIndex

            val column = localPartIndex % spanCount

            outRect.top = spacing
            outRect.left = column * spacing / spanCount
            outRect.right = spacing - (column + 1) * spacing / spanCount
        }
    }

}