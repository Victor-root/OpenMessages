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
package io.openmessages.feature.conversationinfo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.openmessages.R
import io.openmessages.common.Navigator
import io.openmessages.common.base.QkController
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.scrapViews
import io.openmessages.common.util.extensions.themeButtons
import io.openmessages.common.widget.TextInputDialog
import io.openmessages.databinding.ConversationInfoControllerBinding
import io.openmessages.feature.blocking.BlockingDialog
import io.openmessages.feature.conversationinfo.injection.ConversationInfoModule
import io.openmessages.feature.settings.ThemePickerDialog
import io.openmessages.injection.appComponent
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class ConversationInfoController(
    val threadId: Long = 0
) : QkController<ConversationInfoControllerBinding, ConversationInfoView, ConversationInfoState, ConversationInfoPresenter>(), ConversationInfoView {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): ConversationInfoControllerBinding =
        ConversationInfoControllerBinding.inflate(inflater, container, false)

    @Inject override lateinit var presenter: ConversationInfoPresenter
    @Inject lateinit var blockingDialog: BlockingDialog
    @Inject lateinit var navigator: Navigator
    @Inject lateinit var adapter: ConversationInfoAdapter
    @Inject lateinit var colors: Colors

    private val nameDialog: TextInputDialog by lazy {
        TextInputDialog(activity!!, themeColor, activity!!.getString(R.string.info_name), nameChangeSubject::onNext)
    }

    private val nameChangeSubject: Subject<String> = PublishSubject.create()
    private val confirmDeleteSubject: Subject<Unit> = PublishSubject.create()

    // The conversation's theme colour, kept in sync from the host activity's theme stream
    private var themeColor: Int = 0

    init {
        appComponent
                .conversationInfoBuilder()
                .conversationInfoModule(ConversationInfoModule(this))
                .build()
                .inject(this)
    }

    override fun onViewCreated() {
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(GridSpacingItemDecoration(adapter, activity!!))
        binding.recyclerView.layoutManager = GridLayoutManager(activity, 3).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = if (adapter.getItemViewType(position) == 2) 1 else 3
            }
        }

        themedActivity?.theme
                ?.autoDisposable(scope())
                ?.subscribe { theme ->
                    themeColor = theme.theme
                    binding.recyclerView.scrapViews()
                }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.info_title)
        showBackButton(true)
    }

    override fun render(state: ConversationInfoState) {
        if (state.hasError) {
            activity?.finish()
            return
        }

        adapter.data = state.data
    }

    override fun recipientClicks(): Observable<Long> = adapter.recipientClicks
    override fun recipientLongClicks(): Observable<Long> = adapter.recipientLongClicks
    override fun themeClicks(): Observable<Long> = adapter.themeClicks
    override fun nameClicks(): Observable<*> = adapter.nameClicks
    override fun nameChanges(): Observable<String> = nameChangeSubject
    override fun notificationClicks(): Observable<*> = adapter.notificationClicks
    override fun markUnreadClicks(): Observable<*> = adapter.markUnreadClicks
    override fun archiveClicks(): Observable<*> = adapter.archiveClicks
    override fun blockClicks(): Observable<*> = adapter.blockClicks
    override fun deleteClicks(): Observable<*> = adapter.deleteClicks
    override fun confirmDelete(): Observable<*> = confirmDeleteSubject
    override fun mediaClicks(): Observable<Long> = adapter.mediaClicks

    override fun showNameDialog(name: String) = nameDialog.setText(name).show()

    override fun showThemePicker(recipientId: Long) {
        val fm = themedActivity?.supportFragmentManager ?: return
        // Open on the conversation's current colour (which may be an auto-colour, not a stored
        // override), so the dialog's preview, chrome and buttons match the conversation, not the
        // global app theme.
        ThemePickerDialog.newInstance(recipientId, themeColor.takeIf { it != 0 }).show(fm, "theme_picker")
    }

    override fun showBlockingDialog(conversations: List<Long>, block: Boolean) {
        blockingDialog.show(activity!!, conversations, block)
    }

    override fun requestDefaultSms() {
        navigator.showDefaultSmsDialog(activity!!)
    }

    override fun showDeleteDialog() {
        AlertDialog.Builder(activity!!)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(resources?.getQuantityString(R.plurals.dialog_delete_message, 1))
                .setPositiveButton(R.string.button_delete) { _, _ -> confirmDeleteSubject.onNext(Unit) }
                .setNegativeButton(R.string.button_cancel, null)
                .show()
                .themeButtons(themeColor)
    }
}
