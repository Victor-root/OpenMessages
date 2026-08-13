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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.RouterTransaction
import com.jakewharton.rxbinding2.view.clicks
import io.openmessages.R
import io.openmessages.common.QkChangeHandler
import io.openmessages.common.base.QkController
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.animateLayoutChanges
import io.openmessages.feature.blocking.manager.BlockingManagerController
import io.openmessages.feature.blocking.messages.BlockedMessagesController
import io.openmessages.feature.blocking.numbers.BlockedNumbersController
import io.openmessages.feature.blocking.filters.MessageContentFiltersController
import io.openmessages.injection.appComponent
import io.openmessages.databinding.BlockingControllerBinding
import javax.inject.Inject

class BlockingController : QkController<BlockingControllerBinding, BlockingView, BlockingState, BlockingPresenter>(), BlockingView {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): BlockingControllerBinding =
        BlockingControllerBinding.inflate(inflater, container, false)

    override val blockingManagerIntent get() = binding.blockingManager.clicks()
    override val blockedNumbersIntent get() = binding.blockedNumbers.clicks()
    override val phishingSourceIntent get() = binding.phishingSource.clicks()
    override val flagBlockSourceIntent get() = binding.flagBlockSource.clicks()
    override val messageContentFiltersIntent get() = binding.messageContentFilters.clicks()
    override val blockedMessagesIntent get() = binding.blockedMessages.clicks()
    override val dropClickedIntent get() = binding.drop.clicks()

    @Inject lateinit var colors: Colors
    @Inject override lateinit var presenter: BlockingPresenter

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    override fun onViewCreated() {
        super.onViewCreated()
        binding.parent.postDelayed({ binding.parent.animateLayoutChanges = true }, 100)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.blocking_title)
        showBackButton(true)
    }

    override fun render(state: BlockingState) {
        binding.blockingManager.summary = state.blockingManager
        binding.phishingSource.checkbox?.isChecked = state.phishingEnabled
        if (state.phishingSummary.isNotEmpty()) binding.phishingSource.summary = state.phishingSummary
        binding.flagBlockSource.checkbox?.isChecked = state.flagBlockEnabled
        binding.drop.checkbox?.isChecked = state.dropEnabled
        binding.blockedMessages.isEnabled = !state.dropEnabled
    }

    override fun openBlockedNumbers() {
        router.pushController(RouterTransaction.with(BlockedNumbersController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

    override fun openMessageContentFilters() {
        router.pushController(RouterTransaction.with(MessageContentFiltersController())
            .pushChangeHandler(QkChangeHandler())
            .popChangeHandler(QkChangeHandler()))
    }

    override fun openBlockedMessages() {
        router.pushController(RouterTransaction.with(BlockedMessagesController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

    override fun openBlockingManager() {
        router.pushController(RouterTransaction.with(BlockingManagerController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

}
