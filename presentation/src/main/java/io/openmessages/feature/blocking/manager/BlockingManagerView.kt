package io.openmessages.feature.blocking.manager

import io.openmessages.common.base.ControllerView
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

interface BlockingManagerView : ControllerView<BlockingManagerState> {

    fun activityResumed(): Observable<*>
    fun builtInClicked(): Observable<*>
    fun callBlockerClicked(): Observable<*>
    fun callControlClicked(): Observable<*>
    fun siaClicked(): Observable<*>

    fun showCopyDialog(manager: String): Single<Boolean>

}
