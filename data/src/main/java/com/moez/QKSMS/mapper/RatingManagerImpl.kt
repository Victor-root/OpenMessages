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
package io.openmessages.mapper

import io.openmessages.manager.RatingManager
import io.openmessages.util.RxSharedPreferences
import io.reactivex.rxjava3.core.Observable
import javax.inject.Inject

class RatingManagerImpl @Inject constructor(
    rxPrefs: RxSharedPreferences,
) : RatingManager {

    companion object {
        private const val RATING_THRESHOLD = 10
    }

    private val sessions = rxPrefs.getInteger("sessions", 0)
    private val rated = rxPrefs.getBoolean("rated", false)
    private val dismissed = rxPrefs.getBoolean("dismissed", false)

    override val shouldShowRating = Observable.combineLatest(
            sessions.asObservable(),
            rated.asObservable(),
            dismissed.asObservable()
    ) { sessions, rated, dismissed ->
        sessions > RATING_THRESHOLD && !rated && !dismissed
    }

    override fun addSession() {
        sessions.set(sessions.get() + 1)
    }

    override fun rate() {
        rated.set(true)
    }

    override fun dismiss() {
        dismissed.set(true)
    }

}
