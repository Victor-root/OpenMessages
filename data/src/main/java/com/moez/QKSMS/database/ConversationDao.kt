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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.openmessages.model.Conversation
import io.openmessages.model.Recipient
import io.reactivex.rxjava3.core.Flowable

/**
 * The conversations the inbox shows: a conversation with an id, recipients and either a message or
 * a draft, which is neither blocked nor in the other of the inbox and the archive
 */
private const val LISTED = """
    FROM conversation
    LEFT JOIN message AS lastMessage ON lastMessage.id = conversation.lastMessageId
    WHERE conversation.id != 0
    AND conversation.archived = :archived
    AND conversation.blocked = 0
    AND EXISTS (SELECT 1 FROM conversation_recipient WHERE conversationId = conversation.id)
    AND (lastMessage.id IS NOT NULL OR conversation.draft != '')
"""

/** The order of the inbox: pinned first, then drafts, then the most recent */
private const val LISTED_ORDER = "conversation.pinned DESC, conversation.draft DESC, lastMessage.date DESC"

/** The conversations that have recipients and a last message, blocked or archived or not */
private const val WITH_MESSAGE = """
    FROM conversation
    INNER JOIN message AS lastMessage ON lastMessage.id = conversation.lastMessageId
    WHERE conversation.id != 0
    AND EXISTS (SELECT 1 FROM conversation_recipient WHERE conversationId = conversation.id)
"""

@Dao
abstract class ConversationDao {

    @Transaction
    @Query("SELECT conversation.* $LISTED ORDER BY $LISTED_ORDER")
    abstract fun observeListed(archived: Boolean): Flowable<List<ConversationWithRelations>>

    @Transaction
    @Query("SELECT conversation.* $LISTED ORDER BY lastMessage.read ASC, $LISTED_ORDER")
    abstract fun observeListedUnreadFirst(archived: Boolean): Flowable<List<ConversationWithRelations>>

    @Transaction
    @Query("SELECT conversation.* $LISTED ORDER BY $LISTED_ORDER")
    abstract fun getListed(archived: Boolean): List<ConversationWithRelations>

    @Transaction
    @Query("SELECT conversation.* $LISTED ORDER BY lastMessage.read ASC, $LISTED_ORDER")
    abstract fun getListedUnreadFirst(archived: Boolean): List<ConversationWithRelations>

    /** The conversations that are pinned or had a message since [since], outside the archive */
    @Transaction
    @Query("""
        SELECT conversation.* $WITH_MESSAGE
        AND conversation.archived = 0 AND conversation.blocked = 0
        AND (conversation.pinned = 1 OR lastMessage.date > :since)
    """)
    abstract fun getRecent(since: Long): List<ConversationWithRelations>

    /** The conversations a search looks through */
    @Transaction
    @Query("""
        SELECT conversation.* $WITH_MESSAGE
        AND conversation.blocked = 0
        ORDER BY conversation.pinned DESC, lastMessage.date DESC
    """)
    abstract fun getSearchable(): List<ConversationWithRelations>

    /** The five conversations with the most recent messages, outside the archive */
    @Transaction
    @Query("""
        SELECT conversation.* $WITH_MESSAGE
        AND conversation.archived = 0 AND conversation.blocked = 0
        ORDER BY lastMessage.date DESC
        LIMIT 5
    """)
    abstract fun observeLatest(): Flowable<List<ConversationWithRelations>>

    @Transaction
    @Query("""
        SELECT conversation.* FROM conversation
        LEFT JOIN message AS lastMessage ON lastMessage.id = conversation.lastMessageId
        WHERE conversation.blocked = 1
        ORDER BY lastMessage.date DESC
    """)
    abstract fun getBlocked(): List<ConversationWithRelations>

    @Transaction
    @Query("""
        SELECT conversation.* FROM conversation
        LEFT JOIN message AS lastMessage ON lastMessage.id = conversation.lastMessageId
        WHERE conversation.blocked = 1
        ORDER BY lastMessage.date DESC
    """)
    abstract fun observeBlocked(): Flowable<List<ConversationWithRelations>>

    /** The conversation, as a list of at most one */
    @Transaction
    @Query("SELECT * FROM conversation WHERE id = :id")
    abstract fun observe(id: Long): Flowable<List<ConversationWithRelations>>

    @Transaction
    @Query("SELECT * FROM conversation WHERE id = :id")
    abstract fun get(id: Long): ConversationWithRelations?

