/*
 * Copyright (C) 2026 OpenMessages contributors
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

import io.openmessages.extensions.anyOf
import io.openmessages.model.AllowedNumber
import io.openmessages.util.PhoneNumberUtils
import io.realm.Realm
import io.realm.RealmResults
import javax.inject.Inject

class AllowlistRepositoryImpl @Inject constructor(
    private val phoneNumberUtils: PhoneNumberUtils
) : AllowlistRepository {

    override fun allowNumber(vararg addresses: String) {
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            val allowedNumbers = realm.where(AllowedNumber::class.java).findAll()
            val newAddresses = addresses.filter { address ->
                allowedNumbers.none { number -> phoneNumberUtils.compare(number.address, address) }
            }

            val maxId = realm.where(AllowedNumber::class.java)
                    .max("id")?.toLong() ?: -1

            realm.executeTransaction {
                realm.insert(newAddresses.mapIndexed { index, address ->
                    AllowedNumber(maxId + 1 + index, address)
                })
            }
        }
    }

    override fun getAllowedNumbers(): RealmResults<AllowedNumber> {
        return Realm.getDefaultInstance()
                .where(AllowedNumber::class.java)
                .findAllAsync()
    }

    override fun getAllowedNumber(id: Long): AllowedNumber? {
        return Realm.getDefaultInstance()
                .where(AllowedNumber::class.java)
                .equalTo("id", id)
                .findFirst()
    }

    override fun isAllowed(address: String): Boolean {
        return Realm.getDefaultInstance().use { realm ->
            realm.where(AllowedNumber::class.java)
                    .findAll()
                    .any { number -> phoneNumberUtils.compare(number.address, address) }
        }
    }

    override fun removeNumber(id: Long) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(AllowedNumber::class.java)
                        .equalTo("id", id)
                        .findAll()
                        .deleteAllFromRealm()
            }
        }
    }

    override fun removeNumbers(vararg addresses: String) {
        Realm.getDefaultInstance().use { realm ->
            val ids = realm.where(AllowedNumber::class.java)
                    .findAll()
                    .filter { number ->
                        addresses.any { address -> phoneNumberUtils.compare(number.address, address) }
                    }
                    .map { number -> number.id }
                    .toLongArray()

            realm.executeTransaction {
                realm.where(AllowedNumber::class.java)
                        .anyOf("id", ids)
                        .findAll()
                        .deleteAllFromRealm()
            }
        }
    }

}
