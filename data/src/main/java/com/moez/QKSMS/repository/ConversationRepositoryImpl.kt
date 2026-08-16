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
import io.openmessages.extensions.anyOf
import io.openmessages.extensions.asObservable
import io.openmessages.extensions.map
import io.openmessages.filter.ConversationFilter
import io.openmessages.mapper.CursorToConversation
import io.openmessages.mapper.CursorToRecipient
import io.openmessages.model.Contact
import io.openmessages.model.Conversation
import io.openmessages.model.Message
import io.openmessages.model.Recipient
import io.openmessages.model.SearchResult
import io.openmessages.util.PhoneNumberUtils
import io.openmessages.util.tryOrNull
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.realm.Case
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ConversationRepositoryImpl @Inject constructor(
    private val context: Context,
    private val conversationFilter: ConversationFilter,
    private val cursorToConversation: CursorToConversation,
    private val cursorToRecipient: CursorToRecipient,
    private val phoneNumberUtils: PhoneNumberUtils
) : ConversationRepository {
    private fun getConversationsBase(
        realm: Realm,
        unreadAtTop: Boolean,
        archived: Boolean
    ): RealmQuery<Conversation> {
        val sortOrder = mutableListOf("pinned", "draft", "lastMessage.date")
        val sortDirections = mutableListOf(Sort.DESCENDING, Sort.DESCENDING, Sort.DESCENDING)

        if (unreadAtTop) {
            sortOrder.add(0, "lastMessage.read")
            sortDirections.add(0, Sort.ASCENDING)
        }

        return realm
            .where(Conversation::class.java)
            .notEqualTo("id", 0L)
            .equalTo("archived", archived)
            .equalTo("blocked", false)
            .isNotEmpty("recipients")
            .beginGroup()
            .isNotNull("lastMessage")
            .or()
            .isNotEmpty("draft")
            .endGroup()
            .sort(sortOrder.toTypedArray(), sortDirections.toTypedArray())
    }

    override fun getConversations(
        unreadAtTop: Boolean,
        archived: Boolean
    ): RealmResults<Conversation> =
        getConversationsBase(Realm.getDefaultInstance(), unreadAtTop, archived)
            .findAllAsync()

    override fun getConversationsSnapshot(unreadAtTop: Boolean): List<Conversation> =
        Realm.getDefaultInstance().use { realm ->
            getConversationsBase(realm, unreadAtTop, false)
                .findAll()
                .let(realm::copyFromRealm)
        }

    override fun getTopConversations() =
        Realm.getDefaultInstance().use { realm ->
            realm.where(Conversation::class.java)
                .notEqualTo("id", 0L)
                .isNotNull("lastMessage")
                .beginGroup()
                .equalTo("pinned", true)
                .or()
                .greaterThan(
                    "lastMessage.date",
                    System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
                )
                .endGroup()
                .equalTo("archived", false)
                .equalTo("blocked", false)
                .isNotEmpty("recipients")
                .findAll()
                .let(realm::copyFromRealm)
                .sortedWith(compareByDescending<Conversation> {
                        conversation -> conversation.pinned
                }
                    .thenByDescending { conversation ->
                        realm.where(Message::class.java)
                            .equalTo("threadId", conversation.id)
                            .greaterThan(
                                "date",
                                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
                            )
                            .count()
                    }
                )
        }

    override fun setConversationName(id: Long, name: String) =
        Completable.fromAction {
            Realm.getDefaultInstance().use { realm ->
                realm.executeTransaction {
                    realm.where(Conversation::class.java)
                        .equalTo("id", id)
                        .findFirst()
                        ?.name = name
                }
            }
        }.subscribeOn(Schedulers.io()) // Ensure the operation is performed on a background thread

    override fun searchConversations(query: CharSequence): List<SearchResult> {
        val realm = Realm.getDefaultInstance()

        val searchQuery = query.toString()
        val conversations = realm.copyFromRealm(realm
            .where(Conversation::class.java)
            .notEqualTo("id", 0L)
            .isNotNull("lastMessage")
            .equalTo("blocked", false)
            .isNotEmpty("recipients")
            .sort("pinned", Sort.DESCENDING, "lastMessage.date", Sort.DESCENDING)
            .findAll())

        val messagesByConversation = realm.copyFromRealm(realm
            .where(Message::class.java)
            .beginGroup()
            .contains("body", searchQuery, Case.INSENSITIVE)
            .or()
            .contains("parts.text", searchQuery, Case.INSENSITIVE)
            .endGroup()
            .findAll())
            .groupBy { message -> message.threadId }
            .filter { (threadId, _) -> conversations.firstOrNull { it.id == threadId } != null }
            .map { (threadId, messages) -> Pair(conversations.first { it.id == threadId }, messages.size) }
            .map { (conversation, messages) -> SearchResult(searchQuery, conversation, messages) }
            .sortedByDescending { result -> result.messages }
            .toList()

        realm.close()

        return conversations
            .filter { conversation -> conversationFilter.filter(conversation, searchQuery) }
            .map {
                    conversation -> SearchResult(searchQuery, conversation, 0)
            } + messagesByConversation
    }

    override fun getBlockedConversations(): RealmResults<Conversation> =
        Realm.getDefaultInstance()
            .where(Conversation::class.java)
            .equalTo("blocked", true)
            .sort(
                arrayOf("lastMessage.date"),
                arrayOf(Sort.DESCENDING)
            )
            .findAll()

    override fun getBlockedConversationsAsync(): RealmResults<Conversation> =
        Realm.getDefaultInstance()
            .where(Conversation::class.java)
            .equalTo("blocked", true)
            .sort(
                arrayOf("lastMessage.date"),
                arrayOf(Sort.DESCENDING)
            )
            .findAllAsync()

    override fun getConversationAsync(threadId: Long): Conversation =
        Realm.getDefaultInstance()
            .where(Conversation::class.java)
            .equalTo("id", threadId)
            .findFirstAsync()

    override fun getConversation(threadId: Long) =
        tryOrNull(true) {
            Realm.getDefaultInstance()
                .apply { refresh() }
                .where(Conversation::class.java)
                .equalTo("id", threadId)
                .findFirst()
        }

    override fun updateSendAsGroup(threadId: Long, sendAsGroup: Boolean) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            realm.where(Conversation::class.java)
                .equalTo("id", threadId)
                .findFirst()
                ?.let { conversation ->
                    realm.executeTransaction { conversation.sendAsGroup = sendAsGroup }
                }
        }

    override fun getUnseenIds(archived: Boolean) =
        ArrayList<Long>().apply {
            Realm.getDefaultInstance()
                .where(Conversation::class.java)
                .notEqualTo("id", 0L)
                .equalTo("archived", archived)
                .equalTo("blocked", false)
                .equalTo("lastMessage.seen", false)
                .sort(
                    arrayOf("lastMessage.date"),
                    arrayOf(Sort.DESCENDING)
                )
                .findAllAsync()
                .forEach { conversation -> add(conversation.id) }
        }


    override fun getUnreadIds(archived: Boolean) =
        ArrayList<Long>().apply {
            Realm.getDefaultInstance()
                .where(Conversation::class.java)
                .notEqualTo("id", 0L)
                .equalTo("archived", archived)
                .equalTo("blocked", false)
                .equalTo("lastMessage.read", false)
                .sort(
                    arrayOf("lastMessage.date"),
                    arrayOf(Sort.DESCENDING)
                )
                .findAllAsync()
                .forEach { conversation -> add(conversation.id) }
        }

    override fun getConversationAndLastSenderContactName(threadId: Long): Pair<Conversation?, String?>? =
        Realm.getDefaultInstance()
            .apply { refresh() }
            .where(Conversation::class.java)
            .equalTo("id", threadId)
            .findFirst()
            ?.let { conversation ->
                val conversationLastSmsSender: String? = conversation.recipients.find { recipient ->
                    phoneNumberUtils.compare(recipient.address, conversation.lastMessage!!.address)
                }?.contact?.name

                Pair(conversation, conversationLastSmsSender)
            }

    override fun getConversations(vararg threadIds: Long): RealmResults<Conversation> =
        Realm.getDefaultInstance()
            .where(Conversation::class.java)
            .anyOf("id", threadIds)
            .findAll()

    override fun getUnmanagedConversations(): Observable<List<Conversation>> =
        Realm.getDefaultInstance().let { realm->
            realm.where(Conversation::class.java)
                .sort("lastMessage.date", Sort.DESCENDING)
                .notEqualTo("id", 0L)
                .isNotNull("lastMessage")
                .equalTo("archived", false)
                .equalTo("blocked", false)
                .isNotEmpty("recipients")
                .limit(5)
                .findAllAsync()
                .asObservable()
                .filter { it.isLoaded }
                .filter { it.isValid }
                .map { realm.copyFromRealm(it) }
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
        }

    override fun getRecipients(): RealmResults<Recipient> =
        Realm.getDefaultInstance()
            .where(Recipient::class.java)
            .findAll()

    override fun getUnmanagedRecipients(): Observable<List<Recipient>> =
        Realm.getDefaultInstance().let { realm ->
            realm.where(Recipient::class.java)
                .isNotNull("contact")
                .findAllAsync()
                .asObservable()
                .filter { it.isLoaded && it.isValid }
                .map { realm.copyFromRealm(it) }
                .subscribeOn(AndroidSchedulers.mainThread())
        }

    override fun getRecipient(recipientId: Long): Recipient? =
        Realm.getDefaultInstance()
            .where(Recipient::class.java)
            .equalTo("id", recipientId)
            .findFirst()

    override fun createConversation(threadId: Long, sendAsGroup: Boolean, onCreate: ((Conversation) -> Unit)?) =
        createConversationFromCp(threadId, sendAsGroup, onCreate)


    override fun getConversation(recipients: Collection<String>): Conversation? =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.where(Conversation::class.java)
                .findAll()
                .filter { conversation -> conversation.recipients.size == recipients.size }
                .find { conversation ->
                    conversation.recipients.map { it.address }.all { recipientAddress ->
                        recipients.any { phoneNumberUtils.compare(it, recipientAddress) }
                    }
                }
                ?.let { realm.copyFromRealm(it) }
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
        // Which thread these addresses belong to is the provider's to answer, and it answers it
        // again, on its own, when the message is written. Asking it here is what keeps the
        // conversation opened on screen and the message that follows on the same thread.
        //
        // Searching Realm by address instead, as this did, compares numbers loosely enough to
        // settle on a conversation the provider does not agree with. The screen then watches one
        // thread while the message lands on another, and stays empty until it is left and reopened
        // on the right one. Realm is still searched, by thread id, so a conversation already known
        // is not rebuilt; only the question of which thread that is has changed hands.
        TelephonyCompat.getOrCreateThreadId(context, addresses.toSet())
            .takeIf { it != 0L }
            ?.let { providerThreadId ->
                getConversation(providerThreadId)
                    ?: createConversationFromCp(providerThreadId, sendAsGroup)
                    ?: createEmptyConversation(providerThreadId, addresses, sendAsGroup)
            }
            ?: getConversation(addresses)

    override fun saveDraft(threadId: Long, draft: String) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            val conversation = realm.where(Conversation::class.java)
                .equalTo("id", threadId)
                .findFirst()

            realm.executeTransaction {
                conversation?.takeIf { it.isValid }?.draft = draft
                conversation?.takeIf { it.isValid }?.draftDate = System.currentTimeMillis()
            }
        }

    override fun updateConversations(threadIds: Collection<Long>) =
        Realm.getDefaultInstance().use { realm ->
            // This is what gives a conversation the last message the list requires to show it, so
            // an empty set here means whatever was just sent leaves its conversation invisible.
            Timber.v("refreshing the last message of conversations $threadIds")

            realm.refresh()

            realm.where(Conversation::class.java)
                .anyOf("id", threadIds.toLongArray())
                .findAll()
                ?.map { conversation ->
                    Pair(
                        conversation,
                        realm.where(Message::class.java)
                            .equalTo("threadId", conversation.id)
                            .sort("date", Sort.DESCENDING)
                            .findFirst()
                    )
                }
                ?.let { conversationAndMessages ->
                    realm.executeTransaction {
                        conversationAndMessages.forEach { (conversation, message) ->
                            conversation.lastMessage = message
                        }
                    }
                }

            Unit
        }

    override fun markArchived(vararg threadIds: Long) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
                .findAll()

            realm.executeTransaction { conversations.forEach { it.archived = true } }
        }

    override fun markUnarchived(threadIds: Collection<Long>) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds.toLongArray())
                .findAll()

            realm.executeTransaction { conversations.forEach { it.archived = false } }
        }

    override fun markPinned(vararg threadIds: Long) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
                .findAll()

            realm.executeTransaction { conversations.forEach { it.pinned = true } }
        }

    override fun markUnpinned(vararg threadIds: Long) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
                .findAll()

            realm.executeTransaction { conversations.forEach { it.pinned = false } }
        }

    override fun markBlocked(threadIds: Collection<Long>, blockingClient: Int, blockReason: String?) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds.toLongArray())
                .equalTo("blocked", false)
                .findAll()

            realm.executeTransaction {
                conversations.forEach { conversation ->
                    conversation.blocked = true
                    conversation.blockingClient = blockingClient
                    conversation.blockReason = blockReason
                    // Blocking supersedes the soft "suspected spam" flag
                    conversation.flagged = false
                    conversation.flagReason = null
                }
            }
        }

    override fun markUnblocked(vararg threadIds: Long) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
                .findAll()

            realm.executeTransaction {
                conversations.forEach { conversation ->
                    conversation.blocked = false
                    conversation.blockingClient = null
                    conversation.blockReason = null
                }
            }
        }

    override fun markFlagged(threadIds: Collection<Long>, flagReason: String?) =
        Realm.getDefaultInstance().use { realm ->
            // Already-flagged conversations used to be filtered out here, which meant a second flag
            // for a different reason left the banner explaining the first one. They are included now
            // so the reason shown is the one that flagged the conversation most recently.
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds.toLongArray())
                .findAll()

            realm.executeTransaction {
                conversations.forEach { conversation ->
                    conversation.flagged = true
                    // A flag that carries no reason leaves the existing one in place rather than
                    // replacing an explanation with nothing.
                    if (flagReason != null) conversation.flagReason = flagReason
                }
            }
        }

    override fun markUnflagged(vararg threadIds: Long) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
                .equalTo("flagged", true)
                .findAll()

            realm.executeTransaction {
                conversations.forEach { conversation ->
                    conversation.flagged = false
                    conversation.flagReason = null
                }
            }
        }

    override fun deleteConversations(vararg threadIds: Long) {
        Realm.getDefaultInstance().use { realm ->
            val conversation = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
                .findAll()
            val messages = realm.where(Message::class.java)
                .anyOf("threadId", threadIds)
                .findAll()

            realm.executeTransaction {
                conversation.deleteAllFromRealm()
                messages.deleteAllFromRealm()
            }
        }

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
                    Realm.getDefaultInstance().use { realm ->
                        val realmContacts = realm.where(Contact::class.java).findAll()

                        // match recipients from provider to recipients in realm
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
                                    contact = realmContacts.firstOrNull { realmContact ->
                                        realmContact.numbers.any {
                                            phoneNumberUtils.compare(it.address, address)
                                        }
                                    }
                                    ?.let { realm.copyFromRealm(it) }
                                }
                            }

                        conversation.apply {
                            recipients.clear()
                            recipients.addAll(matchedRecipients)

                            this.sendAsGroup =
                                if (recipients.size <= 1) false
                                else sendAsGroup

                            lastMessage = realm.where(Message::class.java)
                                .equalTo("threadId", threadId)
                                .sort("date", Sort.DESCENDING)
                                .findFirst()
                        }

                        // Let the caller seed initial state (e.g. blocked/flagged) so it is part of
                        // the very first commit, instead of a follow-up transaction that a live inbox
                        // query would briefly observe (which caused blocked spam to flash in the list).
                        onCreate?.invoke(conversation)

                        // The list only shows a conversation that has recipients and a last
                        // message, so both counts decide whether it is ever seen.
                        Timber.v("conversation $threadId built from the provider: " +
                                "${conversation.recipients.size} recipient(s), " +
                                "last message ${conversation.lastMessage?.id ?: "none"}, " +
                                "archived ${conversation.archived}")

                        realm.executeTransaction { it.insertOrUpdate(conversation) }
                    }
                }
        }

    /**
     * In some cases [createConversationFromCp] will return null if there are no messages present in the convo.
     * In order to allow the conversation to be accessed
     * we need to create an empty conversation in Realm to match the conversation created in the content provider.
     *
     * This is a bit of a hack, but is necessary on devices running HyperOS or variants.
     */
    private fun createEmptyConversation(threadId: Long, addresses: Collection<String>, sendAsGroup: Boolean): Conversation {
        Realm.getDefaultInstance().use { realm ->
            val realmContacts = realm.where(Contact::class.java).findAll()
            val matchedRecipients = addresses.map { address ->
                Recipient().apply {
                    this.address = address
                    contact = realmContacts.firstOrNull { realmContact ->
                            realmContact.numbers.any {
                                phoneNumberUtils.compare(it.address, address)
                            }
                        }
                        ?.let { realm.copyFromRealm(it) }
                }
            }
            val conversation = Conversation().apply {
                id = threadId
                recipients.clear()
                recipients.addAll(matchedRecipients)
                this.sendAsGroup =
                    if (recipients.size <= 1) false
                    else sendAsGroup
            }
            Timber.v("conversation $threadId was not in the provider, built from the addresses " +
                    "given: ${matchedRecipients.size} recipient(s)")

            realm.executeTransaction { it.copyToRealmOrUpdate(conversation) }
            return conversation
        }
    }

}