    @Transaction
    @Query("SELECT * FROM conversation WHERE id IN (:ids)")
    protected abstract fun getIn(ids: List<Long>): List<ConversationWithRelations>

    @Transaction
    open fun get(ids: Collection<Long>): List<ConversationWithRelations> {
        return ids.chunked(MAX_IN_LIST).flatMap(::getIn)
    }

    @Transaction
    @Query("SELECT * FROM conversation")
    abstract fun getAll(): List<ConversationWithRelations>

    /** The ids of the conversations whose last message has not been seen, newest first */
    @Query("""
        SELECT conversation.id FROM conversation
        INNER JOIN message AS lastMessage ON lastMessage.id = conversation.lastMessageId
        WHERE conversation.id != 0 AND conversation.archived = :archived AND conversation.blocked = 0
        AND lastMessage.seen = 0
        ORDER BY lastMessage.date DESC
    """)
    abstract fun getUnseenIds(archived: Boolean): List<Long>

    /** The ids of the conversations whose last message has not been read, newest first */
    @Query("""
        SELECT conversation.id FROM conversation
        INNER JOIN message AS lastMessage ON lastMessage.id = conversation.lastMessageId
        WHERE conversation.id != 0 AND conversation.archived = :archived AND conversation.blocked = 0
        AND lastMessage.read = 0
        ORDER BY lastMessage.date DESC
    """)
    abstract fun getUnreadIds(archived: Boolean): List<Long>

    /** How many conversations in the inbox have an unread last message */
    @Query("""
        SELECT COUNT(*) FROM conversation
        INNER JOIN message AS lastMessage ON lastMessage.id = conversation.lastMessageId
        WHERE conversation.archived = 0 AND conversation.blocked = 0 AND lastMessage.read = 0
    """)
    abstract fun getUnreadCount(): Long

    @Transaction
    @Query("SELECT * FROM recipient")
    abstract fun getRecipients(): List<RecipientWithContact>

    @Transaction
    @Query("SELECT * FROM recipient WHERE contactLookupKey IN (SELECT lookupKey FROM contact)")
    abstract fun observeRecipientsWithContact(): Flowable<List<RecipientWithContact>>

    @Transaction
    @Query("SELECT * FROM recipient WHERE id = :id")
    abstract fun getRecipient(id: Long): RecipientWithContact?

    @Query("UPDATE conversation SET name = :name WHERE id = :id")
    abstract fun setName(id: Long, name: String)

    @Query("UPDATE conversation SET sendAsGroup = :sendAsGroup WHERE id = :id")
    abstract fun setSendAsGroup(id: Long, sendAsGroup: Boolean)

    @Query("UPDATE conversation SET draft = :draft, draftDate = :draftDate WHERE id = :id")
    abstract fun setDraft(id: Long, draft: String, draftDate: Long)

    /** Points each of these conversations at its newest message, or at none when it has none */
    @Query("""
        UPDATE conversation
        SET lastMessageId = (SELECT id FROM message WHERE threadId = conversation.id ORDER BY date DESC LIMIT 1)
        WHERE id IN (:ids)
    """)
    protected abstract fun updateLastMessageIn(ids: List<Long>)

    @Transaction
    open fun updateLastMessage(ids: Collection<Long>) {
        ids.chunked(MAX_IN_LIST).forEach(::updateLastMessageIn)
    }

    /** Points every conversation at its newest message, or at none when it has none */
    @Query("""
        UPDATE conversation
        SET lastMessageId = (SELECT id FROM message WHERE threadId = conversation.id ORDER BY date DESC LIMIT 1)
    """)
    abstract fun updateAllLastMessages()

    @Query("UPDATE conversation SET archived = :archived WHERE id IN (:ids)")
    protected abstract fun setArchivedIn(ids: List<Long>, archived: Boolean)

    @Transaction
    open fun setArchived(ids: Collection<Long>, archived: Boolean) {
        ids.chunked(MAX_IN_LIST).forEach { chunk -> setArchivedIn(chunk, archived) }
    }

    @Query("UPDATE conversation SET pinned = :pinned WHERE id IN (:ids)")
    protected abstract fun setPinnedIn(ids: List<Long>, pinned: Boolean)

