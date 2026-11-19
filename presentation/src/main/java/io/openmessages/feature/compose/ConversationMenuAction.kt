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

/** What was picked in the conversation's ⋮ menu, see [ComposeView.showConversationMenu] */
sealed class ConversationMenuAction {

    /** A participant tapped: their contact, or adding them as one */
    data class Contact(val recipientId: Long) : ConversationMenuAction()

    /** A participant held: their number, copied */
    data class CopyAddress(val recipientId: Long) : ConversationMenuAction()

    object Media : ConversationMenuAction()

    object Rename : ConversationMenuAction()

    object Notifications : ConversationMenuAction()

    object MarkUnread : ConversationMenuAction()

    object Archive : ConversationMenuAction()

    object Block : ConversationMenuAction()

    object Delete : ConversationMenuAction()

}
