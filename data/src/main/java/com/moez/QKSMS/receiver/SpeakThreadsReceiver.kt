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
import io.openmessages.interactor.SpeakThreads
import io.openmessages.repository.ConversationRepository
import javax.inject.Inject

class SpeakThreadsReceiver : BroadcastReceiver() {

    @Inject lateinit var speakThread: SpeakThreads
    @Inject lateinit var conversationRepo: ConversationRepository


    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        val pendingResult = goAsync()
        val threadId = intent.getLongExtra("threadId", 0)

        val threads = when {
            (threadId == -1L) -> conversationRepo.getUnseenIds()
            (threadId == -2L) -> conversationRepo.getUnreadIds()
            else -> listOf(threadId)
        }

        speakThread.execute(threads) { pendingResult.finish() }
    }

}