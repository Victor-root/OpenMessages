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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class RestoreNotificationsTest {

    private val conversationRepo = mock(ConversationRepository::class.java)
    private val shortcutManager = mock(ShortcutManager::class.java)
    private val widgetManager = mock(WidgetManager::class.java)
    private val notificationManager = RecordingNotificationManager()

    private val restoreNotifications = RestoreNotifications(
        conversationRepo,
        notificationManager,
        UpdateBadge(shortcutManager, widgetManager)
    )

    @Test
    fun postsANotificationForEveryConversationLeftUnseen() {
        `when`(conversationRepo.getUnseenIds()).thenReturn(listOf(12L, 34L))

        restoreNotifications.buildObservable(Unit).blockingSubscribe()

        assertEquals(
            listOf(
                RecordingNotificationManager.Update(12L, alert = false),
                RecordingNotificationManager.Update(34L, alert = false)
            ),
            notificationManager.updates
        )
    }

    @Test
    fun restoredNotificationsNeverAlert() {
        `when`(conversationRepo.getUnseenIds()).thenReturn(listOf(12L))

        restoreNotifications.buildObservable(Unit).blockingSubscribe()

        assertEquals(listOf(false), notificationManager.updates.map { it.alert })
    }

    @Test
    fun refreshesTheBadgeAndTheWidgetEvenWithNothingUnseen() {
        `when`(conversationRepo.getUnseenIds()).thenReturn(emptyList())

        restoreNotifications.buildObservable(Unit).blockingSubscribe()

        assertEquals(emptyList<RecordingNotificationManager.Update>(), notificationManager.updates)
        verify(shortcutManager).updateBadge()
        verify(widgetManager).sendDatasetChanged()
    }

}
