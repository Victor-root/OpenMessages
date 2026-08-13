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

import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the opt-in blocking lists on the user's request and hands them to their stores.
 *
 * This is the ONLY part of the app that touches the network: nothing here runs unless the user
 * enables an integrated source. The lists are stored locally afterwards and matched offline.
 */
@Singleton
class BlockingListDownloader @Inject constructor(
    private val phishingStore: PhishingDomainStore
) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun phishingDownloaded(): Boolean = phishingStore.hasDomains()

    /** Drops the stored phishing list, for when the user turns the source off. */
    fun clearPhishing() = phishingStore.clear()

    /** Fetches the phishing domain list, stores it, and returns the number of domains. */
    fun updatePhishing(): Single<Int> = Single.fromCallable {
        val body = fetch(PHISHING_URL)
        phishingStore.save(body)
        body.lineSequence().count { line -> line.isNotBlank() && !line.trimStart().startsWith("#") }
    }

    private fun fetch(url: String): String {
        Timber.i("Fetching blocking list: %s", url)
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty response")
            Timber.i("Fetched %s — HTTP %d, %d chars", url, response.code, body.length)
            body
        }
    }

    companion object {
        private const val PHISHING_URL =
            "https://blocklistproject.github.io/Lists/phishing.txt"
    }

}
