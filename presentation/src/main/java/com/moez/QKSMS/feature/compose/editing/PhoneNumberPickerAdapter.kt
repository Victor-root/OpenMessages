/*
 * Copyright (C) 2019 Moez Bhatti <moez.bhatti@gmail.com>
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
package io.openmessages.feature.compose.editing

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import io.openmessages.R
import io.openmessages.common.base.QkAdapter
import io.openmessages.common.base.QkBindingViewHolder
import io.openmessages.common.util.extensions.forwardTouches
import io.openmessages.databinding.PhoneNumberListItemBinding
import io.openmessages.extensions.Optional
import io.openmessages.model.PhoneNumber
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class PhoneNumberPickerAdapter @Inject constructor(
    private val context: Context
) : QkAdapter<PhoneNumber, QkBindingViewHolder<PhoneNumberListItemBinding>>() {

    val selectedItemChanges: Subject<Optional<Long>> = BehaviorSubject.create()

    private var selectedItem: Long? = null
        set(value) {
            data.indexOfFirst { number -> number.id == field }.takeIf { it != -1 }?.run(::notifyItemChanged)
            field = value
            data.indexOfFirst { number -> number.id == field }.takeIf { it != -1 }?.run(::notifyItemChanged)
            selectedItemChanges.onNext(Optional(value))
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<PhoneNumberListItemBinding> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = PhoneNumberListItemBinding.inflate(inflater, parent, false)
        return QkBindingViewHolder(binding).apply {
            binding.number.radioButton.forwardTouches(itemView)

            binding.root.setOnClickListener {
                val phoneNumber = getItemOrNull(adapterPosition) ?: return@setOnClickListener
                selectedItem = phoneNumber.id
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<PhoneNumberListItemBinding>, position: Int) {
        val binding = holder.binding
        val phoneNumber = getItem(position)

        binding.number.radioButton.isChecked = phoneNumber.id == selectedItem
        binding.number.titleView.text = phoneNumber.address
        binding.number.summaryView.text = when (phoneNumber.isDefault) {
            true -> context.getString(R.string.compose_number_picker_default, phoneNumber.type)
            false -> phoneNumber.type
        }
    }

    override fun onDatasetChanged() {
        super.onDatasetChanged()
        selectedItem = data.find { number -> number.isDefault }?.id ?: data.firstOrNull()?.id
    }

}
