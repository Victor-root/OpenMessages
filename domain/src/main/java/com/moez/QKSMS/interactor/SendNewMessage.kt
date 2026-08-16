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
package io.openmessages.interactor

import io.openmessages.manager.ShortcutManager
import io.openmessages.extensions.mapNotNull
import io.openmessages.model.Attachment
import io.openmessages.repository.ConversationRepository
import io.openmessages.repository.MessageRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import timber.log.Timber
import javax.inject.Inject

class SendNewMessage @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository,
    private val updateBadge: UpdateBadge,
    private val shortcutManager: ShortcutManager
) : Interactor<SendNewMessage.Params>() {

    data class Params(
        val subId: Int,
        val threadId: Long,
        val addresses: Collection<String>,
        val body: String,
        val sendAsGroup: Boolean,
        val attachments: Collection<Attachment> = listOf(),
        val delay: Int = 0
    )

    override fun buildObservable(params: Params): Flowable<*> = Flowable.just(Unit)
        .mapNotNull {
            // A thread id is a conversation that has already been settled on, normally the one the
            // message was written in, so it is taken as given. Working the addresses out again
            // instead can arrive somewhere else: the provider is not obliged to name the same
            // thread twice for a conversation that holds no message yet, and where it does not,
            // the message leaves for a conversation nobody is looking at. Addresses remain the
            // answer when there is no thread to go on.
            when {
                (params.threadId > 0) ->
                    conversationRepo.getOrCreateConversation(params.threadId)

                params.addresses.isNotEmpty() ->
                    conversationRepo.getOrCreateConversation(params.addresses)

                else -> null
            }
            ?:let { Timber.e("unable to get or create a conversation record"); null }
        }
        .map { conversation ->
            Timber.v("sending into conversation ${conversation.id} with " +
                    "${conversation.recipients.size} recipient(s)")

            // send the message
            messageRepo.sendNewMessages(params.subId, conversation.id,
                conversation.recipients.map { it.address }, params.body, params.attachments,
                params.sendAsGroup, params.delay)
        }
        .map { messages ->
            // The thread ids below are what the refresh runs on, so nothing coming back means
            // nothing is refreshed: the conversation keeps a null last message, and the list,
            // which only shows conversations that have one, never displays it. The message can
            // still have gone out, since the provider record is written before this point.
            if (messages.isEmpty()) {
                Timber.e("no message record came back from sending, " +
                        "the conversation will stay out of the list")
            }

            messages.map { it.threadId }
        }
        .doOnNext { threadIds ->
            conversationRepo.updateConversations(threadIds)
            conversationRepo.markUnarchived(threadIds)

            AndroidSchedulers.mainThread().scheduleDirect {
                threadIds.forEach { shortcutManager.getOrCreateShortcut(it) }
            }

            // delete attachment local files, if any, because they're saved to mms db by now
            params.attachments.forEach { it.removeCacheFile() }
        }
        .observeOn(AndroidSchedulers.mainThread())
        .flatMap { updateBadge.buildObservable(Unit) } // Update the widget

}