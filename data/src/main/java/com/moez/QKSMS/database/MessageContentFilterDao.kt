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
import io.openmessages.model.MessageContentFilter
import io.reactivex.Flowable

@Dao
interface MessageContentFilterDao {

    @Query("SELECT * FROM message_content_filter ORDER BY id")
    fun observeAll(): Flowable<List<MessageContentFilter>>

    @Query("SELECT * FROM message_content_filter ORDER BY id")
    fun getAll(): List<MessageContentFilter>

    @Query("SELECT * FROM message_content_filter WHERE id = :id")
    fun get(id: Long): MessageContentFilter?

    @Query("SELECT MAX(id) FROM message_content_filter")
    fun getMaxId(): Long?

    @Insert
    fun insert(filter: MessageContentFilter)

    @Query("DELETE FROM message_content_filter WHERE id = :id")
    fun delete(id: Long)

}
