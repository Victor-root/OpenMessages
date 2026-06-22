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
package io.openmessages.feature.notificationprefs

import android.os.Build
import io.openmessages.util.Preferences

data class NotificationPrefsState(
    val threadId: Long = 0,
    val conversationTitle: String = "",
    val notificationsEnabled: Boolean = true,
    val previewSummary: String = "",
    val previewId: Int = Preferences.NOTIFICATION_PREVIEWS_ALL,
    val wakeEnabled: Boolean = false,
    val silentNotContact: Boolean = false,
    val action1Summary: String = "",
    val action2Summary: String = "",
    val action3Summary: String = "",
    val vibrationEnabled: Boolean = true,
    val ringtoneName: String = "",
    val qkReplyEnabled: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.N,
    val qkReplyTapDismiss: Boolean = true
)
