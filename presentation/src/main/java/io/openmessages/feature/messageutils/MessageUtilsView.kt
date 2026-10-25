package io.openmessages.feature.messageutils

import io.openmessages.common.base.QkViewContract
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

interface MessageUtilsView: QkViewContract<MessageUtilsState> {
    val autoDeduplicateClickIntent: Observable<*>
    val deduplicateClickIntent: Observable<Unit>
    val autoDeleteClickIntent: Observable<Unit>

    fun showDeduplicationConfirmationDialog(): Single<Boolean>
    fun handleDeduplicationResult(resIdString: Int)

    fun autoDeleteChanged(): Observable<Int>
    fun showAutoDeleteDialog(days: Int)
    suspend fun showAutoDeleteWarningDialog(messages: Int): Boolean
}
