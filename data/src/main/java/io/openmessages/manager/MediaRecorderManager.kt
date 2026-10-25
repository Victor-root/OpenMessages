/*
 * Copyright (C) 2025
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
package io.openmessages.manager

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.net.toFile
import io.openmessages.util.FileUtils
import java.util.UUID

object MediaRecorderManager {

    enum class RecordingState {
        Initial,
        DataSourceConfigured,
        Prepared,
        Recording,
        Error
    }

    const val AUDIO_FILE_PREFIX = "recorded-"
    const val AUDIO_FILE_SUFFIX = ".3ga"

    private var recordingState: RecordingState = RecordingState.Initial

    var uri: Uri = Uri.EMPTY
        private set

    private var recorder: MediaRecorder? = null

    fun stopRecording(): Uri {
        return try {
            if (recordingState == RecordingState.Recording)
                recorder?.stop()

            recorder?.reset()
            recordingState = RecordingState.Initial

            uri
        }
        catch (e: Exception) {
            Uri.EMPTY
        }
    }

    fun startRecording(context: Context, preferredAudioDevice: AudioDeviceInfo? = null): Uri {
        return try {
            val (newUri, e) = FileUtils.create(
                FileUtils.Location.Cache,
                context,
                "$AUDIO_FILE_PREFIX${UUID.randomUUID()}$AUDIO_FILE_SUFFIX",
                ""
            )
            if (e is Exception)
                throw e

            uri = newUri

            // ensure stopped before using again
            stopRecording()

            val recorder = recorder ?: newRecorder(context).also { recorder = it }

            // configure
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                recorder.preferredDevice = preferredAudioDevice

            recordingState = RecordingState.DataSourceConfigured

            recorder.setOutputFile(uri.toFile().path)

            recorder.prepare()
            recordingState = RecordingState.Prepared

            recorder.start()
            recordingState = RecordingState.Recording

            uri
        }
        catch (e: Exception) {
            Uri.EMPTY
        }
    }

    private fun newRecorder(context: Context): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context.applicationContext)
        } else {
            // The only constructor before Android 12
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

}
