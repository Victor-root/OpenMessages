/*
 * Copyright (C) 2026
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
import io.openmessages.manager.WidgetManager
import io.openmessages.repository.ConversationRepository
import io.openmessages.repository.MessageRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * A notification alerts, meaning it rings, vibrates and pops up, when a message has just arrived,
 * and stays quiet when it is only being rebuilt after something the user did themselves. Getting
 * this the wrong way round is what silences every message after the first one in a conversation
 * whose notification is still on screen.
 */
class NotificationAlertPolicyTest {

    private val conversationRepo = mock(ConversationRepository::class.java)
    private val messageRepo = mock(MessageRepository::class.java)
    private val shortcutManager = mock(ShortcutManager::class.java)
    private val widgetManager = mock(WidgetManager::class.java)
    private val notificationManager = RecordingNotificationManager()
    private val updateBadge = UpdateBadge(shortcutManager, widgetManager)

    @Test
    fun anIncomingMessageAlerts() {
        // What the receiving workers call, spelling out that the contract's own default alerts.
        notificationManager.update(12L)

        assertEquals(listOf(true), notificationManager.updates.map { it.alert })
    }

    @Test
    fun markingAConversationReadDoesNotAlert() {
        MarkRead(messageRepo, notificationManager, updateBadge)
            .buildObservable(listOf(12L, 34L))
            .blockingSubscribe()

        assertEquals(
            listOf(
                RecordingNotificationManager.Update(12L, alert = false),
                RecordingNotificationManager.Update(34L, alert = false)
            ),
            notificationManager.updates
        )
    }

    @Test
    fun deletingMessagesDoesNotAlert() {
        DeleteMessages(conversationRepo, messageRepo, notificationManager, updateBadge)
            .buildObservable(DeleteMessages.Params(listOf(1L, 2L), threadId = 12L))
            .blockingSubscribe()

        assertEquals(
            listOf(RecordingNotificationManager.Update(12L, alert = false)),
            notificationManager.updates
        )
    }

}
