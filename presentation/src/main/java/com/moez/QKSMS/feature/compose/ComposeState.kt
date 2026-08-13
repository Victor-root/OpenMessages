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
package io.openmessages.feature.compose

import io.openmessages.compat.SubscriptionInfoCompat
import io.openmessages.model.Attachment
import io.openmessages.model.Conversation
import io.openmessages.model.Message
import io.openmessages.model.Recipient
import io.openmessages.model.ScheduledMessage
import io.realm.RealmResults

data class ComposeState(
    val hasError: Boolean = false,
    val editingMode: Boolean = false,
    val threadId: Long = 0,
    val selectedChips: List<Recipient> = ArrayList(),
    val sendAsGroup: Boolean = true,
    val conversationtitle: String = "",
    val loading: Boolean = false,
    val query: String = "",
    val searchSelectionId: Long = -1,
    val searchSelectionPosition: Int = 0,
    val searchResults: Int = 0,
    val messages: Pair<Conversation, RealmResults<Message>>? = null,
    /** Waiting for their send time, shown after [messages] since they are always in the future. */
    val scheduledMessages: RealmResults<ScheduledMessage>? = null,
    val selectedMessages: Int = 0,
    val selectedMessagesHaveText: Boolean = false,
    val scheduled: Long = 0,
    val attachments: List<Attachment> = listOf(),
    val attaching: Boolean = false,
    val scheduling: Boolean = false,
    val remaining: String = "",
    val subscription: SubscriptionInfoCompat? = null,
    val canSend: Boolean = false,
    val hasScheduledMessages: Boolean = false,
    val validRecipientNumbers: Int = 1,
    val recipientCount: Int = 1,
    val audioMsgRecording: Boolean = false,
    val saveDraft: Boolean = true,
    val flagged: Boolean = false,
    val flagReason: String = "",
    val dialog: ComposeDialog? = null,
)