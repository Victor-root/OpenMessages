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
package io.openmessages.interactor

import io.openmessages.model.BackupCategory
import io.openmessages.repository.BackupRepository
import io.reactivex.Flowable
import javax.inject.Inject

class PerformBackup @Inject constructor(
    private val backupRepo: BackupRepository
) : Interactor<Set<BackupCategory>>() {

    override fun buildObservable(params: Set<BackupCategory>): Flowable<*> {
        return Flowable.just(params)
                .doOnNext { categories -> backupRepo.performBackup(categories) }
    }

}