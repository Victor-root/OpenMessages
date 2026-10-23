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
package io.openmessages.feature.blocking.filters

import autodispose2.androidx.lifecycle.scope
import autodispose2.autoDispose
import io.openmessages.common.base.QkPresenter
import io.openmessages.repository.MessageContentFilterRepository
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

class MessageContentFiltersPresenter @Inject constructor(
    private val filterRepo: MessageContentFilterRepository,
) : QkPresenter<MessageContentFiltersView, MessageContentFiltersState>(MessageContentFiltersState()) {

    init {
        disposables += filterRepo.getMessageContentFilters()
                .subscribe { filters -> newState { copy(filters = filters) } }
    }

    override fun bindIntents(view: MessageContentFiltersView) {
        super.bindIntents(view)

        view.removeFilter()
            .observeOn(Schedulers.io())
            .doOnNext(filterRepo::removeFilter)
            .subscribeOn(Schedulers.io())
            .autoDispose(view.scope())
            .subscribe()

        view.addFilter()
            .autoDispose(view.scope())
            .subscribe { view.showAddDialog() }

        view.saveFilter()
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .autoDispose(view.scope())
            .subscribe { filterData -> filterRepo.createFilter(filterData) }
    }

}
