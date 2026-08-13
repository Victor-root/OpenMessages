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
package io.openmessages.feature.main

/**
 * Which dialog the conversation list is showing, if any. See [io.openmessages.common.util.DialogHost]
 * for why this belongs to the state rather than being a one-off command to the screen.
 *
 * Each case names the conversations it acts on, so a dialog put back after a rotation still knows
 * them. The selection in the list does not survive that rotation, and reading it back on confirm
 * would have deleted or renamed nothing.
 */
sealed class MainDialog {

    data class DeleteConversations(val conversationIds: List<Long>) : MainDialog()

    data class RenameConversation(val conversationId: Long, val currentName: String) : MainDialog()
}
