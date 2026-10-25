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
package io.openmessages.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.openmessages.model.BackupCategory
import io.openmessages.repository.BackupRepository
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Runs a full backup automatically in the background at the frequency the user picked. It always
 * backs up every category, mirroring a manual "back up everything", and writes to the same
 * destination as a manual backup (Documents/OpenMessages by default, or the user's custom folder).
 */
class AutoBackupWorker(appContext: Context, workerParams: WorkerParameters)
: Worker(appContext, workerParams) {

    companion object {
        private val WORKER_TAG: String = AutoBackupWorker::class.java.simpleName

        /** Schedules (or reschedules) the automatic backup every [frequencyDays], or cancels it at 0. */
        fun register(context: Context, frequencyDays: Int) {
            if (frequencyDays <= 0) {
                cancel(context)
                return
            }

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORKER_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(AutoBackupWorker::class.java, frequencyDays.toLong(), TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            // good citizens don't back up on a low battery
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .addTag(WORKER_TAG)
                    .build()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORKER_TAG)
        }
    }

    @Inject lateinit var backupRepo: BackupRepository

    override fun doWork(): Result {
        return try {
            // A failure here is usually transient (no space left, the backup folder temporarily
            // unavailable), so ask to be retried rather than recording a success that never happened.
            if (backupRepo.performBackup(BackupCategory.values().toSet())) {
                Result.success()
            } else {
                Timber.w("Automatic backup did not complete")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.w(e)
            Result.failure()
        }
    }

}
