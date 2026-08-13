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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import io.openmessages.R
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.forwardTouches
import io.openmessages.common.util.extensions.resolveThemeAttribute
import io.openmessages.common.util.extensions.resolveThemeColor
import io.openmessages.common.util.extensions.setVisible
import io.openmessages.databinding.RadioPreferenceViewBinding
import io.openmessages.injection.appComponent
import javax.inject.Inject

class RadioPreferenceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    @Inject lateinit var colors: Colors
    private var layout: RadioPreferenceViewBinding

    var title: String? = null
        set(value) {
            field = value

            if (isInEditMode) {
                findViewById<TextView>(R.id.titleView).text = value
            } else {
                layout.titleView.text = value
            }
        }

    var summary: String? = null
        set(value) {
            field = value


            if (isInEditMode) {
                findViewById<TextView>(R.id.summaryView).run {
                    text = value
                    setVisible(value?.isNotEmpty() == true)
                }
            } else {
                layout.summaryView.text = value
                layout.summaryView.setVisible(value?.isNotEmpty() == true)
            }
        }

    val radioButton get() = layout.radioButton
    val titleView get() = layout.titleView
    val summaryView get() = layout.summaryView

    init {
        if (!isInEditMode) {
            appComponent.inject(this)
        }

        layout = RadioPreferenceViewBinding.inflate(LayoutInflater.from(context), this)
        setBackgroundResource(context.resolveThemeAttribute(R.attr.selectableItemBackground))

        val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked))

        val themeColor = when (isInEditMode) {
            true -> ContextCompat.getColor(context, R.color.tools_theme)
            false -> colors.theme().theme
        }
        val textSecondary = context.resolveThemeColor(android.R.attr.textColorTertiary)
        layout.radioButton.buttonTintList = ColorStateList(states, intArrayOf(themeColor, textSecondary))
        layout.radioButton.forwardTouches(this)

        context.obtainStyledAttributes(attrs, R.styleable.RadioPreferenceView).run {
            title = getString(R.styleable.RadioPreferenceView_title)
            summary = getString(R.styleable.RadioPreferenceView_summary)

            // If there's a custom view used for the preference's widget, inflate it
            getResourceId(R.styleable.RadioPreferenceView_widget, -1).takeIf { it != -1 }?.let { id ->
                View.inflate(context, id, layout.widgetFrame)
            }

            recycle()
        }
    }

}