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
package io.openmessages.service

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.telephony.TelephonyManager
import dagger.android.AndroidInjection
import io.openmessages.interactor.SendNewMessage
import io.openmessages.repository.ConversationRepository
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Sends the quick replies offered to an incoming call
 */
class HeadlessSmsSendService : Service() {
    @Inject lateinit var conversationRepo: ConversationRepository
    @Inject lateinit var sendNewMessage: SendNewMessage

    // One at a time, in the order they came, so the service only stops once the last is through
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        executor.execute {
            try {
                respond(intent)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun respond(intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) return

        intent.extras?.getString(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { body ->
            val recipients = intent.data?.let(::getRecipients)?.split(";") ?: return@let

            val conversation = conversationRepo.getOrCreateConversation(recipients)

            sendNewMessage.execute(SendNewMessage.Params(
                -1, 0, recipients, body, conversation?.sendAsGroup ?: false
            ))
        }
    }

    private fun getRecipients(uri: Uri): String {
        val base = uri.schemeSpecificPart
        val position = base.indexOf('?')
        return if (position == -1) base else base.substring(0, position)
    }

}
