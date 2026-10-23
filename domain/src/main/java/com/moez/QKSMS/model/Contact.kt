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
package io.openmessages.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "contact")
data class Contact(
    @PrimaryKey var lookupKey: String = "",
    var name: String = "",
    var photoUri: String? = null,
    var starred: Boolean = false,
    var lastUpdate: Long = 0
) {

    @Ignore var numbers: List<PhoneNumber> = emptyList()

    fun getDefaultNumber(): PhoneNumber? = numbers.find { number -> number.isDefault }

    companion object {

        /**
         * A contact standing in for an [address] that none of the contacts has
         */
        fun forAddress(address: String): Contact {
            return Contact().apply { numbers = listOf(PhoneNumber(address = address)) }
        }

    }

}
