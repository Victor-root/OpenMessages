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
package io.openmessages.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import io.openmessages.data.BuildConfig
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
object VideoUtils {

    private const val AUDIO_BITRATE = 32_000

    /** Below this a video is no longer worth watching, so one that would need less is refused */
    private const val MIN_VIDEO_BITRATE = 48_000

    /** Encoders only roughly keep to the bitrate asked of them, so a miss is corrected this often */
    private const val MAX_ATTEMPTS = 3

    private const val TIMEOUT_MINUTES = 3L

    /**
     * The video re-encoded as H.264 and AAC in MP4, weighing no more than [maxBytes], or null when
     * it can't: unreadable, or too long to fit at a quality still worth watching
     */
    fun getScaledVideo(context: Context, uri: Uri, maxBytes: Long): ByteArray? {
        val retriever = MediaMetadataRetriever()
        val durationMs: Long
        val sourceShortSide: Int
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0 } ?: return null
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: return null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: return null
            sourceShortSide = minOf(width, height)
        } catch (e: RuntimeException) {
            Timber.w(e, "Can't read the video $uri")
            return null
        } finally {
            retriever.release()
        }

        // What the file may carry each second, less a share for what the MP4 container adds
        var totalBitrate = (maxBytes * 8 * 1000 / durationMs * 0.9).toInt()
        val output = File(context.cacheDir, "video-${UUID.randomUUID()}.mp4")
        try {
            repeat(MAX_ATTEMPTS) { attempt ->
                val videoBitrate = totalBitrate - AUDIO_BITRATE
                if (videoBitrate < MIN_VIDEO_BITRATE) {
                    Timber.w("A ${durationMs / 1000}s video can't fit in ${maxBytes / 1024}Kb")
                    return null
                }

                val shortSide = minOf(shortSideFor(videoBitrate), sourceShortSide)
                if (!transcode(context, uri, output, videoBitrate, shortSide)) return null

                val size = output.length()
                if (BuildConfig.DEBUG) Timber.v("Video attempt ${attempt + 1}: ${size / 1024}/" +
                        "${maxBytes / 1024}Kb at ${videoBitrate / 1000}kbps, ${shortSide}p")
                if (size <= maxBytes) return output.readBytes()

                // How far off it landed is how far the next attempt has to move
                totalBitrate = (totalBitrate * maxBytes.toDouble() / size * 0.9).toInt()
            }
            return null
        } finally {
            output.delete()
        }
    }

    /**
     * The picture size a bitrate can still keep sharp
     */
    private fun shortSideFor(videoBitrate: Int): Int = when {
        videoBitrate < 150_000 -> 144
        videoBitrate < 400_000 -> 240
        videoBitrate < 1_000_000 -> 360
        else -> 480
    }

    /**
     * Encodes the video into [output], waiting until it is done. Transformer works on a looper
     * thread of its own, so it is given one for the length of the encoding.
     */
    private fun transcode(context: Context, uri: Uri, output: File, videoBitrate: Int, shortSide: Int): Boolean {
        val thread = HandlerThread("VideoUtils").apply { start() }
        val handler = Handler(thread.looper)
        val done = CountDownLatch(1)
        var succeeded = false
        var transformer: Transformer? = null

        handler.post {
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(videoBitrate).build())
                .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(AUDIO_BITRATE).build())
                .setEnableFallback(true)
                .build()
            val video = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(Effects(listOf(), listOf(Presentation.createForShortSide(shortSide))))
                .build()

            transformer = Transformer.Builder(context)
                .setLooper(thread.looper)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        succeeded = true
                        done.countDown()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Timber.w(exportException, "Can't encode the video $uri")
                        done.countDown()
                    }
                })
                .build()
                .apply { start(video, output.path) }
        }

        try {
            if (!done.await(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                Timber.w("Encoding the video $uri took too long")
                handler.post { transformer?.cancel() }
                return false
            }
            return succeeded
        } finally {
            thread.quitSafely()
        }
    }

}
