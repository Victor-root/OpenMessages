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
package io.openmessages.common.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import io.openmessages.R
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.withAlpha
import io.openmessages.injection.appComponent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import javax.inject.Inject

/**
 * The Material 3 [MaterialSwitch], wired up to the app's dynamic theme color.
 *
 * The native component gives us the exact MD3 look for free — the thick rounded track, the thumb
 * that grows from 16dp to 24dp when checked, and the outline on the unchecked track. The app runs on
 * a Theme.AppCompat base (not a Material 3 theme), so the view's context is wrapped in a Material 3
 * theme overlay so the component can construct with the M3 color tokens it expects.
 *
 * Only the colors are then applied explicitly: the checked track follows the user's chosen theme
 * color (white thumb), and the unchecked track/thumb/outline use the Material 3
 * surfaceContainerHighest and outline tones. Colors are applied reactively, so a theme-color change
 * is reflected live without rebuilding the activity.
 */
class QkSwitch @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MaterialSwitch(wrap(context), attrs) {

    @Inject lateinit var colors: Colors

    private val states = arrayOf(
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_checked),
        intArrayOf()
    )

    private var themeDisposable: Disposable? = null

    init {
        if (!isInEditMode) {
            appComponent.inject(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            themeDisposable = colors.themeObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { theme -> applyColors(theme.theme) }
        }
    }

    /**
     * Set the checked state, optionally skipping the thumb-slide animation. SwitchCompat ends its
     * position animator inside [jumpDrawablesToCurrentState], so jumping right after toggling snaps
     * the thumb to its final spot with no slide. Used to populate switches from state without
     * replaying their on/off animation — e.g. the first render, including the one after the activity
     * is recreated for a theme change — while genuine user toggles still animate.
     */
    fun setChecked(checked: Boolean, animate: Boolean) {
        isChecked = checked
        if (!animate) jumpDrawablesToCurrentState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        themeDisposable?.dispose()
        themeDisposable = null
    }

    private fun applyColors(themeColor: Int) {
        val trackOff = ContextCompat.getColor(context, R.color.m3SwitchTrackOff)
        val outline = ContextCompat.getColor(context, R.color.m3SwitchOutline)
        val disabledAlpha = 0x61

        // Checked: solid theme track, with a thumb that contrasts against it the same way the toolbar
        // icons do (white on a dark theme color, dark on a light one). Unchecked: surfaceContainerHighest
        // track + outline thumb. The state order is disabled, checked, default — first match wins.
        trackTintList = ColorStateList(states, intArrayOf(
            trackOff.withAlpha(disabledAlpha),
            themeColor,
            trackOff))

        thumbTintList = ColorStateList(states, intArrayOf(
            outline.withAlpha(disabledAlpha),
            colors.contentColorOnTheme(themeColor),
            outline))

        // The track outline ring shows only when unchecked; the filled (checked) track has none.
        trackDecorationTintList = ColorStateList(states, intArrayOf(
            outline.withAlpha(disabledAlpha),
            Color.TRANSPARENT,
            outline))
    }

    companion object {
        /** Wrap the context in a Material 3 theme so the MaterialSwitch can be constructed. */
        private fun wrap(context: Context): Context =
            ContextThemeWrapper(context, R.style.Theme_OpenMessages_Material3Context)
    }
}
