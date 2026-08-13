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
package io.openmessages.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.android.AndroidInjection
import io.openmessages.util.NightModeManager
import javax.inject.Inject

class NightModeReceiver : BroadcastReceiver() {

    @Inject lateinit var nightModeManager: NightModeManager

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        nightModeManager.updateCurrentTheme()
    }
}