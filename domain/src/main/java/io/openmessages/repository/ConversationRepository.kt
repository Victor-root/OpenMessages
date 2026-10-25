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
package io.openmessages.repository

import io.openmessages.extensions.Optional
import io.openmessages.model.Conversation
import io.openmessages.model.Recipient
import io.openmessages.model.SearchResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable

interface ConversationRepository {

    fun getConversations(unreadAtTop: Boolean, archived: Boolean = false): Observable<List<Conversation>>

    fun getConversationsSnapshot(unreadAtTop: Boolean): List<Conversation>

    fun getTopConversations(): List<Conversation>

    fun setConversationName(id: Long, name: String): Completable

    fun searchConversations(query: CharSequence): List<SearchResult>

    fun getBlockedConversations(): List<Conversation>

    fun observeBlockedConversations(): Observable<List<Conversation>>

    /**
     * The conversation as it changes, or an empty [Optional] for as long as there is none with
     * this id, which is also what a deleted conversation turns into
     */
    fun observeConversation(threadId: Long): Observable<Optional<Conversation>>

    fun getConversation(threadId: Long): Conversation?

    fun updateSendAsGroup(threadId: Long, sendAsGroup: Boolean): Unit?

    fun getUnseenIds(archived: Boolean = false): List<Long>

    fun getUnreadIds(archived: Boolean = false): List<Long>

    fun getConversationAndLastSenderContactName(threadId: Long): Pair<Conversation?, String?>?

    fun getConversations(vararg threadIds: Long): List<Conversation>

    fun getUnmanagedConversations(): Observable<List<Conversation>>

    fun getRecipients(): List<Recipient>

    fun getUnmanagedRecipients(): Observable<List<Recipient>>

    fun getRecipient(recipientId: Long): Recipient?

    fun getOrCreateConversation(
        threadId: Long,
        sendAsGroup: Boolean = true,
        onCreate: ((Conversation) -> Unit)? = null
    ): Conversation?

    fun createConversation(
        threadId: Long,
        sendAsGroup: Boolean = true,
        onCreate: ((Conversation) -> Unit)? = null
    ): Conversation?

    fun getConversation(addresses: Collection<String>): Conversation?

    fun getOrCreateConversation(
        addresses: Collection<String>, sendAsGroup: Boolean = true
    ): Conversation?

    fun createConversation(
        addresses: Collection<String>, sendAsGroup: Boolean = true
    ): Conversation?

    fun saveDraft(threadId: Long, draft: String)

    fun updateConversations(threadIds: Collection<Long>)

    fun markArchived(vararg threadIds: Long)

    fun markUnarchived(threadIds: Collection<Long>)

    fun markPinned(vararg threadIds: Long)

    fun markUnpinned(vararg threadIds: Long)

    fun markBlocked(threadIds: Collection<Long>, blockingClient: Int, blockReason: String?)

    fun markUnblocked(vararg threadIds: Long)

    fun markFlagged(threadIds: Collection<Long>, flagReason: String?)

    fun markUnflagged(vararg threadIds: Long)

    fun deleteConversations(vararg threadIds: Long)

}
