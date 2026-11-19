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
package io.openmessages.feature.conversationmedia

import autodispose2.androidx.lifecycle.scope
import autodispose2.autoDispose
import io.openmessages.common.Navigator
import io.openmessages.common.base.BasePresenter
import io.openmessages.repository.MessageRepository
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Named

class ConversationMediaPresenter @Inject constructor(
    @Named("threadId") threadId: Long,
    messageRepo: MessageRepository,
    private val navigator: Navigator
) : BasePresenter<ConversationMediaView, ConversationMediaState>(
        ConversationMediaState(threadId = threadId)
) {

    init {
        disposables += messageRepo.getPartsForConversation(threadId)
                .subscribe { parts -> newState { copy(media = parts) } }
    }

    override fun bindIntents(view: ConversationMediaView) {
        super.bindIntents(view)

        view.mediaClicks()
                .autoDispose(view.scope())
                .subscribe(navigator::showMedia)
    }
}
