/*
 * Copyright (C) 2025
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
package io.openmessages.worker

import android.content.Context
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.openmessages.blocking.BlockingClient
import io.openmessages.blocking.LinkSpamFilter
import io.openmessages.interactor.UpdateBadge
import io.openmessages.manager.NotificationManager
import io.openmessages.manager.ShortcutManager
import io.openmessages.repository.AllowlistRepository
import io.openmessages.repository.ContactRepository
import io.openmessages.repository.ConversationRepository
import io.openmessages.repository.MessageContentFilterRepository
import io.openmessages.repository.MessageRepository
import io.openmessages.util.Preferences
import timber.log.Timber
import javax.inject.Inject

class ReceiveSmsWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {
    companion object {
        const val INPUT_DATA_KEY_MESSAGE_ID = "messageId"
    }

    @Inject lateinit var conversationRepo: ConversationRepository
    @Inject lateinit var blockingClient: BlockingClient
    @Inject lateinit var prefs: Preferences
    @Inject lateinit var messageRepo: MessageRepository
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var updateBadge: UpdateBadge
    @Inject lateinit var shortcutManager: ShortcutManager
    @Inject lateinit var filterRepo: MessageContentFilterRepository
    @Inject lateinit var contactsRepo: ContactRepository
    @Inject lateinit var linkSpamFilter: LinkSpamFilter
    @Inject lateinit var allowlistRepo: AllowlistRepository

    override fun doWork(): Result {
        Timber.v("started")

        val messageId = inputData.getLong(INPUT_DATA_KEY_MESSAGE_ID, -1)
        if (messageId < 0) {
            Timber.v("failed. message id was {messageId}")
            return Result.failure(inputData)
        }

        val message = messageRepo.getMessage(messageId) ?: return Result.failure(inputData)

        val baseAction = blockingClient.shouldBlock(message.address).blockingGet()

        // Phishing links: if not already blocked and the source is on, treat the message as blocked
        // when its body contains a link to a known phishing domain (approved senders are exempt).
        val phishingAction = when {
            baseAction !is BlockingClient.Action.Block &&
                prefs.blockSourcePhishing.get() &&
                !allowlistRepo.isAllowed(message.address) ->
                linkSpamFilter.firstBlockedDomain(message.getText())
                    ?.let { domain -> BlockingClient.Action.Block(domain) }
                    ?: baseAction
            else -> baseAction
        }

        // "Block suspected spam" option: when enabled, a soft Flag is upgraded to a full block so the
        // message skips the inbox entirely instead of just being tagged as potential spam.
        val action = when {
            phishingAction is BlockingClient.Action.Flag && prefs.blockFlaggedAsSpam.get() ->
                BlockingClient.Action.Block(phishingAction.reason)
            else -> phishingAction
        }

        // Handle the "delete and bail" cases first, before the conversation row is created below.
        // A dropped message must not leave a conversation behind in either the inbox or the blocked
        // list, so these have to run before getOrCreateConversation().
        if ((action is BlockingClient.Action.Block) && prefs.drop.get()) {
            // blocked and 'drop blocked' remove from db and don't continue
            Timber.v("address is blocked and drop blocked is on. dropped")
            messageRepo.deleteMessages(listOf(message.id))
            return Result.failure(inputData)
        }

        val messageFilterAction = filterRepo.isBlocked(message.getText(), message.address, contactsRepo)
        if (messageFilterAction) {
            Timber.v("message dropped based on content filters")
            messageRepo.deleteMessages(listOf(message.id))
            return Result.failure(inputData)
        }

        // Create/refresh the conversation row *before* applying the block/flag state. A message from
        // a brand-new sender has no Realm conversation yet at this point (insertReceivedSms only
        // saves the Message), so marking it blocked/flagged first would no-op and the row created
        // afterwards would default to not-blocked, letting spam slip into the inbox.
        //
        // The onCreate hook seeds the blocked/flagged state *inside* the creation transaction, so a
        // newly arrived spam conversation is born blocked and never flashes in the inbox before a
        // separate block transaction lands. The when() below still covers conversations that already
        // existed (onCreate only runs when the row is actually created).
        conversationRepo.updateConversations(listOf(message.threadId))
        val conversation = conversationRepo.getOrCreateConversation(message.threadId) { created ->
            when (action) {
                is BlockingClient.Action.Block -> {
                    created.blocked = true
                    created.blockingClient = prefs.blockingManager.get()
                    created.blockReason = action.reason
                }
                is BlockingClient.Action.Flag -> {
                    created.flagged = true
                    created.flagReason = action.reason
                }
                else -> Unit
            }
        } ?: return Result.failure(inputData)

        when {
            action is BlockingClient.Action.Block -> {
                // blocked
                Timber.v("address is blocked")
                messageRepo.markRead(listOf(message.threadId))
                conversationRepo.markBlocked(
                    listOf(message.threadId),
                    prefs.blockingManager.get(),
                    action.reason
                )
                conversationRepo.markUnflagged(message.threadId)
            }

            action is BlockingClient.Action.Flag -> {
                // suspected spam: keep the message in the inbox, just tag the conversation
                Timber.v("address flagged as suspected spam")
                conversationRepo.markFlagged(listOf(message.threadId), action.reason)
            }

            action is BlockingClient.Action.Unblock -> {
                // unblock
                Timber.v("unblock conversation if blocked")
                conversationRepo.markUnblocked(message.threadId)
                conversationRepo.markUnflagged(message.threadId)
            }
        }

        // Re-read the freshly-applied state rather than the conversation we fetched before the
        // block decision: don't notify (continue) for blocked conversations.
        if (conversationRepo.getConversation(message.threadId)?.blocked == true) {
            Timber.v("no notifications for blocked")
            return Result.failure(inputData)
        }

        // unarchive conversation if necessary
        if (conversation.archived) {
            Timber.v("conversation unarchived")
            conversationRepo.markUnarchived(listOf(conversation.id))
        }

        // update/create notification
        Timber.v("update/create notification")
        notificationManager.update(conversation.id)

        // update shortcuts
        Timber.v("update shortcuts")
        shortcutManager.updateShortcuts()
        shortcutManager.getOrCreateShortcut(conversation.id)

        // update the badge and widget
        Timber.v("update badge and widget")
        updateBadge.execute(Unit)

        Timber.v("finished")

        return Result.success()
    }

    override fun getForegroundInfo() = ForegroundInfo(
        0,
        notificationManager.getForegroundNotificationForWorkersOnOlderAndroids()
    )

}
