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
package io.openmessages.manager

import android.app.Notification
import androidx.core.app.NotificationCompat

interface NotificationManager {

    fun getForegroundNotificationForWorkersOnOlderAndroids(): Notification

    /**
     * Rebuilds the notification for a conversation from its unread and unseen messages.
     *
     * [alert] tells the system whether this is worth interrupting the user for: a message that just
     * arrived is, so it rings, vibrates and pops up even when the conversation already had a
     * notification standing. A rebuild that follows something else (a deletion, a message being
     * marked read, notifications restored after a reboot) is not, and updates the notification in
     * place without alerting again.
     */
    fun update(threadId: Long, alert: Boolean = true)

    fun notifyFailed(threadId: Long)

    fun createNotificationChannel(threadId: Long = 0L)

    fun buildNotificationChannelId(threadId: Long): String

    fun getNotificationForBackup(): NotificationCompat.Builder

    fun cancel(i: Int)

}
