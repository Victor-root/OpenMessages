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
package io.openmessages.common.util

import android.media.AudioAttributes
import io.openmessages.R
import io.openmessages.util.Preferences

/**
 * Shared with the Settings preview so it plays on the exact same audio stream as a real send,
 * matching its volume instead of just its audio data (MediaPlayer otherwise defaults to the
 * music stream, which most phones control with a separate volume slider from system sounds).
 */
val sendSoundAudioAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/** Maps a Preferences.SEND_SOUND_* id (other than SEND_SOUND_OFF) to its bundled tone resource. */
fun sendSoundRes(id: Int): Int = when (id) {
    Preferences.SEND_SOUND_BRIGHT -> R.raw.message_sent_bright
    Preferences.SEND_SOUND_GENTLE -> R.raw.message_sent_gentle
    Preferences.SEND_SOUND_DESCENDING -> R.raw.message_sent_descending
    Preferences.SEND_SOUND_TRIAD -> R.raw.message_sent_triad
    else -> R.raw.message_sent
}
