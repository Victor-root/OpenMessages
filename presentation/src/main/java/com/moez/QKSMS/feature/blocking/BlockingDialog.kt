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
package io.openmessages.feature.blocking

import io.openmessages.blocking.BlockingClient
import io.openmessages.interactor.MarkBlocked
import io.openmessages.interactor.MarkUnblocked
import io.openmessages.repository.ConversationRepository
import io.openmessages.util.Preferences
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Applies a block or unblock to whole conversations, from wherever the user asked for it.
 *
 * It no longer shows anything. Blocking always lands in the app's own blocklist, which needs no
 * permission from anyone, so the confirmation that used to send the user off to Call Control or
 * Should I Answer? was unreachable and has been removed. The name is kept for now because it is the
 * one every screen calls it by.
 */
class BlockingDialog @Inject constructor(
    private val blockingManager: BlockingClient,
    private val conversationRepo: ConversationRepository,
    private val prefs: Preferences,
    private val markBlocked: MarkBlocked,
    private val markUnblocked: MarkUnblocked
) {

    /**
     * [onComplete] is invoked on the main thread once the block or unblock has actually been
     * applied. Callers use it to react to the change, e.g. closing the conversation that was just
     * blocked. It is never invoked for a conversation with no recipient to act on.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun show(
        conversationIds: List<Long>,
        block: Boolean,
        onComplete: (() -> Unit)? = null
    ) = GlobalScope.launch {
        val addresses = conversationIds.toLongArray()
                .let { conversationRepo.getConversations(*it) }
                .flatMap { conversation -> conversation.recipients }
                .map { it.address }
                .distinct()

        if (addresses.isEmpty()) {
            return@launch
        }

        if (block) {
            markBlocked.execute(MarkBlocked.Params(conversationIds, prefs.blockingManager.get(), null)) { onComplete?.invoke() }
            blockingManager.block(addresses).subscribe()
        } else {
            markUnblocked.execute(conversationIds) { onComplete?.invoke() }
            blockingManager.unblock(addresses).subscribe()
        }
    }
}
