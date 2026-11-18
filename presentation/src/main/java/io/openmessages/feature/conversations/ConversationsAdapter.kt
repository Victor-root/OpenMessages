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
package io.openmessages.feature.conversations

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import io.openmessages.R
import io.openmessages.common.Navigator
import io.openmessages.common.base.BindingViewHolder
import io.openmessages.common.base.BaseAdapter
import io.openmessages.common.util.Colors
import io.openmessages.common.util.DateFormatter
import io.openmessages.common.util.RowGroups
import io.openmessages.common.util.extensions.isPictureSummary
import io.openmessages.common.util.extensions.localiseSummary
import io.openmessages.common.util.extensions.looksLike
import io.openmessages.common.util.extensions.resolveThemeColor
import io.openmessages.common.util.extensions.setTint
import io.openmessages.databinding.ConversationListItemBinding
import io.openmessages.model.Conversation
import io.openmessages.util.DisplayNames
import io.openmessages.util.PhoneNumberUtils
import io.openmessages.util.Preferences
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class ConversationsAdapter @Inject constructor(
    private val displayNames: DisplayNames,
    private val colors: Colors,
    private val context: Context,
    private val dateFormatter: DateFormatter,
    private val navigator: Navigator,
    private val phoneNumberUtils: PhoneNumberUtils,
    private val prefs: Preferences
) : BaseAdapter<Conversation, BindingViewHolder<ConversationListItemBinding>>(), RowGroups.Adapter {
    private val disposables = CompositeDisposable()

    var hasScheduledConversation: Set<Long> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    /**
     * The rows' groups. The list is sorted unread first (when asked), then pinned, then with a
     * draft, then by date, so each of those runs is a group of its own ahead of the days.
     */
    override val rowGroups = RowGroups(context)

    /** Whether each row's group is a day, under which its date need only give the time */
    private var groupedByDay: List<Boolean> = emptyList()

    init {
        // This is how we access the threadId for the swipe actions
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder<ConversationListItemBinding> {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ConversationListItemBinding.inflate(layoutInflater, parent, false)

        if (viewType == 1) {
            val textColorPrimary = parent.context.resolveThemeColor(android.R.attr.textColorPrimary)

            binding.title.setTypeface(binding.title.typeface, Typeface.BOLD)

            binding.snippet.setTypeface(binding.snippet.typeface, Typeface.BOLD)
            binding.snippet.setTextColor(textColorPrimary)
            binding.snippet.maxLines = 5

            binding.unread.isVisible = true

            binding.date.setTypeface(binding.date.typeface, Typeface.BOLD)
            binding.date.setTextColor(textColorPrimary)
        }

        return BindingViewHolder(binding).apply {
            binding.root.setOnClickListener {
                val conversation = getItemOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                when (toggleSelection(conversation.id, false)) {
                    true -> binding.root.isActivated = isSelected(conversation.id)
                    false -> {
                        // The conversation opens in the colour it has here, without waiting on
                        // the database for it
                        colors.rememberConversationColor(conversation.id, colorOf(conversation))
                        navigator.showConversation(conversation.id)
                    }
                }
            }
            binding.root.setOnLongClickListener {
                val conversation = getItemOrNull(bindingAdapterPosition) ?: return@setOnLongClickListener true
                toggleSelection(conversation.id)
                binding.root.isActivated = isSelected(conversation.id)
                true
            }
        }
    }

    override fun onBindViewHolder(holder: BindingViewHolder<ConversationListItemBinding>, position: Int) {
        val conversation = getItemOrNull(position) ?: return
        val binding = holder.binding

        val theme = colorOf(conversation)

        holder.itemView.isActivated = isSelected(conversation.id)
        bindGroup(holder, position)

        binding.avatars.recipients = conversation.recipients
        binding.title.collapseEnabled = conversation.recipients.size > 1
        binding.title.text = buildSpannedString {
            append(displayNames.of(conversation))
        }

        val localSnippet = context.localiseSummary(conversation.snippet)
        val snippetText = when {
            conversation.draft.isNotEmpty() -> context.getString(R.string.main_sender_draft, conversation.draft)
            conversation.me -> context.getString(R.string.main_sender_you, localSnippet)
            else -> localSnippet
        }
        // Flagged ("suspected spam") conversations stay in the inbox but carry a discreet amber label
        binding.snippet.text = when {
            conversation.flagged -> buildSpannedString {
                // Amber so it stands out from the grey snippet regardless of the app theme
                color(0xFFF57C00.toInt()) {
                    bold { append(context.getString(R.string.conversation_flagged_label)) }
                }
                append("  ")
                append(snippetText.orEmpty())
            }
            else -> snippetText
        }

        // The preview is in italics for a draft, and set back for anything else: rows are reused
        val weight = if (conversation.unread) Typeface.BOLD else Typeface.NORMAL
        val style = if (conversation.draft.isNotEmpty()) weight or Typeface.ITALIC else weight
        binding.snippet.setTypeface(Typeface.create(binding.snippet.typeface, style), style)

        val mediaIcon = when {
            isPictureSummary(conversation.snippet) ->
                ContextCompat.getDrawable(context, R.drawable.ic_tabler_photo)?.mutate()?.also {
                    val size = binding.snippet.textSize.toInt()
                    it.setBounds(0, 0, size, size)
                    it.setTint(binding.snippet.currentTextColor)
                }
            else -> null
        }
        binding.snippet.setCompoundDrawablesRelative(mediaIcon, null, null, null)
        binding.snippet.compoundDrawablePadding = (4 * context.resources.displayMetrics.density + 0.5f).toInt()

        binding.scheduled.isVisible = conversation.id in hasScheduledConversation

        binding.pinned.isVisible = conversation.pinned
        binding.unread.setTint(theme)
    }

    /** The colour of [conversation]: its recipient's, or in a group its last sender's */
    private fun colorOf(conversation: Conversation): Int {
        // If the last message wasn't incoming, then the colour doesn't really matter anyway
        val lastMessage = conversation.lastMessage
        val recipient = when {
            conversation.recipients.size == 1 || lastMessage == null -> conversation.recipients.firstOrNull()
            else -> conversation.recipients.find { recipient ->
                phoneNumberUtils.compare(recipient.address, lastMessage.address)
            }
        }
        return colors.theme(recipient).theme
    }

    override fun onBindViewHolder(
        holder: BindingViewHolder<ConversationListItemBinding>,
        position: Int,
        payloads: MutableList<Any>
    ) {
        when (payloads.isNotEmpty() && payloads.all { payload -> payload == RowGroups.PAYLOAD }) {
            true -> bindGroup(holder, position)
            false -> onBindViewHolder(holder, position)
        }
    }

    /** What a row takes from its group: its slice of the block, and how its date is written */
    private fun bindGroup(holder: BindingViewHolder<ConversationListItemBinding>, position: Int) {
        val conversation = getItemOrNull(position) ?: return
        val selected = ColorUtils.setAlphaComponent(colors.theme().theme, 0x29)
        holder.itemView.background = rowGroups.background(holder.itemView.context, position, selected)
        // Under a day's heading the day goes without saying, so only the time is shown
        holder.binding.date.text = conversation.date.takeIf { it > 0 }?.let { date ->
            when (groupedByDay.getOrNull(position)) {
                true -> dateFormatter.getTimestamp(date)
                else -> dateFormatter.getConversationTimestamp(date)
            }
        }
    }

    override fun onDatasetChanged() {
        val unreadAtTop = prefs.unreadAtTop.get()
        val dayLabel = dateFormatter.dayLabeller()
        val groups = data.map { conversation -> groupOf(conversation, unreadAtTop, dayLabel) }
        rowGroups.update(groups.map { (label) -> label })
        groupedByDay = groups.map { (_, byDay) -> byDay }
        notifyItemRangeChanged(0, itemCount, RowGroups.PAYLOAD)
    }

    /** The label of [conversation]'s group, and whether that group is a day */
    private fun groupOf(
        conversation: Conversation, unreadAtTop: Boolean, dayLabel: (Long) -> String
    ): Pair<String, Boolean> = when {
        unreadAtTop && conversation.unread ->
            context.getString(R.string.conversations_group_unread) to false
        conversation.pinned -> context.getString(R.string.conversations_group_pinned) to false
        conversation.draft.isNotEmpty() -> context.getString(R.string.conversations_group_drafts) to false
        else -> dayLabel(conversation.date) to true
    }

    override fun areItemsTheSame(old: Conversation, new: Conversation) = old.id == new.id

    override fun areContentsTheSame(old: Conversation, new: Conversation) = old.looksLike(new)

    override fun getItemId(position: Int): Long {
        return getItemOrNull(position)?.id ?: -1
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItemOrNull(position)?.unread == false) 0 else 1
    }

    /** Whether the conversation at [position] is currently unread, for state-aware swipe icons. */
    fun unreadAt(position: Int): Boolean = getItemOrNull(position)?.unread == true

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        disposables.clear()
    }

}
