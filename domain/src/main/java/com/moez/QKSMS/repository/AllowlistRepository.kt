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
package io.openmessages.repository

import io.openmessages.model.AllowedNumber
import io.realm.RealmResults

/**
 * Stores addresses that the user has explicitly approved (an allowlist).
 *
 * The automatic blocking sources (telemarketing ranges, disposable numbers, phishing links) must
 * ignore any address held here, so an approved sender is never flagged or blocked by them again.
 * The user's own manual blocklist still takes precedence over this list.
 */
interface AllowlistRepository {

    fun allowNumber(vararg addresses: String)

    fun getAllowedNumbers(): RealmResults<AllowedNumber>

    fun getAllowedNumber(id: Long): AllowedNumber?

    fun isAllowed(address: String): Boolean

    fun removeNumber(id: Long)

    fun removeNumbers(vararg addresses: String)

}
