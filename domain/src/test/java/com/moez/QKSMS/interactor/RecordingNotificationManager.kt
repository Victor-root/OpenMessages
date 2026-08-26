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

import android.app.Notification
import androidx.core.app.NotificationCompat
import io.openmessages.manager.NotificationManager

/**
 * Writes down every notification update asked of it, so a test can tell one that alerts the user
 * from one that quietly refreshes what is already on screen.
 */
class RecordingNotificationManager : NotificationManager {

    data class Update(val threadId: Long, val alert: Boolean)

    val updates = mutableListOf<Update>()

    override fun update(threadId: Long, alert: Boolean) {
        updates += Update(threadId, alert)
    }

    override fun getForegroundNotificationForWorkersOnOlderAndroids(): Notification = unused()

    override fun notifyFailed(threadId: Long) {
        unused()
    }

    override fun createNotificationChannel(threadId: Long) {
        unused()
    }

    override fun buildNotificationChannelId(threadId: Long): String = unused()

    override fun getNotificationForBackup(): NotificationCompat.Builder = unused()

    override fun cancel(i: Int) {
        unused()
    }

    private fun unused(): Nothing =
        throw UnsupportedOperationException("the interactors under test never reach this")

}
