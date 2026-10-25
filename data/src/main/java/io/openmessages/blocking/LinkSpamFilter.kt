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
package io.openmessages.blocking

import android.net.Uri
import android.util.Patterns
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans a message body for links whose host (or a parent domain) appears in the downloaded
 * phishing/scam list (see [PhishingDomainStore]). Returns the matched domain so the caller can use
 * it as a block reason, or null when nothing matches.
 */
@Singleton
class LinkSpamFilter @Inject constructor(
    private val store: PhishingDomainStore
) {

    fun firstBlockedDomain(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val domains = store.domains()
        if (domains.isEmpty()) return null

        val matcher = Patterns.WEB_URL.matcher(body)
        while (matcher.find()) {
            val host = hostOf(matcher.group()) ?: continue
            // Check the host and each parent domain, so a new subdomain of a listed domain matches
            // (e.g. login.evil.com matches a listed "evil.com").
            var candidate: String? = host
            while (candidate != null) {
                if (domains.contains(candidate)) return candidate
                val dot = candidate.indexOf('.')
                candidate = if (dot in 0 until candidate.length - 1) candidate.substring(dot + 1) else null
            }
        }
        return null
    }

    private fun hostOf(raw: String): String? {
        val url = if (raw.contains("://")) raw else "http://$raw"
        return try {
            Uri.parse(url).host?.lowercase()?.removePrefix("www.")
        } catch (e: Exception) {
            null
        }
    }

}
