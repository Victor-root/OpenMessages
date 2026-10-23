package io.openmessages.repository

import io.openmessages.database.AppDatabase
import io.openmessages.model.ScheduledMessage
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class ScheduledMessageRepositoryImpl @Inject constructor(
    private val db: AppDatabase
) : ScheduledMessageRepository {

    private val dao = db.scheduledMessageDao()

    private val disposables = CompositeDisposable()

    override fun saveScheduledMessage(
        date: Long,
        subId: Int,
        recipients: List<String>,
        sendAsGroup: Boolean,
        body: String,
        attachments: List<String>,
        conversationId: Long
    ): ScheduledMessage {
        return db.runInTransaction<ScheduledMessage> {
            val id = (dao.getMaxId() ?: -1) + 1

            val message = ScheduledMessage(id, date, subId, recipients, sendAsGroup, body,
                attachments, conversationId)

            dao.upsert(message)

            message
        }
    }

    override fun updateScheduledMessage(scheduledMessage: ScheduledMessage) {
        dao.upsert(scheduledMessage)
    }

    override fun getScheduledMessages(): Observable<List<ScheduledMessage>> {
        return dao.observeAll().toObservable()
    }

    override fun getScheduledMessagesSnapshot(): List<ScheduledMessage> {
        return dao.getAll()
    }

    override fun getScheduledMessage(id: Long): ScheduledMessage? {
        return dao.get(id)
    }

    override fun getScheduledMessagesForConversation(conversationId: Long): Observable<List<ScheduledMessage>> {
        return dao.observeForConversation(conversationId).toObservable()
    }

    override fun deleteScheduledMessage(id: Long) {
        val subscription = Completable.fromAction { dao.delete(id) }
            .subscribeOn(Schedulers.io()) // Run on a background thread and switch to main if needed
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Timber.v("Successfully deleted scheduled messages.")
            }, {
                Timber.e("Deleting scheduled messages failed.")
            })

        disposables.add(subscription)
    }

    override fun deleteScheduledMessages(ids: List<Long>) {
        ids.forEach { deleteScheduledMessage(it) }
    }

    override fun getAllScheduledMessageIdsSnapshot(): List<Long> {
        return dao.getAllIds()
    }
}
