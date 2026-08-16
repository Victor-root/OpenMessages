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
package io.openmessages.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import java.io.ByteArrayOutputStream

object ImageUtils {

    fun getScaledGif(context: Context, uri: Uri, maxWidth: Int, maxHeight: Int, quality: Int = 90): ByteArray {
        val gif = GlideApp
                .with(context)
                .asGif()
                .load(uri)
                .centerInside()
                .encodeQuality(quality)
                .submit(maxWidth, maxHeight)
                .get()

        val outputStream = ByteArrayOutputStream()
        GifEncoder(context, GlideApp.get(context).bitmapPool).encodeTransformedToStream(gif, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Scales the image at [uri] to fit within [maxWidth] by [maxHeight] and returns it as JPEG
     * bytes encoded at [quality].
     *
     * The encoding is done here rather than left to Glide. Asking Glide for bytes hands the bitmap
     * to a converter that was built with a quality of its own and pays no attention to the one the
     * request carries, so whatever was asked for, every image came out at quality 100. That is
     * several times the weight of a quality the eye cannot tell apart, and against a message size
     * limit that weight is paid for in pixels: the picture ends up a thumbnail so that detail
     * nobody can see may be kept.
     */
    fun getScaledImage(context: Context, uri: Uri, maxWidth: Int, maxHeight: Int, quality: Int = 90): ByteArray {
        val target = GlideApp
            .with(context)
            .asBitmap()
            .load(uri)
            .apply(
                RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
            )
            .centerInside()
            .submit(maxWidth, maxHeight)

        try {
            val outputStream = ByteArrayOutputStream()
            target.get().compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            return outputStream.toByteArray()
        } finally {
            // Hands the bitmap back, which is what Glide did for itself when it was the one
            // encoding. These run to full camera resolution, so holding them until the collector
            // notices is not free.
            GlideApp.with(context).clear(target)
        }
    }

}