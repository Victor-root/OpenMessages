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
package io.openmessages.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.openmessages.R
import io.openmessages.common.util.ClipboardUtils

/**
 * Copies a verification code (extracted from an incoming SMS) to the clipboard when the user taps the
 * "copy code" notification action. A plain clipboard side-effect, so no injection is needed.
 */
class CopyCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(EXTRA_CODE)?.takeIf { it.isNotBlank() } ?: return
        ClipboardUtils.copy(context, code)
        Toast.makeText(context, R.string.notification_code_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_CODE = "code"
    }
}
