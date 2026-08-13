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
package io.openmessages.common.base

import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.drawToBitmap
import androidx.core.view.iterator
import androidx.lifecycle.Lifecycle
import com.f2prateek.rx.preferences2.Preference
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.openmessages.R
import io.openmessages.common.util.Colors
import io.openmessages.extensions.Optional
import io.openmessages.extensions.asObservable
import io.openmessages.extensions.mapNotNull
import io.openmessages.repository.ConversationRepository
import io.openmessages.repository.MessageRepository
import io.openmessages.util.PhoneNumberUtils
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.Observables
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Base activity that automatically applies any necessary theme theme settings and colors
 *
 * In most cases, this should be used instead of the base QkActivity, except for when
 * an activity does not depend on the theme
 */
abstract class QkThemedActivity : QkActivity() {

    @Inject lateinit var conversationRepo: ConversationRepository
    @Inject lateinit var messageRepo: MessageRepository
    @Inject lateinit var phoneNumberUtils: PhoneNumberUtils

    /**
     * In case the activity should be themed for a specific conversation, the selected conversation
     * can be changed by pushing the threadId to this subject
     */
    val threadId: Subject<Long> = BehaviorSubject.createDefault(0)

    /**
     * Switch the theme if the threadId changes
     * Set it based on the latest message in the conversation
     */
    val theme: Observable<Colors.Theme> = threadId
            .distinctUntilChanged()
            .switchMap { threadId ->
                val conversation = conversationRepo.getConversation(threadId)
                when {
                    conversation == null -> Observable.just(Optional(null))

                    conversation.recipients.size == 1 -> Observable.just(Optional(conversation.recipients.first()))

                    else -> messageRepo.getLastIncomingMessage(conversation.id)
                            .asObservable()
                            .mapNotNull { messages -> messages.firstOrNull() }
                            .distinctUntilChanged { message -> message.address }
                            .mapNotNull { message ->
                                conversation.recipients.find { recipient ->
                                    phoneNumberUtils.compare(recipient.address, message.address)
                                }
                            }
                            .map { recipient -> Optional(recipient) }
                            .startWith(Optional(conversation.recipients.firstOrNull()))
                            .distinctUntilChanged()
                }
            }
            .switchMap { colors.themeObservable(it.value) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getActivityThemeRes(prefs.black.get()))
        super.onCreate(savedInstanceState)

