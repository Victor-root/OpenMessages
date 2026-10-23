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

import androidx.room.TypeConverter
import org.json.JSONArray

/**
 * Stores a list of strings, such as the recipients and attachments of a scheduled message, as a
 * JSON array, which keeps any character an address or a URI can hold.
 */
class Converters {

    @TypeConverter
    fun fromStringList(values: List<String>): String = JSONArray(values).toString()

    @TypeConverter
    fun toStringList(json: String): List<String> {
        val array = JSONArray(json)
        return List(array.length()) { index -> array.getString(index) }
    }

}
