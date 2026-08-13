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

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.openmessages.R
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.applyInsetPadding
import io.openmessages.common.util.extensions.applyInsetTop
import io.openmessages.common.util.extensions.resolveThemeColor
import io.openmessages.util.Preferences
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

// Translucent veils painted behind the (otherwise transparent) navigation bar in edge-to-edge, so the
// gesture pill / 3-button controls stay legible over scrolling content. Light theme uses a lighter,
// more transparent veil than dark theme.
private const val NAV_SCRIM_LIGHT = 0x1A000000
private const val NAV_SCRIM_DARK = 0x4D000000

abstract class QkActivity : AppCompatActivity() {
    @Inject lateinit var prefs: Preferences
    @Inject lateinit var colors: Colors

    protected val menu: Subject<Menu> = BehaviorSubject.create()

    protected val toolbar: Toolbar? get() = findViewById(R.id.toolbar)
    protected val toolbarTitle: TextView? get() = findViewById(R.id.toolbarTitle)

    /**
     * Colour for everything drawn on the themed toolbar, kept in step with the status-bar icons.
     * Refreshed by [applyToolbarContentColor]; screens that tint a toolbar view outside the theme
     * subscription (a menu icon rebuilt at render time, a drawer arrow) read it from here instead of
     * hard-coding white.
     */
    protected var toolbarContentColor: Int = Color.WHITE
        private set

    /** Whether this screen participates in edge-to-edge. Floating/dialog windows opt out. */
    protected open val supportsEdgeToEdge: Boolean get() = true

    protected fun isEdgeToEdge(): Boolean = supportsEdgeToEdge && prefs.edgeToEdge.get()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isEdgeToEdge()) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        applySystemBars(prefs.theme().get())
        onNewIntent(intent)
        disableScreenshots(prefs.disableScreenshots.get())
    }

    override fun onResume() {
        super.onResume()
        disableScreenshots(prefs.disableScreenshots.get())
    }

    /**
     * Single source of truth for the system bars. [barColor] is drawn behind the status bar (the
     * toolbar / theme color). In edge-to-edge the status bar is transparent and the nav bar carries a
     * subtle self-painted veil, with icon brightness matched to whatever shows through each bar;
     * otherwise the bars are painted opaque with [barColor] (the app's original look).
     */
    protected fun applySystemBars(barColor: Int) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isEdgeToEdge()) {
            // Nav icons sit over the window background; a light background wants dark icons.
            val lightNavIcons = colors.useDarkSystemBarIcons(resolveThemeColor(android.R.attr.windowBackground))
            window.statusBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Our own subtle nav-bar veil, lighter in light theme than in dark.
                window.navigationBarColor = if (lightNavIcons) NAV_SCRIM_LIGHT else NAV_SCRIM_DARK
            }
            // The status bar rests over the themed toolbar, so it needs no scrim there (a screen that
            // scrolls content behind it, like the main list, fades in its own). We paint the nav veil
            // ourselves above, so disable the platform's automatic contrast scrim for both bars.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            // Status icons sit over the toolbar/theme color; nav icons over the window background.
            controller.isAppearanceLightStatusBars = colors.useDarkSystemBarIcons(barColor)
            controller.isAppearanceLightNavigationBars = lightNavIcons
        } else {
            window.statusBarColor = barColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.navigationBarColor = barColor
            }
            val darkIcons = colors.useDarkSystemBarIcons(barColor)
            controller.isAppearanceLightStatusBars = darkIcons
            controller.isAppearanceLightNavigationBars = darkIcons
        }
    }

    /**
     * Repaints the toolbar chrome for [barColor] — the same colour [applySystemBars] gets, so the
     * navigation icon, the overflow icon and the title always resolve light or dark exactly like the
     * status-bar icons immediately above them. Subclasses tinting their own toolbar views afterwards
     * read [toolbarContentColor].
     */
    protected fun applyToolbarContentColor(barColor: Int) {
        toolbarContentColor = colors.contentColorOnTheme(barColor)
        toolbar?.navigationIcon?.setTint(toolbarContentColor)
        toolbar?.overflowIcon = toolbar?.overflowIcon?.apply { setTint(toolbarContentColor) }
        toolbarTitle?.setTextColor(toolbarContentColor)
        // The subtitle ("3 of 9 results") is deliberately dimmer than the title, so it follows the
        // same colour at reduced alpha rather than the theme's tertiary text, which is picked for the
        // window background and not for the toolbar.
        findViewById<TextView>(R.id.toolbarSubtitle)
                ?.setTextColor(ColorUtils.setAlphaComponent(toolbarContentColor, 0xB3))
    }

    private fun installEdgeToEdgeInsets() {
        val content = findViewById<View>(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // max(nav, ime) keeps bottom content above the keyboard once the window stops auto-resizing.
            onApplyEdgeToEdgeInsets(bars.top, maxOf(bars.bottom, ime.bottom))
            insets // returned unconsumed so DrawerLayout etc. still receive them
        }
        // Request insets once attached: during onCreate the window has none yet, so an early pass
        // would apply top=0 and leave the toolbar under the status bar if it didn't re-fire.
        if (content.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(content)
        } else {
            content.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
    }

    /**
     * Applies the system-bar insets. The default pushes the toolbar's themed background behind the
     * status bar and lifts all content above the nav bar / keyboard. Screens whose content should
     * draw behind the nav bar (e.g. the main conversation list) override this.
     */
    protected open fun onApplyEdgeToEdgeInsets(top: Int, bottom: Int) {
        toolbar?.applyInsetTop(top)
        findViewById<View>(android.R.id.content)?.applyInsetPadding(bottom = bottom)
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        title = title // The title may have been set before layout inflation
        if (isEdgeToEdge()) installEdgeToEdgeInsets()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        title = title // The title may have been set before layout inflation
        if (isEdgeToEdge()) installEdgeToEdgeInsets()
    }

    override fun setTitle(titleId: Int) {
        title = getString(titleId)
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        toolbarTitle?.text = title
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        if (menu != null) {
            this.menu.onNext(menu)
        }
        return result
    }

    protected open fun showBackButton(show: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(show)
        toolbar?.navigationIcon?.setTint(toolbarContentColor)
    }

    private fun disableScreenshots(disableScreenshots: Boolean) {
        if (disableScreenshots) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

}
