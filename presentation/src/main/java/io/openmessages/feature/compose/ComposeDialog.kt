/*
 * Copyright (C) 2026 OpenMessages contributors
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
package io.openmessages.feature.compose

import android.net.Uri

/**
 * Which dialog the conversation screen is showing, if any. At most one at a time, which is why this
 * is a single field of [ComposeState] rather than a flag per dialog.
 *
 * Being part of the state is what lets a dialog survive a rotation: the view model outlives the
 * screen, so the rebuilt screen shows again whatever was up, on the same subject. That is also why
 * each case carries what it is about — the ids to delete, the link to open — instead of the dialog
 * holding it. The message ids in particular used to be read back from the adapter's selection when
 * the user confirmed, and that selection does not survive a rotation either.
 *
 * Equality decides whether the screen rebuilds a dialog, so every case must compare by value.
 */
sealed class ComposeDialog {

    data class DeleteMessages(val messageIds: List<Long>) : ComposeDialog()

    data class DeleteConversation(val threadId: Long) : ComposeDialog()

    object ClearMessage : ComposeDialog()

    data class OpenLink(val uri: Uri) : ComposeDialog()

    data class MessageDetails(val details: String) : ComposeDialog()

    data class Reactions(val lines: List<String>) : ComposeDialog()

    /** Scheduling picks a date first, then a time; the date picked travels in [ScheduleTime]. */
    object ScheduleDate : ComposeDialog()

    data class ScheduleTime(val year: Int, val month: Int, val day: Int) : ComposeDialog()
}
