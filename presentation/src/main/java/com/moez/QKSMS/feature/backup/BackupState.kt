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

import io.openmessages.repository.BackupRepository

data class BackupState(
    val backupProgress: BackupRepository.Progress = BackupRepository.Progress.Idle(),
    val restoreProgress: BackupRepository.Progress = BackupRepository.Progress.Idle(),

    val showSelectedBackupError: Boolean = false,
    val showStopRestoreDialog: Boolean = false,
    val showExactAlarmDialog: Boolean = false,

    val backupLocation: String = "",
    val autoBackupFrequency: Int = 0,
    val zipEnabled: Boolean = false,

    val upgraded: Boolean = true
)
