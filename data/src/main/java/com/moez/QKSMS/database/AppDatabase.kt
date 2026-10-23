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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.openmessages.migration.LegacyRealmImport
import io.openmessages.model.AllowedNumber
import io.openmessages.model.BlockedNumber
import io.openmessages.model.Contact
import io.openmessages.model.ContactGroup
import io.openmessages.model.Conversation
import io.openmessages.model.EmojiReaction
import io.openmessages.model.EmojiSyncNeeded
import io.openmessages.model.Message
import io.openmessages.model.MessageContentFilter
import io.openmessages.model.MmsPart
import io.openmessages.model.PhoneNumber
import io.openmessages.model.Recipient
import io.openmessages.model.ScheduledMessage
import io.openmessages.model.SyncLog

@Database(
    version = 1,
    entities = [
        Conversation::class,
        ConversationRecipient::class,
        Recipient::class,
        Contact::class,
        PhoneNumber::class,
        ContactGroup::class,
        ContactGroupMember::class,
        Message::class,
        MessagePart::class,
        MmsPart::class,
        EmojiReaction::class,
        ScheduledMessage::class,
        BlockedNumber::class,
        AllowedNumber::class,
        MessageContentFilter::class,
        SyncLog::class,
        EmojiSyncNeeded::class
    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    abstract fun emojiReactionDao(): EmojiReactionDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun allowedNumberDao(): AllowedNumberDao
    abstract fun messageContentFilterDao(): MessageContentFilterDao
    abstract fun syncDao(): SyncDao

    companion object {
        private const val NAME = "openmessages.db"

        fun build(context: Context, legacyRealmImport: LegacyRealmImport): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addCallback(legacyRealmImport)
                .build()
    }

}
