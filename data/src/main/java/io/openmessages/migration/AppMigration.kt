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
package io.openmessages.migration

import android.content.Context
import io.openmessages.blocking.BuiltInBlockingClient
import io.openmessages.common.util.extensions.versionCode
import io.openmessages.repository.ConversationRepository
import io.openmessages.util.Preferences
import io.openmessages.util.RxSharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class AppMigration @Inject constructor(
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val prefs: Preferences,
    private val rxPrefs: RxSharedPreferences,
    private val builtInBlockingClient: BuiltInBlockingClient,
    private val applicationScope: CoroutineScope
) {

    fun performMigration() {
        applicationScope.launch {
            val oldVersion = prefs.version.get()

            if (oldVersion < 2199) {
                upgradeTo370()
            }

            // Once, whatever the version: build numbers no longer say when this was done
            val paletteMatched = rxPrefs.getBoolean("paletteVFiles")
            if (!paletteMatched.get()) {
                matchVFilesPalette()
                paletteMatched.set(true)
            }

            prefs.version.set(context.versionCode)
        }
    }

    /**
     * The colours on offer became those of VFiles, so a colour picked from the former ones, for the
     * app, a conversation or the app icon, becomes its VFiles twin
     */
    private fun matchVFilesPalette() {
        rxPrefs.keys
            .filter { key -> key == "theme" || key.startsWith("theme_") || key == "appIconColor" }
            .map { key -> rxPrefs.getInteger(key) }
            .forEach { preference -> VFILES_SHADES[preference.get()]?.let(preference::set) }
    }

    private fun upgradeTo370() {
        // Migrate changelog version
        prefs.changelogVersion.set(prefs.version.get())

        // Migrate from old SIA preference to blocking manager preference
        val sia = rxPrefs.getBoolean("sia", false)
        if (sia.get()) {
            prefs.blockingManager.set(Preferences.BLOCKING_MANAGER_SIA)
            sia.delete()
        }

        // Migrate blocked conversations into the built-in blocking client
        val addresses = conversationRepo.getBlockedConversations()
                .flatMap { conversation -> conversation.recipients }
                .map { recipient -> recipient.address }
                .distinct()

        builtInBlockingClient.block(addresses).blockingAwait()
    }

    private companion object {
        /** Each colour of the former palettes, and its VFiles twin */
        val VFILES_SHADES = mapOf(
            0xFF7B2DDC.toInt() to 0xFF6A2CD0.toInt(),
            0xFFF44336.toInt() to 0xFFBC111F.toInt(),
            0xFFE6C400.toInt() to 0xFFFFEB3B.toInt(),
            0xFFD32F2F.toInt() to 0xFFBC111F.toInt(),
            0xFFC2185B.toInt() to 0xFFE91E63.toInt(),
            0xFF7B1FA2.toInt() to 0xFF9C27B0.toInt(),
            0xFF512DA8.toInt() to 0xFF673AB7.toInt(),
            0xFF303F9F.toInt() to 0xFF3F51B5.toInt(),
            0xFF1976D2.toInt() to 0xFF2196F3.toInt(),
            0xFF0288D1.toInt() to 0xFF03A9F4.toInt(),
            0xFF0097A7.toInt() to 0xFF00BCD4.toInt(),
            0xFF00796B.toInt() to 0xFF009688.toInt(),
            0xFF388E3C.toInt() to 0xFF4CAF50.toInt(),
            0xFF689F38.toInt() to 0xFF8BC34A.toInt(),
            0xFFAFB42B.toInt() to 0xFFCDDC39.toInt(),
            0xFFCAAC00.toInt() to 0xFFFFEB3B.toInt(),
            0xFFFFA000.toInt() to 0xFFFFC107.toInt(),
            0xFFF57C00.toInt() to 0xFFFF9800.toInt(),
            0xFFE64A19.toInt() to 0xFFFF5722.toInt(),
            0xFF5D4037.toInt() to 0xFF795548.toInt(),
            0xFF616161.toInt() to 0xFF9E9E9E.toInt(),
            0xFF455A64.toInt() to 0xFF607D8B.toInt()
        )
    }

}