    @Transaction
    open fun setPinned(ids: Collection<Long>, pinned: Boolean) {
        ids.chunked(MAX_IN_LIST).forEach { chunk -> setPinnedIn(chunk, pinned) }
    }

    /** Blocking supersedes the soft "suspected spam" flag, so it is cleared along the way */
    @Query("""
        UPDATE conversation
        SET blocked = 1, blockingClient = :blockingClient, blockReason = :blockReason, flagged = 0, flagReason = NULL
        WHERE blocked = 0 AND id IN (:ids)
    """)
    protected abstract fun markBlockedIn(ids: List<Long>, blockingClient: Int, blockReason: String?)

    @Transaction
    open fun markBlocked(ids: Collection<Long>, blockingClient: Int, blockReason: String?) {
        ids.chunked(MAX_IN_LIST).forEach { chunk -> markBlockedIn(chunk, blockingClient, blockReason) }
    }

    @Query("UPDATE conversation SET blocked = 0, blockingClient = NULL, blockReason = NULL WHERE id IN (:ids)")
    protected abstract fun markUnblockedIn(ids: List<Long>)

    @Transaction
    open fun markUnblocked(ids: Collection<Long>) {
        ids.chunked(MAX_IN_LIST).forEach(::markUnblockedIn)
    }

    /** A flag that carries no reason leaves the existing one in place */
    @Query("""
        UPDATE conversation SET flagged = 1, flagReason = COALESCE(:flagReason, flagReason)
        WHERE id IN (:ids)
    """)
    protected abstract fun markFlaggedIn(ids: List<Long>, flagReason: String?)

    @Transaction
    open fun markFlagged(ids: Collection<Long>, flagReason: String?) {
        ids.chunked(MAX_IN_LIST).forEach { chunk -> markFlaggedIn(chunk, flagReason) }
    }

    @Query("UPDATE conversation SET flagged = 0, flagReason = NULL WHERE flagged = 1 AND id IN (:ids)")
    protected abstract fun markUnflaggedIn(ids: List<Long>)

    @Transaction
    open fun markUnflagged(ids: Collection<Long>) {
        ids.chunked(MAX_IN_LIST).forEach(::markUnflaggedIn)
    }

    @Upsert
    protected abstract fun upsertConversationRows(conversations: List<Conversation>)

    @Upsert
    protected abstract fun upsertRecipientRows(recipients: List<Recipient>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract fun insertConversationRecipients(links: List<ConversationRecipient>)

    @Query("DELETE FROM conversation_recipient WHERE conversationId IN (:ids)")
    protected abstract fun deleteConversationRecipients(ids: List<Long>)

    @Query("DELETE FROM conversation WHERE id IN (:ids)")
    protected abstract fun deleteConversationRows(ids: List<Long>)

    @Query("SELECT id FROM message WHERE threadId IN (:threadIds)")
    protected abstract fun getMessageIds(threadIds: List<Long>): List<Long>

    /**
     * Saves the conversations along with their recipients, replacing the recipients stored for
     * them before. The last message is referred to, not saved: it is stored on its own.
     */
    @Transaction
    open fun upsert(conversations: List<Conversation>) {
        conversations.forEach { conversation -> conversation.lastMessageId = conversation.lastMessage?.id }
        upsertConversationRows(conversations)

        conversations.map { conversation -> conversation.id }
            .chunked(MAX_IN_LIST)
            .forEach(::deleteConversationRecipients)
        upsertRecipients(conversations.flatMap { conversation -> conversation.recipients })
        insertConversationRecipients(conversations.flatMap { conversation ->
            conversation.recipients.mapIndexed { position, recipient ->
                ConversationRecipient(conversation.id, recipient.id, position)
            }
        })
    }

    fun upsert(conversation: Conversation) = upsert(listOf(conversation))

    /**
     * Saves the recipients, each linked to the contact it holds, or to none
     */
    fun upsertRecipients(recipients: List<Recipient>) {
        recipients.forEach { recipient -> recipient.contactLookupKey = recipient.contact?.lookupKey }
        upsertRecipientRows(recipients)
    }

    /**
     * Deletes the conversations with their messages
     */
    @Transaction
    open fun delete(ids: Collection<Long>, messages: MessageDao) {
        ids.chunked(MAX_IN_LIST).forEach { chunk ->
            messages.delete(getMessageIds(chunk))
            deleteConversationRecipients(chunk)
            deleteConversationRows(chunk)
        }
    }

}
