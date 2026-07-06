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
 * language) are considered, so we don't grab unrelated numbers — times, amounts, order references.
 * From such a message it returns the first 4-8 digit run (optionally split once by a space or hyphen,
 * e.g. "123 456"), or null when nothing code-like is found.
 */
object OtpCodeExtractor {

    // English + French cues that a message is delivering a code. Substrings, case-insensitive.
    private val KEYWORDS = Regex(
        "(?i)(code|otp|passcode|password|mot de passe|\\bpin\\b|verif|vérif|authentif|authenticat|" +
        "one[\\- ]?time|usage unique|connexion|identifiant|security|sécurit|\\blogin\\b|\\bsecret\\b)")

    // A 4-8 digit run not glued to other digits, optionally split once by a single space or hyphen.
    private val CODE = Regex("(?<![0-9])([0-9]{3,4})[ -]?([0-9]{3,4})?(?![0-9])")

    fun extract(body: String): String? {
        if (body.isBlank() || !KEYWORDS.containsMatchIn(body)) return null
        val match = CODE.find(body) ?: return null
        val code = match.groupValues[1] + match.groupValues[2]
        return code.takeIf { it.length in 4..8 }
    }
}
