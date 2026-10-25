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

import android.content.Context
import android.net.Uri
import android.provider.BaseColumns
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import io.openmessages.database.AppDatabase
import io.openmessages.database.ContactGroupWithContacts
import io.openmessages.database.ContactWithNumbers
import io.openmessages.extensions.asFlowable
import io.openmessages.extensions.mapNotNull
import io.openmessages.model.Contact
import io.openmessages.model.ContactGroup
import io.openmessages.util.Preferences
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val context: Context,
    db: AppDatabase,
    private val prefs: Preferences
) : ContactRepository {

    private val dao = db.contactDao()

    override fun findContactUri(address: String): Single<Uri> {
        return Flowable.just(address)
                .map {
                    when {
                        address.contains('@') -> {
                            Uri.withAppendedPath(Email.CONTENT_FILTER_URI, Uri.encode(address))
                        }

                        else -> {
                            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
                        }
                    }
                }
                .mapNotNull { uri -> context.contentResolver.query(uri, arrayOf(BaseColumns._ID), null, null, null) }
                .flatMap { cursor -> cursor.asFlowable() }
                .firstOrError()
                .map { cursor -> cursor.getString(cursor.getColumnIndexOrThrow(BaseColumns._ID)) }
                .map { id -> Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id) }
    }

    override fun getContacts(): List<Contact> {
        return dao.getAll().map(ContactWithNumbers::toModel)
    }

    override fun observeContacts(): Observable<List<Contact>> {
        return dao.observeAll()
                .toObservable()
                .map { contacts -> contacts.map(ContactWithNumbers::toModel) }
    }

    override fun getUnmanagedContact(lookupKey: String): Contact? {
        return dao.get(lookupKey)?.toModel()
    }

    override fun getUnmanagedAllContacts(): List<Contact> {
        return dao.getAll().map(ContactWithNumbers::toModel)
    }

    override fun getUnmanagedContacts(starred: Boolean): Observable<List<Contact>> {
        val mobileOnly = prefs.mobileOnly.get()
        val mobileLabel by lazy { Phone.getTypeLabel(context.resources, Phone.TYPE_MOBILE, "Mobile").toString() }

        return (if (starred) dao.observeStarred() else dao.observeAll())
                .toObservable()
                .map { contacts -> contacts.map(ContactWithNumbers::toModel) }
                .map { contacts ->
                    if (mobileOnly) {
                        contacts
                                .filter { contact -> contact.numbers.any { number -> number.type.contains(mobileLabel) } }
                                .onEach { contact ->
                                    contact.numbers = contact.numbers.filter { number -> number.type == mobileLabel }
                                }
                    } else {
                        contacts
                    }
                }
                .map { contacts ->
                    contacts.sortedWith { c1, c2 ->
                        val initial = c1.name.firstOrNull()
                        val other = c2.name.firstOrNull()
                        if (initial?.isLetter() == true && other?.isLetter() != true) {
                            -1
                        } else if (initial?.isLetter() != true && other?.isLetter() == true) {
                            1
                        } else {
                            c1.name.compareTo(c2.name, ignoreCase = true)
                        }
                    }
                }
    }

    override fun getUnmanagedContactGroups(): Observable<List<ContactGroup>> {
        return dao.observeGroupsWithContacts()
                .toObservable()
                .map { groups -> groups.map(ContactGroupWithContacts::toModel) }
    }

    override fun setDefaultPhoneNumber(lookupKey: String, phoneNumberId: Long) {
        dao.setDefaultNumber(lookupKey, phoneNumberId)
    }

    override fun isContact(address: String): Boolean {
        val uri : Uri
        if (address.contains('@')) {
            uri = Uri.withAppendedPath(Email.CONTENT_FILTER_URI, Uri.encode(address))
        } else {
            uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        }
        return context.contentResolver.query(
            uri,
            arrayOf(BaseColumns._ID),
            null,
            null,
            null
        )?.use { cursor ->
            cursor.count > 0
        } ?: false
    }

}
