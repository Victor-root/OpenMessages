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

import android.content.ContentUris
import android.content.Context
import io.openmessages.compat.TelephonyCompat
import io.openmessages.data.BuildConfig
import io.openmessages.database.AppDatabase
import io.openmessages.database.ContactWithNumbers
import io.openmessages.database.ConversationWithRelations
import io.openmessages.database.RecipientWithContact
import io.openmessages.extensions.Optional
import io.openmessages.extensions.map
import io.openmessages.filter.ConversationFilter
import io.openmessages.mapper.CursorToConversation
import io.openmessages.mapper.CursorToRecipient
import io.openmessages.model.Conversation
import io.openmessages.model.Recipient
import io.openmessages.model.SearchResult
import io.openmessages.util.PhoneNumberUtils
import io.openmessages.util.tryOrNull
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ConversationRepositoryImpl @Inject constructor(
    private val context: Context,
    private val db: AppDatabase,
    private val conversationFilter: ConversationFilter,
    private val cursorToConversation: CursorToConversation,
    private val cursorToRecipient: CursorToRecipient,
    private val phoneNumberUtils: PhoneNumberUtils
) : ConversationRepository {

    private val dao = db.conversationDao()
    private val messageDao = db.messageDao()

    override fun getConversations(unreadAtTop: Boolean, archived: Boolean): Observable<List<Conversation>> =
        (if (unreadAtTop) dao.observeListedUnreadFirst(archived) else dao.observeListed(archived))
            .toObservable()
            .map { conversations -> conversations.map(ConversationWithRelations::toModel) }

    override fun getConversationsSnapshot(unreadAtTop: Boolean): List<Conversation> =
        (if (unreadAtTop) dao.getListedUnreadFirst(false) else dao.getListed(false))
            .map(ConversationWithRelations::toModel)

    override fun getTopConversations(): List<Conversation> {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val recentCounts = messageDao.getMessageCountsSince(since).associate { it.threadId to it.count }

        return dao.getRecent(since)
            .map(ConversationWithRelations::toModel)
            .sortedWith(compareByDescending<Conversation> { conversation -> conversation.pinned }
                .thenByDescending { conversation -> recentCounts[conversation.id] ?: 0 })
    }

    override fun setConversationName(id: Long, name: String) =
        Completable.fromAction { dao.setName(id, name) }
            .subscribeOn(Schedulers.io()) // Ensure the operation is performed on a background thread

    override fun searchConversations(query: CharSequence): List<SearchResult> {
        val searchQuery = query.toString()
        val conversations = dao.getSearchable().map(ConversationWithRelations::toModel)
        val conversationsById = conversations.associateBy { conversation -> conversation.id }

        // The text of a message is its body for an SMS and the text of its parts for an MMS, and a
        // message counts once however many of its parts match
        val matchingMessages = (messageDao.getBodies() + messageDao.getPartTexts())
            .filter { message -> message.text?.contains(searchQuery, ignoreCase = true) == true }
            .distinctBy { message -> message.messageId }

        val messagesByConversation = matchingMessages
            .groupingBy { message -> message.threadId }
            .eachCount()
            .mapNotNull { (threadId, count) ->
                conversationsById[threadId]?.let { conversation -> SearchResult(searchQuery, conversation, count) }
            }
            .sortedByDescending { result -> result.messages }

        return conversations
            .filter { conversation -> conversationFilter.filter(conversation, searchQuery) }
            .map { conversation -> SearchResult(searchQuery, conversation, 0) } + messagesByConversation
    }

    override fun getBlockedConversations(): List<Conversation> =
        dao.getBlocked().map(ConversationWithRelations::toModel)

    override fun observeBlockedConversations(): Observable<List<Conversation>> =
        dao.observeBlocked()
            .toObservable()
            .map { conversations -> conversations.map(ConversationWithRelations::toModel) }

    override fun observeConversation(threadId: Long): Observable<Optional<Conversation>> =
        dao.observe(threadId)
            .toObservable()
            .map { conversations -> Optional(conversations.firstOrNull()?.toModel()) }

    override fun getConversation(threadId: Long) = dao.get(threadId)?.toModel()

    override fun updateSendAsGroup(threadId: Long, sendAsGroup: Boolean) =
        dao.setSendAsGroup(threadId, sendAsGroup)

    override fun getUnseenIds(archived: Boolean) = dao.getUnseenIds(archived)

    override fun getUnreadIds(archived: Boolean) = dao.getUnreadIds(archived)

    override fun getConversationAndLastSenderContactName(threadId: Long): Pair<Conversation?, String?>? =
        getConversation(threadId)
            ?.let { conversation ->
                val conversationLastSmsSender: String? = conversation.recipients.find { recipient ->
                    phoneNumberUtils.compare(recipient.address, conversation.lastMessage!!.address)
                }?.contact?.name

                Pair(conversation, conversationLastSmsSender)
            }

    override fun getConversations(vararg threadIds: Long): List<Conversation> =
        dao.get(threadIds.toList()).map(ConversationWithRelations::toModel)

    override fun getUnmanagedConversations(): Observable<List<Conversation>> =
        dao.observeLatest()
            .toObservable()
            .map { conversations -> conversations.map(ConversationWithRelations::toModel) }

    override fun getRecipients(): List<Recipient> = dao.getRecipients().map(RecipientWithContact::toModel)

    override fun getUnmanagedRecipients(): Observable<List<Recipient>> =
        dao.observeRecipientsWithContact()
            .toObservable()
            .map { recipients -> recipients.map(RecipientWithContact::toModel) }

    override fun getRecipient(recipientId: Long): Recipient? = dao.getRecipient(recipientId)?.toModel()

    override fun createConversation(threadId: Long, sendAsGroup: Boolean, onCreate: ((Conversation) -> Unit)?) =
        createConversationFromCp(threadId, sendAsGroup, onCreate)

    override fun getConversation(addresses: Collection<String>): Conversation? =
        dao.getAll()
            .map(ConversationWithRelations::toModel)
            .filter { conversation -> conversation.recipients.size == addresses.size }
            .find { conversation ->
                conversation.recipients.map { it.address }.all { recipientAddress ->
                    addresses.any { phoneNumberUtils.compare(it, recipientAddress) }
                }
            }

    override fun createConversation(addresses: Collection<String>, sendAsGroup: Boolean) =
        TelephonyCompat.getOrCreateThreadId(context, addresses.toSet())
            .takeIf { it != 0L }
            ?.let { providerThreadId ->
                createConversationFromCp(providerThreadId, sendAsGroup) ?:
                    createEmptyConversation(providerThreadId, addresses, sendAsGroup)
            }

    override fun getOrCreateConversation(threadId: Long, sendAsGroup: Boolean, onCreate: ((Conversation) -> Unit)?) =
        getConversation(threadId) ?: createConversation(threadId, sendAsGroup, onCreate)

    override fun getOrCreateConversation(addresses: Collection<String>, sendAsGroup: Boolean) =
        // What is already known wins, and the provider is only asked for addresses never seen
        // before. Asking it every time looks tidier but is not safe: it does not always answer the
        // same thread twice for a conversation that has no message in it yet, so on a device that
        // behaves that way every call would hand back a new thread and leave a trail of empty
        // conversations behind it.
        getConversation(addresses) ?: createConversation(addresses, sendAsGroup)

    override fun saveDraft(threadId: Long, draft: String) =
        dao.setDraft(threadId, draft, System.currentTimeMillis())

    override fun updateConversations(threadIds: Collection<Long>) {
        // This is what gives a conversation the last message the list requires to show it, so
        // an empty set here means whatever was just sent leaves its conversation invisible.
        if (BuildConfig.DEBUG) Timber.v("refreshing the last message of conversations $threadIds")

        dao.updateLastMessage(threadIds)
    }

    override fun markArchived(vararg threadIds: Long) = dao.setArchived(threadIds.toList(), true)

    override fun markUnarchived(threadIds: Collection<Long>) = dao.setArchived(threadIds, false)

    override fun markPinned(vararg threadIds: Long) = dao.setPinned(threadIds.toList(), true)

    override fun markUnpinned(vararg threadIds: Long) = dao.setPinned(threadIds.toList(), false)

    override fun markBlocked(threadIds: Collection<Long>, blockingClient: Int, blockReason: String?) =
        dao.markBlocked(threadIds, blockingClient, blockReason)

    override fun markUnblocked(vararg threadIds: Long) = dao.markUnblocked(threadIds.toList())

    override fun markFlagged(threadIds: Collection<Long>, flagReason: String?) =
        // Already-flagged conversations used to be filtered out here, which meant a second flag
        // for a different reason left the banner explaining the first one. They are included now
        // so the reason shown is the one that flagged the conversation most recently.
        dao.markFlagged(threadIds, flagReason)

    override fun markUnflagged(vararg threadIds: Long) = dao.markUnflagged(threadIds.toList())

    override fun deleteConversations(vararg threadIds: Long) {
        dao.delete(threadIds.toList(), messageDao)

        threadIds.forEach {
            context.contentResolver.delete(
                ContentUris.withAppendedId(TelephonyCompat.THREADS_CONTENT_URI, it),
                null,
                null
            )
        }
    }

    /**
     * Returns a [Conversation] from the system SMS ContentProvider, based on the [threadId]
     *
     * It should be noted that even if we have a valid [threadId], that does not guarantee that
     * we can return a [Conversation]. On some devices, the ContentProvider won't return the
     * conversation unless it contains at least 1 message
     */
    private fun createConversationFromCp(
        threadId: Long,
        sendAsGroup: Boolean,
        onCreate: ((Conversation) -> Unit)? = null
    ) =
        tryOrNull(true) {
            cursorToConversation.getConversationsCursor()
                ?.map(cursorToConversation::map)
                ?.firstOrNull { conversation -> conversation.id == threadId }
                ?.also { conversation ->
                    val contacts = db.contactDao().getAll().map(ContactWithNumbers::toModel)

                    // match recipients from provider to the known contacts
                    val matchedRecipients = conversation.recipients
                        .mapNotNull { recipient ->
                            // map the recipient cursor to a list of recipients
                            cursorToRecipient.getRecipientCursor(recipient.id)?.use { cursor ->
                                cursor.map { cursorToRecipient.map(it) }
                            }
                        }
                        .flatten()
                        .map { recipient ->
                            recipient.apply {
                                contact = contacts.firstOrNull { contact ->
                                    contact.numbers.any {
                                        phoneNumberUtils.compare(it.address, address)
                                    }
                                }
                            }
                        }

                    conversation.apply {
                        recipients = matchedRecipients

                        this.sendAsGroup =
                            if (recipients.size <= 1) false
                            else sendAsGroup

                        lastMessage = messageDao.getNewest(threadId)?.toModel()
                    }

                    // Let the caller seed initial state (e.g. blocked/flagged) so it is part of
                    // the very first commit, instead of a follow-up transaction that a live inbox
                    // query would briefly observe (which caused blocked spam to flash in the list).
                    onCreate?.invoke(conversation)

                    // The list only shows a conversation that has recipients and a last
                    // message, so both counts decide whether it is ever seen.
                    if (BuildConfig.DEBUG) Timber.v("conversation $threadId built from the provider: " +
                            "${conversation.recipients.size} recipient(s), " +
                            "last message ${conversation.lastMessage?.id ?: "none"}, " +
                            "archived ${conversation.archived}")

                    dao.upsert(conversation)
                }
        }

    /**
     * In some cases [createConversationFromCp] will return null if there are no messages present in the convo.
     * In order to allow the conversation to be accessed
     * we need to create an empty conversation in the database to match the conversation created in the content provider.
     *
     * This is a bit of a hack, but is necessary on devices running HyperOS or variants.
     */
    private fun createEmptyConversation(threadId: Long, addresses: Collection<String>, sendAsGroup: Boolean): Conversation {
        val contacts = db.contactDao().getAll().map(ContactWithNumbers::toModel)
        val matchedRecipients = addresses.map { address ->
            Recipient().apply {
                this.address = address
                contact = contacts.firstOrNull { contact ->
                    contact.numbers.any {
                        phoneNumberUtils.compare(it.address, address)
                    }
                }
            }
        }
        val conversation = Conversation().apply {
            id = threadId
            recipients = matchedRecipients
            this.sendAsGroup =
                if (recipients.size <= 1) false
                else sendAsGroup
        }
        if (BuildConfig.DEBUG) Timber.v("conversation $threadId was not in the provider, built from the addresses " +
                "given: ${matchedRecipients.size} recipient(s)")

        dao.upsert(conversation)
        return conversation
    }

}
