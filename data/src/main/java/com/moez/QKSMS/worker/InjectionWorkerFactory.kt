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
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.openmessages.blocking.BlockingClient
import io.openmessages.blocking.LinkSpamFilter
import io.openmessages.interactor.UpdateBadge
import io.openmessages.manager.ActiveConversationManager
import io.openmessages.manager.NotificationManager
import io.openmessages.manager.ShortcutManager
import io.openmessages.repository.AllowlistRepository
import io.openmessages.repository.BackupRepository
import io.openmessages.repository.ContactRepository
import io.openmessages.repository.ConversationRepository
import io.openmessages.repository.MessageContentFilterRepository
import io.openmessages.repository.MessageRepository
import io.openmessages.repository.ScheduledMessageRepository
import io.openmessages.repository.SyncRepository
import io.openmessages.util.Preferences
import javax.inject.Inject

class InjectionWorkerFactory @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val blockingClient: BlockingClient,
    private val prefs: Preferences,
    private val messageRepo: MessageRepository,
    private val updateBadge: UpdateBadge,
    private val shortcutManager: ShortcutManager,
    private val scheduledMessageRepository: ScheduledMessageRepository,
    private val notificationManager: NotificationManager,
    private val activeConversationManager: ActiveConversationManager,
    private val syncRepo: SyncRepository,
    private val filterRepo: MessageContentFilterRepository,
    private val contactRepo: ContactRepository,
    private val linkSpamFilter: LinkSpamFilter,
    private val allowlistRepo: AllowlistRepository,
    private val backupRepo: BackupRepository,

) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val instance = Class
            .forName(workerClassName)
            .asSubclass(Worker::class.java)
            .getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)
            .newInstance(appContext, workerParameters)

        when (instance) {
            is HousekeepingWorker ->
                instance.scheduledMessageRepository = scheduledMessageRepository
            is AutoBackupWorker ->
                instance.backupRepo = backupRepo
            is ReceiveSmsWorker -> {
                instance.conversationRepo  = conversationRepo
                instance.blockingClient = blockingClient
                instance.prefs = prefs
                instance.messageRepo = messageRepo
                instance.shortcutManager = shortcutManager
                instance.notificationManager = notificationManager
                instance.updateBadge =  updateBadge
                instance.filterRepo = filterRepo
                instance.contactsRepo = contactRepo
                instance.linkSpamFilter = linkSpamFilter
                instance.allowlistRepo = allowlistRepo
            }
            is ReceiveMmsWorker -> {
                instance.syncRepo = syncRepo
                instance.activeConversationManager = activeConversationManager
                instance.conversationRepo = conversationRepo
                instance.blockingClient = blockingClient
                instance.prefs = prefs
                instance.messageRepo = messageRepo
                instance.shortcutManager = shortcutManager
                instance.notificationManager = notificationManager
                instance.updateBadge = updateBadge
                instance.filterRepo = filterRepo
                instance.contactsRepo = contactRepo
                instance.linkSpamFilter = linkSpamFilter
                instance.allowlistRepo = allowlistRepo
            }
        }

        return instance
    }
}