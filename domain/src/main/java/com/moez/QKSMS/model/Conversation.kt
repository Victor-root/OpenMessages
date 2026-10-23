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
package io.openmessages.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation",
    indices = [Index("archived"), Index("blocked"), Index("pinned"), Index("flagged"), Index("lastMessageId")]
)
data class Conversation(
    @PrimaryKey var id: Long = 0,
    var archived: Boolean = false,
    var blocked: Boolean = false,
    var pinned: Boolean = false,
    /** The [Message.id] of the latest message in the conversation */
    var lastMessageId: Long? = null,
    var draft: String = "",
    var draftDate: Long = 0,

    var blockingClient: Int? = null,
    var blockReason: String? = null,

    // Soft "suspected spam" state: the message stays in the inbox but the conversation is tagged.
    // Cleared when the user approves the sender (see AllowlistRepository).
    var flagged: Boolean = false,
    var flagReason: String? = null,

    var name: String = "", // custom title

    var sendAsGroup: Boolean = true,
) {

    @Ignore var recipients: List<Recipient> = emptyList()
    @Ignore var lastMessage: Message? = null

    val date: Long get() = lastMessage?.date ?: if (draft.isNotEmpty()) draftDate else 0
    val snippet: String? get() = lastMessage?.getSummary()
    val unread: Boolean get() = lastMessage?.read == false
    val me: Boolean get() = lastMessage?.isMe() == true

    fun getTitle(): String {
        return name.takeIf { it.isNotBlank() } ?: recipients.joinToString { recipient -> recipient.getDisplayName() }
    }

}
