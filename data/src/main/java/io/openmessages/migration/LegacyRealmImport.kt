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
package io.openmessages.migration

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.openmessages.database.Converters
import io.realm.DynamicRealm
import io.realm.DynamicRealmObject
import io.realm.Realm
import io.realm.RealmConfiguration
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Copies the Realm database the app kept before Room into the Room database, the one time that
 * database is created.
 *
 * Realm is read without any of the classes it was written through, as a dynamic Realm, so nothing
 * of it is left in the app but the library that reads the file. A file written by an older version
 * of the app is first brought up to the last schema it had, through the same migration it used to
 * go through on launch, inside a transaction that is cancelled once the copy is done: the file
 * itself is never changed.
 *
 * Either everything is copied or nothing is: a copy that fails empties the new database again,
 * so the app starts from a fresh sync of the system provider as it would on a new install, and
 * the Realm file is left where it is. It is only deleted once Room has committed the copy.
 */
class LegacyRealmImport @Inject constructor(
    private val context: Context,
    private val realmMigration: LegacyRealmMigration
) : RoomDatabase.Callback() {

    private val converters = Converters()

    private var imported = false

    override fun onCreate(db: SupportSQLiteDatabase) {
        if (!File(context.filesDir, Realm.DEFAULT_REALM_NAME).exists()) return

        try {
            Realm.init(context)
            DynamicRealm.getInstance(configuration()).use { realm ->
                realm.beginTransaction()
                try {
                    migrate(realm)
                    copy(realm, db)
                } finally {
                    realm.cancelTransaction()
                }
            }
            imported = true
        } catch (e: Throwable) {
            // Errors too, such as Realm's native library failing to load or the copy running out of
            // memory: whatever goes wrong here, Room must still end up with a database it can open,
            // or the app could never start again
            Timber.e(e, "Could not copy the Realm database into Room")
            TABLES.forEach { table -> db.execSQL("DELETE FROM $table") }
        }
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        if (!imported) return

        imported = false
        try {
            Realm.deleteRealm(configuration())
        } catch (e: Exception) {
            Timber.e(e, "Could not delete the Realm database after copying it into Room")
        }
    }

    private fun configuration() = RealmConfiguration.Builder()
        .schemaVersion(LegacyRealmMigration.SCHEMA_VERSION)
        .build()

    private fun migrate(realm: DynamicRealm) {
        val version = realm.version
        if (version < LegacyRealmMigration.SCHEMA_VERSION) {
            realmMigration.migrate(realm, version, LegacyRealmMigration.SCHEMA_VERSION)
        }
    }

    private fun copy(realm: DynamicRealm, db: SupportSQLiteDatabase) {
        val conversations = realm.where("Conversation").findAll()
        db.insertAll(
            "conversation",
            listOf("id", "archived", "blocked", "pinned", "lastMessageId", "draft", "draftDate",
                "blockingClient", "blockReason", "flagged", "flagReason", "name", "sendAsGroup"),
            conversations.asSequence().map { conversation ->
                listOf(
                    conversation.getLong("id"),
                    conversation.getBoolean("archived"),
                    conversation.getBoolean("blocked"),
                    conversation.getBoolean("pinned"),
                    conversation.getObject("lastMessage")?.getLong("id"),
                    conversation.getString("draft") ?: "",
                    conversation.getLong("draftDate"),
                    conversation.getNullableInt("blockingClient"),
                    conversation.getString("blockReason"),
                    conversation.getBoolean("flagged"),
                    conversation.getString("flagReason"),
                    conversation.getString("name") ?: "",
                    conversation.getBoolean("sendAsGroup")
                )
            }
        )
        db.insertAll(
            "conversation_recipient",
            listOf("conversationId", "recipientId", "position"),
            conversations.asSequence().flatMap { conversation ->
                conversation.getList("recipients").mapIndexed { position, recipient ->
                    listOf(conversation.getLong("id"), recipient.getLong("id"), position)
                }
            }
        )

        db.insertAll(
            "recipient",
            listOf("id", "address", "contactLookupKey", "lastUpdate"),
            realm.where("Recipient").findAll().asSequence().map { recipient ->
                listOf(
                    recipient.getLong("id"),
                    recipient.getString("address") ?: "",
                    recipient.getObject("contact")?.getString("lookupKey"),
                    recipient.getLong("lastUpdate")
                )
            }
        )

        val contacts = realm.where("Contact").findAll()
        db.insertAll(
            "contact",
            listOf("lookupKey", "name", "photoUri", "starred", "lastUpdate"),
            contacts.asSequence().map { contact ->
                listOf(
                    contact.getString("lookupKey") ?: "",
                    contact.getString("name") ?: "",
                    contact.getString("photoUri"),
                    contact.getBoolean("starred"),
                    contact.getLong("lastUpdate")
                )
            }
        )
        db.insertAll(
            "phone_number",
            listOf("id", "accountType", "address", "type", "isDefault", "contactLookupKey"),
            contacts.asSequence().flatMap { contact ->
                contact.getList("numbers").map { number ->
                    listOf(
                        number.getLong("id"),
                        number.getString("accountType"),
                        number.getString("address") ?: "",
                        number.getString("type") ?: "",
                        number.getBoolean("isDefault"),
                        contact.getString("lookupKey") ?: ""
                    )
                }
            }
        )

        val groups = realm.where("ContactGroup").findAll()
        db.insertAll(
            "contact_group",
            listOf("id", "title"),
            groups.asSequence().map { group -> listOf(group.getLong("id"), group.getString("title") ?: "") }
        )
        db.insertAll(
            "contact_group_member",
            listOf("groupId", "contactLookupKey"),
            groups.asSequence().flatMap { group ->
                group.getList("contacts").map { contact ->
                    listOf(group.getLong("id"), contact.getString("lookupKey") ?: "")
                }
            }
        )

        // A reaction is held in the list of the message it reacts to, which is what its target is
        val reactionTargets = mutableMapOf<Long, Long>()
        val messages = realm.where("Message").findAll()
        db.insertAll(
            "message",
            listOf("id", "threadId", "contentId", "address", "boxId", "type", "date", "dateSent",
                "seen", "read", "locked", "subId", "body", "errorCode", "deliveryStatus",
                "attachmentTypeString", "mmsDeliveryStatusString", "readReportString", "errorType",
                "messageSize", "messageType", "mmsStatus", "subject", "textContentType",
                "isEmojiReaction", "sendAsGroup"),
            messages.asSequence().map { message ->
                message.getList("emojiReactions").forEach { reaction ->
                    reactionTargets[reaction.getLong("id")] = message.getLong("id")
                }

                listOf(
                    message.getLong("id"),
                    message.getLong("threadId"),
                    message.getLong("contentId"),
                    message.getString("address") ?: "",
                    message.getInt("boxId"),
                    message.getString("type") ?: "",
                    message.getLong("date"),
                    message.getLong("dateSent"),
                    message.getBoolean("seen"),
                    message.getBoolean("read"),
                    message.getBoolean("locked"),
                    message.getInt("subId"),
                    message.getString("body") ?: "",
                    message.getInt("errorCode"),
                    message.getInt("deliveryStatus"),
                    message.getString("attachmentTypeString") ?: "",
                    message.getString("mmsDeliveryStatusString") ?: "",
                    message.getString("readReportString") ?: "",
                    message.getInt("errorType"),
                    message.getInt("messageSize"),
                    message.getInt("messageType"),
                    message.getInt("mmsStatus"),
                    message.getString("subject") ?: "",
                    message.getString("textContentType") ?: "",
                    message.getBoolean("isEmojiReaction"),
                    message.getBoolean("sendAsGroup")
                )
            }
        )
        db.insertAll(
            "message_part",
            listOf("messageId", "partId"),
            messages.asSequence().flatMap { message ->
                message.getList("parts").map { part -> listOf(message.getLong("id"), part.getLong("id")) }
            }
        )

        db.insertAll(
            "mms_part",
            listOf("id", "messageId", "type", "seq", "name", "text"),
            realm.where("MmsPart").findAll().asSequence().map { part ->
                listOf(
                    part.getLong("id"),
                    part.getLong("messageId"),
                    part.getString("type") ?: "",
                    part.getInt("seq"),
                    part.getString("name"),
                    part.getString("text")
                )
            }
        )

        db.insertAll(
            "emoji_reaction",
            listOf("id", "reactionMessageId", "targetMessageId", "senderAddress", "emoji",
                "originalMessageText", "threadId"),
            realm.where("EmojiReaction").findAll().asSequence().map { reaction ->
                listOf(
                    reaction.getLong("id"),
                    reaction.getLong("reactionMessageId"),
                    reactionTargets[reaction.getLong("id")],
                    reaction.getString("senderAddress") ?: "",
                    reaction.getString("emoji") ?: "",
                    reaction.getString("originalMessageText") ?: "",
                    reaction.getLong("threadId")
                )
            }
        )

        db.insertAll(
            "scheduled_message",
            listOf("id", "date", "subId", "recipients", "sendAsGroup", "body", "attachments", "conversationId"),
            realm.where("ScheduledMessage").findAll().asSequence().map { message ->
                listOf(
                    message.getLong("id"),
                    message.getLong("date"),
                    message.getInt("subId"),
                    converters.fromStringList(message.getList("recipients", String::class.java).filterNotNull()),
                    message.getBoolean("sendAsGroup"),
                    message.getString("body") ?: "",
                    converters.fromStringList(message.getList("attachments", String::class.java).filterNotNull()),
                    message.getLong("conversationId")
                )
            }
        )

        listOf("BlockedNumber" to "blocked_number", "AllowedNumber" to "allowed_number").forEach { (from, to) ->
            db.insertAll(
                to,
                listOf("id", "address"),
                realm.where(from).findAll().asSequence().map { number -> listOf(number.getLong("id"), number.getString("address") ?: "") }
            )
        }

        db.insertAll(
            "message_content_filter",
            listOf("id", "value", "caseSensitive", "isRegex", "includeContacts"),
            realm.where("MessageContentFilter").findAll().asSequence().map { filter ->
                listOf(
                    filter.getLong("id"),
                    filter.getString("value") ?: "",
                    filter.getBoolean("caseSensitive"),
                    filter.getBoolean("isRegex"),
                    filter.getBoolean("includeContacts")
                )
            }
        )

        db.insertAll(
            "sync_log",
            listOf("date"),
            realm.where("SyncLog").findAll().asSequence().map { log -> listOf(log.getLong("date")) }
        )

        db.insertAll(
            "emoji_sync_needed",
            listOf("createdAt"),
            realm.where("EmojiSyncNeeded").findAll().asSequence().map { needed -> listOf(needed.getLong("createdAt")) }
        )
    }

    private fun DynamicRealmObject.getNullableInt(field: String): Int? =
        if (isNull(field)) null else getInt(field)

    private fun SupportSQLiteDatabase.insertAll(table: String, columns: List<String>, rows: Sequence<List<Any?>>) {
        compileStatement(
            "INSERT OR REPLACE INTO $table (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})"
        ).use { statement ->
            rows.forEach { values ->
                statement.clearBindings()
                values.forEachIndexed { index, value ->
                    when (value) {
                        null -> statement.bindNull(index + 1)
                        is String -> statement.bindString(index + 1, value)
                        is Boolean -> statement.bindLong(index + 1, if (value) 1 else 0)
                        is Long -> statement.bindLong(index + 1, value)
                        is Int -> statement.bindLong(index + 1, value.toLong())
                        else -> error("No way to store a ${value::class.java} in $table")
                    }
                }
                statement.executeInsert()
            }
        }
    }

    companion object {
        /** Every table the copy writes to, emptied again when it fails part way */
        private val TABLES = listOf(
            "conversation", "conversation_recipient", "recipient", "contact", "phone_number",
            "contact_group", "contact_group_member", "message", "message_part", "mms_part",
            "emoji_reaction", "scheduled_message", "blocked_number", "allowed_number",
            "message_content_filter", "sync_log", "emoji_sync_needed"
        )
    }

}
