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

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AlertDialog
import io.openmessages.R
import io.openmessages.blocking.BlockingClient
import io.openmessages.common.util.extensions.themeButtons
import io.openmessages.interactor.MarkBlocked
import io.openmessages.interactor.MarkUnblocked
import io.openmessages.repository.ConversationRepository
import io.openmessages.util.Preferences
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// TODO: Once we have a custom dialog based on conductor, turn this into a controller
class BlockingDialog @Inject constructor(
    private val blockingManager: BlockingClient,
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val prefs: Preferences,
    private val markBlocked: MarkBlocked,
    private val markUnblocked: MarkUnblocked
) {

    /**
     * [onComplete] is invoked on the main thread once the block/unblock has actually been applied
     * (never on cancel). Callers use it to react to the change, e.g. closing the conversation that
     * was just blocked.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun show(
        activity: Activity,
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

        if (blockingManager.getClientCapability() == BlockingClient.Capability.BLOCK_WITHOUT_PERMISSION) {
            // If we can block/unblock in the external manager, then just fire that off and exit
            if (block) {
                markBlocked.execute(MarkBlocked.Params(conversationIds, prefs.blockingManager.get(), null)) { onComplete?.invoke() }
                blockingManager.block(addresses).subscribe()
            } else {
                markUnblocked.execute(conversationIds) { onComplete?.invoke() }
                blockingManager.unblock(addresses).subscribe()
            }
        } else if (block == allBlocked(addresses)) {
            // If all of the addresses are already in their correct state in the blocking manager, just marked the
            // conversations blocked and exit
            when (block) {
                true -> markBlocked.execute(MarkBlocked.Params(conversationIds, prefs.blockingManager.get(), null)) { onComplete?.invoke() }
                false -> markUnblocked.execute(conversationIds) { onComplete?.invoke() }
            }
        } else {
            // Otherwise, show the UI that lets the users know they need to mark the number as blocked in the client
            showDialog(activity, conversationIds, addresses, block, onComplete)
        }
    }

    private fun allBlocked(addresses: List<String>): Boolean = addresses.all { address ->
        blockingManager.isBlacklisted(address).blockingGet() is BlockingClient.Action.Block
    }

    private suspend fun showDialog(
        activity: Activity,
        conversationIds: List<Long>,
        addresses: List<String>,
        block: Boolean,
        onComplete: (() -> Unit)? = null
    ) = withContext(MainScope().coroutineContext) {
        val res = when (block) {
            true -> R.plurals.blocking_block_external
            false -> R.plurals.blocking_unblock_external
        }

        val manager = context.getString(when (prefs.blockingManager.get()) {
            Preferences.BLOCKING_MANAGER_CB -> R.string.blocking_manager_call_blocker_title
            Preferences.BLOCKING_MANAGER_CC -> R.string.blocking_manager_call_control_title
            Preferences.BLOCKING_MANAGER_SIA -> R.string.blocking_manager_sia_title
            else -> R.string.app_name
        })

        val message = context.resources.getQuantityString(res, addresses.size, manager)

        // Otherwise, show a dialog asking the user if they want to be directed to the external
        // blocking manager
        AlertDialog.Builder(activity)
                .setTitle(when (block) {
                    true -> R.string.blocking_block_title
                    false -> R.string.blocking_unblock_title
                })
                .setMessage(message)
                .setPositiveButton(R.string.button_continue) { _, _ ->
                    if (block) {
                        markBlocked.execute(MarkBlocked.Params(conversationIds, prefs.blockingManager.get(), null)) { onComplete?.invoke() }
                        blockingManager.block(addresses).subscribe()
                    } else {
                        markUnblocked.execute(conversationIds) { onComplete?.invoke() }
                        blockingManager.unblock(addresses).subscribe()
                    }
                }
                .setNegativeButton(R.string.button_cancel) { _, _ -> }
                .create()
                .themeButtons(prefs.theme().get())
                .show()
    }

}
