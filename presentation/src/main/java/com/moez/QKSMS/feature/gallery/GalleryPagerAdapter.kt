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
package io.openmessages.feature.gallery

import android.content.Context
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.mms.ContentType
import io.openmessages.R
import io.openmessages.common.base.QkAdapter
import io.openmessages.common.base.QkViewHolder
import io.openmessages.databinding.GalleryImagePageBinding
import io.openmessages.databinding.GalleryVideoPageBinding
import io.openmessages.extensions.isImage
import io.openmessages.extensions.isVideo
import io.openmessages.model.MmsPart
import io.openmessages.util.GlideApp
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.util.*
import javax.inject.Inject

class GalleryPagerAdapter @Inject constructor(private val context: Context) : QkAdapter<MmsPart, QkViewHolder>() {

    companion object {
        private const val VIEW_TYPE_INVALID = 0
        private const val VIEW_TYPE_IMAGE = 1
        private const val VIEW_TYPE_VIDEO = 2
    }

    val clicks: Subject<View> = PublishSubject.create()

    private val contentResolver = context.contentResolver
    private val exoPlayers = Collections.newSetFromMap(WeakHashMap<ExoPlayer?, Boolean>())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return QkViewHolder(when (viewType) {
            VIEW_TYPE_IMAGE -> {
                val image = GalleryImagePageBinding.inflate(inflater, parent, false).image
                // A double tap goes straight between the fitted picture and the largest zoom, rather
                // than through the middle step PhotoView takes by default. Handling double taps
                // replaces PhotoView's own handling of single taps too, so the tap is passed on here.
                image.setOnDoubleTapListener(object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        clicks.onNext(image)
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val scale = when {
                            image.scale > image.minimumScale -> image.minimumScale
                            else -> image.maximumScale
                        }
                        image.setScale(scale, e.x, e.y, true)
                        return true
                    }
                })
                image
            }

            VIEW_TYPE_VIDEO -> inflater.inflate(R.layout.gallery_video_page, parent, false)
                .apply { setOnClickListener(clicks::onNext) }

            else -> inflater.inflate(R.layout.gallery_invalid_page, parent, false)
                .apply { setOnClickListener(clicks::onNext) }
        })
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        val part = getItem(position)
        when (getItemViewType(position)) {
            VIEW_TYPE_IMAGE -> {
                val binding = GalleryImagePageBinding.bind(holder.itemView)
                // We need to explicitly request a gif from glide for animations to work
                when (part.getUri().let(contentResolver::getType)) {
                    ContentType.IMAGE_GIF -> GlideApp.with(context)
                            .asGif()
                            .load(part.getUri())
                            .into(binding.image)

                    else -> GlideApp.with(context)
                            .asBitmap()
                            .load(part.getUri())
                            .into(binding.image)
                }
            }

            VIEW_TYPE_VIDEO -> {
                val binding = GalleryVideoPageBinding.bind(holder.itemView)
                val exoPlayer = ExoPlayer.Builder(context).build()
                binding.video.player = exoPlayer
                exoPlayers.add(exoPlayer)

                exoPlayer.setMediaItem(MediaItem.fromUri(part.getUri()))
                exoPlayer.prepare()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val part = getItem(position)
        return when {
            part.isImage() -> VIEW_TYPE_IMAGE
            part.isVideo() -> VIEW_TYPE_VIDEO
            else -> VIEW_TYPE_INVALID
        }
    }

    fun destroy() {
        exoPlayers.forEach { exoPlayer -> exoPlayer?.release() }
    }

}
