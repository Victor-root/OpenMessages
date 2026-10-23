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

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import io.openmessages.model.Contact
import io.openmessages.model.ContactGroup
import io.openmessages.model.Conversation
import io.openmessages.model.EmojiReaction
import io.openmessages.model.Message
import io.openmessages.model.MmsPart
import io.openmessages.model.PhoneNumber
import io.openmessages.model.Recipient

/*
 * The rows of an entity together with the rows it refers to, as Room reads them in one go. Each
 * hands back the model with its related objects filled in, which is how the rest of the app sees
 * them.
 */

class ContactWithNumbers(
    @Embedded val contact: Contact,
    @Relation(parentColumn = "lookupKey", entityColumn = "contactLookupKey")
    val numbers: List<PhoneNumber>
) {
    fun toModel(): Contact = contact.also { it.numbers = numbers }
}

class ContactGroupWithContacts(
    @Embedded val group: ContactGroup,
    @Relation(
        parentColumn = "id",
        entityColumn = "lookupKey",
        entity = Contact::class,
        associateBy = Junction(
            ContactGroupMember::class,
            parentColumn = "groupId",
            entityColumn = "contactLookupKey"
        )
    )
    val contacts: List<ContactWithNumbers>
) {
    fun toModel(): ContactGroup = group.also { it.contacts = contacts.map(ContactWithNumbers::toModel) }
}

class RecipientWithContact(
    @Embedded val recipient: Recipient,
    @Relation(parentColumn = "contactLookupKey", entityColumn = "lookupKey", entity = Contact::class)
    val contact: ContactWithNumbers?
) {
    fun toModel(): Recipient = recipient.also { it.contact = contact?.toModel() }
}

class MessageWithRelations(
    @Embedded val message: Message,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(MessagePart::class, parentColumn = "messageId", entityColumn = "partId")
    )
    val parts: List<MmsPart>,
    @Relation(parentColumn = "id", entityColumn = "targetMessageId")
    val emojiReactions: List<EmojiReaction>
) {
    fun toModel(): Message = message.also {
        it.parts = parts
        it.emojiReactions = emojiReactions
    }
}

class ConversationWithRelations(
    @Embedded val conversation: Conversation,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        entity = Recipient::class,
        associateBy = Junction(
            ConversationRecipient::class,
            parentColumn = "conversationId",
            entityColumn = "recipientId"
        )
    )
    val recipients: List<RecipientWithContact>,
    @Relation(parentColumn = "lastMessageId", entityColumn = "id", entity = Message::class)
    val lastMessage: MessageWithRelations?
) {
    fun toModel(): Conversation = conversation.also {
        it.recipients = recipients.map(RecipientWithContact::toModel)
        it.lastMessage = lastMessage?.toModel()
    }
}

class PartWithMessages(
    @Embedded val part: MmsPart,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(MessagePart::class, parentColumn = "partId", entityColumn = "messageId")
    )
    val messages: List<Message>
) {
    fun toModel(): MmsPart = part.also { it.messages = messages }
}
