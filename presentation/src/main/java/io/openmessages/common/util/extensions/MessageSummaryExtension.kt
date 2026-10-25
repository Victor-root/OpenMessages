/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
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
package io.openmessages.common.util.extensions

import android.content.Context
import io.openmessages.R
import io.openmessages.model.MmsPart

/**
 * Turns the summary of a message into the reader's language.
 *
 * A message that is not plain text is summarised with fixed English words standing in for its
 * parts, and that summary is what the conversation list, the search results, the widget and the
 * notifications all show. Each of them therefore has to translate it, which is why the translating
 * lives here rather than four times over.
 *
 * A message is summarised a line per part, so each line is read on its own. Looking at the summary
 * as a whole only ever recognised a message holding a single part: one picture came out translated
 * and three pictures did not.
 */
fun Context.localiseSummary(summary: String?): String? = summary
    ?.lineSequence()
    ?.joinToString("\n") { line ->
        when (line) {
            MmsPart.SUMMARY_PICTURE -> getString(R.string.snippet_picture)
            MmsPart.SUMMARY_VIDEO -> getString(R.string.snippet_video)
            MmsPart.SUMMARY_AUDIO -> getString(R.string.snippet_audio)
            MmsPart.SUMMARY_CONTACT_CARD -> getString(R.string.snippet_contact_card)
            else -> line
        }
    }

/**
 * Whether [summary] is nothing but pictures, which is what earns the conversation list its little
 * photo icon. A summary is a line per part, so a message holding three of them says so three times.
 */
fun isPictureSummary(summary: String?): Boolean = !summary.isNullOrEmpty() &&
        summary.lineSequence().all { it == MmsPart.SUMMARY_PICTURE }
