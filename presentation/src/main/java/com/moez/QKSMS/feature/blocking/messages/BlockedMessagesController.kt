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

import android.content.Context
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import io.openmessages.R
import io.openmessages.common.base.QkController
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.themeButtons
import io.openmessages.databinding.BlockedMessagesControllerBinding
import io.openmessages.feature.blocking.BlockingDialog
import io.openmessages.injection.appComponent
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class BlockedMessagesController : QkController<BlockedMessagesControllerBinding, BlockedMessagesView, BlockedMessagesState, BlockedMessagesPresenter>(),
    BlockedMessagesView {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): BlockedMessagesControllerBinding =
        BlockedMessagesControllerBinding.inflate(inflater, container, false)

    override val menuReadyIntent: Subject<Unit> = PublishSubject.create()
    override val optionsItemIntent: Subject<Int> = PublishSubject.create()
    override val conversationClicks by lazy { blockedMessagesAdapter.clicks }
    override val selectionChanges by lazy { blockedMessagesAdapter.selectionChanges }
    override val confirmDeleteIntent: Subject<List<Long>> = PublishSubject.create()
    override val backClicked: Subject<Unit> = PublishSubject.create()

    @Inject lateinit var blockedMessagesAdapter: BlockedMessagesAdapter
    @Inject lateinit var blockingDialog: BlockingDialog
    @Inject lateinit var colors: Colors
    @Inject lateinit var context: Context
    @Inject override lateinit var presenter: BlockedMessagesPresenter

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    override fun onViewCreated() {
        super.onViewCreated()
        blockedMessagesAdapter.emptyView = binding.empty
        binding.conversations.adapter = blockedMessagesAdapter
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.blocked_messages_title)
        showBackButton(true)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.blocked_messages, menu)
        menuReadyIntent.onNext(Unit)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        optionsItemIntent.onNext(item.itemId)
        return true
    }

    override fun handleBack(): Boolean {
        backClicked.onNext(Unit)
        return true
    }

    override fun render(state: BlockedMessagesState) {
        blockedMessagesAdapter.updateData(state.data)

        val toolbarMenu = themedActivity?.findViewById<Toolbar>(R.id.toolbar)?.menu
        toolbarMenu?.findItem(R.id.block)?.isVisible = state.selected > 0
        toolbarMenu?.findItem(R.id.delete)?.isVisible = state.selected > 0

        setTitle(when (state.selected) {
            0 -> context.getString(R.string.blocked_messages_title)
            else -> context.getString(R.string.main_title_selected, state.selected)
        })
    }

    override fun clearSelection() = blockedMessagesAdapter.clearSelection()

    override fun showBlockingDialog(conversations: List<Long>, block: Boolean) {
        blockingDialog.show(conversations, block)
    }

    override fun showDeleteDialog(conversations: List<Long>) {
        val count = conversations.size
        AlertDialog.Builder(activity!!)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(resources?.getQuantityString(R.plurals.dialog_delete_message, count, count))
                .setPositiveButton(R.string.button_delete) { _, _ -> confirmDeleteIntent.onNext(conversations) }
                .setNegativeButton(R.string.button_cancel, null)
                .show()
                .themeButtons(colors.theme().theme)
    }

    override fun goBack() {
        router.popCurrentController()
    }

}
