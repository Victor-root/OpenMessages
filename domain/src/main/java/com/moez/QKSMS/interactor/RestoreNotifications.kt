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

import io.openmessages.manager.NotificationManager
import io.openmessages.repository.ConversationRepository
import io.reactivex.Flowable
import javax.inject.Inject

/**
 * Puts back the notification of every conversation holding messages the user has not seen yet.
 *
 * Android drops an app's notifications when the phone restarts, and it is the standing notifications
 * that most launchers count to draw the badge on the app icon. Without this, a restart leaves the
 * unread messages announced by nothing at all: no notification, no counter, and the only hint left
 * that they arrived is opening the app.
 *
 * Nothing alerts: these messages announced themselves when they arrived, and a phone starting up is
 * not the moment to ring for each of them again.
 */
class RestoreNotifications @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val notificationManager: NotificationManager,
    private val updateBadge: UpdateBadge
) : Interactor<Unit>() {

    override fun buildObservable(params: Unit): Flowable<*> {
        return Flowable.just(params)
            .doOnNext {
                conversationRepo.getUnseenIds().forEach { threadId ->
                    notificationManager.update(threadId, alert = false)
                }
            }
            .flatMap { updateBadge.buildObservable(Unit) } // Update the badge and widget
    }

}
