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
package io.openmessages.common.util

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import io.openmessages.util.Preferences
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class LauncherIconManager @Inject constructor(
    private val context: Context,
    private val prefs: Preferences,
    private val sharedPrefs: SharedPreferences
) {

    companion object {
        // Activity-alias components are declared in the manifest relative to the app's namespace,
        // which is "io.openmessages" regardless of the installed applicationId. Debug builds append
        // ".debug" to the applicationId, but NOT to the component class names. We must therefore
        // build the ComponentName's class name from the namespace below — using context.packageName
        // (the applicationId) would point at a non-existent component and the enable/disable call
        // would silently do nothing.
        private const val COMPONENT_PACKAGE = "io.openmessages"

        // The one alias the manifest ships with android:enabled="true". Every other alias is disabled
        // there, so a component we have never explicitly toggled reports COMPONENT_ENABLED_STATE_DEFAULT
        // and means "enabled" only for this one.
        private const val MANIFEST_DEFAULT_SUFFIX = "Violet"

        // After our package's ACTION_PACKAGE_CHANGED arrives, how long to let the launcher finish
        // repainting behind the still-visible app before closing it.
        private const val ICON_SETTLE_MS = 300L

        // Hard cap on how long to stay open waiting for that broadcast, so we always close.
        private const val ICON_REFRESH_TIMEOUT_MS = 1500L

        // Ordered to match the material color grid in ThemePickerDialog.
        // Each pair is (argb color int, alias name suffix).
        val ICON_ALIASES: List<Pair<Int, String>> = listOf(
            0xFF7B2DDC.toInt() to "Violet",
            0xFFD32F2F.toInt() to "Red",
            0xFFE91E63.toInt() to "Pink",
            0xFF9C27B0.toInt() to "Purple",
            0xFF673AB7.toInt() to "DeepPurple",
            0xFF3F51B5.toInt() to "Indigo",
            0xFF2196F3.toInt() to "Blue",
            0xFF03A9F4.toInt() to "LightBlue",
            0xFF00BCD4.toInt() to "Cyan",
            0xFF009688.toInt() to "Teal",
            0xFF4CAF50.toInt() to "Green",
            0xFF8BC34A.toInt() to "LightGreen",
            0xFFCDDC39.toInt() to "Lime",
            0xFFE6C400.toInt() to "Yellow",
            0xFFFFC107.toInt() to "Amber",
            0xFFFF9800.toInt() to "Orange",
            0xFFFF5722.toInt() to "DeepOrange",
            0xFF795548.toInt() to "Brown",
            0xFF9E9E9E.toInt() to "Gray",
            0xFF607D8B.toInt() to "BlueGray"
        )

        /** Returns the alias color from [ICON_ALIASES] that is perceptually closest to [color]. */
        fun nearestAliasColor(color: Int): Int {
            val h = FloatArray(3)
            Color.colorToHSV(color, h)
            return ICON_ALIASES.minByOrNull { (aliasColor, _) ->
                val a = FloatArray(3)
                Color.colorToHSV(aliasColor, a)
                // Hue distance (circular), plus saturation & value distances
                val dh = minOf(abs(a[0] - h[0]), 360f - abs(a[0] - h[0]))
                val ds = abs(a[1] - h[1]) * 100f
                val dv = abs(a[2] - h[2]) * 100f
                dh + ds + dv
            }?.first ?: ICON_ALIASES[0].first
        }
    }

    /**
     * The colour of the alias the launcher is *actually* showing, asked of the package manager rather
     * than read from [Preferences.appIconColor].
     *
     * The preference records what the user picked; only the enabled alias says what is on screen. The
     * two can drift — a restored backup rewrites the preference on an install whose alias it cannot
     * touch — and trusting the preference then made [isIconChangeNeeded] answer "nothing to do" while
     * the launcher still showed the old icon, leaving no way to fix it from the picker.
     */
    fun currentAliasColor(): Int {
        val pm = context.packageManager
        return ICON_ALIASES.firstOrNull { (_, suffix) ->
            when (runCatching { pm.getComponentEnabledSetting(aliasComponent(suffix)) }.getOrNull()) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                else -> suffix == MANIFEST_DEFAULT_SUFFIX
            }
        }?.first ?: ICON_ALIASES.first { it.second == MANIFEST_DEFAULT_SUFFIX }.first
    }

    /**
     * The icon colour the user's settings ask for: the theme colour when the icon is set to follow it,
     * otherwise the colour picked in the app-icon tab. Shared by the theme picker and by the post-restore
     * check so both resolve it the same way.
     */
    fun desiredColor(): Int = when {
        prefs.linkIconToTheme.get() -> prefs.theme().get()
        else -> prefs.appIconColor.get()
    }

    /** Whether applying [themeColor] would switch the launcher to a different icon than the active one. */
    fun isIconChangeNeeded(themeColor: Int): Boolean =
        nearestAliasColor(themeColor) != currentAliasColor()

    /**
     * Switches the launcher activity-alias to the icon closest to [themeColor], then invokes
     * [onIconRefreshed] once the launcher actually has the new icon — call finishAffinity() from there.
     *
     * Timing matters: the swap fires an ACTION_PACKAGE_CHANGED broadcast that the launcher reacts to by
     * repainting the home-screen icon. If we close the app the instant the swap call returns, the home
     * screen is revealed mid-repaint and the user sees the icon morph from the old colour to the new
     * one. So instead we keep the (still visible) app in front and wait for that same broadcast to come
     * back to us — our own package's ACTION_PACKAGE_CHANGED is the signal the system has committed the
     * change — plus a short settle for the launcher to finish drawing behind us. Only then do we let the
     * caller close, so the home screen is revealed already showing the final icon. A timeout guarantees
     * the callback still fires if the broadcast never arrives.
     *
     * finishAffinity() (in the callback) is also what keeps the next launch from crashing: the app was
     * started from an activity-alias, so the task's base intent points at that alias, and once it is
     * disabled here the system could no longer restore the task (PackageManager.NameNotFoundException).
     * Finishing the task forces the next launch to start a clean one rooted at the freshly enabled alias.
     */
    fun commitIconForColor(themeColor: Int, onIconRefreshed: () -> Unit) {
        val targetColor = nearestAliasColor(themeColor)
        val targetSuffix = ICON_ALIASES.first { it.first == targetColor }.second
        val pm = context.packageManager

        val handler = Handler(Looper.getMainLooper())
        var finished = false
        var registeredReceiver: BroadcastReceiver? = null
        fun finish() {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            registeredReceiver?.let { runCatching { context.unregisterReceiver(it) } }
            onIconRefreshed()
        }

        // Listen (before the swap, so we can't miss it) for our own package's ACTION_PACKAGE_CHANGED —
        // the launcher receives the same broadcast and repaints from it, so when it reaches us the new
        // icon is landing. Give the launcher a brief settle to finish drawing behind the still-visible
        // app, then let the caller close.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.data?.encodedSchemeSpecificPart == context.packageName) {
                    handler.postDelayed({ finish() }, ICON_SETTLE_MS)
                }
            }
        }
        registeredReceiver = receiver
        val filter = IntentFilter(Intent.ACTION_PACKAGE_CHANGED).apply { addDataScheme("package") }
        // ACTION_PACKAGE_CHANGED is a protected system broadcast, so NOT_EXPORTED still receives it
        // while satisfying Android 14's registration rule should targetSdk ever be raised to 34+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        // Off the main thread: rewriting twenty component states is a single binder round-trip into
        // PackageManagerService that persists them and fires the broadcast, and the system re-checks
        // our default-SMS role on top. Done inline it stalled the caller long enough for Android to
        // report the app as not responding. The finish path below stays on the main thread.
        Schedulers.io().scheduleDirect {
            // Android 14+ supports batching all alias state changes into a single call, which emits
            // a single ACTION_PACKAGE_CHANGED broadcast. On devices where we're the default SMS app
            // the system re-evaluates the SMS role on each broadcast, so collapsing 20 calls into 1
            // makes the launcher icon refresh near-instant. On older devices we fall back to enabling
            // the target first (no launcher drop-out), then disabling the rest.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val settings = ICON_ALIASES.map { (_, suffix) ->
                    PackageManager.ComponentEnabledSetting(
                        aliasComponent(suffix),
                        if (suffix == targetSuffix) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
                runCatching { pm.setComponentEnabledSettings(settings) }
            } else {
                setAliasEnabled(pm, targetSuffix, true)
                ICON_ALIASES.filter { it.second != targetSuffix }.forEach { (_, suffix) ->
                    setAliasEnabled(pm, suffix, false)
                }
            }

            // Persist synchronously (commit, not the default async apply) so appIconColor can never end
            // up out of sync with the actually-enabled alias even if the process is later killed by the
            // OS — otherwise isIconChangeNeeded() would wrongly report "no change" on the next launch
            // (e.g. switching back to the default theme would no longer prompt a change). Reusing the
            // rx-preference key keeps observers notified. A blocking write, hence off the main thread
            // along with the swap above.
            sharedPrefs.edit(commit = true) { putInt(prefs.appIconColor.key(), targetColor) }
        }

        // Safety net: if the broadcast never comes back, close anyway so we don't hang open.
        handler.postDelayed({ finish() }, ICON_REFRESH_TIMEOUT_MS)
    }

    private fun aliasComponent(suffix: String) =
        ComponentName(context.packageName, "$COMPONENT_PACKAGE.LauncherAlias_$suffix")

    private fun setAliasEnabled(pm: PackageManager, suffix: String, enabled: Boolean) {
        val state = if (enabled)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        runCatching {
            pm.setComponentEnabledSetting(aliasComponent(suffix), state, PackageManager.DONT_KILL_APP)
        }
    }
}
