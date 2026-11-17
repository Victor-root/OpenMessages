/*
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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import io.openmessages.R
import io.openmessages.common.util.extensions.dpToPx

/**
 * The button that starts and stops an audio message: a disc in the conversation's colour with a
 * stop sign while recording, ringed by halos that swell and fade, and a microphone once stopped
 */
class MicInputCloudView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    enum class ViewState {
        /** Stopped: the microphone shows, to record again */
        PAUSED_STATE,

        /** Recording: the stop sign shows, among the halos */
        PLAY_STATE
    }

    private var state = ViewState.PLAY_STATE

    private val discRadius = 32.dpToPx(context).toFloat()
    private val haloReach = 16.dpToPx(context).toFloat()
    private val iconSize = 28.dpToPx(context)

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var iconColor = Color.WHITE

    private val stopIcon: Drawable = ContextCompat.getDrawable(context, R.drawable.ic_stop_black_24dp)!!.mutate()
    private val micIcon: Drawable = ContextCompat.getDrawable(context, R.drawable.ic_mic_black_24dp)!!.mutate()

    /** Where the halos are in their swell, from 0 to 1 */
    private var halo = 0f
    private val haloAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = HALO_DURATION
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            halo = animator.animatedValue as Float
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = ((discRadius + haloReach) * 2).toInt()
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        if (state == ViewState.PLAY_STATE) {
            // Two halos, half a swell apart
            listOf(halo, (halo + 0.5f) % 1f).forEach { swell ->
                haloPaint.alpha = ((1f - swell) * HALO_ALPHA).toInt()
                canvas.drawCircle(cx, cy, discRadius + haloReach * swell, haloPaint)
            }
        }
        canvas.drawCircle(cx, cy, discRadius, discPaint)

        val icon = if (state == ViewState.PLAY_STATE) stopIcon else micIcon
        val half = iconSize / 2
        icon.setBounds(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
        icon.setTint(iconColor)
        icon.draw(canvas)
    }

    override fun performClick(): Boolean {
        setState(if (state == ViewState.PAUSED_STATE) ViewState.PLAY_STATE else ViewState.PAUSED_STATE)
        return super.performClick()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) state = ViewState.PLAY_STATE
        updateHalo()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        haloAnimator.cancel()
    }

    /** The disc and halos in [accent], its sign in [content], the colour that reads on it */
    fun setColors(accent: Int, content: Int) {
        discPaint.color = accent
        haloPaint.color = accent
        iconColor = content
        invalidate()
    }

    fun setState(s: ViewState) {
        state = s
        updateHalo()
        invalidate()
    }

    fun getState(): ViewState = state

    private fun updateHalo() {
        val swell = state == ViewState.PLAY_STATE && isShown
        if (swell && !haloAnimator.isStarted) haloAnimator.start()
        if (!swell) haloAnimator.cancel()
    }

    private companion object {
        const val HALO_DURATION = 1600L
        const val HALO_ALPHA = 0x66
    }
}
