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
package io.openmessages.feature.themepicker

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.view.clicks
import io.openmessages.R
import io.openmessages.common.base.QkController
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.dpToPx
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.common.util.extensions.setVisible
import io.openmessages.databinding.ThemePickerControllerBinding
import io.openmessages.feature.themepicker.injection.ThemePickerModule
import io.openmessages.injection.appComponent
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class ThemePickerController(
    val recipientId: Long = 0L
) : QkController<ThemePickerControllerBinding, ThemePickerView, ThemePickerState, ThemePickerPresenter>(), ThemePickerView {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): ThemePickerControllerBinding =
        ThemePickerControllerBinding.inflate(inflater, container, false)

    @Inject override lateinit var presenter: ThemePickerPresenter

    @Inject lateinit var colors: Colors
    @Inject lateinit var themeAdapter: ThemeAdapter
    @Inject lateinit var themePagerAdapter: ThemePagerAdapter

    private val viewQksmsPlusSubject: Subject<Unit> = PublishSubject.create()

    init {
        appComponent
                .themePickerBuilder()
                .themePickerModule(ThemePickerModule(this))
                .build()
                .inject(this)
    }

    override fun onViewCreated() {
        binding.pager.offscreenPageLimit = 1
        binding.pager.adapter = themePagerAdapter
        binding.tabs.pager = binding.pager

        themeAdapter.data = colors.materialColors

        binding.materialColors.layoutManager = LinearLayoutManager(activity)
        binding.materialColors.adapter = themeAdapter
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.title_theme)
        showBackButton(true)

        themedActivity?.supportActionBar?.let { toolbar ->
            ObjectAnimator.ofFloat(toolbar, "elevation", toolbar.elevation, 0f).start()
        }
    }

    override fun onDetach(view: View) {
        super.onDetach(view)

        themedActivity?.supportActionBar?.let { toolbar ->
            ObjectAnimator.ofFloat(toolbar, "elevation", toolbar.elevation, 8.dpToPx(toolbar.themedContext).toFloat()).start()
        }
    }

    override fun showQksmsPlusSnackbar() {
        Snackbar.make(binding.contentView, R.string.toast_qksms_plus, Snackbar.LENGTH_LONG).run {
            setAction(R.string.button_more) { viewQksmsPlusSubject.onNext(Unit) }
            setActionTextColor(colors.theme().theme)
            show()
        }
    }

    override fun themeSelected(): Observable<Int> = themeAdapter.colorSelected

    override fun hsvThemeSelected(): Observable<Int> = binding.hsvPicker.picker.selectedColor

    override fun clearHsvThemeClicks(): Observable<*> = binding.hsvPicker.clear.clicks()

    override fun applyHsvThemeClicks(): Observable<*> = binding.hsvPicker.apply.clicks()

    override fun viewQksmsPlusClicks(): Observable<*> = viewQksmsPlusSubject

    override fun render(state: ThemePickerState) {
        binding.tabs.setRecipientId(state.recipientId)

        binding.hsvPicker.hex.setText(Integer.toHexString(state.newColor).takeLast(6))

        binding.hsvPicker.applyGroup.setVisible(state.applyThemeVisible)
        binding.hsvPicker.apply.setBackgroundTint(state.newColor)
        binding.hsvPicker.apply.setTextColor(state.newTextColor)
    }

    override fun setCurrentTheme(color: Int) {
        binding.hsvPicker.picker.setColor(color)
        themeAdapter.selectedColor = color
    }

}
