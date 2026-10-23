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

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.openmessages.model.Message
import io.openmessages.model.MmsPart
import io.reactivex.Flowable

/**
 * The messages that can be emoji reactions: an SMS with a body, or an MMS that is not a notification
 * and has a part with text
 */
private const val REACTION_CANDIDATE = """
    ((type = 'sms' AND body != '')
    OR (type = 'mms' AND messageType != 130 AND id IN (
        SELECT message_part.messageId FROM message_part
        INNER JOIN mms_part ON mms_part.id = message_part.partId
        WHERE mms_part.text IS NOT NULL AND mms_part.text != ''
    )))
"""

@Dao
abstract class MessageDao {

    /** How many messages of a conversation match, see [getMessageCountsSince] and [getOldMessageCounts] */
    class ThreadCount(val threadId: Long, val count: Int)

    /** The fields a message is told apart from its duplicates by */
    class MessageSignature(
        val id: Long,
        val address: String,
        val dateSent: Long,
        val boxId: Int,
        val body: String,
        val attachmentTypeString: String
    )

    @Transaction
    @Query("SELECT * FROM message WHERE threadId = :threadId AND isEmojiReaction = 0 ORDER BY date")
    abstract fun observeMessages(threadId: Long): Flowable<List<MessageWithRelations>>

    @Transaction
    @Query("SELECT * FROM message WHERE threadId = :threadId AND isEmojiReaction = 0 ORDER BY date")
    abstract fun getMessages(threadId: Long): List<MessageWithRelations>

    @Transaction
    @Query("SELECT * FROM message WHERE threadId = :threadId ORDER BY date DESC")
    abstract fun getMessagesNewestFirst(threadId: Long): List<MessageWithRelations>

    @Query("SELECT COUNT(*) FROM message WHERE type = :type")
    abstract fun countOfType(type: String): Int

    /** The messages of the type oldest first, [limit] of them from [offset] on */
    @Transaction
    @Query("SELECT * FROM message WHERE type = :type ORDER BY date, id LIMIT :limit OFFSET :offset")
    abstract fun getPageOfType(type: String, limit: Int, offset: Int): List<MessageWithRelations>

    @Transaction
    @Query("SELECT * FROM message WHERE threadId = :threadId ORDER BY date DESC LIMIT 1")
    abstract fun getNewest(threadId: Long): MessageWithRelations?

    @Transaction
    @Query("SELECT * FROM message WHERE id = :id")
    abstract fun get(id: Long): MessageWithRelations?

    @Transaction
    @Query("SELECT * FROM message WHERE id IN (:ids)")
    protected abstract fun getIn(ids: List<Long>): List<MessageWithRelations>

    @Transaction
    open fun get(ids: Collection<Long>): List<MessageWithRelations> {
        return ids.chunked(MAX_IN_LIST).flatMap(::getIn)
    }

    @Transaction
    @Query("""
        SELECT message.* FROM message
        INNER JOIN message_part ON message_part.messageId = message.id
        WHERE message_part.partId = :partId
        LIMIT 1
    """)
    abstract fun getMessageForPart(partId: Long): MessageWithRelations?

    @Query("""
        SELECT id FROM message
        WHERE (type = :type AND contentId = :contentId) OR (id = :messageId AND contentId = 0)
        LIMIT 1
    """)
    abstract fun findId(type: String, contentId: Long, messageId: Long): Long?

    /**
     * The newest message of the conversation whose box is one of [smsBoxes] for an SMS or one of
     * [mmsBoxes] for an MMS, as a list of at most one
     */
    @Transaction
    @Query("""
        SELECT * FROM message
        WHERE threadId = :threadId
        AND ((type = 'sms' AND boxId IN (:smsBoxes)) OR (type = 'mms' AND boxId IN (:mmsBoxes)))
        ORDER BY date DESC
        LIMIT 1
    """)
    abstract fun observeLatestInBoxes(
        threadId: Long,
        smsBoxes: List<Int>,
        mmsBoxes: List<Int>
    ): Flowable<List<MessageWithRelations>>

    @Query("SELECT COUNT(*) FROM message WHERE $REACTION_CANDIDATE")
    abstract fun countReactionCandidates(): Int

