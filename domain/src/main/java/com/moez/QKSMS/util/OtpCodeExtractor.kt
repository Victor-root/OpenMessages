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

/**
 * Best-effort extraction of a one-time / verification code from an SMS body, powering the "copy code
 * from notification" action.
 *
 * Only messages that read like a code delivery (they mention a code / OTP / PIN in a supported
 * language) are considered, so we don't grab unrelated numbers: times, amounts, order references.
 * From such a message it returns, in order of preference:
 *  - a short letter-prefixed code glued to digits by a hyphen (e.g. "ABC-4921"), kept as-is; or
 *  - a 4-8 digit run, optionally split into up to three groups by a space or hyphen (e.g. "123456",
 *    "123 456", "51-84-22"), with the separators stripped;
 * or null when nothing code-like is found.
 */
object OtpCodeExtractor {

    // English + French cues that a message is delivering a code. Substrings, case-insensitive.
    private val KEYWORDS = Regex(
        "(?i)(code|otp|passcode|password|mot de passe|\\bpin\\b|verif|vérif|authentif|authenticat|" +
        "one[\\- ]?time|usage unique|connexion|identifiant|security|sécurit|\\blogin\\b|\\bsecret\\b)")

    // A 2-6 letter prefix glued to a 3-8 digit run by a hyphen (e.g. "ABC-4921"). The hyphen is part
    // of how these codes are typed back in, so the match is returned verbatim, unlike the digit code
    // below. Requires the hyphen (no space) so ordinary phrasing like "page 12" is never mistaken for
    // one.
    private val ALPHA_CODE = Regex("(?<![A-Za-z0-9])([A-Za-z]{2,6}-[0-9]{3,8})(?![A-Za-z0-9])")

    // 1-3 digit groups of 2-4 digits each, every group after the first optionally separated from the
    // previous one by a single space or hyphen (e.g. "123456", "123 456", "51-84-22"), not glued to
    // other digits.
    private val DIGIT_CODE = Regex("(?<![0-9])([0-9]{2,4}(?:[ -]?[0-9]{2,4}){0,2})(?![0-9])")

    fun extract(body: String): String? {
        if (body.isBlank() || !KEYWORDS.containsMatchIn(body)) return null
        ALPHA_CODE.find(body)?.let { return it.groupValues[1] }
        val match = DIGIT_CODE.find(body) ?: return null
        val code = match.groupValues[1].replace(Regex("[ -]"), "")
        return code.takeIf { it.length in 4..8 }
    }
}
