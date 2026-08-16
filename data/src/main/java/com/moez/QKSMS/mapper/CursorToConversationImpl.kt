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
import android.net.Uri
import android.provider.Telephony.Threads
import io.openmessages.manager.PermissionManager
import io.openmessages.model.Conversation
import io.openmessages.model.Recipient
import timber.log.Timber
import javax.inject.Inject

class CursorToConversationImpl @Inject constructor(
    private val context: Context,
    private val permissionManager: PermissionManager
) : CursorToConversation {

    companion object {
        val URI: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val PROJECTION = arrayOf(
                Threads._ID,
                Threads.RECIPIENT_IDS,
                Threads.ARCHIVED
        )

        private val PROJECTION_WITHOUT_ARCHIVED = arrayOf(
                Threads._ID,
                Threads.RECIPIENT_IDS
        )

        private const val SORT_ORDER = "date desc"

        const val ID = 0
        const val RECIPIENT_IDS = 1

        /**
         * Whether the provider answers for [Threads.ARCHIVED].
         *
         * Kept for the process rather than per instance, because it is a fact about the device and
         * this class is not a singleton: a device without the column would otherwise pay for
         * finding out again on every instance that ever asks.
         */
        @Volatile
        private var archivedColumnPresent = true
    }

    override fun map(from: Cursor): Conversation {
        return Conversation().apply {
            id = from.getLong(ID)

            // Read by name, not by position: the column is absent when the fallback projection
            // was the one that went through.
            val archivedColumn = from.getColumnIndex(Threads.ARCHIVED)
            archived = archivedColumn != -1 && from.getInt(archivedColumn) != 0

            recipients.addAll(from.getString(RECIPIENT_IDS)
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .map { recipientId -> recipientId.toLong() }
                    .map { recipientId -> Recipient().apply { id = recipientId } })
        }
    }

    override fun getConversationsCursor(): Cursor? {
        if (!permissionManager.hasReadSms()) {
            return null
        }

        // Threads.ARCHIVED carries the archived state left behind by whichever messaging app owned
        // the inbox before, but only providers that follow AOSP have the column. Asking one that
        // doesn't for it fails the entire query, which would leave the conversation list empty
        // rather than merely unarchived, so what cannot be done without is asked for on its own
        // instead.
        //
        // Asked once. Finding out costs a rejected query and the stack trace that comes with it,
        // and this runs every time a conversation is drawn or created, so a device without the
        // column was paying that repeatedly for an answer that cannot change.
        if (archivedColumnPresent) {
            try {
                return context.contentResolver.query(URI, PROJECTION, null, null, SORT_ORDER)
            } catch (e: Exception) {
                archivedColumnPresent = false
                Timber.i(e, "the conversations provider has no ${Threads.ARCHIVED} column, so a " +
                        "conversation archived in another app cannot be recognised as archived here")
            }
        }

        return context.contentResolver.query(URI, PROJECTION_WITHOUT_ARCHIVED, null, null, SORT_ORDER)
    }

}