    @Query("SELECT DISTINCT threadId FROM message WHERE $REACTION_CANDIDATE")
    abstract fun getReactionCandidateThreadIds(): List<Long>

    /**
     * The messages of the conversation that can be emoji reactions, oldest first, the order
     * reactions and their removal have to be read in
     */
    @Transaction
    @Query("SELECT * FROM message WHERE threadId = :threadId AND $REACTION_CANDIDATE ORDER BY date")
    abstract fun getReactionCandidates(threadId: Long): List<MessageWithRelations>

    @Transaction
    @Query("SELECT * FROM message WHERE threadId = :threadId AND seen = 0 AND read = 0 ORDER BY date")
    abstract fun getUnreadUnseen(threadId: Long): List<MessageWithRelations>

    @Transaction
    @Query("SELECT * FROM message WHERE threadId = :threadId AND read = 0 ORDER BY date")
    abstract fun getUnread(threadId: Long): List<MessageWithRelations>

    @Transaction
    @Query("SELECT * FROM message WHERE threadId = :threadId AND read = 0 ORDER BY date")
    abstract fun observeUnread(threadId: Long): Flowable<List<MessageWithRelations>>

    @Query("SELECT DISTINCT threadId FROM message WHERE seen = 0")
    abstract fun getUnseenThreadIds(): List<Long>

    @Query("SELECT threadId, COUNT(*) AS count FROM message WHERE date > :since GROUP BY threadId")
    abstract fun getMessageCountsSince(since: Long): List<ThreadCount>

    @Query("SELECT threadId, COUNT(*) AS count FROM message WHERE date < :before GROUP BY threadId")
    abstract fun getOldMessageCounts(before: Long): List<ThreadCount>

    @Query("SELECT * FROM message WHERE date < :before")
    abstract fun getOlderThan(before: Long): List<Message>

    @Query("SELECT id, address, dateSent, boxId, body, attachmentTypeString FROM message ORDER BY id")
    abstract fun getSignatures(): List<MessageSignature>

    /**
     * The text of every message as rows of messageId, threadId and text: the body of an SMS, and
     * each part with text of an MMS. A cursor, so that the texts can be gone through one at a time
     * rather than all held at once.
     */
    @Query("""
        SELECT id AS messageId, threadId, body AS text FROM message WHERE body != ''
        UNION ALL
        SELECT message.id, message.threadId, mms_part.text FROM message
        INNER JOIN message_part ON message_part.messageId = message.id
        INNER JOIN mms_part ON mms_part.id = message_part.partId
        WHERE mms_part.text IS NOT NULL AND mms_part.text != ''
    """)
    abstract fun getTexts(): Cursor

    @Query("SELECT MAX(id) FROM message")
    abstract fun getMaxId(): Long?

    @Transaction
    @Query("SELECT * FROM mms_part WHERE id = :id")
    abstract fun getPart(id: Long): PartWithMessages?

    @Transaction
    @Query("""
        SELECT DISTINCT mms_part.* FROM mms_part
        INNER JOIN message_part ON message_part.partId = mms_part.id
        INNER JOIN message ON message.id = message_part.messageId
        WHERE message.threadId = :threadId
        AND (mms_part.type LIKE '%image/%' OR mms_part.type LIKE '%video/%')
        ORDER BY mms_part.id DESC
    """)
    abstract fun observePartsForConversation(threadId: Long): Flowable<List<PartWithMessages>>

    @Query("SELECT * FROM mms_part WHERE messageId = :contentId")
    abstract fun getPartsOfMms(contentId: Long): List<MmsPart>

    @Query("UPDATE message SET seen = 1 WHERE seen = 0 AND threadId IN (:threadIds)")
    protected abstract fun markSeenIn(threadIds: List<Long>)

    @Transaction
    open fun markSeen(threadIds: Collection<Long>) {
        threadIds.chunked(MAX_IN_LIST).forEach(::markSeenIn)
    }

    @Query("UPDATE message SET seen = 1 WHERE seen = 0")
    abstract fun markAllSeen()

