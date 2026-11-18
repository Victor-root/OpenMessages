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
package io.openmessages.common.base

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import io.openmessages.common.util.RowGroups

/**
 * A list whose rows make up one rounded block, as the settings do. A row joins the block with
 * [joinBlock], and is given its corners again, by itself, whenever its neighbours change.
 */
abstract class BlockAdapter<T, VHT : RecyclerView.ViewHolder>(context: Context) :
    BaseAdapter<T, VHT>(), RowGroups.Adapter {

    override val rowGroups = RowGroups(context)

    /** The tint of a row while it is selected, if rows can be */
    protected open val selectedTint: Int? get() = null

    protected fun joinBlock(holder: VHT, position: Int) {
        holder.itemView.background = rowGroups.background(holder.itemView.context, position, selectedTint)
    }

    override fun onBindViewHolder(holder: VHT, position: Int, payloads: MutableList<Any>) {
        when (payloads.isNotEmpty() && payloads.all { payload -> payload == RowGroups.PAYLOAD }) {
            true -> joinBlock(holder, position)
            false -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onDatasetChanged() {
        rowGroups.update(data.map { "" })
        notifyItemRangeChanged(0, itemCount, RowGroups.PAYLOAD)
    }

}
