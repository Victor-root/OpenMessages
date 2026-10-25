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

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.Telephony
import io.openmessages.database.AppDatabase
import io.openmessages.database.ConversationWithRelations
import io.openmessages.database.RecipientWithContact
import io.openmessages.extensions.forEach
import io.openmessages.extensions.map
import io.openmessages.interactor.DeduplicateMessages
import io.openmessages.manager.KeyManager
import io.openmessages.mapper.CursorToContact
import io.openmessages.mapper.CursorToContactGroup
import io.openmessages.mapper.CursorToContactGroupMember
import io.openmessages.mapper.CursorToConversation
import io.openmessages.mapper.CursorToMessage
import io.openmessages.mapper.CursorToPart
import io.openmessages.mapper.CursorToRecipient
import io.openmessages.model.Contact
import io.openmessages.model.ContactGroup
import io.openmessages.model.Conversation
import io.openmessages.model.Message
import io.openmessages.model.MmsPart
import io.openmessages.model.PhoneNumber
import io.openmessages.model.Recipient
import io.openmessages.model.SyncLog
import io.openmessages.util.PhoneNumberUtils
import io.openmessages.util.RxSharedPreferences
import io.openmessages.util.tryOrNull
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    private val conversationRepo: ConversationRepository,
    private val cursorToConversation: CursorToConversation,
    private val cursorToMessage: CursorToMessage,
    private val cursorToPart: CursorToPart,
    private val cursorToRecipient: CursorToRecipient,
    private val cursorToContact: CursorToContact,
    private val cursorToContactGroup: CursorToContactGroup,
    private val cursorToContactGroupMember: CursorToContactGroupMember,
    private val db: AppDatabase,
    private val keys: KeyManager,
    private val phoneNumberUtils: PhoneNumberUtils,
    private val messageRepo: Provider<MessageRepository>,
    private val rxPrefs: RxSharedPreferences,
    private val reactions: EmojiReactionRepository,
) : SyncRepository {

    companion object {
        /** How many rows the sync writes at a time, so a large inbox is never held in full */
        private const val BATCH_SIZE = 500
    }

    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()
    private val contactDao = db.contactDao()
    private val syncDao = db.syncDao()

    override val syncProgress: Subject<SyncRepository.SyncProgress> =
            BehaviorSubject.createDefault(SyncRepository.SyncProgress.Idle)

    override fun syncMessages() {

        val oldBlockedSenders = rxPrefs.getStringSet("pref_key_blocked_senders")

        // If the sync is already running, don't try to do another one
        if (syncProgress.blockingFirst() is SyncRepository.SyncProgress.Running) return
        syncProgress.onNext(SyncRepository.SyncProgress.Running(0, 0, true))

        Completable.fromAction {
            db.runInTransaction {
                // Prepare existing conversation data
                val persistedData = conversationDao.getAll()
                    .map(ConversationWithRelations::toModel)
                    .filter { conversation ->
                        conversation.archived || conversation.blocked || conversation.pinned ||
                                conversation.name.isNotEmpty() || conversation.blockingClient != null ||
                                !conversation.blockReason.isNullOrEmpty()
                    }
                    .associateBy { conversation -> conversation.id }
                    .toMutableMap()

                // Archiving is only ever written to the database, never back to the system provider,
                // so the provider's flag is whatever the messaging app used before this one left
                // there. It is the only thing to go on while migrating an inbox into a fresh
                // install, and misleading from then on: a conversation unarchived here drops out
                // of persistedData, and reading the provider again would archive it right back.
                val isFirstSync = !hasSyncedMessages()

                // Read before the old contacts go, since the default number of each contact is the
                // one thing about them the provider does not hold
                val contacts = getContacts()

                removeOldMessages()

                keys.reset()

                val partsCursor = cursorToPart.getPartsCursor()
                val messageCursor = cursorToMessage.getMessagesCursor()
                val conversationCursor = cursorToConversation.getConversationsCursor()
                val recipientCursor = cursorToRecipient.getRecipientCursor()

                val max = (partsCursor?.count ?: 0) +
                        (messageCursor?.count ?: 0) +
                        (conversationCursor?.count ?: 0) +
                        (recipientCursor?.count ?: 0)

                var progress = 0

                // Sync message parts
                partsCursor?.use {
                    val parts = mutableListOf<MmsPart>()
                    partsCursor.forEach { cursor ->
                        tryOrNull {
                            parts += cursorToPart.map(cursor)
                            progress++
                        }
                        if (parts.size == BATCH_SIZE) {
                            messageDao.upsertParts(parts)
                            parts.clear()
                        }
                    }
                    messageDao.upsertParts(parts)
                }

                // Sync messages
                messageCursor?.use {
                    val messageColumns = CursorToMessage.MessageColumns(messageCursor)
                    val messages = mutableListOf<Message>()
                    messageCursor.forEach { cursor ->
                        tryOrNull {
                            syncProgress.onNext(
                                SyncRepository.SyncProgress.Running(
                                    max,
                                    ++progress,
                                    false
                                )
                            )
                            messages += cursorToMessage.map(Pair(cursor, messageColumns)).apply {
                                if (isMms()) {
                                    parts = messageDao.getPartsOfMms(contentId)
                                }
                            }
                        }
                        if (messages.size == BATCH_SIZE) {
                            messageDao.upsert(messages)
                            messages.clear()
                        }
                    }
                    messageDao.upsert(messages)
                }

                // Migrate blocked conversations from 2.7.3
                oldBlockedSenders.get()
                    .map { threadIdString -> threadIdString.toLong() }
                    .filter { threadId -> !persistedData.contains(threadId) }
                    .forEach { threadId ->
                        persistedData[threadId] = Conversation(id = threadId, blocked = true)
                    }

                // Sync conversations
                conversationCursor?.use {
                    val conversations = mutableListOf<Conversation>()
                    conversationCursor.forEach { cursor ->
                        tryOrNull {
                            syncProgress.onNext(
                                SyncRepository.SyncProgress.Running(
                                    max,
                                    ++progress,
                                    false
                                )
                            )
                            conversations += cursorToConversation.map(cursor).apply {
                                // Past that first sync, persistedData is the only source for it
                                if (!isFirstSync) {
                                    archived = false
                                }

                                persistedData[id]?.let { persistedConversation ->
                                    archived = persistedConversation.archived
                                    blocked = persistedConversation.blocked
                                    pinned = persistedConversation.pinned
                                    name = persistedConversation.name
                                    blockingClient = persistedConversation.blockingClient
                                    blockReason = persistedConversation.blockReason
                                    sendAsGroup = persistedConversation.sendAsGroup
                                }
                            }
                        }
                        if (conversations.size == BATCH_SIZE) {
                            conversationDao.upsert(conversations)
                            conversations.clear()
                        }
                    }
                    conversationDao.upsert(conversations)
                }

                // Point every conversation at its newest message, now that both are stored
                conversationDao.updateAllLastMessages()

                // Sync recipients
                contactDao.upsert(contacts)
                recipientCursor?.use {
                    val recipients = mutableListOf<Recipient>()
                    recipientCursor.forEach { cursor ->
                        tryOrNull {
                            syncProgress.onNext(
                                SyncRepository.SyncProgress.Running(
                                    max,
                                    ++progress,
                                    false
                                )
                            )
                            recipients += cursorToRecipient.map(cursor).apply {
                                contact = contacts.firstOrNull { c ->
                                    c.numbers.any { num ->
                                        phoneNumberUtils.compare(
                                            address,
                                            num.address
                                        )
                                    }
                                }
                            }
                        }
                        if (recipients.size == BATCH_SIZE) {
                            conversationDao.upsertRecipients(recipients)
                            recipients.clear()
                        }
                    }
                    conversationDao.upsertRecipients(recipients)
                }

                syncProgress.onNext(SyncRepository.SyncProgress.ParsingEmojis(0, 0, true))

                // Now that we have all the messages, we can scan for emoji reactions
                reactions.deleteAndReparseAllEmojiReactions(
                    onProgress = { progress ->
                        syncProgress.onNext(progress)
                    })

                if (rxPrefs.getBoolean("autoDeduplicateMessages").get()) {
                    DeduplicateMessages(messageRepo.get())
                        .buildObservable(Unit)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { result ->
                            when (result) {
                                is MessageRepository.DeduplicationResult.NoDuplicates -> {
                                    Timber.i("No duplicate messages found.")
                                }
                                is MessageRepository.DeduplicationResult.Success -> {
                                    Timber.i("Deleted duplicate messages")
                                }
                                is MessageRepository.DeduplicationResult.Failure -> {
                                    Timber.e(result.error, "Deduplication failed")
                                }
                            }
                        }
                }

                syncDao.insert(SyncLog())
            }
        }
            .subscribeOn(Schedulers.io())
            .subscribe({
                oldBlockedSenders.delete()
                syncProgress.onNext(SyncRepository.SyncProgress.Idle)
            }, { error ->
                Timber.e(error, "syncMessages Failed")
                syncProgress.onNext(SyncRepository.SyncProgress.Idle)
            })
    }

    override fun syncMessage(uri: Uri, messageId: Long): Message? {

        // If we don't have a valid type, return null
        val type = when {
            uri.toString().contains(Message.TYPE_MMS) -> Message.TYPE_MMS
            uri.toString().contains(Message.TYPE_SMS) -> Message.TYPE_SMS
            else -> return null
        }

        // If we don't have a valid id, return null
        val contentId = tryOrNull(false) { ContentUris.parseId(uri) } ?: return null

        // Check if the message already exists, so we can reuse the id
        val existingId = messageDao.findId(type, contentId, messageId)

        // The uri might be something like content://mms/inbox/id
        // The box might change though, so we should just use the mms/id uri
        val stableUri = when (type) {
            Message.TYPE_MMS -> ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, contentId)
            else -> ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, contentId)
        }

        return contentResolver.query(stableUri, null, null, null, null)?.use { cursor ->

            // If there are no rows, return null. Otherwise, we've moved to the first row
            if (!cursor.moveToFirst()) return null

            val columnsMap = CursorToMessage.MessageColumns(cursor)
            cursorToMessage.map(Pair(cursor, columnsMap)).apply {
                existingId?.let { this.id = it }

                if (isMms()) {
                    parts = cursorToPart.getPartsCursor(contentId)?.map { cursorToPart.map(it) }.orEmpty()
                }

                conversationRepo.getOrCreateConversation(threadId)
                messageDao.upsert(this)

                val text = getText(false)
                val parsedReaction = reactions.parseEmojiReaction(text)
                if (parsedReaction != null) {
                    val targetMessage = reactions.findTargetMessage(threadId, parsedReaction.originalMessage)
                    reactions.saveEmojiReaction(this, parsedReaction, targetMessage)
                }
            }
        }
    }

    override fun syncContacts() {
        // Load all the contacts
        val contacts = getContacts()

        db.runInTransaction {
            val recipients = conversationDao.getRecipients().map(RecipientWithContact::toModel)

            contactDao.replaceAll(contacts, getContactGroups(contacts))

            // Update all the recipients with the new contacts
            recipients.forEach { recipient ->
                recipient.contact = contacts.find { contact ->
                    contact.numbers.any { phoneNumberUtils.compare(recipient.address, it.address) }
                }
            }

            conversationDao.upsertRecipients(recipients)
        }
    }

    override fun hasSyncedMessages(): Boolean = syncDao.getSyncCount() > 0

    override fun reparseEmojiReactionsIfNeeded() {
        db.runInTransaction {
            if (syncDao.getEmojiSyncNeeded() != null) {
                reactions.deleteAndReparseAllEmojiReactions { /* No progress ui needed here */ }
                syncDao.deleteEmojiSyncNeeded()
            }
        }
    }

    private fun getContacts(): List<Contact> {
        val defaultNumberIds = contactDao.getDefaultNumberIds()

        return cursorToContact.getContactsCursor()
                ?.map { cursor -> cursorToContact.map(cursor) }
                ?.groupBy { contact -> contact.lookupKey }
                ?.map { contacts ->
                    // Sometimes, contacts providers on the phone will create duplicate phone number entries. This
                    // commonly happens with Whatsapp. Let's try to detect these duplicate entries and filter them out
                    val uniqueNumbers = mutableListOf<PhoneNumber>()
                    contacts.value
                            .flatMap { it.numbers }
                            .forEach { number ->
                                number.isDefault = defaultNumberIds.any { id -> id == number.id }
                                val duplicate = uniqueNumbers.find { other ->
                                    phoneNumberUtils.compare(number.address, other.address)
                                }

                                if (duplicate == null) {
                                    uniqueNumbers += number
                                } else if (!duplicate.isDefault && number.isDefault) {
                                    duplicate.isDefault = true
                                }
                            }

                    contacts.value.first().apply {
                        numbers = uniqueNumbers
                    }
                } ?: listOf()
    }

    private fun getContactGroups(contacts: List<Contact>): List<ContactGroup> {
        val groupMembers = cursorToContactGroupMember.getGroupMembersCursor()
                ?.map(cursorToContactGroupMember::map)
                .orEmpty()

        val groups = cursorToContactGroup.getContactGroupsCursor()
                ?.map(cursorToContactGroup::map)
                .orEmpty()

        groups.forEach { group ->
            group.contacts = groupMembers
                    .filter { member -> member.groupId == group.id }
                    .mapNotNull { member -> contacts.find { contact -> contact.lookupKey == member.lookupKey } }
        }

        return groups
    }

    private fun removeOldMessages() {
        contactDao.deleteAll()
        syncDao.deleteSyncedMessages()
    }

}
