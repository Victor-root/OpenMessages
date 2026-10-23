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
package io.openmessages.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.openmessages.model.EmojiSyncNeeded
import io.openmessages.model.SyncLog

/**
 * What the full sync from the system provider keeps track of
 */
@Dao
abstract class SyncDao {

    @Query("SELECT COUNT(*) FROM sync_log")
    abstract fun getSyncCount(): Long

    @Insert
    abstract fun insert(syncLog: SyncLog)

    @Query("SELECT * FROM emoji_sync_needed LIMIT 1")
    abstract fun getEmojiSyncNeeded(): EmojiSyncNeeded?

    @Insert
    abstract fun insert(emojiSyncNeeded: EmojiSyncNeeded)

    @Query("DELETE FROM emoji_sync_needed")
    abstract fun deleteEmojiSyncNeeded()

    @Query("DELETE FROM conversation")
    protected abstract fun deleteConversations()

    @Query("DELETE FROM conversation_recipient")
    protected abstract fun deleteConversationRecipients()

    @Query("DELETE FROM recipient")
    protected abstract fun deleteRecipients()

    @Query("DELETE FROM message")
    protected abstract fun deleteMessages()

    @Query("DELETE FROM message_part")
    protected abstract fun deleteMessageParts()

    @Query("DELETE FROM mms_part")
    protected abstract fun deleteParts()

    /**
     * Deletes everything a full sync reads back from the system provider, contacts aside
     */
    @Transaction
    open fun deleteSyncedMessages() {
        deleteConversations()
        deleteConversationRecipients()
        deleteRecipients()
        deleteMessages()
        deleteMessageParts()
        deleteParts()
    }

}
