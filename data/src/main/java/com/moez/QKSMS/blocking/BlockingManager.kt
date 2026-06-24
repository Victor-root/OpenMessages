package io.openmessages.blocking

import io.openmessages.repository.AllowlistRepository
import io.openmessages.util.Preferences
import io.reactivex.Completable
import io.reactivex.Single
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Combines every active blocking source into a single decision.
 *
 * Sources are additive (OR): a message is blocked/flagged if any active source says so. The user's
 * manual blocklist is always active; the integrated list sources (e.g. Saracroche démarchage) are
 * opt-in; and at most one external app (Call Control / Should I Answer? / Call Blocker) can be added
 * on top. Precedence is Block > Flag > Unblock.
 *
 * The allowlist is honoured here: an approved sender is ignored by the automatic sources, but the
 * user's own manual blocklist still wins over it.
 */
@Singleton
class BlockingManager @Inject constructor(
    private val prefs: Preferences,
    private val allowlistRepo: AllowlistRepository,
    private val qksmsBlockingClient: QksmsBlockingClient,
    private val saracrocheBlockingClient: SaracrocheBlockingClient,
    private val callBlockerBlockingClient: CallBlockerBlockingClient,
    private val callControlBlockingClient: CallControlBlockingClient,
    private val shouldIAnswerBlockingClient: ShouldIAnswerBlockingClient
) : BlockingClient {

    /** The external app the user delegates to, if any (single choice, additive on top of internal). */
    private fun externalApp(): BlockingClient? = when (prefs.blockingManager.get()) {
        Preferences.BLOCKING_MANAGER_CB -> callBlockerBlockingClient
        Preferences.BLOCKING_MANAGER_SIA -> shouldIAnswerBlockingClient
        Preferences.BLOCKING_MANAGER_CC -> callControlBlockingClient
        else -> null
    }

    /** Automatic address-based sources (everything except the always-on manual blocklist). */
    private fun autoSources(): List<BlockingClient> {
        val sources = mutableListOf<BlockingClient>()
        if (prefs.blockSourceArcep.get()) sources.add(saracrocheBlockingClient)
        externalApp()?.let { sources.add(it) }
        return sources
    }

    // We can always block via the manual blocklist, so blocking never needs an external permission.
    override fun isAvailable(): Boolean = true

    override fun getClientCapability() = BlockingClient.Capability.BLOCK_WITHOUT_PERMISSION

    override fun shouldBlock(address: String): Single<BlockingClient.Action> =
        combine(address) { client -> client.shouldBlock(address) }

    override fun isBlacklisted(address: String): Single<BlockingClient.Action> =
        combine(address) { client -> client.isBlacklisted(address) }

    private fun combine(
        address: String,
        query: (BlockingClient) -> Single<BlockingClient.Action>
    ): Single<BlockingClient.Action> = Single.fromCallable {
        // The manual blocklist wins over everything, including the allowlist.
        val manual = query(qksmsBlockingClient).blockingGet()
        if (manual is BlockingClient.Action.Block) return@fromCallable manual

        // An approved sender is ignored by the automatic sources.
        if (allowlistRepo.isAllowed(address)) return@fromCallable BlockingClient.Action.Unblock

        val results = autoSources().map { client -> query(client).blockingGet() }
        results.firstOrNull { it is BlockingClient.Action.Block }
            ?: results.firstOrNull { it is BlockingClient.Action.Flag }
            ?: BlockingClient.Action.Unblock
    }

    override fun block(addresses: List<String>): Completable {
        // Blocking always lands in the manual blocklist, and overrides any previous approval.
        val operations = mutableListOf(
            Completable.fromAction { allowlistRepo.removeNumbers(*addresses.toTypedArray()) },
            qksmsBlockingClient.block(addresses)
        )
        // Best-effort sync to an external app that supports pushing numbers (not Should I Answer?).
        externalApp()
            ?.takeIf { it.getClientCapability() != BlockingClient.Capability.CANT_BLOCK }
            ?.let { operations.add(it.block(addresses)) }
        return Completable.merge(operations)
    }

    override fun unblock(addresses: List<String>): Completable {
        val operations = mutableListOf(qksmsBlockingClient.unblock(addresses))
        externalApp()
            ?.takeIf { it.getClientCapability() != BlockingClient.Capability.CANT_BLOCK }
            ?.let { operations.add(it.unblock(addresses)) }
        return Completable.merge(operations)
    }

    override fun openSettings() {
        externalApp()?.openSettings()
    }

}
