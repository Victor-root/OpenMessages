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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.android.AndroidInjection
import io.openmessages.blocking.BlockingClient
import io.openmessages.interactor.MarkBlocked
import io.openmessages.repository.ConversationRepository
import io.openmessages.util.Preferences
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class BlockThreadReceiver : BroadcastReceiver() {

    @Inject lateinit var blockingClient: BlockingClient
    @Inject lateinit var conversationRepo: ConversationRepository
    @Inject lateinit var markBlocked: MarkBlocked
    @Inject lateinit var prefs: Preferences

    @SuppressLint("CheckResult")
    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        Timber.v("received")

        val pendingResult = goAsync()
        val threadId = intent.getLongExtra("threadId", 0)

        // Deferred, so that the conversation is read off the main thread, which the database does
        // not allow
        Completable
            .defer {
                blockingClient.block(
                    conversationRepo.getConversation(threadId)
                        ?.recipients
                        ?.map { it.address }
                        ?: listOf()
                )
            }
            .andThen(
                markBlocked.buildObservable(
                    MarkBlocked.Params(listOf(threadId), prefs.blockingManager.get(), null)
                )
            )
            .subscribeOn(Schedulers.io())
            .doFinally { pendingResult.finish() }
            .subscribe({}, { error -> Timber.e(error, "blocking failed") })
    }
}
