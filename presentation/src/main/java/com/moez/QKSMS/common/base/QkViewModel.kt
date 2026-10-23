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
package io.openmessages.common.base

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import autodispose2.androidx.lifecycle.scope
import autodispose2.autoDispose
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject

abstract class QkViewModel<in View : QkView<State>, State: Any>(initialState: State) : ViewModel() {

    protected val disposables = CompositeDisposable()
    protected val state: Subject<State> = BehaviorSubject.createDefault(initialState)

    // Reducers can arrive from more than one thread at once (e.g. a sampled background
    // observable racing a UI event), and a plain Subject doesn't allow that: onNext must be
    // serialized, or an update can be silently dropped.
    private val stateReducer: Subject<State.() -> State> = PublishSubject.create<State.() -> State>().toSerialized()

    init {
        // Views render on the main thread, so the state is reduced there too
        disposables += stateReducer
            .observeOn(AndroidSchedulers.mainThread())
            .scan(initialState) { state, reducer -> reducer(state) }
            .subscribe(state::onNext)
    }

    @CallSuper
    open fun bindView(view: View) {
        state
            .observeOn(AndroidSchedulers.mainThread())
            .autoDispose(view.scope())
            .subscribe(view::render)
    }

    protected fun newState(reducer: State.() -> State) = stateReducer.onNext(reducer)

    override fun onCleared() = disposables.dispose()

}