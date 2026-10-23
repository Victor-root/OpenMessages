package io.openmessages.feature.messageutils

import android.content.Context
import autodispose2.androidx.lifecycle.scope
import autodispose2.autoDispose
import io.openmessages.R
import io.openmessages.common.base.QkPresenter
import io.openmessages.interactor.DeduplicateMessages
import io.openmessages.interactor.DeleteOldMessages
import io.openmessages.repository.MessageRepository
import io.openmessages.service.AutoDeleteService
import io.openmessages.util.Preferences
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MessageUtilsPresenter @Inject constructor(
    private val context: Context,
    private val deduplicateMessages: DeduplicateMessages,
    private val prefs: Preferences,
    private val deleteOldMessages: DeleteOldMessages,
    private val messageRepo: MessageRepository
) : QkPresenter<MessageUtilsView, MessageUtilsState>(MessageUtilsState()) {
    init {
        disposables += messageRepo.deduplicationProgress
            .sample(16, TimeUnit.MILLISECONDS)
            .distinctUntilChanged()
            .subscribe { deduplicateProgress -> newState { copy(deduplicationProgress = deduplicateProgress)}}

        disposables += prefs.autoDeduplicate.asObservable()
            .subscribe { enabled -> newState { copy(autoDeduplicateMessages = enabled) } }

        disposables += prefs.autoDelete.asObservable()
            .subscribe { autoDelete -> newState { copy(autoDelete = autoDelete) } }
    }

    override fun bindIntents(view: MessageUtilsView) {
        super.bindIntents(view)

        view.deduplicateClickIntent
            .flatMapSingle { view.showDeduplicationConfirmationDialog() }
            .filter { it }
            .observeOn(Schedulers.io())
            .autoDispose(view.scope())
            .subscribe {
                deduplicateMessages.buildObservable(Unit)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { result ->
                        when (result) {
                            is MessageRepository.DeduplicationResult.Success -> {
                                view.handleDeduplicationResult(R.string.deduplicating_messages_finished)
                            }

                            is MessageRepository.DeduplicationResult.NoDuplicates -> {
                                view.handleDeduplicationResult(R.string.deduplicating_messages_no_messages)
                            }

                            is MessageRepository.DeduplicationResult.Failure -> {
                                view.handleDeduplicationResult(R.string.deduplicating_messages_failed)
                                Timber.e("Error while deduplicating messages: ${result.error}")
                            }
                        }
                    }
            }

        view.autoDeduplicateClickIntent
            .autoDispose(view.scope())
            .subscribe { prefs.autoDeduplicate.set(!prefs.autoDeduplicate.get()) }

        view.autoDeleteClickIntent
            .autoDispose(view.scope())
            .subscribe { view.showAutoDeleteDialog(prefs.autoDelete.get()) }

        view.autoDeleteChanged()
            .observeOn(Schedulers.io())
            .filter { maxAge ->
                if (maxAge == 0) {
                    return@filter true
                }

                val counts = messageRepo.getOldMessageCounts(maxAge)
                if (counts.values.sum() == 0) {
                    return@filter true
                }

                runBlocking { view.showAutoDeleteWarningDialog(counts.values.sum()) }
            }
            .doOnNext { maxAge ->
                when (maxAge == 0) {
                    true -> AutoDeleteService.cancelJob(context)
                    false -> {
                        AutoDeleteService.scheduleJob(context)
                        deleteOldMessages.execute(Unit)
                    }
                }
            }
            .doOnNext(prefs.autoDelete::set)
            .autoDispose(view.scope())
            .subscribe()
    }
}
