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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.GET_DEVICES_INPUTS
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.openmessages.data.BuildConfig
import timber.log.Timber


// this class is, by design, as simplistic it can be to support easy and fast connection
// for recording from first bluetooth mic found, if any

class BluetoothMicManager(
    private val context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onNoDeviceFound()
        fun onDeviceFound(device: AudioDeviceInfo?)
        fun onConnecting(device: AudioDeviceInfo?)
        fun onConnected(device: AudioDeviceInfo?)
        fun onDisconnected(device: AudioDeviceInfo?)
    }

    /** How the audio link to the headset is opened and followed */
    private interface Link {
        /** Whether a headset can be used for anything but a call */
        fun isAvailable(): Boolean
        fun open()
        fun close()
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val link: Link = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        CommunicationDeviceLink()
    } else {
        ScoLink()
    }

    var device: AudioDeviceInfo? = null
        private set

    private var closed: Boolean = false

    fun close() {
        if (closed)
            return
        closed = true

        device = null
        link.close()
    }

    // note: caller may get connected() callback before this method returns if this method is
    // called from a non-UI thread and the UI thread processes a sticky sco_audio_state_connected
    fun startBluetoothDevice() {
        device = null

        // get all audio input devices
        val allAudioInputDevices = audioManager.getDevices(GET_DEVICES_INPUTS)

// @TODO: below code can be used for BLE headset devices if and when one can be tested
//      : also need to register for BLE headset intent broadcasts and other things later
//        // firstly (highest priority), check for a connected BLE headset input device
//        for (btInputDevice in allAudioInputDevices) {
//            if (btInputDevice.type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
//                device = btInputDevice
//                break
//            }
//        }

        // if no ble device found, check if platform supports non-phone-call sco connections
        if ((device == null) && link.isAvailable()) {
            // find first SCO input device
            for (btInputDevice in allAudioInputDevices) {
                if (btInputDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    device = btInputDevice
                    break
                }
            }
        }

        // if no usable device found, callback to caller and return
        if (device == null) {
            callbacks.onNoDeviceFound()
            return
        }

        // callback that a sco capable bluetooth device was found
        callbacks.onDeviceFound(device)

        link.open()
    }

    /**
     * Before Android 12, the link is a SCO connection opened by hand, whose state comes by broadcast
     */
    @Suppress("DEPRECATION")
    private inner class ScoLink : BroadcastReceiver(), Link {

        private var hadFirstStickyIntent: Boolean = false

        override fun isAvailable() = audioManager.isBluetoothScoAvailableOffCall

        init {
            // register for bluetooth sco broadcast intents
            context.registerReceiver(this, IntentFilter().apply {
                addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            })
        }

        override fun open() {
            // start sco connection on bluetooth device (even if already started by other app)
            try {    // see https://stackoverflow.com/a/26929741 for try-catch reason
                audioManager.startBluetoothSco()
            }
            catch (e: Exception) { /* nothing */ }

            // if sco device is already on (maybe by another app?) callback now
            if (audioManager.isBluetoothScoOn)
                callbacks.onConnected(device)
        }

        override fun close() {
            audioManager.stopBluetoothSco()
            context.unregisterReceiver(this)
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (!hadFirstStickyIntent) {
                hadFirstStickyIntent = true
                return
            }

            // if sco connection state has changed
            val action = intent.action ?: return
            if (action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                when (intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)) {
                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> callbacks.onConnected(device)
                    AudioManager.SCO_AUDIO_STATE_CONNECTING -> callbacks.onConnecting(device)
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                        this@BluetoothMicManager.close()
                        callbacks.onDisconnected(device)
                    }
                }
            }
        }
    }

    /**
     * From Android 12, the headset is made the communication device, and followed as such
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private inner class CommunicationDeviceLink : Link, AudioManager.OnCommunicationDeviceChangedListener {

        private var listening = false
        private var connected = false

        private val headset: AudioDeviceInfo?
            get() = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

        override fun isAvailable() = headset != null

        override fun open() {
            val headset = headset
            audioManager.addOnCommunicationDeviceChangedListener(ContextCompat.getMainExecutor(context), this)
            listening = true
            val selected = headset != null && audioManager.setCommunicationDevice(headset)
            if (BuildConfig.DEBUG) Timber.d("bluetooth mic: headset $headset, selected $selected")

            when {
                // Recording goes on without it, through the phone's own microphone
                !selected -> {
                    this@BluetoothMicManager.close()
                    callbacks.onNoDeviceFound()
                }

                // Already the communication device (maybe by another app?), so nothing will change
                audioManager.communicationDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> connect()
                else -> callbacks.onConnecting(device)
            }
        }

        override fun close() {
            if (listening) {
                audioManager.removeOnCommunicationDeviceChangedListener(this)
                audioManager.clearCommunicationDevice()
                listening = false
            }
        }

        override fun onCommunicationDeviceChanged(device: AudioDeviceInfo?) {
            if (BuildConfig.DEBUG) Timber.d("bluetooth mic: communication device now ${device?.type}")
            if (device?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                connect()
            } else if (connected) {
                this@BluetoothMicManager.close()
                callbacks.onDisconnected(this@BluetoothMicManager.device)
            }
        }

        private fun connect() {
            if (connected) return
            connected = true
            callbacks.onConnected(device)
        }
    }
}
