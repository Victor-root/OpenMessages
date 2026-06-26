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

import android.app.Dialog
import android.app.WallpaperManager
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import io.openmessages.R
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.resolveThemeColor
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.common.util.extensions.setTint
import io.openmessages.databinding.ThemeColorItemBinding
import io.openmessages.databinding.ThemePickerDialogBinding
import io.openmessages.common.util.LauncherIconManager
import io.openmessages.injection.appComponent
import io.openmessages.manager.WidgetManager
import io.openmessages.util.Preferences
import io.reactivex.disposables.Disposable
import javax.inject.Inject

class ThemePickerDialog : DialogFragment() {

    @Inject lateinit var prefs: Preferences
    @Inject lateinit var colors: Colors
    @Inject lateinit var widgetManager: WidgetManager
    @Inject lateinit var launcherIconManager: LauncherIconManager

    private lateinit var binding: ThemePickerDialogBinding
    private lateinit var colorAdapter: ColorGridAdapter
    private lateinit var iconColorAdapter: ColorGridAdapter

    private var selectedColor: Int = DEFAULT_COLOR
    private var selectedIconColor: Int = DEFAULT_COLOR
    private var wallpaperColor: Int? = null
    private var defaultCheck: ImageView? = null
    private var wallpaperCheck: ImageView? = null

    // 0 = the global app theme; a non-zero recipient id themes a single conversation instead.
    private var recipientId: Long = 0L

    private var originalStatusColor: Int = DEFAULT_COLOR
    private var originalNavColor: Int = DEFAULT_COLOR
    private var originalToolbarBackground: Drawable? = null
    private var colorSaved = false

    private var hsvDisposable: Disposable? = null

