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
import io.openmessages.model.MessageContentFilter
import io.openmessages.model.MessageContentFilterData
import io.reactivex.rxjava3.core.Observable
import javax.inject.Inject

class MessageContentFilterRepositoryImpl @Inject constructor(
    private val db: AppDatabase
) : MessageContentFilterRepository {

    private val dao = db.messageContentFilterDao()

    override fun createFilter(data: MessageContentFilterData) {
        db.runInTransaction {
            val maxId = dao.getMaxId() ?: -1
            dao.insert(MessageContentFilter(maxId + 1, data.value, data.caseSensitive, data.isRegex, data.includeContacts))
        }
    }

    override fun getMessageContentFilters(): Observable<List<MessageContentFilter>> {
        return dao.observeAll().toObservable()
    }

    override fun getMessageContentFilter(id: Long): MessageContentFilter? {
        return dao.get(id)
    }

    override fun isBlocked(messageBody: String, address: String, contactsRepo: ContactRepository): Boolean {
        val isContact = contactsRepo.isContact(address)

        return dao.getAll().any { filter ->
            if (isContact && !filter.includeContacts) {
                false
            } else if (filter.isRegex) {
                Regex(filter.value).matches(messageBody)
            } else if (filter.caseSensitive) {
                val regexp = "[\\s\\S]*\\b" + Regex.escape(filter.value) + "\\b[\\s\\S]*"
                Regex(regexp).matches(messageBody)
            } else {
                val regexp = "[\\s\\S]*\\b" + Regex.escape(filter.value.lowercase()) + "\\b[\\s\\S]*"
                Regex(regexp).matches(messageBody.lowercase())
            }
        }
    }

    override fun removeFilter(id: Long) {
        dao.delete(id)
    }

}
