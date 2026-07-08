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
package io.openmessages.feature.settings

import android.animation.ObjectAnimator
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.RouterTransaction
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.view.longClicks
import io.openmessages.BuildConfig
import io.openmessages.R
import io.openmessages.common.MenuItem
import io.openmessages.common.QkChangeHandler
import io.openmessages.common.QkDialog
import io.openmessages.common.base.QkController
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.animateLayoutChanges
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.common.util.extensions.setVisible
import io.openmessages.common.widget.PreferenceView
import io.openmessages.common.widget.TextInputDialog
import io.openmessages.databinding.SettingsControllerBinding
import io.openmessages.feature.settings.about.AboutController
import io.openmessages.feature.settings.swipe.SwipeActionsController
import io.openmessages.injection.appComponent
import io.openmessages.repository.SyncRepository
import io.openmessages.util.Preferences
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class SettingsController : QkController<SettingsControllerBinding, SettingsView, SettingsState, SettingsPresenter>(), SettingsView {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): SettingsControllerBinding =
        SettingsControllerBinding.inflate(inflater, container, false)

    @Inject lateinit var context: Context
    @Inject lateinit var colors: Colors
    @Inject lateinit var nightModeDialog: QkDialog
    @Inject lateinit var textSizeDialog: QkDialog
    @Inject lateinit var sendDelayDialog: QkDialog
    @Inject lateinit var mmsSizeDialog: QkDialog
    @Inject lateinit var messageLinkHandlingDialog: QkDialog

    @Inject override lateinit var presenter: SettingsPresenter

    private val signatureDialog: TextInputDialog by lazy {
        TextInputDialog(activity!!, colors.theme().theme, context.getString(R.string.settings_signature_title), signatureSubject::onNext)
    }

    private val viewQksmsPlusSubject: Subject<Unit> = PublishSubject.create()
    private val startTimeSelectedSubject: Subject<Pair<Int, Int>> = PublishSubject.create()
    private val endTimeSelectedSubject: Subject<Pair<Int, Int>> = PublishSubject.create()
    private val signatureSubject: Subject<String> = PublishSubject.create()

    private val progressAnimator by lazy { ObjectAnimator.ofInt(binding.syncingProgress, "progress", 0, 0) }

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    override fun onViewCreated() {
        binding.preferences.postDelayed({ binding.preferences.animateLayoutChanges = true }, 100)

        when (Build.VERSION.SDK_INT >= 29) {
            true -> nightModeDialog.adapter.setData(R.array.night_modes)
            false -> nightModeDialog.adapter.data = context.resources.getStringArray(R.array.night_modes)
                    .mapIndexed { index, title -> MenuItem(title, index) }
                    .drop(1)
        }
        textSizeDialog.adapter.setData(R.array.text_sizes)
        sendDelayDialog.adapter.setData(R.array.delayed_sending_labels)
        mmsSizeDialog.adapter.setData(R.array.mms_sizes, R.array.mms_sizes_ids)
        messageLinkHandlingDialog.adapter.setData(R.array.messageLinkHandlings, R.array.messageLinkHandling_ids)

        binding.about.summary = context.getString(R.string.settings_version, BuildConfig.VERSION_NAME)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        // Treat the first render after each (re)attach as a silent population, so the switches don't
        // replay their slide — notably after the activity is recreated for a theme change such as
        // toggling pure-black mode. bindIntents() emits state synchronously, so reset before it.
        switchesPopulated = false
        presenter.bindIntents(this)
        setTitle(R.string.title_settings)
        showBackButton(true)
    }

    override fun preferenceClicks(): Observable<PreferenceView> = (0 until binding.preferences.childCount)
            .map { index -> binding.preferences.getChildAt(index) }
            .mapNotNull { view -> view as? PreferenceView }
            .map { preference -> preference.clicks().map { preference } }
            .let { preferences -> Observable.merge(preferences) }

    override fun aboutLongClicks(): Observable<*> = binding.about.longClicks()

    override fun viewQksmsPlusClicks(): Observable<*> = viewQksmsPlusSubject

    override fun nightModeSelected(): Observable<Int> = nightModeDialog.adapter.menuItemClicks

    override fun nightStartSelected(): Observable<Pair<Int, Int>> = startTimeSelectedSubject

    override fun nightEndSelected(): Observable<Pair<Int, Int>> = endTimeSelectedSubject

    override fun textSizeSelected(): Observable<Int> = textSizeDialog.adapter.menuItemClicks

    override fun sendDelaySelected(): Observable<Int> = sendDelayDialog.adapter.menuItemClicks

    override fun signatureChanged(): Observable<String> = signatureSubject

    override fun mmsSizeSelected(): Observable<Int> = mmsSizeDialog.adapter.menuItemClicks

    override fun messageLinkHandlingSelected(): Observable<Int> = messageLinkHandlingDialog.adapter.menuItemClicks

    // Becomes true after the switches have been populated once, so only later renders animate.
    private var switchesPopulated = false

    override fun render(state: SettingsState) {
        // The first render (including the one when the activity is recreated for a theme change,
        // e.g. toggling pure-black mode) populates the switches without animating, so they appear
        // already in position instead of replaying their on/off slide. Later renders animate, so a
        // genuine user toggle still slides.
        val animate = switchesPopulated

        binding.theme.findViewById<View>(R.id.themePreview)?.setBackgroundTint(state.theme)
        binding.night.summary = state.nightModeSummary
        nightModeDialog.adapter.selectedItem = state.nightModeId
        binding.nightStart.setVisible(state.nightModeId == Preferences.NIGHT_MODE_AUTO)
        binding.nightStart.summary = state.nightStart
        binding.nightEnd.setVisible(state.nightModeId == Preferences.NIGHT_MODE_AUTO)
        binding.nightEnd.summary = state.nightEnd

        binding.black.setVisible(state.nightModeId != Preferences.NIGHT_MODE_OFF)
        binding.black.checkbox?.setChecked(state.black, animate)

        binding.autoEmoji.checkbox?.setChecked(state.autoEmojiEnabled, animate)

        binding.delayed.summary = state.sendDelaySummary
        sendDelayDialog.adapter.selectedItem = state.sendDelayId

        binding.delivery.checkbox?.setChecked(state.deliveryEnabled, animate)

        binding.unreadAtTop.checkbox?.setChecked(state.unreadAtTopEnabled, animate)

        binding.signature.summary = state.signature.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.settings_signature_summary)

        binding.textSize.summary = state.textSizeSummary
        textSizeDialog.adapter.selectedItem = state.textSizeId

        binding.autoColor.checkbox?.setChecked(state.autoColor, animate)

        binding.systemFont.checkbox?.setChecked(state.systemFontEnabled, animate)

        binding.showAvatar.checkbox?.setChecked(state.showAvatarEnabled, animate)

        binding.edgeToEdge.checkbox?.setChecked(state.edgeToEdgeEnabled, animate)

        binding.showStt.checkbox?.setChecked(state.showStt, animate)

        binding.unicode.checkbox?.setChecked(state.stripUnicodeEnabled, animate)
        binding.mobileOnly.checkbox?.setChecked(state.mobileOnly, animate)

        binding.longAsMms.checkbox?.setChecked(state.longAsMms, animate)

        binding.mmsSize.summary = state.maxMmsSizeSummary
        mmsSizeDialog.adapter.selectedItem = state.maxMmsSizeId

        binding.messsageLinkHandling.summary = state.messageLinkHandlingSummary
        messageLinkHandlingDialog.adapter.selectedItem = state.messageLinkHandlingId

        binding.disableScreenshots.checkbox?.setChecked(state.disableScreenshotsEnabled, animate)

        switchesPopulated = true

        when (state.syncProgress) {
            is SyncRepository.SyncProgress.Idle -> binding.syncingProgress.isVisible = false

            is SyncRepository.SyncProgress.Running -> {
                binding.syncingProgress.isVisible = true
                binding.syncingProgress.max = state.syncProgress.max
                progressAnimator.apply { setIntValues(binding.syncingProgress.progress, state.syncProgress.progress) }.start()
                binding.syncingProgress.isIndeterminate = state.syncProgress.indeterminate
            }

            is SyncRepository.SyncProgress.ParsingEmojis -> {
                binding.syncingProgress.isVisible = true
                binding.syncingProgress.max = state.syncProgress.max
                progressAnimator.apply { setIntValues(binding.syncingProgress.progress, state.syncProgress.progress) }.start()
                binding.syncingProgress.isIndeterminate = state.syncProgress.indeterminate
            }
        }
    }

    override fun showQksmsPlusSnackbar() {
        view?.run {
            Snackbar.make(binding.contentView, R.string.toast_qksms_plus, Snackbar.LENGTH_LONG).run {
                setAction(R.string.button_more) { viewQksmsPlusSubject.onNext(Unit) }
                setActionTextColor(colors.theme().theme)
                show()
            }
        }
    }

    // TODO change this to a PopupWindow
    override fun showNightModeDialog() = nightModeDialog.show(activity!!)

    override fun showStartTimePicker(hour: Int, minute: Int) {
        TimePickerDialog(activity, { _, newHour, newMinute ->
            startTimeSelectedSubject.onNext(Pair(newHour, newMinute))
        }, hour, minute, DateFormat.is24HourFormat(activity)).show()
    }

    override fun showEndTimePicker(hour: Int, minute: Int) {
        TimePickerDialog(activity, { _, newHour, newMinute ->
            endTimeSelectedSubject.onNext(Pair(newHour, newMinute))
        }, hour, minute, DateFormat.is24HourFormat(activity)).show()
    }

    override fun showTextSizePicker() = textSizeDialog.show(activity!!)

    override fun showDelayDurationDialog() = sendDelayDialog.show(activity!!)

    override fun showSignatureDialog(signature: String) = signatureDialog.setText(signature).show()

    override fun showMmsSizePicker() = mmsSizeDialog.show(activity!!)

    override fun showMessageLinkHandlingDialogPicker() = messageLinkHandlingDialog.show(activity!!)

    override fun showSwipeActions() {
        router.pushController(RouterTransaction.with(SwipeActionsController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

    override fun showThemePicker() {
        val fm = themedActivity?.supportFragmentManager ?: return
        ThemePickerDialog.newInstance().show(fm, "theme_picker")
    }

    override fun showAbout() {
        router.pushController(RouterTransaction.with(AboutController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

}