    companion object {
        const val DEFAULT_COLOR = 0xFF7B2DDC.toInt()
        private const val ARG_RECIPIENT_ID = "recipientId"
        private const val ARG_INITIAL_COLOR = "initialColor"

        /**
         * [initialColor], when provided, is the colour the dialog opens on (selection, live preview
         * and chrome). Pass the conversation's *resolved* colour for a per-conversation picker, since
         * that colour can come from auto-colouring rather than the stored [Preferences.theme] override.
         */
        fun newInstance(recipientId: Long = 0L, initialColor: Int? = null) = ThemePickerDialog().apply {
            arguments = Bundle().apply {
                putLong(ARG_RECIPIENT_ID, recipientId)
                if (initialColor != null) putInt(ARG_INITIAL_COLOR, initialColor)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        appComponent.inject(this)
        recipientId = arguments?.getLong(ARG_RECIPIENT_ID) ?: 0L
        val themePref = prefs.theme(recipientId)
        selectedColor = if (arguments?.containsKey(ARG_INITIAL_COLOR) == true) {
            arguments!!.getInt(ARG_INITIAL_COLOR)
        } else {
            themePref.get()
        }

        val inflater = LayoutInflater.from(requireContext())
        binding = ThemePickerDialogBinding.inflate(inflater)

        // Default theme swatch — the signature violet gradient, in its own category
        val defaultDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(DEFAULT_COLOR, colors.deriveGradientEndColor(DEFAULT_COLOR))
        ).apply { shape = GradientDrawable.OVAL }
        defaultCheck = addSwatch(
            binding.defaultColors,
            defaultDrawable,
            colors.textPrimaryOnThemeForColor(DEFAULT_COLOR),
            selectedColor == DEFAULT_COLOR
        ) { selectColor(DEFAULT_COLOR) }

        // Color grid — the Material 500 shade of each family (4 columns).
        // Red (index 0) uses 700 in light mode (#D32F2F) and 500 in dark mode (#F44336) so it
        // remains legible on white backgrounds while still being vibrant in dark mode.
        val night = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val flatColors = colors.materialColors.map { family -> family[5] }
            .toMutableList()
            .apply { set(0, colors.materialColors[0][if (night) 5 else 7]) }
        colorAdapter = ColorGridAdapter(flatColors, selectedColor) { color -> selectColor(color) }
        binding.colorGrid.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.colorGrid.adapter = colorAdapter
        binding.colorGrid.itemAnimator = null

        // HSV picker
        binding.hsvPicker.setColor(selectedColor)
        hsvDisposable = binding.hsvPicker.selectedColor.subscribe { color -> applySelection(color) }

        // Tabs — preset palette, fine-tuning, and the app-icon colour. Toggle the pages on selection.
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.palettePage.isVisible = tab.position == 0
                binding.customPage.isVisible = tab.position == 1
                binding.iconPage.isVisible = tab.position == 2
                // The HSV picker can only position its cursor once it's laid out, which only happens
                // when its tab becomes visible — (re)apply the current color at that point.
                if (tab.position == 1) binding.hsvPicker.setColor(selectedColor)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        updateTabColors(selectedColor)

        // App-icon colour tab. The launcher icon can only be one of the alias colours, so it gets its
        // own discrete grid. "Follow theme" reuses the theme colour for the icon; when unchecked the
        // user picks an icon colour independently of the app theme.
        // The launcher icon is a global, app-wide concept, so the icon tab is only shown when theming
        // the whole app — a per-conversation colour drops it and keeps just Presets + Custom.
        if (recipientId == 0L) {
            selectedIconColor = prefs.appIconColor.get()
            iconColorAdapter = ColorGridAdapter(
                LauncherIconManager.ICON_ALIASES.map { it.first }, selectedIconColor
            ) { color -> selectIconColor(color) }
            binding.iconColorGrid.layoutManager = GridLayoutManager(requireContext(), 4)
            binding.iconColorGrid.adapter = iconColorAdapter
            binding.iconColorGrid.itemAnimator = null
            binding.iconFollowTheme.isChecked = prefs.linkIconToTheme.get()
            binding.iconFollowTheme.setOnCheckedChangeListener { _, _ -> updateIconGridVisibility() }
            updateIconGridVisibility()
        } else {
            binding.tabLayout.getTabAt(2)?.let { binding.tabLayout.removeTab(it) }
            binding.iconPage.isVisible = false
        }

        // Wallpaper color (API 27+, no permission required)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val wm = WallpaperManager.getInstance(requireContext())
                val wallpaperColors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                val primary = wallpaperColors?.primaryColor?.toArgb()
                if (primary != null) {
                    wallpaperColor = primary
                    binding.wallpaperSection.isVisible = true
                    val circleDrawable = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(primary)
                    }
                    wallpaperCheck = addSwatch(
                        binding.wallpaperColors,
                        circleDrawable,
                        colors.textPrimaryOnThemeForColor(primary),
                        selectedColor == primary
                    ) { selectColor(primary) }
                }
            } catch (ignored: Exception) {
                binding.wallpaperSection.isVisible = false
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_theme)
            .setView(binding.root)
            .setNegativeButton(R.string.button_cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                colorSaved = true
                themePref.set(selectedColor)
                // Icon and widget are global; only touch them when theming the whole app.
                if (recipientId == 0L) {
                    saveIconChoice()
                    widgetManager.updateTheme()
                }
            }
            .create()
    }

    override fun onStart() {
        super.onStart()
        val window = activity?.window
        originalStatusColor = window?.statusBarColor ?: DEFAULT_COLOR
        originalNavColor = window?.navigationBarColor ?: DEFAULT_COLOR
        originalToolbarBackground = activity?.findViewById<Toolbar>(R.id.toolbar)?.background
        previewColor(selectedColor)
        updateButtonColors(selectedColor)
        applyRoundedDialogBackground()
    }

    /** Gives the dialog window rounded corners for a more modern look. */
    private fun applyRoundedDialogBackground() {
        val density = resources.displayMetrics.density
        val background = GradientDrawable().apply {
            cornerRadius = 24f * density
            setColor(requireContext().resolveThemeColor(android.R.attr.windowBackground))
        }
        val inset = (16 * density + 0.5f).toInt()
        dialog?.window?.setBackgroundDrawable(InsetDrawable(background, inset))
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // If the user cancelled, revert the live header/bar preview to what it was.
        // If a color was saved, the host activity's own theme subscription takes over.
        if (!colorSaved) {
            val window = activity?.window
            window?.statusBarColor = originalStatusColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window?.navigationBarColor = originalNavColor
            }
            activity?.findViewById<Toolbar>(R.id.toolbar)?.background = originalToolbarBackground
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hsvDisposable?.dispose()
        hsvDisposable = null
    }

    /** Select a color from the grid / default / wallpaper swatches and sync the HSV picker. */
    private fun selectColor(color: Int) {
        applySelection(color)
        binding.hsvPicker.setColor(color)
    }

    /** Apply a color everywhere (swatches, header, bars, buttons) without touching the HSV picker. */
    private fun applySelection(color: Int) {
        selectedColor = color
        colorAdapter.setSelectedColor(color)
        defaultCheck?.isVisible = color == DEFAULT_COLOR
        wallpaperCheck?.isVisible = wallpaperColor != null && color == wallpaperColor
        previewColor(color)
        updateButtonColors(color)
        updateTabColors(color)
    }

    /** Tints the tab indicator and selected-tab label with the current color. */
    private fun updateTabColors(color: Int) {
        binding.tabLayout.setSelectedTabIndicatorColor(color)
        binding.tabLayout.setTabTextColors(
            requireContext().resolveThemeColor(android.R.attr.textColorSecondary), color
        )
    }

    /** The app-icon grid is only relevant when the icon is NOT following the theme colour. */
    private fun updateIconGridVisibility() {
        binding.iconColorGrid.isVisible = !binding.iconFollowTheme.isChecked
    }

    /** Remember the independently-chosen app-icon colour and move the check to it. */
    private fun selectIconColor(color: Int) {
        selectedIconColor = color
        iconColorAdapter.setSelectedColor(color)
    }

    private fun saveIconChoice() {
        val followTheme = binding.iconFollowTheme.isChecked
        prefs.linkIconToTheme.set(followTheme)
        // The icon either tracks the theme colour or uses the colour picked in the app-icon tab.
        val color = if (followTheme) selectedColor else selectedIconColor
        // Nothing to do if the chosen icon colour already maps to the active alias — this is also what
        // keeps a plain theme tweak (icon left on a custom colour that didn't change) from closing the app.
        if (!launcherIconManager.isIconChangeNeeded(color)) return
        // Swap the alias in the foreground, then close the app once the launcher has actually repainted
        // the new icon (signalled from inside commitIconForColor) — closing earlier would reveal the
        // home screen mid-repaint and the user would see the icon transition. Capture the activity now
        // because the dialog detaches on dismiss (getActivity() would be null by the time the callback
        // fires); the host activity itself outlives the dialog, so finishAffinity() on it is valid.
        // finishAffinity also drops the task whose base intent now points at the disabled alias, so the
        // next launch starts clean instead of crashing.
        val host = activity ?: return
        launcherIconManager.commitIconForColor(color) {
            host.finishAffinity()
        }
    }

    /** Live-preview the chosen color in the status bar, navigation bar and toolbar header. */
    private fun previewColor(color: Int) {
        val window = activity?.window ?: return
        window.statusBarColor = color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = color
        }
        // Match the real header: the default violet previews as a gradient, custom colors as flat.
        activity?.findViewById<Toolbar>(R.id.toolbar)?.background = if (colors.usesBrandGradient(color)) {
            GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(color, colors.deriveGradientEndColor(color)))
        } else {
            ColorDrawable(color)
        }
    }

    private fun updateButtonColors(color: Int) {
        (dialog as? AlertDialog)?.apply {
            getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(color)
            getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(color)
            getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(color)
        }
        binding.iconFollowTheme.buttonTintList = ColorStateList.valueOf(color)
    }

    /** Builds a circular color swatch with a centered check overlay and adds it to [container]. */
    private fun addSwatch(
        container: LinearLayout,
        background: Drawable,
        checkTint: Int,
        selected: Boolean,
        onClick: () -> Unit
    ): ImageView {
        val density = resources.displayMetrics.density
        val sizePx = (48 * density + 0.5f).toInt()
        val marginPx = (6 * density + 0.5f).toInt()
        val checkPx = (24 * density + 0.5f).toInt()

        val frame = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            this.background = background
            setOnClickListener { onClick() }
        }
        val check = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(checkPx, checkPx, Gravity.CENTER)
            setImageResource(R.drawable.ic_check_white_24dp)
            setTint(checkTint)
            isVisible = selected
        }
        frame.addView(check)
        container.addView(frame)
        return check
    }

    private inner class ColorGridAdapter(
        private val colorList: List<Int>,
        initialSelected: Int,
        private val onColorSelected: (Int) -> Unit
    ) : RecyclerView.Adapter<ColorGridAdapter.ViewHolder>() {

        private var selectedColor: Int = initialSelected

        fun setSelectedColor(color: Int) {
            val old = colorList.indexOf(selectedColor)
            val new = colorList.indexOf(color)
            if (old == new) return
            selectedColor = color
            if (old >= 0) notifyItemChanged(old)
            if (new >= 0) notifyItemChanged(new)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ThemeColorItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val color = colorList[position]
            holder.binding.colorCircle.setBackgroundTint(color)
            val isSelected = color == selectedColor
            holder.binding.checkIcon.isVisible = isSelected
            if (isSelected) {
                holder.binding.checkIcon.setTint(colors.textPrimaryOnThemeForColor(color))
            }
            holder.binding.root.setOnClickListener { onColorSelected(color) }
        }

        override fun getItemCount() = colorList.size

        inner class ViewHolder(val binding: ThemeColorItemBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
