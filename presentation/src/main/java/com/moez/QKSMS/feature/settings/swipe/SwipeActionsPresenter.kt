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
package io.openmessages.feature.settings.swipe

import android.content.Context
import androidx.annotation.DrawableRes
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.openmessages.R
import io.openmessages.common.base.QkPresenter
import io.openmessages.common.util.extensions.labelFor
import io.openmessages.util.Preferences
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class SwipeActionsPresenter @Inject constructor(
    context: Context,
    private val prefs: Preferences
) : QkPresenter<SwipeActionsView, SwipeActionsState>(SwipeActionsState()) {

    init {
        val actionLabels = context.resources.getStringArray(R.array.settings_swipe_actions)

        disposables += prefs.swipeRight.asObservable()
                .subscribe { action ->
                    val label = actionLabels.labelFor(action, prefs.swipeRight.defaultValue())
                    newState { copy(rightLabel = label, rightIcon = iconForAction(action)) }
                }

        disposables += prefs.swipeLeft.asObservable()
                .subscribe { action ->
                    val label = actionLabels.labelFor(action, prefs.swipeLeft.defaultValue())
                    newState { copy(leftLabel = label, leftIcon = iconForAction(action)) }
                }
    }

    override fun bindIntents(view: SwipeActionsView) {
        super.bindIntents(view)

        view.actionClicks()
                .map { action ->
                    when (action) {
                        SwipeActionsView.Action.RIGHT -> prefs.swipeRight.get()
                        SwipeActionsView.Action.LEFT -> prefs.swipeLeft.get()
                    }
                }
                .autoDisposable(view.scope())
                .subscribe(view::showSwipeActions)

        view.actionSelected()
                .withLatestFrom(view.actionClicks()) { actionId, action ->
                    when (action) {
                        SwipeActionsView.Action.RIGHT -> prefs.swipeRight.set(actionId)
                        SwipeActionsView.Action.LEFT -> prefs.swipeLeft.set(actionId)
                    }
                }
                .autoDisposable(view.scope())
                .subscribe()
    }

    @DrawableRes
    private fun iconForAction(action: Int) = when (action) {
        Preferences.SWIPE_ACTION_ARCHIVE -> R.drawable.ic_archive_white_24dp
        Preferences.SWIPE_ACTION_DELETE -> R.drawable.ic_delete_white_24dp
        Preferences.SWIPE_ACTION_BLOCK -> R.drawable.ic_block_white_24dp
        Preferences.SWIPE_ACTION_CALL -> R.drawable.ic_call_white_24dp
        Preferences.SWIPE_ACTION_READ -> R.drawable.ic_check_white_24dp
        Preferences.SWIPE_ACTION_UNREAD -> R.drawable.ic_markunread_black_24dp
        Preferences.SWIPE_ACTION_TOGGLE_READ -> R.drawable.ic_mail
        Preferences.SWIPE_ACTION_SPEAK -> R.drawable.ic_speaker_black_24dp
        else -> 0
    }

}