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
import androidx.room.Query
import androidx.room.Upsert
import io.openmessages.model.ScheduledMessage
import io.reactivex.rxjava3.core.Flowable

@Dao
interface ScheduledMessageDao {

    @Query("SELECT * FROM scheduled_message ORDER BY date")
    fun observeAll(): Flowable<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_message ORDER BY date")
    fun getAll(): List<ScheduledMessage>

    @Query("SELECT * FROM scheduled_message WHERE conversationId = :conversationId ORDER BY date")
    fun observeForConversation(conversationId: Long): Flowable<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_message WHERE id = :id")
    fun get(id: Long): ScheduledMessage?

    @Query("SELECT id FROM scheduled_message ORDER BY date")
    fun getAllIds(): List<Long>

    @Query("SELECT MAX(id) FROM scheduled_message")
    fun getMaxId(): Long?

    @Upsert
    fun upsert(message: ScheduledMessage)

    @Upsert
    fun upsert(messages: List<ScheduledMessage>)

    @Query("DELETE FROM scheduled_message WHERE id = :id")
    fun delete(id: Long)

}
