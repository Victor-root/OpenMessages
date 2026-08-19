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
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import io.openmessages.R
import io.openmessages.common.Navigator
import io.openmessages.common.base.QkBindingViewHolder
import io.openmessages.common.base.QkRealmAdapter
import io.openmessages.common.util.Colors
import io.openmessages.common.util.DateFormatter
import io.openmessages.common.util.extensions.isPictureSummary
import io.openmessages.common.util.extensions.localiseSummary
import io.openmessages.common.util.extensions.resolveThemeColor
import io.openmessages.common.util.extensions.setTint
import io.openmessages.databinding.ConversationListItemBinding
import io.openmessages.model.Conversation
import io.openmessages.util.PhoneNumberUtils
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class ConversationsAdapter @Inject constructor(
    private val colors: Colors,
    private val context: Context,
    private val dateFormatter: DateFormatter,
    private val navigator: Navigator,
    private val phoneNumberUtils: PhoneNumberUtils
) : QkRealmAdapter<Conversation, QkBindingViewHolder<ConversationListItemBinding>>() {
    private val disposables = CompositeDisposable()

    var hasScheduledConversation: Set<Long> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    init {
        // This is how we access the threadId for the swipe actions
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<ConversationListItemBinding> {
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

        return QkBindingViewHolder(binding).apply {
            binding.root.setOnClickListener {
                val conversation = getItem(adapterPosition) ?: return@setOnClickListener
                when (toggleSelection(conversation.id, false)) {
                    true -> binding.root.isActivated = isSelected(conversation.id)
                    false -> navigator.showConversation(conversation.id)
                }
            }
            binding.root.setOnLongClickListener {
                val conversation = getItem(adapterPosition) ?: return@setOnLongClickListener true
                toggleSelection(conversation.id)
                binding.root.isActivated = isSelected(conversation.id)
                true
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<ConversationListItemBinding>, position: Int) {
        val conversation = getItem(position) ?: return
        val binding = holder.binding

        // If the last message wasn't incoming, then the colour doesn't really matter anyway
        val lastMessage = conversation.lastMessage
        val recipient = when {
            conversation.recipients.size == 1 || lastMessage == null -> conversation.recipients.firstOrNull()
            else -> conversation.recipients.find { recipient ->
                phoneNumberUtils.compare(recipient.address, lastMessage.address)
            }
        }
        val theme = colors.theme(recipient).theme

        holder.itemView.isActivated = isSelected(conversation.id)

        binding.avatars.recipients = conversation.recipients
        binding.title.collapseEnabled = conversation.recipients.size > 1
        binding.title.text = buildSpannedString {
            append(conversation.getTitle())
        }
        binding.date.text = conversation.date.takeIf { it > 0 }?.let(dateFormatter::getConversationTimestamp)

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

        // Make the preview in italics if draft
        if (conversation.draft.isNotEmpty()) binding.snippet.setTypeface(null, Typeface.ITALIC)

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

    override fun getItemId(position: Int): Long {
        return getItem(position)?.id ?: -1
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position)?.unread == false) 0 else 1
    }

    /** Whether the conversation at [position] is currently unread, for state-aware swipe icons. */
    fun unreadAt(position: Int): Boolean = getItem(position)?.unread == true

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        disposables.clear()
    }


}
