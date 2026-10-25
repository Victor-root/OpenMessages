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
package io.openmessages.mapper

import android.content.Context
import android.database.Cursor
import android.provider.Telephony.CanonicalAddressesColumns
import io.openmessages.manager.PermissionManager
import io.openmessages.model.Recipient
import javax.inject.Inject
import androidx.core.net.toUri

class CursorToRecipientImpl @Inject constructor(
    private val context: Context,
    private val permissionManager: PermissionManager
) : CursorToRecipient {

    companion object {
        val URI = "content://mms-sms/canonical-addresses".toUri()

        // Asked for by name, because the two below are positions and nothing obliges a provider to
        // lay its table out the way this one expects. Letting the provider pick the columns, as
        // this did, leaves those positions resting on a convention rather than on anything stated,
        // and a recipient read off the wrong column is a wrong number to send to.
        private val PROJECTION = arrayOf(
                CanonicalAddressesColumns._ID,
                CanonicalAddressesColumns.ADDRESS
        )

        const val COLUMN_ID = 0
        const val COLUMN_ADDRESS = 1
    }

    override fun map(from: Cursor) = Recipient(
            id = from.getLong(COLUMN_ID),
            address = from.getString(COLUMN_ADDRESS),
            lastUpdate = System.currentTimeMillis())

    override fun getRecipientCursor(): Cursor? {
        return when (permissionManager.hasReadSms()) {
            true -> context.contentResolver.query(URI, PROJECTION, null, null, null)
            false -> null
        }
    }

    override fun getRecipientCursor(id: Long): Cursor? {
        return context.contentResolver.query(URI, PROJECTION,
                "${CanonicalAddressesColumns._ID} = ?", arrayOf(id.toString()), null)
    }

}