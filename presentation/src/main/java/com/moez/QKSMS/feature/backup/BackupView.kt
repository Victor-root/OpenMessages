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

import android.net.Uri
import io.openmessages.common.base.QkViewContract
import io.openmessages.model.BackupCategory
import io.openmessages.model.BackupFolder
import io.openmessages.model.BackupItem
import io.reactivex.Observable

interface BackupView : QkViewContract<BackupState> {
    fun setBackupLocationClicks(): Observable<*>
    fun autoBackupClicks(): Observable<*>
    fun zipClicks(): Observable<*>
    fun manageBackupsClicks(): Observable<*>
    fun restoreClicks(): Observable<*>

    /** Emits a backup to rename plus its new name. */
    fun renameBackupSelected(): Observable<Pair<BackupItem, String>>

    /** Emits a backup the user confirmed to delete. */
    fun deleteBackupSelected(): Observable<BackupItem>

    fun backupClicks(): Observable<*>

    /** Emits the automatic-backup interval the user picked, in days (0 = off, or any custom count). */
    fun autoBackupFrequencySelected(): Observable<Int>

    fun selectedBackupErrorClicks(): Observable<*>

    fun exactAlarmGrantClicks(): Observable<*>
    fun exactAlarmSkipClicks(): Observable<*>

    /** Emits when the user returns from the exact-alarm settings opened via [openExactAlarmSettings]. */
    fun exactAlarmSettingsClosed(): Observable<*>

    fun stopRestoreClicks(): Observable<*>
    fun stopRestoreConfirmed(): Observable<*>
    fun stopRestoreCancel(): Observable<*>

    /** Emits the tree Uri the user picked as a custom backup destination folder. */
    fun documentTreeSelected(): Observable<Uri>

    /** Emits the tree Uri of the folder the user picked to restore from. */
    fun restoreFolderSelected(): Observable<Uri>

    /** Emits the Uri of the .zip file the user picked to restore from. */
    fun restoreZipSelected(): Observable<Uri>

    /** Emits the categories the user ticked in the backup dialog. */
    fun backupCategoriesSelected(): Observable<Set<BackupCategory>>

    /** Emits the folder plus the backup set the user picked when the folder holds several. */
    fun restoreSourceSelected(): Observable<Pair<Uri, BackupFolder>>

    /** Emits the folder, the chosen backup's sub-folder name, and the ticked categories. */
    fun restoreCategoriesSelected(): Observable<Triple<Uri, String, Set<BackupCategory>>>

    fun selectFolder(initialUri: Uri)
    fun selectRestoreFolder(initialUri: Uri)
    fun selectRestoreZip()

    /** Lets the user choose whether to restore from a folder or a .zip file. */
    fun showRestoreSourceChoice(initialUri: Uri)

    fun showAutoBackupFrequencyPicker(current: Int)
    fun openExactAlarmSettings()

    /** Shows the backup manager: the list of existing backups with rename/delete actions. */
    fun showBackupManager(backups: List<BackupItem>)

    fun showBackupCategoryPicker()
    fun showRestoreSourcePicker(folder: Uri, backups: List<BackupFolder>, labels: List<String>)
    fun showRestoreCategoryPicker(folder: Uri, folderName: String, available: Set<BackupCategory>, dateLabel: String)
}
