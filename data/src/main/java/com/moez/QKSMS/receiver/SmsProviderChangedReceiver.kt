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
import io.openmessages.interactor.SyncMessage
import javax.inject.Inject

/**
 * When the SMS contentprovider is changed by a process other than us, we need to sync the Uri that
 * was changed.
 *
 * This can happen if a message is sent through something like Pushbullet or Google Assistant.
 *
 * This only works on API 24+, so to fully solve this problem we'll need a smarter way of running
 * partial syncs on older devices.
 *
 * https://developer.android.com/reference/android/provider/Telephony.Sms.Intents.html#ACTION_EXTERNAL_PROVIDER_CHANGE
 */
class SmsProviderChangedReceiver : BroadcastReceiver() {

    @Inject lateinit var syncMessage: SyncMessage

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        // Sync the message to our realm
        val pendingResult = goAsync()
        syncMessage.execute(SyncMessage.Params(intent.data ?: return)) {
            pendingResult.finish()
        }
    }

}