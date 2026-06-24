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
 *
 * ---------------------------------------------------------------------------
 * The phone-number normalisation and wildcard matching below is ported from
 * Saracroche (util/PhoneNumberMatcher.kt) by Camille Bouvat, licensed GPLv3:
 *   https://codeberg.org/cbouvat/saracroche-android
 * The algorithm is kept identical so French telemarketing ("démarchage")
 * ranges behave here exactly as they do in the original app. Only the data
 * lists differ — Open Messages never bundles Saracroche's (NC-licensed) list.
 * ---------------------------------------------------------------------------
 */
package io.openmessages.blocking

/**
 * A single wildcard phone-number pattern. '#' matches any single digit; every other character must
 * match exactly. Patterns are fixed-length and compared against a number's digit string of equal
 * length (e.g. "33162######" matches a French number 0162 xx xx xx).
 */
data class BlockingPattern(
    val name: String,
    val pattern: String
)

/**
 * Pure phone-number normalisation + wildcard pattern matching. No Android dependencies, so it can
 * live in the domain layer and be reused by any blocking source backed by prefix patterns.
 */
object PatternMatcher {

    /**
     * Normalise a raw phone number string and generate its possible international variants as Longs.
     * If the number already carries an international prefix (+ or 00) it is returned as-is; otherwise
     * each known country [prefixes] entry is prepended to produce every plausible variant.
     */
    fun normalizePhoneNumber(phoneNumber: String, prefixes: Set<String>): List<Long> {
        val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")
        val hasInternationalPrefix = cleaned.startsWith("+") || cleaned.startsWith("00")
        val normalized = when {
            cleaned.startsWith("+") -> cleaned.substring(1)
            else -> cleaned.trimStart('0')
        }

        if (hasInternationalPrefix) {
            return listOfNotNull(normalized.toLongOrNull())
        }
        if (prefixes.any { normalized.startsWith(it) }) {
            return listOfNotNull(normalized.toLongOrNull())
        }
        return prefixes.mapNotNull { prefix -> (prefix + normalized).toLongOrNull() }
    }

    /**
     * Generate variants of an already-normalised Long number for each country prefix: if it starts
     * with a known prefix, swap it for each alternative; otherwise prepend each prefix.
     */
    fun generateVariants(phoneNumber: Long, prefixes: Set<String>): List<Long> {
        val phoneStr = phoneNumber.toString()
        val matchedPrefix = prefixes.sortedByDescending { it.length }.firstOrNull { phoneStr.startsWith(it) }

        if (matchedPrefix != null) {
            val withoutPrefix = phoneStr.removePrefix(matchedPrefix)
            return prefixes.mapNotNull { prefix -> (prefix + withoutPrefix).toLongOrNull() }
        }

        return prefixes.mapNotNull { prefix -> (prefix + phoneStr).toLongOrNull() }
    }

    /** Check if a number's digit string matches a single wildcard pattern. Lengths must be equal. */
    fun matchesPattern(phoneNumber: Long, pattern: String): Boolean {
        val phoneStr = phoneNumber.toString()
        if (phoneStr.length != pattern.length) return false

        for (i in pattern.indices) {
            when (pattern[i]) {
                '#' -> if (!phoneStr[i].isDigit()) return false
                else -> if (phoneStr[i] != pattern[i]) return false
            }
        }
        return true
    }

    /** True if any of [normalizedVariants] matches any of [patterns]. */
    fun matchesAnyPattern(normalizedVariants: List<Long>, patterns: List<BlockingPattern>): Boolean =
        normalizedVariants.any { normalized ->
            patterns.any { pattern -> matchesPattern(normalized, pattern.pattern) }
        }

    /**
     * Returns the first [patterns] entry that matches [phoneNumber] (so the caller can surface its
     * name as a reason), or null if none match.
     */
    fun findMatchingPattern(
        phoneNumber: String,
        prefixes: Set<String>,
        patterns: List<BlockingPattern>
    ): BlockingPattern? {
        for (variant in normalizePhoneNumber(phoneNumber, prefixes)) {
            val match = patterns.firstOrNull { pattern -> matchesPattern(variant, pattern.pattern) }
            if (match != null) return match
        }
        return null
    }

    /** Convenience: normalise [phoneNumber] and report whether it matches any of [patterns]. */
    fun matches(phoneNumber: String, prefixes: Set<String>, patterns: List<BlockingPattern>): Boolean =
        matchesAnyPattern(normalizePhoneNumber(phoneNumber, prefixes), patterns)

}
