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

import android.content.Context
import android.net.Uri
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.openmessages.R
import io.openmessages.common.Navigator
import io.openmessages.common.base.QkPresenter
import io.openmessages.common.util.DateFormatter
import io.openmessages.common.util.extensions.makeToast
import io.openmessages.interactor.PerformBackup
import io.openmessages.manager.BillingManager
import io.openmessages.manager.PermissionManager
import io.openmessages.model.BackupCategory
import io.openmessages.repository.BackupRepository
import io.openmessages.util.Preferences
import io.openmessages.worker.AutoBackupWorker
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BackupPresenter @Inject constructor(
    private val backupRepo: BackupRepository,
    private val billingManager: BillingManager,
    private val context: Context,
    private val dateFormatter: DateFormatter,
    private val navigator: Navigator,
    private val performBackup: PerformBackup,
    private val permissionManager: PermissionManager,
    private val prefs: Preferences
) : QkPresenter<BackupView, BackupState>(BackupState()) {

    /** Restore parameters held while we ask the user to grant the exact-alarm permission. */
    private var pendingRestore: Triple<Uri, String, Set<BackupCategory>>? = null

    init {
        disposables += backupRepo.getBackupProgress()
                .sample(16, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .subscribe { progress -> newState { copy(backupProgress = progress) } }

        disposables += backupRepo.getRestoreProgress()
                .sample(16, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .subscribe { progress -> newState { copy(restoreProgress = progress) } }

        disposables += billingManager.upgradeStatus
                .subscribe { upgraded -> newState { copy(upgraded = upgraded) } }

        disposables += prefs.backupFrequency.asObservable()
                .subscribe { frequency -> newState { copy(autoBackupFrequency = frequency) } }

        newState { copy(backupLocation = backupRepo.getBackupLocationLabel()) }
    }

    override fun bindIntents(view: BackupView) {
        super.bindIntents(view)

        // Optional: let the user pick a custom destination folder instead of the default
        view.setBackupLocationClicks()
                .observeOn(AndroidSchedulers.mainThread())
                .autoDisposable(view.scope())
                .subscribe { view.selectFolder(backupRepo.getBackupPathUriForPicker()) }

        // Automatic backup: pick how often a full backup should run on its own
        view.autoBackupClicks()
                .autoDisposable(view.scope())
                .subscribe { view.showAutoBackupFrequencyPicker(prefs.backupFrequency.get()) }

        view.autoBackupFrequencySelected()
                .autoDisposable(view.scope())
                .subscribe { frequency ->
                    prefs.backupFrequency.set(frequency)
                    AutoBackupWorker.register(context, frequency)
                }

        // Backup writes automatically (no folder to pick), so go straight to the category picker
        view.backupClicks()
                .withLatestFrom(billingManager.upgradeStatus) { _, upgraded -> upgraded }
                .autoDisposable(view.scope())
                .subscribe { upgraded ->
                    when {
                        !upgraded -> navigator.showQksmsPlusActivity("backup_fab")
                        else -> view.showBackupCategoryPicker()
                    }
                }

        view.backupCategoriesSelected()
                .autoDisposable(view.scope())
                .subscribe { categories -> performBackup.execute(categories) }

        // Restore: gate the same way, then pick the backup folder to restore from
        view.restoreClicks()
                .withLatestFrom(
                        backupRepo.getBackupProgress(),
                        backupRepo.getRestoreProgress(),
                        billingManager.upgradeStatus)
                { _, backupProgress, restoreProgress, upgraded ->
                    when {
                        !upgraded -> context.makeToast(R.string.backup_restore_error_plus)
                        backupProgress.running -> context.makeToast(R.string.backup_restore_error_backup)
                        restoreProgress.running -> context.makeToast(R.string.backup_restore_error_restore)
                        else -> view.selectRestoreFolder(backupRepo.getBackupPathUriForPicker())
                    }
                }
                .autoDisposable(view.scope())
                .subscribe()

        // A folder was picked: list the backup sets it holds off the main thread
        view.restoreFolderSelected()
                .observeOn(Schedulers.io())
                .map { uri -> uri to backupRepo.listBackups(uri) }
                .observeOn(AndroidSchedulers.mainThread())
                .autoDisposable(view.scope())
                .subscribe({ (uri, backups) ->
                    when {
                        backups.isEmpty() -> newState { copy(showSelectedBackupError = true) }
                        backups.size == 1 -> backups.first().let { backup ->
                            view.showRestoreCategoryPicker(uri, backup.folderName, backup.categories,
                                    dateFormatter.getDetailedTimestamp(backup.date))
                        }
                        else -> view.showRestoreSourcePicker(uri, backups,
                                backups.map { dateFormatter.getDetailedTimestamp(it.date) })
                    }
                }, { newState { copy(showSelectedBackupError = true) } })

        // Several backups in the folder: the user picked which one to restore from
        view.restoreSourceSelected()
                .autoDisposable(view.scope())
                .subscribe { (uri, backup) ->
                    view.showRestoreCategoryPicker(uri, backup.folderName, backup.categories,
                            dateFormatter.getDetailedTimestamp(backup.date))
                }

        view.restoreCategoriesSelected()
                .autoDisposable(view.scope())
                .subscribe { (folder, folderName, categories) ->
                    // Scheduled messages need the exact-alarm permission to fire on time; ask for it first
                    if (BackupCategory.SCHEDULED in categories && !permissionManager.hasExactAlarms()) {
                        pendingRestore = Triple(folder, folderName, categories)
                        newState { copy(showExactAlarmDialog = true) }
                    } else {
                        RestoreBackupService.start(context, folder, folderName, categories)
                    }
                }

        // "Grant": start the restore and open the exact-alarm settings (its alarms become exact on the
        // next re-arm once granted). "Skip": just run the restore now with the inexact-alarm fallback.
        view.exactAlarmGrantClicks()
                .doOnNext { newState { copy(showExactAlarmDialog = false) } }
                .autoDisposable(view.scope())
                .subscribe {
                    startPendingRestore()
                    navigator.showExactAlarmsSettings()
                }

        view.exactAlarmSkipClicks()
                .doOnNext { newState { copy(showExactAlarmDialog = false) } }
                .autoDisposable(view.scope())
                .subscribe { startPendingRestore() }

        view.selectedBackupErrorClicks()
                .autoDisposable(view.scope())
                .subscribe { newState { copy(showSelectedBackupError = false) } }

        view.stopRestoreClicks()
                .autoDisposable(view.scope())
                .subscribe { newState { copy(showStopRestoreDialog = true) } }

        view.stopRestoreConfirmed()
                .doOnNext { newState { copy(showStopRestoreDialog = false) } }
                .autoDisposable(view.scope())
                .subscribe { backupRepo.stopRestore() }

        view.stopRestoreCancel()
                .autoDisposable(view.scope())
                .subscribe { newState { copy(showStopRestoreDialog = false) } }

        // The user picked a custom destination folder: persist it and refresh the shown location
        view.documentTreeSelected()
                .autoDisposable(view.scope())
                .subscribe { uri ->
                    backupRepo.persistBackupDirectory(uri)
                    newState { copy(backupLocation = backupRepo.getBackupLocationLabel()) }
                }
    }

    /** Kicks off the restore held back while the exact-alarm dialog was showing. */
    private fun startPendingRestore() {
        pendingRestore?.let { (folder, folderName, categories) ->
            RestoreBackupService.start(context, folder, folderName, categories)
        }
        pendingRestore = null
    }

}
