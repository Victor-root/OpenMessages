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
package io.openmessages.repository

import android.net.Uri
import io.openmessages.model.Message
import io.reactivex.Observable

interface SyncRepository {

    sealed class SyncProgress {
        object Idle : SyncProgress()
        data class Running(val max: Int, val progress: Int, val indeterminate: Boolean) : SyncProgress()
        data class ParsingEmojis(val max: Int, val progress: Int, val indeterminate: Boolean) : SyncProgress()
    }

    val syncProgress: Observable<SyncProgress>

    fun syncMessages()

    fun syncMessage(uri: Uri, messageId: Long = 0): Message?

    fun syncContacts()

}
