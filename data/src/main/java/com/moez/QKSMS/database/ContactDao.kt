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
package io.openmessages.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.openmessages.model.Contact
import io.openmessages.model.ContactGroup
import io.openmessages.model.PhoneNumber
import io.reactivex.Flowable

@Dao
abstract class ContactDao {

    @Transaction
    @Query("SELECT * FROM contact ORDER BY name")
    abstract fun getAll(): List<ContactWithNumbers>

    @Transaction
    @Query("SELECT * FROM contact WHERE lookupKey = :lookupKey")
    abstract fun get(lookupKey: String): ContactWithNumbers?

    @Transaction
    @Query("SELECT * FROM contact ORDER BY name")
    abstract fun observeAll(): Flowable<List<ContactWithNumbers>>

    @Transaction
    @Query("SELECT * FROM contact WHERE starred = 1")
    abstract fun observeStarred(): Flowable<List<ContactWithNumbers>>

    @Transaction
    @Query("SELECT * FROM contact_group WHERE id IN (SELECT groupId FROM contact_group_member)")
    abstract fun observeGroupsWithContacts(): Flowable<List<ContactGroupWithContacts>>

    @Query("SELECT id FROM phone_number WHERE isDefault = 1")
    abstract fun getDefaultNumberIds(): List<Long>

    @Query("UPDATE phone_number SET isDefault = (id = :phoneNumberId) WHERE contactLookupKey = :lookupKey")
    abstract fun setDefaultNumber(lookupKey: String, phoneNumberId: Long)

    @Upsert
    protected abstract fun upsertContacts(contacts: List<Contact>)

    @Upsert
    protected abstract fun upsertNumbers(numbers: List<PhoneNumber>)

    @Upsert
    protected abstract fun upsertGroups(groups: List<ContactGroup>)

    @Upsert
    protected abstract fun upsertGroupMembers(members: List<ContactGroupMember>)

    @Query("DELETE FROM contact")
    protected abstract fun deleteContacts()

    @Query("DELETE FROM phone_number")
    protected abstract fun deleteNumbers()

    @Query("DELETE FROM contact_group")
    protected abstract fun deleteGroups()

    @Query("DELETE FROM contact_group_member")
    protected abstract fun deleteGroupMembers()

    /**
     * Saves the contacts along with their numbers, replacing whatever was stored for them
     */
    @Transaction
    open fun upsert(contacts: List<Contact>) {
        upsertContacts(contacts)
        upsertNumbers(contacts.flatMap { contact -> contact.numbers })
    }

    /**
     * Replaces every contact and contact group with these
     */
    @Transaction
    open fun replaceAll(contacts: List<Contact>, groups: List<ContactGroup>) {
        deleteAll()
        upsert(contacts)
        upsertGroups(groups)
        upsertGroupMembers(groups.flatMap { group ->
            group.contacts.map { contact -> ContactGroupMember(group.id, contact.lookupKey) }
        })
    }

    @Transaction
    open fun deleteAll() {
        deleteContacts()
        deleteNumbers()
        deleteGroups()
        deleteGroupMembers()
    }

}
