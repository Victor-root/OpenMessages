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
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import io.openmessages.R
import io.openmessages.common.base.BaseAdapter
import io.openmessages.common.base.BindingViewHolder
import io.openmessages.common.util.RowGroups
import io.openmessages.common.util.extensions.forwardTouches
import io.openmessages.extensions.associateByNotNull
import io.openmessages.model.Contact
import io.openmessages.model.ContactGroup
import io.openmessages.model.Conversation
import io.openmessages.model.Recipient
import io.openmessages.repository.ConversationRepository
import io.openmessages.databinding.ContactListItemBinding
import io.openmessages.util.DisplayNames
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import javax.inject.Inject

class ComposeItemAdapter @Inject constructor(
    private val context: Context,
    private val displayNames: DisplayNames,
    private val conversationRepo: ConversationRepository
) : BaseAdapter<ComposeItem, BindingViewHolder<ContactListItemBinding>>(), RowGroups.Adapter {

    /**
     * The rows' groups: what was just typed on its own, then the recent conversations, the starred
     * contacts and the contact groups, then a group per initial
     */
    override val rowGroups = RowGroups(context)

    val clicks: Subject<ComposeItem> = PublishSubject.create()
    val longClicks: Subject<ComposeItem> = PublishSubject.create()

    private val numbersViewPool = RecyclerView.RecycledViewPool()
    private val disposables = CompositeDisposable()

    var recipients: Map<String, Recipient> = mapOf()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder<ContactListItemBinding> {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ContactListItemBinding.inflate(layoutInflater, parent, false)

        binding.numbers.setRecycledViewPool(numbersViewPool)
        binding.numbers.adapter = PhoneNumberAdapter(displayNames)
        binding.numbers.forwardTouches(binding.root)

        return BindingViewHolder(binding).apply {
            binding.root.setOnClickListener {
                val item = getItemOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                clicks.onNext(item)
            }
            binding.root.setOnLongClickListener {
                val item = getItemOrNull(bindingAdapterPosition) ?: return@setOnLongClickListener true
                longClicks.onNext(item)
                true
            }
        }
    }

    override fun onBindViewHolder(holder: BindingViewHolder<ContactListItemBinding>, position: Int) {
        val binding = holder.binding
        holder.itemView.background = rowGroups.background(holder.itemView.context, position)
        when (val item = getItem(position)) {
            is ComposeItem.New -> bindNew(binding, item.value)
            is ComposeItem.Recent -> bindRecent(binding, item.value)
            is ComposeItem.Starred -> bindStarred(binding, item.value)
            is ComposeItem.Person -> bindPerson(binding, item.value)
            is ComposeItem.Group -> bindGroup(binding, item.value)
        }
    }

    override fun onBindViewHolder(
        holder: BindingViewHolder<ContactListItemBinding>,
        position: Int,
        payloads: MutableList<Any>
    ) {
        when (payloads.isNotEmpty() && payloads.all { payload -> payload == RowGroups.PAYLOAD }) {
            true -> holder.itemView.background = rowGroups.background(holder.itemView.context, position)
            false -> onBindViewHolder(holder, position)
        }
    }

    override fun onDatasetChanged() {
        rowGroups.update(data.map { item ->
            when (item) {
                // What was typed stands alone, under no heading
                is ComposeItem.New -> ""
                is ComposeItem.Recent -> context.getString(R.string.contacts_group_recent)
                is ComposeItem.Starred -> context.getString(R.string.contacts_group_starred)
                is ComposeItem.Group -> context.getString(R.string.contacts_group_groups)
                is ComposeItem.Person -> initialOf(item.value)
            }
        })
        notifyItemRangeChanged(0, itemCount, RowGroups.PAYLOAD)
    }

    /** The letter [contact] is listed under: its name's first letter, or # */
    private fun initialOf(contact: Contact): String =
        contact.name.firstOrNull()?.takeIf(Char::isLetter)?.uppercase() ?: "#"

    /** The position each initial's group starts at, in list order */
    fun initials(): Map<String, Int> = buildMap {
        data.forEachIndexed { index, item ->
            if (item is ComposeItem.Person) getOrPut(initialOf(item.value)) { index }
        }
    }

    private fun bindNew(binding: ContactListItemBinding, contact: Contact) {
        binding.avatar.recipients = listOf(createRecipient(contact))

        binding.title.text = contact.numbers.joinToString { displayNames.number(it.address) }

        binding.subtitle.isVisible = false

        binding.numbers.isVisible = false
    }

    private fun bindRecent(binding: ContactListItemBinding, conversation: Conversation) {
        binding.avatar.recipients = conversation.recipients

        binding.title.text = displayNames.of(conversation)

        binding.subtitle.isVisible = conversation.recipients.size > 1 && conversation.name.isBlank()
        binding.subtitle.text = conversation.recipients.joinToString(", ") { recipient ->
            recipient.contact?.name ?: recipient.address
        }
        binding.subtitle.collapseEnabled = conversation.recipients.size > 1

        binding.numbers.isVisible = conversation.recipients.size == 1
        (binding.numbers.adapter as PhoneNumberAdapter).data = conversation.recipients
                .mapNotNull { recipient -> recipient.contact }
                .flatMap { contact -> contact.numbers }
    }

    private fun bindStarred(binding: ContactListItemBinding, contact: Contact) {
        binding.avatar.recipients = listOf(createRecipient(contact))

        binding.title.text = contact.name

        binding.subtitle.isVisible = false

        binding.numbers.isVisible = true
        (binding.numbers.adapter as PhoneNumberAdapter).data = contact.numbers
    }

    private fun bindGroup(binding: ContactListItemBinding, group: ContactGroup) {
        binding.avatar.recipients = group.contacts.map(::createRecipient)

        binding.title.text = group.title

        binding.subtitle.isVisible = true
        binding.subtitle.text = group.contacts.joinToString(", ") { it.name }
        binding.subtitle.collapseEnabled = group.contacts.size > 1

        binding.numbers.isVisible = false
    }

    private fun bindPerson(binding: ContactListItemBinding, contact: Contact) {
        binding.avatar.recipients = listOf(createRecipient(contact))

        binding.title.text = contact.name

        binding.subtitle.isVisible = false

        binding.numbers.isVisible = true
        (binding.numbers.adapter as PhoneNumberAdapter).data = contact.numbers
    }

    private fun createRecipient(contact: Contact): Recipient {
        return recipients[contact.lookupKey]
            ?: Recipient(address = contact.numbers.firstOrNull()?.address ?: "").apply { this.contact = contact }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        disposables += conversationRepo.getUnmanagedRecipients()
                .map { recipients -> recipients.associateByNotNull { recipient -> recipient.contact?.lookupKey } }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { recipients -> this@ComposeItemAdapter.recipients = recipients }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        disposables.clear()
    }

    override fun areItemsTheSame(old: ComposeItem, new: ComposeItem): Boolean {
        val oldIds = old.getContacts().map { contact -> contact.lookupKey }
        val newIds = new.getContacts().map { contact -> contact.lookupKey }
        return oldIds == newIds
    }

    override fun areContentsTheSame(old: ComposeItem, new: ComposeItem): Boolean {
        return false
    }

}
