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
package io.openmessages.repository

import io.openmessages.model.Message
import io.realm.Realm

data class ParsedEmojiReaction(val emoji: String, val originalMessage: String, val isRemoval: Boolean = false)

interface EmojiReactionRepository {
    fun parseEmojiReaction(body: String): ParsedEmojiReaction?

    fun findTargetMessage(threadId: Long, originalMessageText: String, realm: Realm): Message?

    fun saveEmojiReaction(
        reactionMessage: Message,
        parsedReaction: ParsedEmojiReaction,
        targetMessage: Message?,
        realm: Realm,
    )

    fun deleteAndReparseAllEmojiReactions(
        realm: Realm,
        onProgress: (SyncRepository.SyncProgress) -> Unit
    )
}
