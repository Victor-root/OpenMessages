/*
 * Copyright (C) 2019 Moez Bhatti <moez.bhatti@gmail.com>
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
package io.openmessages.feature.blocking.messages

import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDispose
import io.openmessages.R
import io.openmessages.blocking.BlockingClient
import io.openmessages.common.Navigator
import io.openmessages.common.base.QkPresenter
import io.openmessages.interactor.DeleteConversations
import io.openmessages.repository.ConversationRepository
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class BlockedMessagesPresenter @Inject constructor(
    conversationRepo: ConversationRepository,
    private val blockingClient: BlockingClient,
    private val deleteConversations: DeleteConversations,
    private val navigator: Navigator
) : QkPresenter<BlockedMessagesView, BlockedMessagesState>(BlockedMessagesState()) {

    init {
        disposables += conversationRepo.observeBlockedConversations()
                .subscribe { conversations -> newState { copy(data = conversations) } }
    }

    override fun bindIntents(view: BlockedMessagesView) {
        super.bindIntents(view)

        view.menuReadyIntent
                .autoDispose(view.scope())
                .subscribe { newState { copy() } }

        view.optionsItemIntent
                .withLatestFrom(view.selectionChanges) { itemId, conversations ->
                    when (itemId) {
                        R.id.block -> {
                            view.showBlockingDialog(conversations, false)
                            view.clearSelection()
                        }
                        R.id.delete -> {
                            view.showDeleteDialog(conversations)
                        }
                    }

                }
                .autoDispose(view.scope())
                .subscribe()

        view.confirmDeleteIntent
                .autoDispose(view.scope())
                .subscribe { conversations ->
                    deleteConversations.execute(conversations)
                    view.clearSelection()
                }

        view.conversationClicks
                .autoDispose(view.scope())
                .subscribe { threadId -> navigator.showConversation(threadId) }

        view.selectionChanges
                .autoDispose(view.scope())
                .subscribe { selection -> newState { copy(selected = selection.size) } }

        view.backClicked
                .withLatestFrom(state) { _, state ->
                    when (state.selected) {
                        0 -> view.goBack()
                        else -> view.clearSelection()
                    }
                }
                .autoDispose(view.scope())
                .subscribe()
    }

}
