/*
 * Copyright (C) 2026 OpenMessages contributors
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
package io.openmessages.feature.compose

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import io.openmessages.R
import io.openmessages.common.base.QkBindingViewHolder
import io.openmessages.common.base.QkRealmAdapter
import io.openmessages.common.util.Colors
import io.openmessages.common.util.DateFormatter
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.databinding.ScheduledMessageBubbleBinding
import io.openmessages.model.ScheduledMessage
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

/**
 * The messages of a conversation that are still waiting for their send time.
 *
 * They live in their own Realm collection rather than among the conversation's messages, so they are
 * shown by their own adapter, concatenated after [MessagesAdapter]. Being scheduled, they are always
 * in the future and therefore always belong last: a message sent in the meantime lands before them
 * without anything having to reorder them.
 */
class ScheduledMessagesAdapter @Inject constructor(
    private val context: Context,
    private val colors: Colors,
    private val dateFormatter: DateFormatter
) : QkRealmAdapter<ScheduledMessage, QkBindingViewHolder<ScheduledMessageBubbleBinding>>() {

    /** Emits the id of the scheduled message tapped, so the screen can offer to act on it. */
    val clicks: Subject<Long> = PublishSubject.create()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): QkBindingViewHolder<ScheduledMessageBubbleBinding> {
        val binding = ScheduledMessageBubbleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QkBindingViewHolder(binding).apply {
            binding.root.setOnClickListener {
                val message = getItem(adapterPosition) ?: return@setOnClickListener
                clicks.onNext(message.id)
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<ScheduledMessageBubbleBinding>, position: Int) {
        val message = getItem(position) ?: return

        // Outgoing colours: a scheduled message is always one of the user's own.
        val theme = colors.theme()
        holder.binding.body.setTextColor(theme.textPrimary)
        holder.binding.body.setBackgroundTint(theme.theme)
        // A scheduled message can be attachments only, and an empty bubble would read as a bug.
        holder.binding.body.text = message.body.takeIf { it.isNotBlank() }
                ?: context.resources.getQuantityString(
                        R.plurals.compose_scheduled_bubble_attachments,
                        message.attachments.size,
                        message.attachments.size)

        holder.binding.scheduledAt.text =
                context.getString(R.string.compose_scheduled_bubble, dateFormatter.getScheduledTimestamp(message.date))
    }
}
