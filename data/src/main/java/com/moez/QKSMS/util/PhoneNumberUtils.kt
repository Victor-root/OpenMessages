/*
 * Copyright (C) 2019 Moez Bhatti <moez.bhatti@gmail.com>
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

import android.content.Context
import android.telephony.PhoneNumberUtils
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.michaelrocks.libphonenumber.android.Phonenumber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneNumberUtils @Inject constructor(context: Context) {

    private val countryCode = Locale.getDefault().country
    private val phoneNumberUtil = PhoneNumberUtil.createInstance(context)

    /**
     * Android's implementation is too loose and causes false positives
     * libphonenumber is stricter but too slow
     *
     * This method will run successfully stricter checks without compromising much speed
     */
    fun compare(first: String, second: String): Boolean {
        val normalizedFirst = normalizeNumber(first)
        val normalizedSecond = normalizeNumber(second)
        if (normalizedFirst.equals(normalizedSecond, true)) {
            return true
        }

        // The cheap, loose check that spares most pairs the slow one below. Its replacement,
        // areSamePhoneNumber, is itself built on libphonenumber, so it would cost what this saves.
        @Suppress("DEPRECATION")
        if (PhoneNumberUtils.compare(first, second)) {
            val matchType = phoneNumberUtil.isNumberMatch(normalizedFirst, normalizedSecond)
            if (matchType >= PhoneNumberUtil.MatchType.SHORT_NSN_MATCH) {
                return true
            }
        }

        return false
    }

    fun isPossibleNumber(number: CharSequence): Boolean {
        return parse(number) != null
    }

    fun isReallyDialable(digit: Char): Boolean {
        return PhoneNumberUtils.isReallyDialable(digit)
    }

    fun formatNumber(number: CharSequence): String {
        // PhoneNumberUtil doesn't maintain country code input
        return PhoneNumberUtils.formatNumber(number.toString(), countryCode) ?: number.toString()
    }

    /**
     * Same as [formatNumber], but a number of the device's own country loses its country code,
     * e.g. "+33 6 ..." -> "06 ...". A foreign number keeps it, or it would pass for a local one, and
     * a sender name is left alone rather than read as letters on a keypad.
     */
    fun formatNumberNational(number: CharSequence): String {
        val parsed = number.takeIf { it.none(Char::isLetter) }
                ?.let(::parse)
                ?.takeIf { it.countryCode == phoneNumberUtil.getCountryCodeForRegion(countryCode) }
                ?: return formatNumber(number)
        return phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
    }

    fun normalizeNumber(number: String): String =
        number.filter { it.isLetterOrDigit() || it in "+*#" }

    private fun parse(number: CharSequence): Phonenumber.PhoneNumber? {
        return tryOrNull(false) { phoneNumberUtil.parse(number, countryCode) }
    }

}
