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
package io.openmessages.feature.scheduled

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import io.openmessages.common.base.QkAdapter
import io.openmessages.common.base.QkBindingViewHolder
import io.openmessages.databinding.ScheduledMessageListItemBinding
import io.openmessages.common.util.DateFormatter
import io.openmessages.model.Contact
import io.openmessages.model.Recipient
import io.openmessages.model.ScheduledMessage
import io.openmessages.util.PhoneNumberUtils
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import javax.inject.Inject

class ScheduledMessageAdapter @Inject constructor(
    private val context: Context,
    private val dateFormatter: DateFormatter,
    private val phoneNumberUtils: PhoneNumberUtils
) : QkAdapter<ScheduledMessage, QkBindingViewHolder<ScheduledMessageListItemBinding>>() {

    /**
     * The contacts the recipients are named after
     */
    var contacts: List<Contact> = emptyList()
        set(value) {
            if (field == value) return

            field = value
            contactCache.clear()
            notifyDataSetChanged()
        }

    private val contactCache = ContactCache()
    private val imagesViewPool = RecyclerView.RecycledViewPool()

    val clicks: Subject<Long> = PublishSubject.create()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<ScheduledMessageListItemBinding> {
        val binding = ScheduledMessageListItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )

        binding.attachments.adapter = ScheduledMessageAttachmentAdapter(context)
        binding.attachments.setRecycledViewPool(imagesViewPool)

        return QkBindingViewHolder(binding).apply {
            binding.root.setOnClickListener {
                val message = getItemOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                if (toggleSelection(message.id, false))
                    binding.root.isActivated = isSelected(message.id)
            }
            binding.root.setOnClickListener {
                val message = getItemOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                toggleSelection(message.id)
                binding.root.isActivated = isSelected(message.id)
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<ScheduledMessageListItemBinding>, position: Int) {
        val message = getItemOrNull(position) ?: return

        // GroupAvatarView only accepts recipients, so map the phone numbers to recipients
        holder.binding.avatars.recipients =
            message.recipients.map { address -> Recipient(address = address) }

        holder.binding.recipients.text = message.recipients.joinToString(",") { address ->
            contactCache[address]?.name?.takeIf { it.isNotBlank() } ?: address
        }

        holder.binding.date.text = dateFormatter.getScheduledTimestamp(message.date)
        holder.binding.body.text = message.body

        // update the selected/highlighted state
        holder.binding.root.isActivated = isSelected(message.id)

        val adapter = holder.binding.attachments.adapter as ScheduledMessageAttachmentAdapter
        adapter.data = message.attachments.map(Uri::parse)
        holder.binding.attachments.isVisible = message.attachments.isNotEmpty()
    }

    override fun areItemsTheSame(old: ScheduledMessage, new: ScheduledMessage) = old.id == new.id

    override fun getItemId(position: Int): Long {
        return getItemOrNull(position)?.id ?: -1
    }

    /**
     * Cache the contacts in a map by the address, because the messages we're binding don't have
     * a reference to the contact.
     */
    private inner class ContactCache : HashMap<String, Contact?>() {

        override fun get(key: String): Contact? {
            if (!containsKey(key)) {
                put(key, contacts.firstOrNull { contact ->
                    contact.numbers.any {
                        phoneNumberUtils.compare(it.address, key)
                    }
                })
            }

            return super.get(key)
        }
    }
}
