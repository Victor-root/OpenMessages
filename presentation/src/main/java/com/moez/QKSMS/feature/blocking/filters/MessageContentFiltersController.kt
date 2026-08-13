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
package io.openmessages.feature.blocking.filters

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.jakewharton.rxbinding2.view.clicks
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.openmessages.R
import io.openmessages.common.base.QkController
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.common.util.extensions.setTint
import io.openmessages.common.util.extensions.themeButtons
import io.openmessages.common.widget.PreferenceView
import io.openmessages.injection.appComponent
import io.openmessages.model.MessageContentFilterData
import io.openmessages.databinding.MessageContentFiltersControllerBinding
import io.openmessages.databinding.MessageContentFiltersAddDialogBinding
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class MessageContentFiltersController : QkController<MessageContentFiltersControllerBinding, MessageContentFiltersView, MessageContentFiltersState,
        MessageContentFiltersPresenter>(), MessageContentFiltersView {

    @Inject override lateinit var presenter: MessageContentFiltersPresenter
    @Inject lateinit var colors: Colors

    private val adapter = MessageContentFiltersAdapter()
    private val saveFilterSubject: Subject<MessageContentFilterData> = PublishSubject.create()

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater, container: android.view.ViewGroup): MessageContentFiltersControllerBinding =
        MessageContentFiltersControllerBinding.inflate(inflater, container, false)

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.message_content_filters_title)
        showBackButton(true)
    }

    override fun onViewCreated() {
        super.onViewCreated()
        binding.add.setBackgroundTint(colors.theme().theme)
        binding.add.setTint(colors.theme().textPrimary)
        adapter.emptyView = binding.empty
        binding.filters.adapter = adapter
    }

    override fun render(state: MessageContentFiltersState) {
        adapter.updateData(state.filters)
    }

    override fun removeFilter(): Observable<Long> = adapter.removeMessageContentFilter
    override fun addFilter(): Observable<*> = binding.add.clicks()
    override fun saveFilter(): Observable<MessageContentFilterData> = saveFilterSubject

    override fun showAddDialog() {
        val layout = MessageContentFiltersAddDialogBinding.inflate(LayoutInflater.from(activity))

        (0 until layout.addDialog.childCount)
            .map { index -> layout.addDialog.getChildAt(index) }
            .mapNotNull { view -> view as? PreferenceView }
            .map { preference -> preference.clicks().map { preference } }
            .let { Observable.merge(it) }
            .autoDisposable(scope())
            .subscribe { pref ->
                pref.checkbox?.let { it.isChecked = !it.isChecked }
                val regexChecked = layout.regexp.checkbox?.isChecked ?: false
                layout.caseSensitivity.isEnabled = !regexChecked
            }

        val dialog = AlertDialog.Builder(activity!!)
                .setView(layout.root)
                .setPositiveButton(R.string.message_content_filters_dialog_create) { _, _ ->
                    var text = layout.input.text.toString();
                    if (!text.isBlank()) {
                        val regexChecked = layout.regexp.checkbox?.isChecked ?: false
                        if (!regexChecked) text = text.trim()
                        saveFilterSubject.onNext(
                            MessageContentFilterData(
                                text,
                                (layout.caseSensitivity.checkbox?.isChecked == true) && !regexChecked,
                                regexChecked,
                                layout.contacts.checkbox?.isChecked == true
                            )
                        )
                    }
                }
                .setNegativeButton(R.string.button_cancel) { _, _ -> }
        dialog.show().themeButtons(colors.theme().theme)
    }

}
