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

import android.content.Context
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores and parses the downloaded phishing/scam domain list.
 *
 * The list (~190k domains) is far too large and too volatile to bundle, so it is fetched on the
 * user's request and cached here as a private file, then matched fully offline afterwards. The
 * file is in hosts format ("0.0.0.0 domain"), optionally with "#" comment lines.
 */
@Singleton
class PhishingDomainStore @Inject constructor(
    private val context: Context
) {

    private val file: File
        get() = File(context.filesDir, "blocking/phishing_domains.txt")

    @Volatile private var cache: Set<String>? = null

    /** True once a list has been downloaded and cached locally. */
    fun hasDomains(): Boolean = file.exists() && file.length() > 0

    /** Persist freshly-downloaded list content and drop the in-memory cache. */
    @Synchronized
    fun save(content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
        cache = null
    }

    /** Forget the downloaded list entirely (e.g. when the user disables the source). */
    @Synchronized
    fun clear() {
        file.delete()
        cache = null
    }

    /** The set of blocked domains, parsed and cached on first use. */
    fun domains(): Set<String> = cache ?: load()

    @Synchronized
    private fun load(): Set<String> {
        cache?.let { return it }

        val domains = HashSet<String>()
        try {
            if (hasDomains()) {
                file.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                    // hosts format: "0.0.0.0 domain" (or a plain "domain"); take the last token
                    val domain = trimmed.substringAfterLast(' ').substringAfterLast('\t').lowercase()
                    if (domain.isNotEmpty() && domain != "localhost") domains.add(domain)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse phishing domain list")
        }

        cache = domains
        return domains
    }

}
