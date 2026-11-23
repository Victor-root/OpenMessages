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

import android.net.Uri
import io.openmessages.extensions.Optional
import io.openmessages.model.Attachment
import io.openmessages.model.Message
import io.openmessages.model.MmsPart
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable

interface MessageRepository {
    sealed class DeduplicationProgress {
        object Idle : DeduplicationProgress()
        data class Running(val max: Int, val progress: Int, val indeterminate: Boolean) : DeduplicationProgress()
    }

    sealed class DeduplicationResult {
        object Success : DeduplicationResult()
        object NoDuplicates : DeduplicationResult()
        data class Failure(val error: Throwable) : DeduplicationResult()
    }

    val deduplicationProgress: Observable<DeduplicationProgress>

    fun getMessages(threadId: Long, query: String = ""): Observable<List<Message>>

    fun getMessagesSync(threadId: Long, query: String = ""): List<Message>

    /**
     * The messages of a conversation dated [since] or later, oldest first. Watching a whole
     * conversation reads all of it again on every change, which grows with its history; this costs
     * what the part on screen costs, however long the conversation has run.
     */
    fun getMessagesSince(threadId: Long, since: Long): Observable<List<Message>>

    /**
     * The date to watch a conversation from for its [count] newest messages to be in, or 0 for the
     * whole conversation when it holds no more than that
     */
    fun getDateOfLatest(threadId: Long, count: Int): Long

    fun getMessage(messageId: Long): Message?

    fun getMessages(messageIds: Collection<Long>): List<Message>

    fun getMessageForPart(id: Long): Message?

    /**
     * The latest message received in the conversation as it changes, or an empty [Optional] while
     * nothing has been received in it
     */
    fun observeLastIncomingMessage(threadId: Long): Observable<Optional<Message>>

    fun getUnreadCount(): Long

    fun getPart(id: Long): MmsPart?

    fun getPartsForConversation(threadId: Long): Observable<List<MmsPart>>

    fun savePart(id: Long): Uri?

    fun getUnreadUnseenMessages(threadId: Long): List<Message>

    fun getUnreadMessages(threadId: Long): List<Message>

    fun observeUnreadMessages(threadId: Long): Observable<List<Message>>

    /** How many unread messages each conversation holds, by its id; one with none is left out */
    fun observeUnreadCounts(): Observable<Map<Long, Int>>

    fun markAllSeen(): Int

    fun markSeen(threadIds: Collection<Long>): Int

    fun markRead(threadIds: Collection<Long>): Int

    fun markUnread(threadIds: Collection<Long>): Int

    fun markSending(messageId: Long)

    fun markSent(messageId: Long)

    fun markFailed(messageId: Long, resultCode: Int): Boolean

    fun markDelivered(messageId: Long)

    fun markDeliveryFailed(messageId: Long, resultCode: Int)

    /**
     * [threadId] is the conversation the message belongs to. It is stated rather than worked out
     * again from [toAddresses], because the provider does not always name the same thread twice for
     * the same recipient, and a message filed under a thread nobody is looking at is a message that
     * never appears.
     */
    fun sendNewMessages(
        subId: Int, threadId: Long, toAddresses: Collection<String>, body: String,
        attachments: Collection<Attachment>, sendAsGroup: Boolean, delayMs: Int = 0
    ): Collection<Message>

    /**
     * The SMS [sendNewMessages] is about to write for [body] to [address] alone, as it will read
     * once written, to be shown while it is; null when it will go as an MMS instead
     */
    fun outgoingSmsPreview(subId: Int, threadId: Long, address: String, body: String): Message?

    fun sendMessage(message: Message): Collection<Message>

    /**
     * The longest video, in milliseconds, that can still be brought down to the size an MMS on
     * this SIM may weigh
     */
    fun getMaxVideoDuration(subId: Int): Long

    fun sendMessage(messageId: Long): Collection<Message>

    fun cancelDelayedSmsAlarm(messageId: Long)

    fun insertReceivedSms(subId: Int, address: String, body: String, sentTime: Long): Message

    fun deleteMessages(messageIds: Collection<Long>)

    fun getOldMessageCounts(maxAgeDays: Int): Map<Long, Int>

    fun deleteOldMessages(maxAgeDays: Int)

    fun deduplicateMessages(): Flowable<DeduplicationResult>

    fun markAsSendingNow(messageId: Long)
}
