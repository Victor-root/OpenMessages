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
package io.openmessages.common.util.extensions

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.CompoundButton
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.CompoundButtonCompat
import androidx.core.widget.TextViewCompat
import io.reactivex.subjects.Subject

/**
 * Tints the dialog's buttons (positive/negative/neutral) with [color] so dialogs follow the user's
 * chosen theme color instead of the static brand accent. Works whether called before or after the
 * dialog is shown — the buttons only exist once shown, so we colour them on show otherwise.
 */
fun AlertDialog.themeButtons(@ColorInt color: Int): AlertDialog {
    val apply = {
        getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(color)
        getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(color)
        getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(color)
    }
    if (isShowing) apply() else setOnShowListener { apply() }
    return this
}

/**
 * Tints the check marks of a choice dialog's rows (the boxes of a multi-choice list, the radios of a
 * single-choice one) with [color]. They otherwise keep colorAccent, which is the static brand violet,
 * and clash with buttons already following the user's colour.
 *
 * Call this *after* show(): the list only exists then, and even then it is still empty — rows are
 * created during the first layout pass. Hence the layout listener rather than a single sweep: it fires
 * for every pass, and a ListView reuses the same row Views when it recycles, so rows tinted once stay
 * tinted while scrolling.
 */
fun AlertDialog.themeChoiceItems(@ColorInt color: Int): AlertDialog {
    val tint = ColorStateList.valueOf(color)
    val list = listView ?: return this

    list.viewTreeObserver.addOnGlobalLayoutListener {
        for (index in 0 until list.childCount) list.getChildAt(index).tintCheckMarks(tint)
    }
    for (index in 0 until list.childCount) list.getChildAt(index).tintCheckMarks(tint)
    return this
}

/**
 * Applies [tint] to whatever indicates "checked" in this row, whichever widget the platform picked.
 *
 * AppCompat's dialog rows draw the box as a *compound* drawable (android:drawableStart, which is why
 * it sits before the label) and leave checkMarkDrawable null, so tinting the check mark alone did
 * nothing. Both are covered here, plus the CompoundButton some layouts use instead.
 */
private fun View.tintCheckMarks(tint: ColorStateList) {
    when (this) {
        is CompoundButton -> CompoundButtonCompat.setButtonTintList(this, tint)

        is CheckedTextView -> {
            TextViewCompat.setCompoundDrawableTintList(this, tint)
            checkMarkTintList = tint
            invalidate()
        }

        is ViewGroup -> for (index in 0 until childCount) getChildAt(index).tintCheckMarks(tint)
    }
}

fun AlertDialog.Builder.setPositiveButton(@StringRes textId: Int, subject: Subject<Unit>): AlertDialog.Builder {
    return setPositiveButton(textId) { _, _ -> subject.onNext(Unit) }
}

fun AlertDialog.Builder.setNegativeButton(@StringRes textId: Int, subject: Subject<Unit>): AlertDialog.Builder {
    return setNegativeButton(textId) { _, _ -> subject.onNext(Unit) }
}

fun AlertDialog.Builder.setNeutralButton(@StringRes textId: Int, subject: Subject<Unit>): AlertDialog.Builder {
    return setNeutralButton(textId) { _, _ -> subject.onNext(Unit) }
}

fun AlertDialog.setShowing(show: Boolean) {
    if (isShowing && !show) {
        dismiss()
    } else if (!isShowing && show) {
        show()
    }
}
