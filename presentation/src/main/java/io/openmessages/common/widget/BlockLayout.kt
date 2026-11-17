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
package io.openmessages.common.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import io.openmessages.R
import io.openmessages.common.util.extensions.resolveThemeColor
import io.openmessages.common.util.extensions.setBackgroundTint

/**
 * A vertical group of rows shown as one rounded block, like the inbox groups. The rows' ripples and
 * highlights are clipped to its corners.
 */
class BlockLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.rounded_rect_20dp)
        if (!isInEditMode) setBackgroundTint(context.resolveThemeColor(R.attr.cardColor))
        clipToOutline = true
    }

}
