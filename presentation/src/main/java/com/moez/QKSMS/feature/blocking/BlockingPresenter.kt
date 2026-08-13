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
package io.openmessages.feature.blocking

import android.content.Context
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.openmessages.R
import io.openmessages.blocking.BlockingClient
import io.openmessages.blocking.BlockingListDownloader
import io.openmessages.common.base.QkPresenter
import io.openmessages.util.Preferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class BlockingPresenter @Inject constructor(
    private val context: Context,
    private val blockingClient: BlockingClient,
    private val prefs: Preferences,
    private val downloader: BlockingListDownloader
) : QkPresenter<BlockingView, BlockingState>(BlockingState()) {

    // Kept apart from the presenter's other subscriptions so turning the source off can cancel a
    // download still in flight, which would otherwise store a list for a source that is now off.
    private var phishingDownload: Disposable? = null

    init {
        disposables += prefs.blockingManager.asObservable()
                .map { client ->
                    when (client) {
                        Preferences.BLOCKING_MANAGER_CB -> R.string.blocking_manager_call_blocker_title
                        Preferences.BLOCKING_MANAGER_CC -> R.string.blocking_manager_call_control_title
                        Preferences.BLOCKING_MANAGER_SIA -> R.string.blocking_manager_sia_title
                        else -> R.string.app_name
                    }
                }
                .map(context::getString)
                .subscribe { manager -> newState { copy(blockingManager = manager) } }

        disposables += prefs.blockSourcePhishing.asObservable()
                .subscribe { enabled -> newState { copy(phishingEnabled = enabled) } }

        disposables += prefs.blockFlaggedAsSpam.asObservable()
                .subscribe { enabled -> newState { copy(flagBlockEnabled = enabled) } }

        disposables += prefs.drop.asObservable()
                .subscribe { enabled -> newState { copy(dropEnabled = enabled) } }

        // The source is only on if there is a list to match against. It can be stored as on with no
        // list behind it — a download that failed on an older build, or a restore onto a device the
        // list was never fetched on — and the switch would then claim a protection that isn't there.
        if (prefs.blockSourcePhishing.get() && !downloader.phishingDownloaded()) {
            prefs.blockSourcePhishing.set(false)
        }

        newState { copy(phishingSummary = phishingSummary()) }
    }

    override fun bindIntents(view: BlockingView) {
        super.bindIntents(view)

        view.blockingManagerIntent
                .autoDisposable(view.scope())
                .subscribe { view.openBlockingManager() }

        view.blockedNumbersIntent
                .autoDisposable(view.scope())
                .subscribe {
                    if (prefs.blockingManager.get() == Preferences.BLOCKING_MANAGER_DEFAULT) {
                        // TODO: This is a hack, get rid of it once we implement AndroidX navigation
                        view.openBlockedNumbers()
                    } else {
                        blockingClient.openSettings()
                    }
                }

        view.phishingSourceIntent
                .autoDisposable(view.scope())
                .subscribe { togglePhishing() }

        view.flagBlockSourceIntent
                .autoDisposable(view.scope())
                .subscribe { prefs.blockFlaggedAsSpam.set(!prefs.blockFlaggedAsSpam.get()) }

        view.messageContentFiltersIntent
                .autoDisposable(view.scope())
                .subscribe { view.openMessageContentFilters() }

        view.blockedMessagesIntent
                .autoDisposable(view.scope())
                .subscribe { view.openBlockedMessages() }

        view.dropClickedIntent
                .autoDisposable(view.scope())
                .subscribe { prefs.drop.set(!prefs.drop.get()) }
    }

    private fun phishingSummary(): String = when {
        prefs.blockSourcePhishing.get() && downloader.phishingDownloaded() ->
            context.getString(R.string.blocking_source_status_updated, prefs.blockPhishingCount.get())
        else -> context.getString(R.string.blocking_source_phishing_summary)
    }

    /** Toggle the source; the first time it is enabled, download its list (the only network call). */
    private fun togglePhishing() {
        val enabling = !prefs.blockSourcePhishing.get()
        prefs.blockSourcePhishing.set(enabling)
        phishingDownload?.dispose()
        if (enabling) {
            newState { copy(phishingSummary = context.getString(R.string.blocking_source_status_updating)) }
            val download = downloader.updatePhishing()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ count ->
                        prefs.blockPhishingCount.set(count)
                        newState { copy(phishingSummary = context.getString(R.string.blocking_source_status_updated, count)) }
                    }, { error ->
                        Timber.w(error, "Phishing list download failed")
                        // Nothing was stored, so nothing can be matched: switch back off rather than
                        // sit there enabled and filtering nothing. Toggling it again retries.
                        prefs.blockSourcePhishing.set(false)
                        newState { copy(phishingSummary = context.getString(R.string.blocking_source_status_failed)) }
                    })
            phishingDownload = download
            disposables += download
        } else {
            // Several megabytes that nothing matches against any more: drop the list rather than
            // leave it on the device. Turning the source back on downloads a fresh one, so the
            // stored count goes with it instead of describing a list that is gone.
            downloader.clearPhishing()
            prefs.blockPhishingCount.set(0)
            newState { copy(phishingSummary = phishingSummary()) }
        }
    }

}
