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
package io.openmessages.model

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.Index
import io.realm.annotations.LinkingObjects
import io.realm.annotations.PrimaryKey
import java.io.File

open class MmsPart : RealmObject() {

    companion object {
        /**
         * What [getSummary] stands a part in for when the part is not text it can quote. They are
         * named here because whatever puts a summary in front of the reader has to turn them into
         * the reader's language, and doing that means recognising them.
         */
        const val SUMMARY_CONTACT_CARD = "Contact card"
        const val SUMMARY_PICTURE = "Picture"
        const val SUMMARY_VIDEO = "Video"
        const val SUMMARY_AUDIO = "Audio"
    }

    @PrimaryKey var id: Long = 0
    @Index var messageId: Long = 0
    var type: String = ""
    var seq: Int = -1
    var name: String? = null
    var text: String? = null

    @LinkingObjects("parts")
    val messages: RealmResults<Message>? = null

    fun getUri(): Uri = Uri
        .Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority("mms")
        .encodedPath("part/$id")
        .build()

    fun getBestFilename(): String =
        if (name.isNullOrBlank()) "unknown"
        else if (File(name!!).extension.isNotEmpty()) name!!
        else "$name" +
                if (type.isBlank()) ""
                else ".${MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
                    ?: type.substringAfter("/")}"

    fun getSummary(): String? = when {
        type == "application/smil" -> null
        type == "text/plain" -> text
        type == "text/x-vcard" -> SUMMARY_CONTACT_CARD
        type.startsWith("image") -> SUMMARY_PICTURE
        type.startsWith("video") -> SUMMARY_VIDEO
        type.startsWith("audio") -> SUMMARY_AUDIO
        else -> type.substring(type.indexOf('/') + 1)
    }

}