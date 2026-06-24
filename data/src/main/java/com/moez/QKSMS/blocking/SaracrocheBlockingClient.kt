/*
 * Copyright (C) 2026 OpenMessages contributors
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
package io.openmessages.blocking

import io.reactivex.Completable
import io.reactivex.Single
import javax.inject.Inject

/**
 * Internal blocking source backed by Saracroche's French telemarketing ("démarchage") pattern list
 * (see [SaracrochePatternStore]). Read-only: the list can't be edited from here, so block/unblock
 * are no-ops. It returns [BlockingClient.Action.Block] for confirmed-spam ranges and
 * [BlockingClient.Action.Flag] for "potential spam" ranges so the conversation is merely tagged.
 */
class SaracrocheBlockingClient @Inject constructor(
    private val store: SaracrochePatternStore
) : BlockingClient {

    override fun isAvailable(): Boolean = store.hasPatterns()

    override fun getClientCapability() = BlockingClient.Capability.BLOCK_WITHOUT_PERMISSION

    override fun shouldBlock(address: String): Single<BlockingClient.Action> = isBlacklisted(address)

    override fun isBlacklisted(address: String): Single<BlockingClient.Action> = Single.fromCallable {
        val blockMatch = PatternMatcher.findMatchingPattern(address, PREFIXES, store.blockPatterns())
        val identifyMatch = when (blockMatch) {
            null -> PatternMatcher.findMatchingPattern(address, PREFIXES, store.identifyPatterns())
            else -> null
        }
        when {
            blockMatch != null -> BlockingClient.Action.Block(blockMatch.name)
            identifyMatch != null -> BlockingClient.Action.Flag(identifyMatch.name)
            else -> BlockingClient.Action.DoNothing
        }
    }

    override fun block(addresses: List<String>): Completable = Completable.complete()

    override fun unblock(addresses: List<String>): Completable = Completable.complete()

    override fun openSettings() = Unit

    companion object {
        // Saracroche's list targets French numbers; national numbers are normalised to the 33 code.
        private val PREFIXES = setOf("33")
    }

}
