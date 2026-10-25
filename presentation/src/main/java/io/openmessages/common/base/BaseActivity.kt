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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
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
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import javax.inject.Inject

// Translucent veils painted behind the (otherwise transparent) navigation bar in edge-to-edge, so the
// gesture pill / 3-button controls stay legible over scrolling content. Light theme uses a lighter,
// more transparent veil than dark theme.
private const val NAV_SCRIM_LIGHT = 0x1A000000
private const val NAV_SCRIM_DARK = 0x4D000000

abstract class BaseActivity : AppCompatActivity() {
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

    /**
     * Whether the window draws behind the system bars: by choice in edge-to-edge, and from Android
     * 15 on for every window that isn't floating, whatever the app asks for
     */
    private fun drawsBehindSystemBars(): Boolean = isEdgeToEdge() ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && !window.isFloating)

    /**
     * What shows behind the system bars from Android 15 on, where the system no longer paints them
     * and the app does: the veil behind the navigation bar in edge-to-edge, or the bar colour behind
     * both bars in the original look
     */
    private val systemBarsBackground = SystemBarsDrawable()

    /** The colour last given to [applySystemBars] */
    var systemBarColor: Int = Color.TRANSPARENT
        private set

    /** What shows behind the status bar where that isn't [systemBarColor], see [setStatusBarBackdrop] */
    private var statusBarBackdrop: Int? = null

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
    fun applySystemBars(barColor: Int) {
        systemBarColor = barColor
        // Building the window's decor reads the bar settings from the theme, over anything set on
        // the window before it, so it is built first: the first call comes before setContentView
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val edgeToEdge = isEdgeToEdge()
        // In edge-to-edge the nav icons sit over the window background; a light background wants dark
        // icons. Otherwise they sit over the bar colour, like the status icons.
        val lightNavIcons = colors.useDarkSystemBarIcons(
                if (edgeToEdge) resolveThemeColor(android.R.attr.windowBackground) else barColor)
        // Our own subtle nav-bar veil in edge-to-edge, lighter in light theme than in dark.
        val navigationBarColor = when {
            edgeToEdge -> if (lightNavIcons) NAV_SCRIM_LIGHT else NAV_SCRIM_DARK
            else -> barColor
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Painted over the content: the nav bar always, the status bar only in the original
            // look, which in edge-to-edge shows the toolbar reaching up behind it.
            systemBarsBackground.color = navigationBarColor
        } else {
            paintSystemBars(if (edgeToEdge) Color.TRANSPARENT else barColor, navigationBarColor)
        }

        // We paint the nav veil ourselves, so disable the platform's automatic contrast scrim
        if (drawsBehindSystemBars() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        controller.isAppearanceLightNavigationBars = lightNavIcons
        applyStatusBarIcons()
    }

    /**
     * Tells the status bar what shows behind it, for its icons to stay legible: [color] where the
     * screen scrolls something else than the bar colour under it, null for the bar colour
     */
    protected fun setStatusBarBackdrop(color: Int?) {
        if (statusBarBackdrop == color) return
        statusBarBackdrop = color
        applyStatusBarIcons()
    }

    private fun applyStatusBarIcons() {
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                colors.useDarkSystemBarIcons(statusBarBackdrop ?: systemBarColor)
    }

    /**
     * Has the system paint the bars, which it only does before Android 15
     */
    @Suppress("DEPRECATION")
    private fun paintSystemBars(statusBarColor: Int, navigationBarColor: Int) {
        window.statusBarColor = statusBarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = navigationBarColor
        }
        // The status bar rests over the themed toolbar, so it needs no scrim there (a screen that
        // scrolls content behind it, like the main list, fades in its own)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            content.foreground = systemBarsBackground
            // The content frame lays its foreground out by its gravity, and one without a height of
            // its own came out at -1 tall: the nav bar strip was drawn above the screen
            content.foregroundGravity = Gravity.FILL
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            systemBarsBackground.topHeight = if (isEdgeToEdge()) 0 else bars.top
            systemBarsBackground.bottomHeight = bars.bottom
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
     * Applies the system-bar insets. In edge-to-edge the default pushes the toolbar's themed
     * background behind the status bar and lifts all content above the nav bar / keyboard; screens
     * whose content should draw behind the nav bar (e.g. the main conversation list) override this.
     * In the original look the content keeps clear of both bars, which are painted.
     */
    protected open fun onApplyEdgeToEdgeInsets(top: Int, bottom: Int) {
        val content = findViewById<View>(android.R.id.content)
        if (isEdgeToEdge()) {
            toolbar?.applyInsetTop(top)
            content?.applyInsetPadding(bottom = bottom)
        } else {
            content?.applyInsetPadding(top = top, bottom = bottom)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
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
        if (drawsBehindSystemBars()) installEdgeToEdgeInsets()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        title = title // The title may have been set before layout inflation
        if (drawsBehindSystemBars()) installEdgeToEdgeInsets()
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

/**
 * A colour over the top [topHeight] and the bottom [bottomHeight] pixels of what it is drawn on
 */
private class SystemBarsDrawable : Drawable() {

    private val paint = Paint()

    var color: Int
        get() = paint.color
        set(value) {
            paint.color = value
            invalidateSelf()
        }

    var topHeight: Int = 0
        set(value) {
            field = value
            invalidateSelf()
        }

    var bottomHeight: Int = 0
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), (bounds.top + topHeight).toFloat(), paint)
        canvas.drawRect(bounds.left.toFloat(), (bounds.bottom - bottomHeight).toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

}
