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
package io.openmessages.feature.backup

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.android.AndroidInjection
import io.openmessages.common.util.extensions.getLabel
import io.openmessages.manager.NotificationManager
import io.openmessages.model.BackupCategory
import io.openmessages.repository.BackupRepository
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class RestoreBackupService : Service() {

    companion object {
        private const val NOTIFICATION_ID = -1

        private const val ACTION_START = "ACTION_START"
        private const val ACTION_STOP = "ACTION_STOP"
        private const val EXTRA_FOLDER_URI = "EXTRA_FOLDER_URI"
        private const val EXTRA_FOLDER_NAME = "EXTRA_FOLDER_NAME"
        private const val EXTRA_CATEGORIES = "EXTRA_CATEGORIES"

        fun start(context: Context, folder: Uri, folderName: String, categories: Set<BackupCategory>) {
            val intent = Intent(context, RestoreBackupService::class.java)
                .setAction("${context.packageName}.$ACTION_START")
                .putExtra("${context.packageName}.$EXTRA_FOLDER_URI", folder.toString())
                .putExtra("${context.packageName}.$EXTRA_FOLDER_NAME", folderName)
                .putExtra("${context.packageName}.$EXTRA_CATEGORIES", categories.map { it.name }.toTypedArray())

            ContextCompat.startForegroundService(context, intent)
        }
    }

    @Inject lateinit var backupRepo: BackupRepository
    @Inject lateinit var notificationManager: NotificationManager

    private val notification by lazy { notificationManager.getNotificationForBackup() }

    override fun onCreate() = AndroidInjection.inject(this)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent.action) {
            "${baseContext.packageName}.${ACTION_START}" -> start(intent)
            "${baseContext.packageName}.${ACTION_STOP}" -> stop()
        }

        return START_STICKY
    }

    @SuppressLint("CheckResult")
    private fun start(intent: Intent) {
        val notificationManager = NotificationManagerCompat.from(this)

        startForeground(NOTIFICATION_ID, notification.build())

        backupRepo.getRestoreProgress()
            .sample(200, TimeUnit.MILLISECONDS, true)
            .subscribeOn(Schedulers.io())
            .subscribe { progress ->
                when (progress) {
                    is BackupRepository.Progress.Idle -> stop()

                    is BackupRepository.Progress.Running -> notification
                        .setProgress(progress.max, progress.count, progress.indeterminate)
                        .setContentText(progress.getLabel(this))
                        .let { notificationManager.notify(NOTIFICATION_ID, it.build()) }

                    else -> notification
                        .setProgress(0, 0, progress.indeterminate)
                        .setContentText(progress.getLabel(this))
                        .let { notificationManager.notify(NOTIFICATION_ID, it.build()) }
                }
            }

        // Start the restore
        Observable.just(intent)
            .map { i ->
                val folder = Uri.parse(i.getStringExtra("${baseContext.packageName}.$EXTRA_FOLDER_URI"))
                val folderName = i.getStringExtra("${baseContext.packageName}.$EXTRA_FOLDER_NAME").orEmpty()
                val categories = (i.getStringArrayExtra("${baseContext.packageName}.$EXTRA_CATEGORIES") ?: emptyArray())
                    .mapNotNull { name -> BackupCategory.values().firstOrNull { it.name == name } }
                    .toSet()
                Triple(folder, folderName, categories)
            }
            .subscribeOn(Schedulers.io())
            .subscribe({ (folder, folderName, categories) ->
                backupRepo.performRestore(folder, folderName, categories)
            }, Timber::w)
    }

    @Suppress("DEPRECATION")
    private fun stop() {
        stopForeground(true)
        stopSelf()
    }

}