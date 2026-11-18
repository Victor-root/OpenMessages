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
package io.openmessages.feature.compose

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import io.openmessages.common.util.extensions.dpToPx

/**
 * Brings a new message into the conversation the way chat apps do: the bubble rises into place
 * from the composer, growing out of its own side (a sent one from the right, a received one from
 * the left), while the messages above make room for it at the same time.
 *
 * The stock animator stages its work: the messages above move first and only once they have
 * settled does the new one fade in, so a message that was ready at once still showed up a quarter
 * of a second late. A message changing state (sending, sent, failed) is rebound in place instead
 * of cross-faded, which flickered, and one doing so while it rises carries on rising from where it
 * is, even when its status line makes it taller.
 *
 * Only a lone message arriving at the end rises. The batch that fills the list when it opens, or
 * anything inserted further up, keeps the stock fade.
 */
class MessageItemAnimator : DefaultItemAnimator() {

    private val pendingRises = mutableListOf<RecyclerView.ViewHolder>()
    private val runningRises = mutableListOf<RecyclerView.ViewHolder>()

    /** Every add of the pass being collected, risen or not: a rise is for a lone message only */
    private var addsThisPass = 0

    init {
        supportsChangeAnimations = false
        moveDuration = RISE_DURATION
    }

    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        addsThisPass++
        val side = arrivingSide(holder) ?: return super.animateAdd(holder)

        endAnimation(holder)
        val view = holder.itemView
        view.pivotX = if (side == Side.END) view.width.toFloat() else 0f
        view.pivotY = view.height.toFloat()
        view.translationY = minOf(view.height, MAX_RISE_DP.dpToPx(view.context)).toFloat()
        view.scaleX = START_SCALE
        view.scaleY = START_SCALE
        view.alpha = 0f
        pendingRises += holder
        return true
    }

    override fun runPendingAnimations() {
        val lone = (addsThisPass == 1)
        addsThisPass = 0

        val rises = pendingRises.toList()
        pendingRises.clear()
        rises.forEach { holder ->
            if (lone) {
                rise(holder)
            } else {
                // Part of a batch after all: back to where the stock animator expects to find it
                reset(holder.itemView)
                super.animateAdd(holder)
            }
        }

        super.runPendingAnimations()
    }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder, newHolder: RecyclerView.ViewHolder?,
        fromX: Int, fromY: Int, toX: Int, toY: Int
    ): Boolean {
        // A message changing state as it rises keeps rising: the stock animator would end the rise
        // first, and the bubble would jump the rest of the way. Laid out again, taller once its
        // status shows, it is held where it was on screen and starts over from there, as the
        // messages above it start their move over at the same moment.
        if (oldHolder === newHolder && oldHolder in runningRises) {
            val view = oldHolder.itemView
            view.animate().setListener(null).cancel()
            view.translationY += (fromY - toY)
            view.pivotY = view.height.toFloat()
            animateRise(oldHolder)
            dispatchMoveFinished(oldHolder)
            return false
        }
        return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)
    }

    private fun rise(holder: RecyclerView.ViewHolder) {
        runningRises += holder
        dispatchAddStarting(holder)
        animateRise(holder)
    }

    private fun animateRise(holder: RecyclerView.ViewHolder) {
        val view = holder.itemView
        view.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(RISE_DURATION)
            .setInterpolator(MOVE_INTERPOLATOR)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animator: Animator) = reset(view)

                override fun onAnimationEnd(animator: Animator) {
                    view.animate().setListener(null)
                    runningRises -= holder
                    dispatchAddFinished(holder)
                    if (!isRunning) dispatchAnimationsFinished()
                }
            })
            .start()
    }

    /**
     * Which side the bubble grows from, or null when [holder] isn't a message arriving at the end
     * of the conversation
     */
    private fun arrivingSide(holder: RecyclerView.ViewHolder): Side? {
        val adapter = holder.bindingAdapter as? MessagesAdapter ?: return null
        val position = holder.bindingAdapterPosition
        if (position != adapter.itemCount - 1) return null
        val message = adapter.getItemOrNull(position) ?: return null
        return if (message.isMe()) Side.END else Side.START
    }

    override fun endAnimation(item: RecyclerView.ViewHolder) {
        if (pendingRises.remove(item)) {
            reset(item.itemView)
            dispatchAddFinished(item)
        }
        if (item in runningRises) item.itemView.animate().cancel()
        super.endAnimation(item)
    }

    override fun endAnimations() {
        pendingRises.toList().forEach { holder ->
            reset(holder.itemView)
            dispatchAddFinished(holder)
        }
        pendingRises.clear()
        runningRises.toList().forEach { holder -> holder.itemView.animate().cancel() }
        super.endAnimations()
    }

    override fun isRunning(): Boolean =
        pendingRises.isNotEmpty() || runningRises.isNotEmpty() || super.isRunning()

    private fun reset(view: View) {
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
    }

    private enum class Side { START, END }

    companion object {
        private const val RISE_DURATION = 280L
        private const val START_SCALE = 0.9f
        /** How far a bubble travels at most, so a long message doesn't sweep half the screen */
        private const val MAX_RISE_DP = 72

        /**
         * The curve the stock animator moves the messages above with, over the same duration, so
         * the new bubble and the conversation it pushes up travel in step instead of overlapping
         */
        private val MOVE_INTERPOLATOR = AccelerateDecelerateInterpolator()
    }

}
