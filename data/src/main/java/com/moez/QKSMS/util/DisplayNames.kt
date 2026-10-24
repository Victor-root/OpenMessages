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
package io.openmessages.util

import io.openmessages.model.Conversation
import io.openmessages.model.Recipient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How recipients and conversations are named on screen: by their contact or custom name, or else by
 * their number, without the country code where the user asked for that
 */
@Singleton
class DisplayNames @Inject constructor(
    private val phoneNumberUtils: PhoneNumberUtils,
    private val prefs: Preferences
) {

    fun number(address: String): String = when (prefs.hideCountryCode.get()) {
        true -> phoneNumberUtils.formatNumberNational(address)
        false -> phoneNumberUtils.formatNumber(address)
    }

    fun of(recipient: Recipient): String =
        recipient.contact?.name?.takeIf { it.isNotBlank() } ?: number(recipient.address)

    fun of(conversation: Conversation): String =
        conversation.name.takeIf { it.isNotBlank() } ?: conversation.recipients.joinToString(transform = ::of)

}
