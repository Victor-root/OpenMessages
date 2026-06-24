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
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores and parses the Saracroche "démarchage" (French telemarketing) pattern list.
 *
 * The list is deliberately NOT bundled with the app: Saracroche's data is CC BY-NC-SA, which is
 * incompatible with Open Messages' GPLv3. Instead, the user fetches it on demand; we cache the
 * raw response as a private file here and match against it entirely offline afterwards.
 *
 * Expected JSON shape (https://app.saracroche.org/api/v1/lists/french-list-arcep-operators):
 *   { "version": ..., "patterns": [ { "name": ..., "action": "block"|"identify", "pattern": ... } ] }
 */
@Singleton
class SaracrochePatternStore @Inject constructor(
    private val context: Context
) {

    private val file: File
        get() = File(context.filesDir, "blocking/saracroche_arcep.json")

    @Volatile private var blockCache: List<BlockingPattern>? = null
    @Volatile private var identifyCache: List<BlockingPattern>? = null

    /** True once a list has been downloaded and cached locally. */
    fun hasPatterns(): Boolean = file.exists() && file.length() > 0

    /** Persist freshly-downloaded list content and drop the in-memory cache. */
    @Synchronized
    fun save(json: String) {
        file.parentFile?.mkdirs()
        file.writeText(json)
        blockCache = null
        identifyCache = null
    }

    /** Forget the downloaded list entirely (e.g. when the user disables the source). */
    @Synchronized
    fun clear() {
        file.delete()
        blockCache = null
        identifyCache = null
    }

    /** Patterns whose action is "block" (confirmed telemarketing ranges). */
    fun blockPatterns(): List<BlockingPattern> {
        ensureLoaded()
        return blockCache ?: emptyList()
    }

    /** Patterns whose action is "identify" (potential spam — surfaced as a soft flag, not a block). */
    fun identifyPatterns(): List<BlockingPattern> {
        ensureLoaded()
        return identifyCache ?: emptyList()
    }

    @Synchronized
    private fun ensureLoaded() {
        if (blockCache != null && identifyCache != null) return

        val block = mutableListOf<BlockingPattern>()
        val identify = mutableListOf<BlockingPattern>()
        try {
            if (hasPatterns()) {
                val patterns = JSONObject(file.readText()).optJSONArray("patterns")
                if (patterns != null) {
                    for (i in 0 until patterns.length()) {
                        val obj = patterns.optJSONObject(i) ?: continue
                        val pattern = obj.optString("pattern").takeIf { it.isNotBlank() } ?: continue
                        val entry = BlockingPattern(obj.optString("name"), pattern)
                        when (obj.optString("action")) {
                            "identify" -> identify += entry
                            else -> block += entry
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse Saracroche pattern list")
        }

        blockCache = block
        identifyCache = identify
    }

}
