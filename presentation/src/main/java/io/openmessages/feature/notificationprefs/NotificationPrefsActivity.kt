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
package io.openmessages.feature.notificationprefs

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import autodispose2.androidx.lifecycle.scope
import autodispose2.autoDispose
import com.jakewharton.rxbinding4.view.clicks
import dagger.android.AndroidInjection
import io.openmessages.R
import io.openmessages.common.MenuDialog
import io.openmessages.common.ViewModelFactory
import io.openmessages.common.base.ThemedActivity
import io.openmessages.common.util.extensions.animateLayoutChanges
import io.openmessages.common.util.extensions.setVisible
import io.openmessages.common.widget.PreferenceView
import io.openmessages.databinding.NotificationPrefsActivityBinding
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import javax.inject.Inject

class NotificationPrefsActivity : ThemedActivity(), NotificationPrefsView {

    private lateinit var binding: NotificationPrefsActivityBinding

    @Inject lateinit var previewModeDialog: MenuDialog
    @Inject lateinit var actionsDialog: MenuDialog
    @Inject lateinit var viewModelFactory: ViewModelFactory

    override val preferenceClickIntent: Subject<PreferenceView> = PublishSubject.create()
    override val previewModeSelectedIntent by lazy { previewModeDialog.adapter.menuItemClicks }
    override val ringtoneSelectedIntent: Subject<String> = PublishSubject.create()
    override val actionsSelectedIntent by lazy { actionsDialog.adapter.menuItemClicks }

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[NotificationPrefsViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = NotificationPrefsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(R.string.title_notification_prefs)
        showBackButton(true)
        viewModel.bindView(this)

        binding.preferences.postDelayed({ binding.preferences.animateLayoutChanges = true }, 100)

        val hasOreo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        binding.notificationsO.setVisible(hasOreo)
        binding.notifications.setVisible(!hasOreo)
        binding.vibration.setVisible(!hasOreo)
        binding.ringtone.setVisible(!hasOreo)

        previewModeDialog.setTitle(R.string.settings_notification_previews_title)
        previewModeDialog.adapter.setData(R.array.notification_preview_options)
        actionsDialog.adapter.setData(R.array.notification_actions)

        // Listen to clicks for all of the preferences
        (0 until binding.preferences.childCount)
                .map { index -> binding.preferences.getChildAt(index) }
                .mapNotNull { view -> view as? PreferenceView }
                .map { preference -> preference.clicks().map { preference } }
                .let { Observable.merge(it) }
                .autoDispose(scope())
                .subscribe(preferenceClickIntent)
    }

    override fun render(state: NotificationPrefsState) {
        if (state.threadId != 0L) {
            title = state.conversationTitle
        }

        binding.notifications.checkbox?.isChecked = state.notificationsEnabled
        binding.previews.summary = state.previewSummary
        previewModeDialog.adapter.selectedItem = state.previewId
        binding.wake.checkbox?.isChecked = state.wakeEnabled
        binding.silentNotContact.checkbox?.isChecked = state.silentNotContact
        binding.silentNotContact.isVisible = state.threadId == 0L
        binding.copyCode.checkbox?.isChecked = state.copyCodeEnabled
        binding.copyCode.isVisible = state.threadId == 0L
        binding.vibration.checkbox?.isChecked = state.vibrationEnabled
        binding.ringtone.summary = state.ringtoneName

        binding.actionsDivider.isVisible = state.threadId == 0L
        binding.actionsTitle.isVisible = state.threadId == 0L
        binding.action1.isVisible = state.threadId == 0L
        binding.action1.summary = state.action1Summary
        binding.action2.isVisible = state.threadId == 0L
        binding.action2.summary = state.action2Summary
        binding.action3.isVisible = state.threadId == 0L
        binding.action3.summary = state.action3Summary

        binding.quickReplyDivider.isVisible = state.threadId == 0L
        binding.quickReplyTitle.isVisible = state.threadId == 0L
        binding.quickReply.checkbox?.isChecked = state.quickReplyEnabled
        binding.quickReply.isVisible = state.threadId == 0L
        binding.quickReplyTapDismiss.isVisible = state.threadId == 0L
        binding.quickReplyTapDismiss.isEnabled = state.quickReplyEnabled
        binding.quickReplyTapDismiss.checkbox?.isChecked = state.quickReplyTapDismiss
    }

    override fun showPreviewModeDialog() = previewModeDialog.show(this)

    private val ringtonePicker = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let { data ->
                IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            ringtoneSelectedIntent.onNext(uri?.toString() ?: "")
        }
    }

    override fun showRingtonePicker(default: Uri?) {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, default)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        ringtonePicker.launch(intent)
    }

    override fun showActionDialog(selected: Int) {
        actionsDialog.adapter.selectedItem = selected
        actionsDialog.show(this)
    }

}