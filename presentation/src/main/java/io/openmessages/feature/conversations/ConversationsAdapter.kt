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
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
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
import io.openmessages.common.util.extensions.isPictureSummary
import io.openmessages.common.util.extensions.localiseSummary
import io.openmessages.common.util.extensions.looksLike
import io.openmessages.common.util.extensions.resolveThemeAttribute
import io.openmessages.common.util.extensions.resolveThemeColor
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.databinding.ConversationListItemBinding
import io.openmessages.model.Conversation
import io.openmessages.util.DisplayNames
import io.openmessages.util.PhoneNumberUtils
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class ConversationsAdapter @Inject constructor(
    private val displayNames: DisplayNames,
    private val colors: Colors,
    private val context: Context,
    private val dateFormatter: DateFormatter,
    private val navigator: Navigator,
    private val phoneNumberUtils: PhoneNumberUtils
) : BaseAdapter<Conversation, BindingViewHolder<ConversationListItemBinding>>() {
    private val disposables = CompositeDisposable()

    var hasScheduledConversation: Set<Long> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    /** How many unread messages each conversation holds, by its id, for the badge of an unread row */
    var unreadCounts: Map<Long, Int> = emptyMap()
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, UNREAD_COUNT)
        }

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

            binding.unread.isVisible = true

            binding.date.setTypeface(binding.date.typeface, Typeface.BOLD)
            binding.date.setTextColor(textColorPrimary)
        }

        // The row runs edge to edge, tinted across its whole width while it is selected
        binding.root.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_activated),
                ColorDrawable(ColorUtils.setAlphaComponent(colors.theme().theme, 0x29)))
        }
        binding.root.foreground = ContextCompat.getDrawable(parent.context,
            parent.context.resolveThemeAttribute(android.R.attr.selectableItemBackground))

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
        binding.date.text = conversation.date.takeIf { it > 0 }?.let(dateFormatter::getConversationTimestamp)
        bindUnreadCount(holder, conversation)

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
        binding.unread.setBackgroundTint(theme)
        binding.unread.setTextColor(colors.contentColorOnTheme(theme))
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
        val conversation = getItemOrNull(position)
        if (conversation != null && payloads.isNotEmpty() && payloads.all { payload -> payload == UNREAD_COUNT }) {
            bindUnreadCount(holder, conversation)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    /** The number on an unread row's badge, as Google Messages shows it */
    private fun bindUnreadCount(holder: BindingViewHolder<ConversationListItemBinding>, conversation: Conversation) {
        holder.binding.unread.text = unreadCounts[conversation.id]
            ?.let { count -> if (count > MAX_UNREAD_SHOWN) "$MAX_UNREAD_SHOWN+" else count.toString() }
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

    companion object {
        /** A row's payload when only its unread count changed */
        private const val UNREAD_COUNT = "unread_count"
        private const val MAX_UNREAD_SHOWN = 99
    }

}
