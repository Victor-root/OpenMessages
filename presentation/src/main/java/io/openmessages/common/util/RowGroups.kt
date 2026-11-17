/*
 * Copyright (C) 2026 OpenMessages contributors
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

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import io.openmessages.R
import io.openmessages.common.util.extensions.resolveThemeColor

/**
 * The groups a list's rows fall into, each shown as one rounded block under its heading. A group is
 * a run of consecutive rows with the same label.
 */
class RowGroups(context: Context) {

    private val radius = 20 * context.resources.displayMetrics.density

    private var labels: List<String> = emptyList()

    /** Takes the label of every row, in list order */
    fun update(labels: List<String>) {
        this.labels = labels
    }

    /** The heading to show above [position], or null when it continues the group above it */
    fun labelAt(position: Int): String? {
        val label = labels.getOrNull(position) ?: return null
        return label.takeIf { position == 0 || labels.getOrNull(position - 1) != label }
    }

    private fun endsGroup(position: Int): Boolean =
        position == labels.lastIndex || labels.getOrNull(position + 1) != labels.getOrNull(position)

    /** The corners of the row at [position]: rounded where it starts or ends its group */
    fun cornerRadii(position: Int): FloatArray {
        val top = if (labelAt(position) != null) radius else 0f
        val bottom = if (endsGroup(position)) radius else 0f
        return floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
    }

    /**
     * The row's slice of its group's block, rippling within its corners and, when [selected] is
     * given, tinted with it while the row is activated. The colours come from [themed], the
     * screen's own context, as the app's carries no theme.
     */
    fun background(themed: Context, position: Int, selected: Int? = null): Drawable {
        val radii = cornerRadii(position)
        fun shape(color: Int) = GradientDrawable().apply { cornerRadii = radii; setColor(color) }
        val card = shape(themed.resolveThemeColor(R.attr.cardColor))
        val content = when (selected) {
            null -> card
            else -> LayerDrawable(arrayOf(card, StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_activated), shape(selected))
                addState(intArrayOf(), shape(Color.TRANSPARENT))
            }))
        }
        val ripple = ColorStateList.valueOf(themed.resolveThemeColor(android.R.attr.colorControlHighlight))
        return RippleDrawable(ripple, content, shape(Color.WHITE))
    }

    /** An adapter whose rows are grouped */
    interface Adapter {
        val rowGroups: RowGroups
    }

    companion object {
        /**
         * Rebinds only what a row takes from its group: its corners and spacing depend on its
         * neighbours, which an insertion or a removal elsewhere can change without the row
         * itself changing
         */
        const val PAYLOAD = "rowGroups"
    }

}
