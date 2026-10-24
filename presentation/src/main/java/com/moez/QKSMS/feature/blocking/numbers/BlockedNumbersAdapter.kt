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
package io.openmessages.feature.blocking.numbers

import android.view.LayoutInflater
import android.view.ViewGroup
import io.openmessages.common.base.QkAdapter
import io.openmessages.common.base.QkBindingViewHolder
import io.openmessages.model.BlockedNumber
import io.openmessages.databinding.BlockedNumberListItemBinding
import io.openmessages.util.DisplayNames
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject

class BlockedNumbersAdapter(
    private val displayNames: DisplayNames
) : QkAdapter<BlockedNumber, QkBindingViewHolder<BlockedNumberListItemBinding>>() {

    val unblockAddress: Subject<Long> = PublishSubject.create()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<BlockedNumberListItemBinding> {
        val binding = BlockedNumberListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QkBindingViewHolder(binding).apply {
            binding.unblock.setOnClickListener {
                val number = getItemOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                unblockAddress.onNext(number.id)
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<BlockedNumberListItemBinding>, position: Int) {
        val item = getItem(position)

        holder.binding.number.text = displayNames.number(item.address)
    }

}
