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

import android.telephony.PhoneNumberUtils
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "recipient", indices = [Index("contactLookupKey")])
data class Recipient(
    @PrimaryKey var id: Long = 0,
    var address: String = "",
    /** The [Contact.lookupKey] of the contact this address belongs to, if any */
    var contactLookupKey: String? = null,
    var lastUpdate: Long = 0
) {

    @Ignore var contact: Contact? = null

    /**
     * Return a string that can be displayed to represent the name of this contact
     */
    fun getDisplayName(): String = contact?.name?.takeIf { it.isNotBlank() }
            ?: PhoneNumberUtils.formatNumber(address, Locale.getDefault().country) // TODO: Use our own PhoneNumberUtils
            ?: address

}
