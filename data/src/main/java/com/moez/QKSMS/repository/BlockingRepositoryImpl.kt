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

import io.openmessages.database.AppDatabase
import io.openmessages.model.BlockedNumber
import io.openmessages.util.PhoneNumberUtils
import io.reactivex.Observable
import javax.inject.Inject

class BlockingRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val phoneNumberUtils: PhoneNumberUtils
) : BlockingRepository {

    private val dao = db.blockedNumberDao()

    override fun blockNumber(vararg addresses: String) {
        db.runInTransaction {
            val blockedNumbers = dao.getAll()
            val newAddresses = addresses.filter { address ->
                blockedNumbers.none { number -> phoneNumberUtils.compare(number.address, address) }
            }

            val maxId = dao.getMaxId() ?: -1

            dao.insert(newAddresses.mapIndexed { index, address ->
                BlockedNumber(maxId + 1 + index, address)
            })
        }
    }

    override fun getBlockedNumbers(): Observable<List<BlockedNumber>> {
        return dao.observeAll().toObservable()
    }

    override fun getBlockedNumber(id: Long): BlockedNumber? {
        return dao.get(id)
    }

    override fun isBlocked(address: String): Boolean {
        return dao.getAll().any { number -> phoneNumberUtils.compare(number.address, address) }
    }

    override fun unblockNumber(id: Long) {
        dao.delete(listOf(id))
    }

    override fun unblockNumbers(vararg addresses: String) {
        db.runInTransaction {
            val ids = dao.getAll()
                    .filter { number ->
                        addresses.any { address -> phoneNumberUtils.compare(number.address, address) }
                    }
                    .map { number -> number.id }

            dao.delete(ids)
        }
    }

}
