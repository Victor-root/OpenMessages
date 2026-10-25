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
package io.openmessages.feature.blocking.filters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.openmessages.common.base.BaseAdapter
import io.openmessages.common.base.BindingViewHolder
import io.openmessages.model.MessageContentFilter
import io.openmessages.databinding.MessageContentFilterListItemBinding
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject

class MessageContentFiltersAdapter : BaseAdapter<MessageContentFilter, BindingViewHolder<MessageContentFilterListItemBinding>>() {

    val removeMessageContentFilter: Subject<Long> = PublishSubject.create()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder<MessageContentFilterListItemBinding> {
        val binding = MessageContentFilterListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingViewHolder(binding).apply {
            binding.removeFilter.setOnClickListener {
                val filter = getItemOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                removeMessageContentFilter.onNext(filter.id)
            }
        }
    }

    override fun onBindViewHolder(holder: BindingViewHolder<MessageContentFilterListItemBinding>, position: Int) {
        val item = getItem(position)
        holder.binding.caseIcon.visibility = if (item.caseSensitive) View.VISIBLE else View.GONE
        holder.binding.regexIcon.visibility = if (item.isRegex) View.VISIBLE else View.GONE
        holder.binding.contactsIcon.visibility = if (item.includeContacts) View.VISIBLE else View.GONE
        holder.binding.filter.text = item.value
    }

}