    @Query("UPDATE message SET seen = 1, read = 1 WHERE (read = 0 OR seen = 0) AND threadId IN (:threadIds)")
    protected abstract fun markReadIn(threadIds: List<Long>)

    @Transaction
    open fun markRead(threadIds: Collection<Long>) {
        threadIds.chunked(MAX_IN_LIST).forEach(::markReadIn)
    }

    @Query("""
        UPDATE message SET read = 0
        WHERE read = 1 AND id IN (SELECT lastMessageId FROM conversation WHERE id IN (:threadIds))
    """)
    protected abstract fun markLastMessagesUnreadIn(threadIds: List<Long>)

    /** Marks the last message of each of these conversations unread */
    @Transaction
    open fun markLastMessagesUnread(threadIds: Collection<Long>) {
        threadIds.chunked(MAX_IN_LIST).forEach(::markLastMessagesUnreadIn)
    }

    @Query("UPDATE message SET date = :date WHERE id = :id")
    abstract fun setDate(id: Long, date: Long)

    @Query("UPDATE message SET boxId = :boxId WHERE id = :id")
    abstract fun setBoxId(id: Long, boxId: Int)

    @Query("UPDATE message SET boxId = :boxId, errorCode = :errorCode WHERE id = :id")
    abstract fun setFailed(id: Long, boxId: Int, errorCode: Int)

    @Query("UPDATE message SET deliveryStatus = :status, dateSent = :dateSent, read = 1 WHERE id = :id")
    abstract fun setDelivered(id: Long, status: Int, dateSent: Long)

    @Query("""
        UPDATE message SET deliveryStatus = :status, dateSent = :dateSent, read = 1, errorCode = :errorCode
        WHERE id = :id
    """)
    abstract fun setDeliveryFailed(id: Long, status: Int, dateSent: Long, errorCode: Int)

    @Query("UPDATE message SET isEmojiReaction = :isEmojiReaction WHERE id = :id")
    abstract fun setEmojiReaction(id: Long, isEmojiReaction: Boolean)

    @Query("UPDATE message SET isEmojiReaction = 0")
    abstract fun clearEmojiReactionFlags()

    @Upsert
    protected abstract fun upsertMessageRows(messages: List<Message>)

    @Upsert
    abstract fun upsertParts(parts: List<MmsPart>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract fun insertMessageParts(links: List<MessagePart>)

    @Query("DELETE FROM message_part WHERE messageId IN (:messageIds)")
    protected abstract fun deleteMessageParts(messageIds: List<Long>)

    @Query("DELETE FROM message WHERE id IN (:ids)")
    protected abstract fun deleteMessageRows(ids: List<Long>)

    @Query("DELETE FROM emoji_reaction WHERE targetMessageId IN (:messageIds)")
    protected abstract fun deleteReactionsTo(messageIds: List<Long>)

    @Query("UPDATE conversation SET lastMessageId = NULL WHERE lastMessageId IN (:messageIds)")
    protected abstract fun clearLastMessage(messageIds: List<Long>)

    /**
     * Saves the messages along with their parts, replacing the parts stored for them before
     */
    @Transaction
    open fun upsert(messages: List<Message>) {
        upsertMessageRows(messages)
        messages.map { message -> message.id }
            .chunked(MAX_IN_LIST)
            .forEach(::deleteMessageParts)
        upsertParts(messages.flatMap { message -> message.parts })
        insertMessageParts(messages.flatMap { message ->
            message.parts.map { part -> MessagePart(message.id, part.id) }
        })
    }

    fun upsert(message: Message) = upsert(listOf(message))

    /**
     * Deletes the messages, and with them whatever pointed at them: their link to their parts, the
     * reactions they received and their place as the last message of a conversation. Ids are
     * reused once the newest message is gone, so none of these can be left to point at whatever
     * message takes the id next.
     */
    @Transaction
    open fun delete(ids: Collection<Long>) {
        ids.chunked(MAX_IN_LIST).forEach { chunk ->
            deleteMessageParts(chunk)
            deleteReactionsTo(chunk)
            clearLastMessage(chunk)
            deleteMessageRows(chunk)
        }
    }

}
