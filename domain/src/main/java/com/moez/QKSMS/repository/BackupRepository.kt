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
package io.openmessages.repository

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.openmessages.model.BackupCategory
import io.openmessages.model.BackupFolder
import io.reactivex.Observable

interface BackupRepository {

    sealed class Progress(val running: Boolean = false, val indeterminate: Boolean = true) {
        class Idle : Progress()
        class Parsing : Progress(true)
        class Running(val max: Int, val count: Int) : Progress(true, false)
        class Saving : Progress(true)
        class Syncing : Progress(true)
        class Finished : Progress(true, false)
    }

    fun getDefaultBackupPath(): String

    fun getBackupDocumentTree(): DocumentFile?

    fun getBackupPathUriForPicker(): Uri

    /** Human-readable label of the current backup destination (custom folder name, or the default). */
    fun getBackupLocationLabel(): String

    fun persistBackupDirectory(directory: Uri)

    /** Writes a date-and-time named sub-folder with a manifest and one file per selected category. */
    fun performBackup(categories: Set<BackupCategory>)

    fun getBackupProgress(): Observable<Progress>

    /** Lists the backup sets found in [folder] (picked as a document tree), most recent first. */
    fun listBackups(folder: Uri): List<BackupFolder>

    /**
     * Extracts a picked .zip backup into a temporary folder and returns its Uri, which can then be
     * passed to [listBackups] and [performRestore] exactly like a normal folder. Returns null on failure.
     */
    fun importZip(zip: Uri): Uri?

    /** Restores the selected [categories] of the backup sub-folder [folderName] within [folder]. */
    fun performRestore(folder: Uri, folderName: String, categories: Set<BackupCategory>)

    fun getRestoreProgress(): Observable<Progress>

    fun stopRestore()

}
