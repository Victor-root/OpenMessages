package io.openmessages.feature.blocking.manager

import io.openmessages.common.base.QkViewContract
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

interface BlockingManagerView : QkViewContract<BlockingManagerState> {

    fun activityResumed(): Observable<*>
    fun qksmsClicked(): Observable<*>
    fun callBlockerClicked(): Observable<*>
    fun callControlClicked(): Observable<*>
    fun siaClicked(): Observable<*>

    fun showCopyDialog(manager: String): Single<Boolean>

}
