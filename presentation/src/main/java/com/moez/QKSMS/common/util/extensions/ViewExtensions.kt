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

import android.animation.LayoutTransition
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import io.openmessages.R

var ViewGroup.animateLayoutChanges: Boolean
    get() = layoutTransition != null
    set(value) {
        layoutTransition = if (value) LayoutTransition() else null
    }

fun EditText.showKeyboard() {
    requestFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

fun EditText.hideKeyboard() {
    requestFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun ImageView.setTint(color: Int?) {
    imageTintList =
        if (color == null) null
        else ColorStateList.valueOf(color)
}

fun TextView.setTint(color: Int?) {
    foregroundTintList =
        if (color == null) null
        else ColorStateList.valueOf(color)
}

fun ProgressBar.setTint(color: Int?) {
    indeterminateTintList =
        if (color == null) null
        else ColorStateList.valueOf(color)
    progressTintList =
        if (color == null) null
        else ColorStateList.valueOf(color)
}

fun View.setBackgroundTint(color: Int?) {

    // API 21 doesn't support this

    backgroundTintList =
        if (color == null) null
        else ColorStateList.valueOf(color)
}

fun View.setPadding(left: Int? = null, top: Int? = null, right: Int? = null, bottom: Int? = null) {
    setPadding(left ?: paddingLeft, top ?: paddingTop, right ?: paddingRight, bottom ?: paddingBottom)
}

/**
 * Adds window-inset padding on top of the view's *original* padding. The original padding is captured
 * once in a tag, so repeated inset passes (rotation, IME show/hide) never accumulate.
 */
fun View.applyInsetPadding(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) {
    val base = getTag(R.id.insetBasePadding) as? Rect
            ?: Rect(paddingLeft, paddingTop, paddingRight, paddingBottom).also { setTag(R.id.insetBasePadding, it) }
    setPadding(base.left + left, base.top + top, base.right + right, base.bottom + bottom)
}

/**
 * Extends a fixed-height bar (e.g. a Toolbar) upward behind the status bar: grows its height by [top]
 * and pads the top by [top], so its background fills the status-bar area while its content stays in
 * the original area. For non-fixed heights (wrap_content/match_parent) it only pads. Idempotent
 * across inset passes via captured base values.
 */
fun View.applyInsetTop(top: Int) {
    val basePad = getTag(R.id.insetBasePadding) as? Rect
            ?: Rect(paddingLeft, paddingTop, paddingRight, paddingBottom).also { setTag(R.id.insetBasePadding, it) }
    setPadding(basePad.left, basePad.top + top, basePad.right, basePad.bottom)
    val params = layoutParams ?: return
    if (params.height >= 0) {
        val baseHeight = getTag(R.id.insetBaseHeight) as? Int ?: params.height.also { setTag(R.id.insetBaseHeight, it) }
        params.height = baseHeight + top
        layoutParams = params
    }
}

/** Adds [extra] to the view's *original* bottom margin (captured once) for window-inset handling. */
fun View.applyInsetBottomMargin(extra: Int) {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    val base = getTag(R.id.insetBaseMargin) as? Int ?: params.bottomMargin.also { setTag(R.id.insetBaseMargin, it) }
    params.bottomMargin = base + extra
    layoutParams = params
}

fun View.setVisible(visible: Boolean, invisible: Int = View.GONE) {
    visibility = if (visible) View.VISIBLE else invisible
}

/**
 * If a view captures clicks at all, then the parent won't ever receive touch events. This is a
 * problem when we're trying to capture link clicks, but tapping or long pressing other areas of
 * the view no longer work. Also problematic when we try to long press on an image in the message
 * view
 */

class CancelableSimpleOnGestureListener(view: View, parentView: View) : SimpleOnGestureListener() {
    private var lastUpEvent: MotionEvent? = null
    private val parent = parentView
    private val thisView = view
    private var textInitiallySelectable = false

    init {
        if (thisView is TextView)
            textInitiallySelectable = thisView.isTextSelectable
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (lastUpEvent !== null) {
            parent.onTouchEvent(e)
            parent.onTouchEvent(lastUpEvent)
            lastUpEvent?.recycle()
            lastUpEvent = null
        }
        return true
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        lastUpEvent = MotionEvent.obtain(e)
        thisView.onTouchEvent(e)
        return true
    }

    override fun onDown(e: MotionEvent): Boolean {
        thisView.onTouchEvent(e)
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        parent.onTouchEvent(e)
        // this is kinda odd but we have to 'bounce' the text selectable value so it doesn't
        // start selecting text on a long press, but will start selecting it on the next double-tap
        if (thisView is TextView) {
            thisView.setTextIsSelectable(false)
            thisView.setTextIsSelectable(textInitiallySelectable)
        }
    }
}

fun View.forwardTouches(parent: View): CancelableSimpleOnGestureListener {
    val gestureListener = CancelableSimpleOnGestureListener(this, parent)

    setOnTouchListener(object : OnTouchListener {
        val gestureDetector = GestureDetector(parent.context, gestureListener)

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            return gestureDetector.onTouchEvent(e)
        }
    })

    return gestureListener
}

fun ViewPager.addOnPageChangeListener(listener: (Int) -> Unit) {
    addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            listener(position)
        }
    })
}

fun RecyclerView.scrapViews() {
    recycledViewPool.clear()
    adapter?.notifyDataSetChanged()
}
