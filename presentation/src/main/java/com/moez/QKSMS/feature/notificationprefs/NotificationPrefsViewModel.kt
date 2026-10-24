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

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import autodispose2.androidx.lifecycle.scope
import autodispose2.autoDispose
import io.openmessages.R
import io.openmessages.common.Navigator
import io.openmessages.common.base.QkViewModel
import io.openmessages.common.util.extensions.labelFor
import io.openmessages.extensions.mapNotNull
import io.openmessages.manager.NotificationManager
import io.openmessages.repository.ConversationRepository
import io.openmessages.util.DisplayNames
import io.openmessages.util.Preferences
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Named

class NotificationPrefsViewModel @Inject constructor(
    private val displayNames: DisplayNames,
    @Named("threadId") private val threadId: Long,
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val navigator: Navigator,
    private val notificationManager: NotificationManager,
    private val prefs: Preferences
) : QkViewModel<NotificationPrefsView, NotificationPrefsState>(NotificationPrefsState(threadId = threadId)) {

    private val notifications = prefs.notifications(threadId)
    private val previews = prefs.notificationPreviews(threadId)
    private val wake = prefs.wakeScreen(threadId)
    private val vibration = prefs.vibration(threadId)
    private val ringtone = prefs.ringtone(threadId)

    init {
        disposables += Flowable.just(threadId)
                .mapNotNull { threadId -> conversationRepo.getConversation(threadId) }
                .map(displayNames::of)
                .subscribeOn(Schedulers.io())
                .subscribe { title -> newState { copy(conversationTitle = title) } }

        disposables += notifications.asObservable()
                .subscribe { enabled -> newState { copy(notificationsEnabled = enabled) } }

        val previewLabels = context.resources.getStringArray(R.array.notification_preview_options)
        disposables += previews.asObservable()
                .subscribe { previewId ->
                    val summary = previewLabels.labelFor(previewId, previews.defaultValue)
                    newState { copy(previewSummary = summary, previewId = previewId) }
                }

        val actionLabels = context.resources.getStringArray(R.array.notification_actions)
        disposables += prefs.notifAction1.asObservable()
                .subscribe { action ->
                    val summary = actionLabels.labelFor(action, prefs.notifAction1.defaultValue)
                    newState { copy(action1Summary = summary) }
                }

        disposables += prefs.notifAction2.asObservable()
                .subscribe { action ->
                    val summary = actionLabels.labelFor(action, prefs.notifAction2.defaultValue)
                    newState { copy(action2Summary = summary) }
                }

        disposables += prefs.notifAction3.asObservable()
                .subscribe { action ->
                    val summary = actionLabels.labelFor(action, prefs.notifAction3.defaultValue)
                    newState { copy(action3Summary = summary) }
                }

        disposables += wake.asObservable()
                .subscribe { enabled -> newState { copy(wakeEnabled = enabled) } }

        disposables += prefs.silentNotContact.asObservable()
                .subscribe { enabled -> newState { copy(silentNotContact = enabled) } }

        disposables += prefs.copyCodeFromNotification.asObservable()
                .subscribe { enabled -> newState { copy(copyCodeEnabled = enabled) } }

        disposables += vibration.asObservable()
                .subscribe { enabled -> newState { copy(vibrationEnabled = enabled) } }

        disposables += ringtone.asObservable()
                .map { uriString ->
                    uriString.takeIf { it.isNotEmpty() }
                            ?.let(Uri::parse)
                            ?.let { uri -> RingtoneManager.getRingtone(context, uri) }?.getTitle(context)
                            ?: context.getString(R.string.settings_ringtone_none)
                }
                .subscribe { title -> newState { copy(ringtoneName = title) } }

        disposables += prefs.qkreply.asObservable()
                .subscribe { enabled -> newState { copy(qkReplyEnabled = enabled) } }

        disposables += prefs.qkreplyTapDismiss.asObservable()
                .subscribe { enabled -> newState { copy(qkReplyTapDismiss = enabled) } }
    }

    override fun bindView(view: NotificationPrefsView) {
        super.bindView(view)

        // The channel of a conversation is made on demand, from the conversation's title
        view.preferenceClickIntent
                .filter { preferenceView -> preferenceView.id == R.id.notificationsO }
                .observeOn(Schedulers.io())
                .doOnNext { if (threadId != 0L) notificationManager.createNotificationChannel(threadId) }
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(view.scope())
                .subscribe { navigator.showNotificationChannel(threadId) }

        view.preferenceClickIntent
                .autoDispose(view.scope())
                .subscribe { preferenceView ->
                    when (preferenceView.id) {
                        R.id.notifications -> notifications.set(!notifications.get())

                        R.id.previews -> view.showPreviewModeDialog()

                        R.id.wake -> wake.set(!wake.get())

                        R.id.silentNotContact -> prefs.silentNotContact.set(!prefs.silentNotContact.get())

                        R.id.copyCode -> prefs.copyCodeFromNotification.set(!prefs.copyCodeFromNotification.get())

                        R.id.vibration -> vibration.set(!vibration.get())

                        R.id.ringtone -> view.showRingtonePicker(
                            ringtone.get().takeIf { it.isNotEmpty() }?.let(Uri::parse)
                        )

                        R.id.action1 -> view.showActionDialog(prefs.notifAction1.get())

                        R.id.action2 -> view.showActionDialog(prefs.notifAction2.get())

                        R.id.action3 -> view.showActionDialog(prefs.notifAction3.get())

                        R.id.qkreply -> prefs.qkreply.set(!prefs.qkreply.get())

                        R.id.qkreplyTapDismiss -> prefs.qkreplyTapDismiss.set(!prefs.qkreplyTapDismiss.get())
                    }
                }

        view.previewModeSelectedIntent
                .autoDispose(view.scope())
                .subscribe { previews.set(it) }

        view.ringtoneSelectedIntent
                .autoDispose(view.scope())
                .subscribe { ringtone -> this.ringtone.set(ringtone) }

        view.actionsSelectedIntent
                .withLatestFrom(view.preferenceClickIntent) { action, preference ->
                    when (preference.id) {
                        R.id.action1 -> prefs.notifAction1.set(action)
                        R.id.action2 -> prefs.notifAction2.set(action)
                        R.id.action3 -> prefs.notifAction3.set(action)
                    }
                }
                .autoDispose(view.scope())
                .subscribe()
    }
}