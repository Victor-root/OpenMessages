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
package io.openmessages.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.klinker.android.send_message.MmsSentReceiver.EXTRA_FILE_PATH
import dagger.android.AndroidInjection
import io.openmessages.interactor.MarkFailed
import io.openmessages.interactor.MarkSent
import io.openmessages.repository.MessageRepository
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class MessageSentReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_OPENMESSAGES_MESSAGE_ID = "messageId"
        const val EXTRA_IS_NOTIFY = "isNotify"
    }

    @Inject lateinit var markSent: MarkSent
    @Inject lateinit var markFailed: MarkFailed
    @Inject lateinit var messageRepo: MessageRepository

    override fun onReceive(context: Context?, intent: Intent) {
        AndroidInjection.inject(this, context)

        Timber.v("received")

        // if have EXTRA_FILE_PATH then need to delete mms cache file
        val mmsFilePath = intent.extras?.getString(EXTRA_FILE_PATH)
        mmsFilePath?.let { filePath ->
            Timber.v("delete mms temp file $filePath")
            File(filePath).delete()
        }

        if (intent.extras?.getInt(EXTRA_IS_NOTIFY, -1) != -1) {
            Timber.v("notify message sent resultcode $resultCode")
            return
        }

        intent.extras?.getLong(EXTRA_OPENMESSAGES_MESSAGE_ID)?.takeIf { it > 0 }
            ?.let { messageId ->
                // The bare number this recorded says neither what went wrong nor whether the
                // carrier was even reached, and a send that failed is what this line gets read
                // for.
                val httpStatus = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, 0)
                val httpStatusText = if (httpStatus != 0) ", http status $httpStatus" else ""
                Timber.v("resultcode: ${describeResult(resultCode, mmsFilePath != null)}" +
                        httpStatusText)

                val pendingResult = goAsync()

                when (pendingResult.resultCode) {
                    Activity.RESULT_OK ->
                        markSent.execute(messageId) { pendingResult.finish() }

                    else ->
                        markFailed.execute(
                            MarkFailed.Params(messageId, pendingResult.resultCode)
                        ) {
                            pendingResult.finish()
                        }
                }
            } ?: let { Timber.e("couldn't get message id") }
    }

    /**
     * Names the code the platform answered a send with. SMS and MMS report through two separate
     * sets of constants that happen to share the same small integers, so which set to read a code
     * against is decided by whether the send carried an MMS file.
     */
    private fun describeResult(resultCode: Int, isMms: Boolean): String {
        val name = when {
            resultCode == Activity.RESULT_OK -> "RESULT_OK"

            isMms -> when (resultCode) {
                SmsManager.MMS_ERROR_UNSPECIFIED -> "MMS_ERROR_UNSPECIFIED"
                SmsManager.MMS_ERROR_INVALID_APN -> "MMS_ERROR_INVALID_APN"
                SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS -> "MMS_ERROR_UNABLE_CONNECT_MMS"
                SmsManager.MMS_ERROR_HTTP_FAILURE -> "MMS_ERROR_HTTP_FAILURE"
                SmsManager.MMS_ERROR_IO_ERROR -> "MMS_ERROR_IO_ERROR"
                SmsManager.MMS_ERROR_RETRY -> "MMS_ERROR_RETRY"
                SmsManager.MMS_ERROR_CONFIGURATION_ERROR -> "MMS_ERROR_CONFIGURATION_ERROR"
                SmsManager.MMS_ERROR_NO_DATA_NETWORK -> "MMS_ERROR_NO_DATA_NETWORK"
                // Spelled out by value rather than by constant: these postdate this project's
                // minimum Android version, so naming them cannot depend on the SDK the build
                // happens to compile against. The values are fixed platform API.
                9 -> "MMS_ERROR_INVALID_SUBSCRIPTION_ID"
                10 -> "MMS_ERROR_INACTIVE_SUBSCRIPTION"
                11 -> "MMS_ERROR_DATA_DISABLED"
                12 -> "MMS_ERROR_MMS_DISABLED_BY_CARRIER"
                else -> "unrecognised MMS error"
            }

            else -> when (resultCode) {
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "RESULT_ERROR_GENERIC_FAILURE"
                SmsManager.RESULT_ERROR_RADIO_OFF -> "RESULT_ERROR_RADIO_OFF"
                SmsManager.RESULT_ERROR_NULL_PDU -> "RESULT_ERROR_NULL_PDU"
                SmsManager.RESULT_ERROR_NO_SERVICE -> "RESULT_ERROR_NO_SERVICE"
                SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "RESULT_ERROR_LIMIT_EXCEEDED"
                else -> "unrecognised SMS error"
            }
        }

        return "$name ($resultCode)"
    }

}