        // When certain preferences change, we need to recreate the activity. Screens that want to
        // rebuild on a theme-color change (e.g. settings, whose QkSwitch thumbs and themed category
        // titles read the color once at inflation) add prefs.theme() via recreateOnThemeChangeTriggers().
        // It is deliberately NOT a global trigger: the launcher entry is an activity-alias, so a
        // back-stacked, alias-launched activity must not be relaunched while the icon may be changing.
        // The 400ms debounce guarantees the theme picker dialog has dismissed before the rebuild.
        val triggers: List<Preference<*>> =
                listOf<Preference<*>>(prefs.nightMode, prefs.night, prefs.black, prefs.textSize, prefs.systemFont, prefs.edgeToEdge) +
                        recreateOnThemeChangeTriggers()
        Observable.merge(triggers.map { it.asObservable().skip(1) })
                .debounce(400, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDisposable(scope())
                .subscribe { recreateWithCrossfade() }

    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        Observables.combineLatest(menu, theme) { menu, theme ->
            menu.iterator().forEach { menuItem ->
                val tint = when (menuItem.itemId) {
                    in getColoredMenuItems() -> theme.theme
                    else -> colors.contentColorOnTheme(theme.theme)
                }

                menuItem.icon = menuItem.icon?.apply { setTint(tint) }
            }
        }.autoDisposable(scope(Lifecycle.Event.ON_DESTROY)).subscribe()

        theme
            .autoDisposable(scope(Lifecycle.Event.ON_DESTROY))
            .subscribe { theme ->
                // Transparent bars behind content (edge-to-edge) or opaque themed bars, plus the
                // correct light/dark icon contrast for each. See QkActivity.applySystemBars.
                applySystemBars(theme.theme)

                // The toolbar carries the same colour as the status bar above it, so its icons and
                // title follow the same light/dark rule. Runs before the background is set below so
                // toolbarContentColor is ready for anything a subclass tints from its own theme
                // subscription.
                applyToolbarContentColor(theme.theme)

                // Default violet keeps the brand gradient header; custom colors paint it flat.
                toolbar?.background = if (colors.usesBrandGradient(theme.theme)) {
                    GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(theme.theme, colors.deriveGradientEndColor(theme.theme)))
                } else {
                    ColorDrawable(theme.theme)
                }

                // Keep the recent-apps card header color in sync with the live theme color.
                // The card would otherwise show whatever color was active when the task was created,
                // only refreshing after a full app restart. Passing null for the icon bitmap lets
                // the system keep using the default launcher icon (no need to generate one).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setTaskDescription(ActivityManager.TaskDescription.Builder()
                        .setLabel(getString(R.string.app_name))
                        .setBackgroundColor(theme.theme)
                        .build())
                } else {
                    @Suppress("DEPRECATION")
                    setTaskDescription(ActivityManager.TaskDescription(
                        getString(R.string.app_name), null, theme.theme))
                }
            }

        // Crossfade from the pre-recreate snapshot (if this was a theme-change recreate) so the swap
        // doesn't flash. Done after the theme subscription so the new content is themed underneath
        // the fading snapshot.
        playThemeCrossfade()
    }

    /**
     * Recreate to apply a theme change, but first snapshot the current screen so the recreated
     * activity can crossfade from it ([playThemeCrossfade]) instead of flashing the bare window
     * background. Only the visible activity snapshots; a backgrounded one (e.g. the list behind
     * Settings) recreates invisibly. The snapshot is keyed by activity class so a background activity
     * that also recreates can't consume the visible activity's snapshot.
     */
    private fun recreateWithCrossfade() {
        if (window.decorView.isShown) {
            themeSnapshot?.takeIf { !it.isRecycled }?.recycle()
            themeSnapshot = runCatching { findViewById<View>(android.R.id.content)?.drawToBitmap() }.getOrNull()
            themeSnapshotOwner = if (themeSnapshot != null) javaClass.name else null
        }
        recreate()
    }

    private fun playThemeCrossfade() {
        if (themeSnapshotOwner != javaClass.name) return
        val snapshot = themeSnapshot ?: return
        themeSnapshot = null
        themeSnapshotOwner = null

        val content = findViewById<ViewGroup>(android.R.id.content)
        if (content == null) {
            snapshot.recycle()
            return
        }

        val overlay = ImageView(this).apply {
            setImageBitmap(snapshot)
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        content.addView(overlay)

        // Fade the old screen out over the freshly themed one. The post-delayed cleanup runs whether
        // or not the animation finishes, so a stale snapshot can never be left covering the UI.
        overlay.animate().alpha(0f).setStartDelay(60).setDuration(220).start()
        overlay.postDelayed({
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            if (!snapshot.isRecycled) snapshot.recycle()
        }, 320)
    }

    open fun getColoredMenuItems(): List<Int> {
        return listOf()
    }

    /**
     * Preferences that, when changed, should recreate this specific activity. Used for the theme
     * color, which only the settings screen needs to rebuild for. Kept off the shared trigger list
     * so we never relaunch a back-stacked, activity-alias-launched screen on a theme change.
     */
    open fun recreateOnThemeChangeTriggers(): List<Preference<*>> = emptyList()

    /**
     * This can be overridden in case an activity does not want to use the default themes
     */
    open fun getActivityThemeRes(black: Boolean) = when {
        black -> R.style.AppTheme_Black
        else -> R.style.AppTheme
    }

    companion object {
        // Screen snapshot handed from an activity to its recreated self across a theme-change
        // recreate(), so the new instance can crossfade from it instead of flashing. Static because
        // it must survive the recreate; [themeSnapshotOwner] (the owning activity's class name) keeps
        // a background activity that recreates at the same time from consuming it.
        private var themeSnapshot: Bitmap? = null
        private var themeSnapshotOwner: String? = null
    }

}