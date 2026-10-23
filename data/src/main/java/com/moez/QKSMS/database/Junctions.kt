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
package io.openmessages.database

import androidx.room.Entity
import androidx.room.Index

/**
 * The recipients of a conversation. Kept as rows of their own because a recipient, being an
 * address, can belong to several conversations at once.
 */
@Entity(
    tableName = "conversation_recipient",
    primaryKeys = ["conversationId", "recipientId"],
    indices = [Index("recipientId")]
)
data class ConversationRecipient(
    val conversationId: Long,
    val recipientId: Long,
    /** Where the recipient comes in the conversation, which is the order the provider lists them in */
    val position: Int
)

/**
 * The parts of an MMS message. The part's own messageId is the provider's id for the MMS, which
 * an SMS can share, so the link to the message row is held here instead.
 */
@Entity(
    tableName = "message_part",
    primaryKeys = ["messageId", "partId"],
    indices = [Index("partId")]
)
data class MessagePart(
    val messageId: Long,
    val partId: Long
)

/**
 * The members of a contact group.
 */
@Entity(
    tableName = "contact_group_member",
    primaryKeys = ["groupId", "contactLookupKey"],
    indices = [Index("contactLookupKey")]
)
data class ContactGroupMember(
    val groupId: Long,
    val contactLookupKey: String
)
