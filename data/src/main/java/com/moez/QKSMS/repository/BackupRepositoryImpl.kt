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

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Telephony
import androidx.annotation.RequiresApi
import androidx.core.content.contentValuesOf
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.google.android.mms.pdu_alt.PduHeaders
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import io.openmessages.common.util.extensions.now
import io.openmessages.compat.TelephonyCompat
import io.openmessages.manager.AlarmManager
import io.openmessages.model.AllowedNumber
import io.openmessages.model.BackupCategory
import io.openmessages.model.BackupFolder
import io.openmessages.model.BackupItem
import io.openmessages.model.BlockedNumber
import io.openmessages.model.Conversation
import io.openmessages.model.Message
import io.openmessages.model.MessageContentFilter
import io.openmessages.model.MessageContentFilterData
import io.openmessages.model.MmsPart
import io.openmessages.model.ScheduledMessage
import io.openmessages.util.Preferences
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import io.realm.Realm
import okio.buffer
import okio.source
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.Locale
import java.util.Timer
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val context: Context,
    private val moshi: Moshi,
    private val prefs: Preferences,
    private val sharedPrefs: SharedPreferences,
    private val syncRepo: SyncRepository,
    private val blockingRepo: BlockingRepository,
    private val allowlistRepo: AllowlistRepository,
    private val messageContentFilterRepo: MessageContentFilterRepository,
    private val conversationRepo: ConversationRepository,
    private val scheduledMessageRepo: ScheduledMessageRepository,
    private val alarmManager: AlarmManager
) : BackupRepository {

    companion object {
        /**
         * Bumped when the on-disk backup format changes. Version 3 added MMS; version 4 moved MMS
         * attachments out of messages.json into streamed binary files under an attachments/ sub-folder,
         * so a large MMS history no longer has to fit in memory.
         */
        private const val BACKUP_FORMAT_VERSION = 4

        /** MMS protocol version stamped on restored messages (matches the app's own send path). */
        private const val MMS_VERSION = 18

        /** Backups are written automatically under this folder inside the shared Documents directory. */
        private const val BACKUP_DIR = "OpenMessages"

        /** Sub-folder of a backup set that holds the MMS attachment binaries, one file per part. */
        private const val ATTACHMENTS_DIR = "attachments"

        /** Cache sub-folder a picked .zip backup is extracted into before it's restored. */
        private const val RESTORE_TEMP_DIR = "restore-backup"

        /** How long a failed backup stays on screen before the panel returns to idle. */
        private const val FAILED_LINGER_MS = 4000L

        /**
         * Preference keys that are device-specific or internal and must never be carried across a
         * restore (e.g. the SAF tree Uri of the backup folder, which is meaningless on another
         * device/install).
         */
        // Device-local prefs that must not travel through a backup: the backup folder is per-device,
        // and "have we asked for notification permission" tracks a prompt shown on this device only
        // (restoring it as true makes a fresh install skip the system dialog and jump to settings).
        //
        // The launcher icon colour deliberately stays in the backup: it is the user's choice, and the
        // backup screen applies it once the restore is done. LauncherIconManager reads the enabled
        // alias from the package manager rather than from that preference, so the two can no longer
        // disagree in the meantime.
        private val SETTINGS_DENYLIST = setOf("backupDirectory", "hasAskedForNotificationPermission")
    }

    data class Backup(
        val messageCount: Int = 0,
        val messages: List<BackupMessage> = listOf(),
        val mmsCount: Int = 0,
        val mms: List<BackupMmsMessage> = listOf()
    )

    data class BackupMessage(
        val type: Int,
        val address: String,
        val date: Long,
        val dateSent: Long,
        val read: Boolean,
        val status: Int,
        val body: String,
        val protocol: Int,
        val serviceCenter: String?,
        val locked: Boolean,
        val subId: Int
    )

    /**
     * One MMS message with its address rows and parts. Dates stay in milliseconds (as the app stores
     * them); the telephony provider wants seconds, so restore divides by 1000. Binary parts are streamed
     * to their own file under the attachments/ sub-folder and referenced by [BackupMmsPart.dataFile];
     * text and SMIL parts keep their text inline instead. [recipients] is the conversation's recipient
     * list, used to re-resolve the thread on restore.
     */
    data class BackupMmsMessage(
        val boxId: Int = 0,
        val date: Long = 0,
        val dateSent: Long = 0,
        val read: Boolean = false,
        val seen: Boolean = true,
        val locked: Boolean = false,
        val subId: Int = -1,
        val subject: String = "",
        val messageType: Int = 0,
        val mmsStatus: Int = 0,
        val textContentType: String = "",
        val recipients: List<String> = emptyList(),
        val addresses: List<BackupMmsAddress> = emptyList(),
        val parts: List<BackupMmsPart> = emptyList()
    )

    data class BackupMmsAddress(
        val address: String = "",
        val type: Int = 0,
        val charset: Int = 106
    )

    data class BackupMmsPart(
        val type: String = "",
        val seq: Int = -1,
        val name: String? = null,
        val text: String? = null,
        // Name of this part's binary in the attachments/ sub-folder; null for text and SMIL parts
        val dataFile: String? = null
    )

    /**
     * Every configurable value the user has changed lives in [SharedPreferences] (including the saved
     * message templates, stored as a JSON string), so a snapshot of it captures all app settings.
     * Values are grouped by type because SharedPreferences is strongly typed and Moshi needs a
     * concrete type per field.
     */
    data class SettingsBackup(
        val booleans: Map<String, Boolean> = emptyMap(),
        val ints: Map<String, Int> = emptyMap(),
        val longs: Map<String, Long> = emptyMap(),
        val floats: Map<String, Float> = emptyMap(),
        val strings: Map<String, String> = emptyMap(),
        val stringSets: Map<String, List<String>> = emptyMap()
    ) {
        fun total(): Int =
            booleans.size + ints.size + longs.size + floats.size + strings.size + stringSets.size
    }

    /**
     * Describes one backup set. [files] maps each backed-up category name to the actual timestamped
     * file that holds it, so restore can locate the right sibling files in the folder.
     */
    data class BackupManifest(
        val version: Int = 0,
        val createdAt: Long = 0,
        val app: String = "",
        val files: Map<String, String> = emptyMap(),
        val counts: Map<String, Int> = emptyMap()
    )

    /** Blocklist, allowlist and content filters. Restored through the dedicated repositories. */
    data class BlockingBackup(
        val blockedNumbers: List<String> = emptyList(),
        val allowedNumbers: List<String> = emptyList(),
        val contentFilters: List<ContentFilterBackup> = emptyList()
    )

    data class ContentFilterBackup(
        val value: String = "",
        val caseSensitive: Boolean = false,
        val isRegex: Boolean = false,
        val includeContacts: Boolean = false
    )

    /**
     * Per-conversation overlay (archived / pinned / blocked / custom name / flagged) that isn't stored
     * in the SMS provider. Keyed by recipient [addresses] rather than thread id, since thread ids are
     * re-assigned on restore; the addresses are matched fuzzily against the synced conversations.
     */
    data class ConversationsBackup(
        val conversations: List<ConversationBackup> = emptyList()
    )

    data class ConversationBackup(
        val addresses: List<String> = emptyList(),
        val archived: Boolean = false,
        val pinned: Boolean = false,
        val blocked: Boolean = false,
        val name: String = "",
        val blockingClient: Int? = null,
        val blockReason: String? = null,
        val flagged: Boolean = false,
        val flagReason: String? = null
    )

    /** Pending scheduled messages. Only future-dated ones are restored, then re-armed with an alarm. */
    data class ScheduledBackup(
        val messages: List<ScheduledMessageBackup> = emptyList()
    )

    data class ScheduledMessageBackup(
        val date: Long = 0,
        val subId: Int = -1,
        val recipients: List<String> = emptyList(),
        val sendAsGroup: Boolean = true,
        val body: String = "",
        val attachments: List<String> = emptyList()
    )

    // Subjects to emit our progress events to
    private val backupProgress: Subject<BackupRepository.Progress> =
            BehaviorSubject.createDefault(BackupRepository.Progress.Idle())
    private val restoreProgress: Subject<BackupRepository.Progress> =
            BehaviorSubject.createDefault(BackupRepository.Progress.Idle())

    @Volatile private var stopFlag: Boolean = false

    override fun getDefaultBackupPath(): String {
        return "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)}/$BACKUP_DIR"
    }

    override fun getBackupDocumentTree(): DocumentFile? {
        return prefs.backupDirectory.get()
                .takeIf { uri -> uri != Uri.EMPTY }
                ?.let { uri -> DocumentFile.fromTreeUri(context, uri) }
                ?.takeIf { dir -> dir.exists() && dir.canRead() && dir.canWrite() }
    }

    override fun getBackupPathUriForPicker(): Uri {
        return prefs.backupDirectory.get().takeIf { uri -> uri != Uri.EMPTY }
                ?: getDefaultBackupPath().toUri()
    }

    override fun getBackupLocationLabel(): String {
        return getBackupDocumentTree()?.name ?: "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR"
    }

    override fun persistBackupDirectory(directory: Uri) {
        context.contentResolver.takePersistableUriPermission(directory,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs.backupDirectory.set(directory)

        Timber.v("Updated backup directory: $directory")
    }

    override fun performBackup(categories: Set<BackupCategory>): Boolean {
        // If a backup or restore is already running, or nothing was selected, don't do anything
        if (isBackupOrRestoreRunning() || categories.isEmpty()) return false

        val createdAt = now()
        val folderName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(createdAt)

        try {
            // Create the destination folder up front so each MMS attachment can be streamed straight
            // into it as its message is processed, instead of being held in memory. Custom folder if
            // the user picked one, otherwise the default Documents/OpenMessages (no folder to pick).
            val destination = createBackupDestination(folderName)

            // One plainly-named JSON file per category; each is small enough to build in memory.
            val payloads = linkedMapOf<BackupCategory, Pair<String, ByteArray>>()
            val counts = linkedMapOf<String, Int>()

            if (BackupCategory.MESSAGES in categories) {
                val backup = Realm.getDefaultInstance().use { realm ->
                    val smsMessages = realm.where(Message::class.java)
                            .equalTo("type", Message.TYPE_SMS)
                            .sort("date")
                            .findAll()
                            .createSnapshot()
                    val mmsMessages = realm.where(Message::class.java)
                            .equalTo("type", Message.TYPE_MMS)
                            .sort("date")
                            .findAll()
                            .createSnapshot()

                    val total = smsMessages.size + mmsMessages.size
                    val sms = smsMessages.mapIndexed { index, message ->
                        backupProgress.onNext(BackupRepository.Progress.Running(total, index))
                        messageToBackupMessage(message)
                    }
                    val mms = mmsMessages.mapIndexed { index, message ->
                        backupProgress.onNext(BackupRepository.Progress.Running(total, smsMessages.size + index))
                        messageToBackupMms(message, realm, destination)
                    }
                    Backup(sms.size, sms, mms.size, mms)
                }

                val bytes = moshi.adapter(Backup::class.java).indent("\t").toJson(backup).toByteArray()
                payloads[BackupCategory.MESSAGES] = "messages.json" to bytes
                counts[BackupCategory.MESSAGES.name] = backup.messageCount + backup.mmsCount
            }

            if (BackupCategory.SETTINGS in categories) {
                val settings = buildSettingsBackup()
                val bytes = moshi.adapter(SettingsBackup::class.java).indent("\t").toJson(settings).toByteArray()
                payloads[BackupCategory.SETTINGS] = "settings.json" to bytes
                counts[BackupCategory.SETTINGS.name] = settings.total()
            }

            if (BackupCategory.BLOCKING in categories) {
                val blocking = Realm.getDefaultInstance().use { realm ->
                    BlockingBackup(
                            blockedNumbers = realm.where(BlockedNumber::class.java).findAll().map { it.address },
                            allowedNumbers = realm.where(AllowedNumber::class.java).findAll().map { it.address },
                            contentFilters = realm.where(MessageContentFilter::class.java).findAll().map { filter ->
                                ContentFilterBackup(filter.value, filter.caseSensitive, filter.isRegex, filter.includeContacts)
                            })
                }
                val bytes = moshi.adapter(BlockingBackup::class.java).indent("\t").toJson(blocking).toByteArray()
                payloads[BackupCategory.BLOCKING] = "blocking.json" to bytes
                counts[BackupCategory.BLOCKING.name] =
                        blocking.blockedNumbers.size + blocking.allowedNumbers.size + blocking.contentFilters.size
            }

            if (BackupCategory.CONVERSATIONS in categories) {
                val conversations = Realm.getDefaultInstance().use { realm ->
                    realm.where(Conversation::class.java).findAll()
                            .filter { it.archived || it.pinned || it.blocked || it.flagged || it.name.isNotBlank() }
                            .mapNotNull { conversation ->
                                val addresses = conversation.recipients
                                        .mapNotNull { recipient -> recipient.address.takeIf { addr -> addr.isNotBlank() } }
                                if (addresses.isEmpty()) null
                                else ConversationBackup(addresses, conversation.archived, conversation.pinned,
                                        conversation.blocked, conversation.name, conversation.blockingClient,
                                        conversation.blockReason, conversation.flagged, conversation.flagReason)
                            }
                }
                val bytes = moshi.adapter(ConversationsBackup::class.java).indent("\t")
                        .toJson(ConversationsBackup(conversations)).toByteArray()
                payloads[BackupCategory.CONVERSATIONS] = "conversations.json" to bytes
                counts[BackupCategory.CONVERSATIONS.name] = conversations.size
            }

            if (BackupCategory.SCHEDULED in categories) {
                val scheduled = Realm.getDefaultInstance().use { realm ->
                    realm.where(ScheduledMessage::class.java).findAll().map { message ->
                        ScheduledMessageBackup(message.date, message.subId, message.recipients.toList(),
                                message.sendAsGroup, message.body, message.attachments.toList())
                    }
                }
                val bytes = moshi.adapter(ScheduledBackup::class.java).indent("\t")
                        .toJson(ScheduledBackup(scheduled)).toByteArray()
                payloads[BackupCategory.SCHEDULED] = "scheduled.json" to bytes
                counts[BackupCategory.SCHEDULED.name] = scheduled.size
            }

            // Update the status, and set the progress to be indeterminate since we can no longer calculate progress
            backupProgress.onNext(BackupRepository.Progress.Saving())

            // Write one JSON file per category, then the manifest that marks the set complete.
            payloads.values.forEach { (fileName, bytes) -> destination.writeFile(fileName, bytes) }

            val manifest = BackupManifest(
                    version = BACKUP_FORMAT_VERSION,
                    createdAt = createdAt,
                    app = "OpenMessages",
                    files = payloads.entries.associate { (category, payload) -> category.name to payload.first },
                    counts = counts)
            destination.writeFile("manifest.json",
                    moshi.adapter(BackupManifest::class.java).indent("\t").toJson(manifest).toByteArray())

            // Finalise the set (closes the .zip archive; a no-op for folder destinations).
            destination.finish()
        } catch (e: Exception) {
            // Anything here means the set was never completed: no manifest was written, so the
            // half-written folder is ignored by listBackups(). Report the failure instead of the
            // unconditional "Finished!" this used to end on, which claimed success for a backup that
            // does not exist.
            Timber.w(e, "Backup failed")
            backupProgress.onNext(BackupRepository.Progress.Failed())
            Timer().schedule(FAILED_LINGER_MS) { backupProgress.onNext(BackupRepository.Progress.Idle()) }
            return false
        }

        // Mark the task finished, and set it as Idle a second later
        backupProgress.onNext(BackupRepository.Progress.Finished())
        Timer().schedule(1000) { backupProgress.onNext(BackupRepository.Progress.Idle()) }
        return true
    }

    /** Snapshots every stored preference (minus the device-specific denylist) grouped by type. */
    private fun buildSettingsBackup(): SettingsBackup {
        val all = sharedPrefs.all.filterKeys { it !in SETTINGS_DENYLIST }
        return SettingsBackup(
                booleans = all.filterValues { it is Boolean }.mapValues { it.value as Boolean },
                ints = all.filterValues { it is Int }.mapValues { it.value as Int },
                longs = all.filterValues { it is Long }.mapValues { it.value as Long },
                floats = all.filterValues { it is Float }.mapValues { it.value as Float },
                strings = all.filterValues { it is String }.mapValues { it.value as String },
                stringSets = all.filterValues { it is Set<*> }
                        .mapValues { entry -> (entry.value as Set<*>).filterIsInstance<String>() })
    }

    /**
     * A backup set's destination folder. It writes small JSON files whole, and hands out output streams
     * for MMS attachments so their bytes can be streamed straight from the provider without ever being
     * held in memory. The attachments/ sub-folder is created lazily, only when there's an attachment.
     */
    private interface BackupDestination {
        fun writeFile(name: String, bytes: ByteArray)
        fun openAttachment(name: String): OutputStream
        /** Finalises the set once everything is written (closes the archive for the .zip destination). */
        fun finish() {}
    }

    /**
     * Picks where a backup set is written: as a single .zip archive when the user enabled it, otherwise a
     * date-and-time sub-folder in the user's custom SAF folder, or the default Documents/OpenMessages via
     * MediaStore (Android 10+) or legacy file access (Android 9-). The folder/archive is created here so
     * attachments can be streamed into it right away.
     */
    private fun createBackupDestination(folderName: String): BackupDestination {
        val tree = getBackupDocumentTree()
        if (prefs.backupZip.get()) {
            return ZipBackupDestination(openBackupFileStream(tree, "$folderName.zip", "application/zip"))
        }
        return when {
            tree != null -> SafBackupDestination(tree, folderName)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> MediaStoreBackupDestination(folderName)
            else -> LegacyBackupDestination(folderName)
        }
    }

    /**
     * Opens an output stream for a single top-level file (the .zip archive) in the backup location:
     * the user's SAF folder, or the default Documents/OpenMessages via MediaStore or legacy storage.
     */
    private fun openBackupFileStream(tree: DocumentFile?, name: String, mimeType: String): OutputStream {
        if (tree != null) {
            val file = tree.createFile(mimeType, name) ?: throw Exception("Failed to create $name")
            return context.contentResolver.openOutputStream(file.uri)
                    ?: throw Exception("Failed to open output stream for $name")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR")
            }
            val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                    ?: throw Exception("Failed to create $name via MediaStore")
            return context.contentResolver.openOutputStream(uri)
                    ?: throw Exception("Failed to open output stream for $name")
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), BACKUP_DIR)
        if (!dir.exists() && !dir.mkdirs()) throw Exception("Failed to create backup directory $dir")
        return File(dir, name).outputStream()
    }

    /** Writes the whole backup set as entries inside one .zip archive - a single file to transfer. */
    private inner class ZipBackupDestination(outputStream: OutputStream) : BackupDestination {
        private val zip = ZipOutputStream(BufferedOutputStream(outputStream))

        override fun writeFile(name: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }

        override fun openAttachment(name: String): OutputStream {
            zip.putNextEntry(ZipEntry("$ATTACHMENTS_DIR/$name"))
            // Hand back a stream that closes only the current entry, never the shared archive.
            return object : OutputStream() {
                override fun write(b: Int) = zip.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = zip.write(b, off, len)
                override fun close() = zip.closeEntry()
            }
        }

        override fun finish() {
            zip.close()
        }
    }

    /**
     * Android 10+: write through MediaStore so nothing has to be picked and no storage permission is
     * required - the app owns the files it creates in the shared Documents collection.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private inner class MediaStoreBackupDestination(folderName: String) : BackupDestination {
        private val basePath = "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR/$folderName"
        private val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        override fun writeFile(name: String, bytes: ByteArray) {
            openStream(name, "application/json", basePath).use { it.write(bytes) }
        }

        override fun openAttachment(name: String): OutputStream =
                openStream(name, "application/octet-stream", "$basePath/$ATTACHMENTS_DIR")

        private fun openStream(name: String, mimeType: String, relativePath: String): OutputStream {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val uri = context.contentResolver.insert(collection, values)
                    ?: throw Exception("Failed to create $name via MediaStore")
            return context.contentResolver.openOutputStream(uri)
                    ?: throw Exception("Failed to open output stream for $name")
        }
    }

    /** Android 9 and below: write straight to the public Documents directory (legacy storage). */
    @Suppress("DEPRECATION")
    private inner class LegacyBackupDestination(folderName: String) : BackupDestination {
        private val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "$BACKUP_DIR/$folderName").apply {
            if (!exists() && !mkdirs()) throw Exception("Failed to create backup directory $this")
        }

        override fun writeFile(name: String, bytes: ByteArray) {
            File(dir, name).outputStream().use { it.write(bytes) }
        }

        override fun openAttachment(name: String): OutputStream {
            val attachmentsDir = File(dir, ATTACHMENTS_DIR)
            if (!attachmentsDir.exists() && !attachmentsDir.mkdirs()) {
                throw Exception("Failed to create attachments directory $attachmentsDir")
            }
            return File(attachmentsDir, name).outputStream()
        }
    }

    /** Writes a backup set into a date-and-time sub-folder of a folder the user picked via SAF. */
    private inner class SafBackupDestination(tree: DocumentFile, folderName: String) : BackupDestination {
        private val backupDir = tree.createDirectory(folderName)
                ?: throw Exception("Failed to create backup sub-folder")
        private var attachmentsDir: DocumentFile? = null

        override fun writeFile(name: String, bytes: ByteArray) {
            val file = backupDir.createFile("application/json", name)
                    ?: throw Exception("Failed to create $name")
            (context.contentResolver.openOutputStream(file.uri)
                    ?: throw Exception("Failed to open output stream for $name")).use { it.write(bytes) }
        }

        override fun openAttachment(name: String): OutputStream {
            val dir = attachmentsDir
                    ?: (backupDir.createDirectory(ATTACHMENTS_DIR)
                            ?: throw Exception("Failed to create attachments sub-folder"))
                            .also { attachmentsDir = it }
            val file = dir.createFile("application/octet-stream", name)
                    ?: throw Exception("Failed to create attachment $name")
            return context.contentResolver.openOutputStream(file.uri)
                    ?: throw Exception("Failed to open output stream for $name")
        }
    }

    @SuppressLint("Recycle") // InputStream is closed by Okio BufferedSource
    private fun <T> readJson(file: DocumentFile, adapter: JsonAdapter<T>): T? {
        return try {
            context.contentResolver.openInputStream(file.uri)
                    ?.source()
                    ?.buffer()
                    ?.use(adapter::fromJson)
        } catch (e: Exception) {
            // Some third-party file-manager SAF providers hand back URIs we are not allowed to read
            // (SecurityException). Skip the file instead of letting the restore screen crash.
            Timber.w(e)
            null
        }
    }

    private fun <T> readJson(tree: DocumentFile, fileName: String, adapter: JsonAdapter<T>): T? {
        val file = tree.findFile(fileName) ?: return null
        return readJson(file, adapter)
    }

    private fun messageToBackupMessage(message: Message): BackupMessage = BackupMessage(
            type = message.boxId,
            address = message.address,
            date = message.date,
            dateSent = message.dateSent,
            read = message.read,
            status = message.deliveryStatus,
            body = message.body,
            protocol = 0,
            serviceCenter = null,
            locked = message.locked,
            subId = message.subId
    )

    /**
     * Builds the backup payload for one MMS: its recipient list (used to re-resolve the thread on
     * restore), the raw address rows from the provider, and every part. Binary parts are streamed to
     * their own file under the attachments/ sub-folder and referenced by name; text and SMIL parts keep
     * their text inline.
     */
    private fun messageToBackupMms(message: Message, realm: Realm, destination: BackupDestination): BackupMmsMessage {
        val recipients = realm.where(Conversation::class.java)
                .equalTo("id", message.threadId)
                .findFirst()
                ?.recipients
                ?.mapNotNull { recipient -> recipient.address.takeIf { it.isNotBlank() } }
                ?: emptyList()

        val parts = message.parts.map { part ->
            // Only these live in the provider's TEXT column; everything else (images, audio recordings,
            // video, vCards, PDFs, ...) is a binary file, matching how the platform's PduPersister
            // stores parts. In particular text/x-vcard is binary despite the "text/" prefix.
            val isText = part.type.equals("text/plain", ignoreCase = true) ||
                    part.type.equals("text/html", ignoreCase = true) ||
                    part.type.equals("application/smil", ignoreCase = true)
            if (isText) {
                BackupMmsPart(type = part.type, seq = part.seq, name = part.name, text = part.text)
            } else {
                val fileName = attachmentFileName(part)
                val written = writeAttachment(part, destination, fileName)
                BackupMmsPart(
                        type = part.type,
                        seq = part.seq,
                        name = part.name,
                        dataFile = if (written) fileName else null)
            }
        }

        return BackupMmsMessage(
                boxId = message.boxId,
                date = message.date,
                dateSent = message.dateSent,
                read = message.read,
                seen = message.seen,
                locked = message.locked,
                subId = message.subId,
                subject = message.subject,
                messageType = message.messageType,
                mmsStatus = message.mmsStatus,
                textContentType = message.textContentType,
                recipients = recipients,
                addresses = readMmsAddresses(message.contentId),
                parts = parts)
    }

    /** Reads the address rows (sender + recipients, with their PDU type) for an MMS from the provider. */
    private fun readMmsAddresses(contentId: Long): List<BackupMmsAddress> {
        if (contentId == 0L) return emptyList()
        val uri = Telephony.Mms.CONTENT_URI.buildUpon()
                .appendPath(contentId.toString())
                .appendPath("addr")
                .build()
        val projection = arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE, Telephony.Mms.Addr.CHARSET)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val addresses = mutableListOf<BackupMmsAddress>()
                while (cursor.moveToNext()) {
                    val address = cursor.getString(0)
                    if (address.isNullOrBlank() || address == "insert-address-token") continue
                    val type = cursor.getInt(1)
                    val charset = cursor.getInt(2).takeIf { it > 0 } ?: 106
                    addresses.add(BackupMmsAddress(address, type, charset))
                }
                addresses
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.w(e)
            emptyList()
        }
    }

    /** A unique, filesystem-safe name for a binary part's file, keyed on its unique provider id. */
    private fun attachmentFileName(part: MmsPart): String {
        val ext = part.type.substringAfter('/', "").substringBefore(';').trim()
        return if (ext.isBlank()) "part_${part.id}" else "part_${part.id}.$ext"
    }

    /**
     * Streams one binary MMS part from the provider straight into its own file in the backup, without
     * ever holding it all in memory. Returns true if it was written, so callers only reference files
     * that actually exist.
     */
    @SuppressLint("Recycle") // streams closed by use{}
    private fun writeAttachment(part: MmsPart, destination: BackupDestination, fileName: String): Boolean {
        return try {
            context.contentResolver.openInputStream(part.getUri())?.use { input ->
                destination.openAttachment(fileName).use { output -> input.copyTo(output) }
                true
            } ?: false
        } catch (e: Exception) {
            Timber.w(e)
            false
        }
    }

    override fun getBackupProgress(): Observable<BackupRepository.Progress> = backupProgress

    /** Where a picked .zip backup is unpacked so it can be read like a folder. */
    private val restoreTempDir: File
        get() = File(context.cacheDir, RESTORE_TEMP_DIR)

    override fun clearRestoreCache() {
        runCatching { restoreTempDir.deleteRecursively() }
                .onFailure { error -> Timber.w(error, "Failed to clear the restore cache") }
    }

    override fun importZip(zip: Uri): Uri? {
        return try {
            val dir = restoreTempDir
            // Also cleared once the restore is done; this covers a previous run killed part-way.
            if (dir.exists()) dir.deleteRecursively()
            dir.mkdirs()

            context.contentResolver.openInputStream(zip)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zin ->
                    var entry = zin.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outFile = File(dir, entry.name)
                            // Guard against a malicious archive writing outside the temp directory (Zip Slip).
                            if (outFile.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { output -> zin.copyTo(output) }
                            }
                        }
                        zin.closeEntry()
                        entry = zin.nextEntry
                    }
                }
            }
            DocumentFile.fromFile(dir).uri
        } catch (e: Exception) {
            Timber.w(e)
            null
        }
    }

    /** Resolves a backup source Uri to a document tree, accepting both SAF trees and local file paths. */
    private fun documentTree(uri: Uri): DocumentFile? =
            if (uri.scheme == "file") uri.path?.let { path -> DocumentFile.fromFile(File(path)) }
            else DocumentFile.fromTreeUri(context, uri)

    override fun listBackups(folder: Uri): List<BackupFolder> {
        return try {
            val tree = documentTree(folder) ?: return emptyList()
            val adapter = moshi.adapter(BackupManifest::class.java)

            // If the user picked a single backup sub-folder directly, it holds the manifest itself.
            tree.findFile("manifest.json")?.let { manifest ->
                return listOfNotNull(toBackupFolder(manifest, folderName = "", adapter))
            }

            // Otherwise the user picked the parent folder: each date-and-time sub-folder is one backup.
            tree.listFiles()
                    .filter { file -> file.isDirectory }
                    .mapNotNull { dir ->
                        val name = dir.name ?: return@mapNotNull null
                        val manifest = dir.findFile("manifest.json") ?: return@mapNotNull null
                        toBackupFolder(manifest, folderName = name, adapter)
                    }
                    .sortedByDescending { backup -> backup.date }
        } catch (e: Exception) {
            Timber.w(e)
            emptyList()
        }
    }

    private fun toBackupFolder(
        manifestFile: DocumentFile,
        folderName: String,
        adapter: JsonAdapter<BackupManifest>
    ): BackupFolder? {
        val manifest = readJson(manifestFile, adapter) ?: return null
        val categories = manifest.files.keys
                .mapNotNull { categoryName -> BackupCategory.values().firstOrNull { it.name == categoryName } }
                .toSet()
        return if (categories.isEmpty()) null else BackupFolder(folderName, manifest.createdAt, categories)
    }

    override fun performRestore(folder: Uri, folderName: String, categories: Set<BackupCategory>) {
        // If a backup or restore is already running, or nothing was selected, don't do anything
        if (isBackupOrRestoreRunning() || categories.isEmpty()) return

        restoreProgress.onNext(BackupRepository.Progress.Parsing())

        val tree = documentTree(folder)
        val backupDir = when {
            tree == null -> null
            folderName.isEmpty() -> tree
            else -> tree.findFile(folderName)
        }
        val manifest = backupDir?.let { readJson(it, "manifest.json", moshi.adapter(BackupManifest::class.java)) }
        if (backupDir == null || manifest == null) {
            restoreProgress.onNext(BackupRepository.Progress.Idle())
            return
        }

        // Each category is restored inside its own guard so a failure in one never cascades into the
        // others (the conversation overlay and scheduled messages come last and would otherwise be
        // skipped whenever anything before them threw).

        // Settings first (instant); it seeds templates and every toggle before messages come in.
        if (BackupCategory.SETTINGS in categories) {
            restoreCategory(BackupCategory.SETTINGS, manifest) { fileName -> restoreSettings(backupDir, fileName) }
        }

        if (BackupCategory.BLOCKING in categories) {
            restoreCategory(BackupCategory.BLOCKING, manifest) { fileName -> restoreBlocking(backupDir, fileName) }
        }

        // Messages restore is synchronous end-to-end: restoreMessages now waits for the (otherwise
        // asynchronous) sync to finish, so the conversation overlay below matches real, synced threads.
        if (BackupCategory.MESSAGES in categories) {
            val fileName = manifest.files[BackupCategory.MESSAGES.name]
            if (fileName != null) {
                val completed = try {
                    restoreMessages(backupDir, fileName)
                } catch (e: Exception) {
                    Timber.w(e)
                    true // a thrown failure still shouldn't abort the later categories
                }
                if (!completed) {
                    // The user cancelled mid-restore
                    restoreProgress.onNext(BackupRepository.Progress.Idle())
                    return
                }
            }
        }

        // Conversation overlay after the sync has completed: it matches by address against the now-synced
        // conversations, so archived / pinned / blocked / named threads are re-applied.
        if (BackupCategory.CONVERSATIONS in categories) {
            restoreCategory(BackupCategory.CONVERSATIONS, manifest) { fileName -> restoreConversations(backupDir, fileName) }
        }

        if (BackupCategory.SCHEDULED in categories) {
            restoreCategory(BackupCategory.SCHEDULED, manifest) { fileName -> restoreScheduled(backupDir, fileName) }
        }

        // Mark the task finished, and set it as Idle a second later
        restoreProgress.onNext(BackupRepository.Progress.Finished())
        Timer().schedule(1000) { restoreProgress.onNext(BackupRepository.Progress.Idle()) }
    }

    /**
     * Runs one category's restore in isolation: resolves its file from the manifest, logs when it is
     * selected but missing from the backup, and never lets its failure escape to abort other categories.
     */
    private fun restoreCategory(
        category: BackupCategory,
        manifest: BackupManifest,
        restore: (String) -> Unit
    ) {
        val fileName = manifest.files[category.name]
        if (fileName == null) {
            Timber.w("${category.name} was selected to restore but isn't in this backup")
            return
        }
        try {
            restore(fileName)
        } catch (e: Exception) {
            Timber.w(e, "Failed to restore ${category.name}")
        }
    }

    /** Re-applies backed-up preferences. Returns silently if the file is missing. */
    private fun restoreSettings(tree: DocumentFile, fileName: String) {
        val settings = readJson(tree, fileName, moshi.adapter(SettingsBackup::class.java))
                ?: return

        sharedPrefs.edit().apply {
            settings.booleans.filterKeys { it !in SETTINGS_DENYLIST }.forEach { (key, value) -> putBoolean(key, value) }
            settings.ints.filterKeys { it !in SETTINGS_DENYLIST }.forEach { (key, value) -> putInt(key, value) }
            settings.longs.filterKeys { it !in SETTINGS_DENYLIST }.forEach { (key, value) -> putLong(key, value) }
            settings.floats.filterKeys { it !in SETTINGS_DENYLIST }.forEach { (key, value) -> putFloat(key, value) }
            settings.strings.filterKeys { it !in SETTINGS_DENYLIST }.forEach { (key, value) -> putString(key, value) }
            settings.stringSets.filterKeys { it !in SETTINGS_DENYLIST }.forEach { (key, value) -> putStringSet(key, value.toSet()) }
        }.apply()
    }

    /** Restores the blocklist, allowlist and content filters through their repositories (idempotent). */
    private fun restoreBlocking(dir: DocumentFile, fileName: String) {
        val blocking = readJson(dir, fileName, moshi.adapter(BlockingBackup::class.java)) ?: return

        if (blocking.blockedNumbers.isNotEmpty()) {
            blockingRepo.blockNumber(*blocking.blockedNumbers.toTypedArray())
        }
        if (blocking.allowedNumbers.isNotEmpty()) {
            allowlistRepo.allowNumber(*blocking.allowedNumbers.toTypedArray())
        }
        blocking.contentFilters.forEach { filter ->
            messageContentFilterRepo.createFilter(
                    MessageContentFilterData(filter.value, filter.caseSensitive, filter.isRegex, filter.includeContacts))
        }
    }

    /**
     * Re-applies the per-conversation overlay (archived / pinned / blocked / name / flagged) by matching
     * each backed-up conversation to an existing one by recipient address. Conversations not present on
     * this device are skipped.
     */
    private fun restoreConversations(dir: DocumentFile, fileName: String) {
        val backup = readJson(dir, fileName, moshi.adapter(ConversationsBackup::class.java)) ?: return

        backup.conversations.forEach { conversation ->
            val threadId = conversationRepo.getConversation(conversation.addresses)?.id ?: return@forEach

            if (conversation.archived) conversationRepo.markArchived(threadId)
            if (conversation.pinned) conversationRepo.markPinned(threadId)
            if (conversation.blocked) {
                conversationRepo.markBlocked(listOf(threadId), conversation.blockingClient ?: 0, conversation.blockReason)
            }
            if (conversation.flagged) conversationRepo.markFlagged(listOf(threadId), conversation.flagReason)
            if (conversation.name.isNotBlank()) {
                conversationRepo.setConversationName(threadId, conversation.name).blockingAwait()
            }
        }
    }

    /**
     * Restores pending scheduled messages. Only future-dated ones are kept (past ones are obsolete and
     * would otherwise fire immediately). Each is saved and its alarm re-armed so it sends at its time;
     * the conversation link is re-resolved by recipient address.
     */
    private fun restoreScheduled(dir: DocumentFile, fileName: String) {
        val backup = readJson(dir, fileName, moshi.adapter(ScheduledBackup::class.java)) ?: return

        backup.messages
                .filter { message -> message.date > now() }
                .forEach { message ->
                    val conversationId = conversationRepo.getConversation(message.recipients)?.id ?: 0
                    val saved = scheduledMessageRepo.saveScheduledMessage(
                            message.date, message.subId, message.recipients, message.sendAsGroup,
                            message.body, message.attachments, conversationId)
                    alarmManager.setAlarm(saved.date, alarmManager.getScheduledMessageIntent(saved.id))
                }
    }

    /**
     * Restores SMS and MMS messages into the system provider, then triggers a sync. Returns false if
     * the user cancelled part-way through (so the caller can leave the progress at Idle).
     */
    private fun restoreMessages(tree: DocumentFile, fileName: String): Boolean {
        val backup = readJson(tree, fileName, moshi.adapter(Backup::class.java))
                ?: return true

        // MMS attachment binaries live alongside messages.json in this sub-folder (absent if none)
        val attachmentsDir = tree.findFile(ATTACHMENTS_DIR)

        val total = backup.messages.size + backup.mms.size
        var errorCount = 0

        backup.messages.forEachIndexed { index, message ->
            if (stopFlag) {
                stopFlag = false
                return false
            }

            // Update the progress
            restoreProgress.onNext(BackupRepository.Progress.Running(total, index))

            try {
                val values = contentValuesOf(
                        Telephony.Sms.TYPE to message.type,
                        Telephony.Sms.ADDRESS to message.address,
                        Telephony.Sms.DATE to message.date,
                        Telephony.Sms.DATE_SENT to message.dateSent,
                        Telephony.Sms.READ to message.read,
                        Telephony.Sms.SEEN to 1,
                        Telephony.Sms.STATUS to message.status,
                        Telephony.Sms.BODY to message.body,
                        Telephony.Sms.PROTOCOL to message.protocol,
                        Telephony.Sms.SERVICE_CENTER to message.serviceCenter,
                        Telephony.Sms.LOCKED to message.locked
                )

                if (prefs.canUseSubId.get()) {
                    values.put(Telephony.Sms.SUBSCRIPTION_ID, message.subId)
                }

                if (context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) == null) {
                    errorCount++
                }
            } catch (e: Exception) {
                Timber.w(e)
                errorCount++
            }
        }

        backup.mms.forEachIndexed { index, mms ->
            if (stopFlag) {
                stopFlag = false
                return false
            }

            restoreProgress.onNext(BackupRepository.Progress.Running(total, backup.messages.size + index))

            try {
                restoreMms(mms, attachmentsDir)
            } catch (e: Exception) {
                Timber.w(e)
                errorCount++
            }
        }

        if (errorCount > 0) {
            Timber.w(Exception("Failed to restore $errorCount/$total messages"))
        }

        // Sync the messages, then wait for it to finish. syncMessages() does its Realm work on a
        // background thread, so without this wait the conversation-overlay restore that follows would
        // race an empty Realm (and the restore would report "finished" while the import is still going).
        restoreProgress.onNext(BackupRepository.Progress.Syncing())
        syncRepo.syncMessages()
        awaitSyncCompletion()

        return true
    }

    /**
     * Blocks the calling (restore) thread until the asynchronous message sync has returned to Idle, so
     * everything that depends on synced conversations runs against real data. syncMessages() flips the
     * progress to Running synchronously before returning, so we wait for the genuine completion signal.
     */
    private fun awaitSyncCompletion() {
        try {
            syncRepo.syncProgress
                    .filter { progress -> progress is SyncRepository.SyncProgress.Idle }
                    .timeout(30, TimeUnit.MINUTES)
                    .blockingFirst()
        } catch (e: Exception) {
            Timber.w(e)
        }
    }

    /**
     * When a conversation's recipient list wasn't captured, derive the thread's other party from the raw
     * address rows: the sender (FROM) for an incoming MMS, the recipients (TO) for an outgoing one. This
     * avoids building a bogus "self + sender" group thread from all address rows.
     */
    private fun fallbackRecipients(mms: BackupMmsMessage): List<String> {
        val wantedType = if (mms.boxId == Telephony.Mms.MESSAGE_BOX_INBOX) PduHeaders.FROM else PduHeaders.TO
        return mms.addresses.filter { it.type == wantedType }.map { it.address }
                .ifEmpty { mms.addresses.map { it.address } }
                .distinct()
    }

    /**
     * Re-inserts one MMS into the system provider, mirroring the app's own send path: create the
     * message row, then its parts (text inline, binary written to the part's output stream), then its
     * address rows. The thread is re-resolved from the recipient list, and dates go back to seconds.
     */
    private fun restoreMms(mms: BackupMmsMessage, attachmentsDir: DocumentFile?) {
        val recipients = mms.recipients.ifEmpty { fallbackRecipients(mms) }
        if (recipients.isEmpty()) {
            Timber.w("MMS skipped: no recipients (box=${mms.boxId}, parts=${mms.parts.size})")
            return
        }

        val threadId = TelephonyCompat.getOrCreateThreadId(context, recipients)
        if (threadId <= 0L) {
            Timber.w("MMS skipped: couldn't resolve a thread for $recipients")
            return
        }

        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, mms.date / 1000L)
            put(Telephony.Mms.DATE_SENT, mms.dateSent / 1000L)
            put(Telephony.Mms.MESSAGE_BOX, mms.boxId)
            put(Telephony.Mms.READ, if (mms.read) 1 else 0)
            put(Telephony.Mms.SEEN, if (mms.seen) 1 else 0)
            put(Telephony.Mms.LOCKED, if (mms.locked) 1 else 0)
            put(Telephony.Mms.MESSAGE_TYPE, mms.messageType)
            put(Telephony.Mms.STATUS, mms.mmsStatus)
            put(Telephony.Mms.MMS_VERSION, MMS_VERSION)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            if (mms.subject.isNotBlank()) {
                put(Telephony.Mms.SUBJECT, mms.subject)
                put(Telephony.Mms.SUBJECT_CHARSET, 106)
            }
            if (prefs.canUseSubId.get()) {
                put(Telephony.Mms.SUBSCRIPTION_ID, mms.subId)
            }
        }

        val mmsUri = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, values)
        if (mmsUri == null) {
            Timber.w("MMS insert refused (box=${mms.boxId}, thread=$threadId, parts=${mms.parts.size})")
            return
        }
        val mmsId = ContentUris.parseId(mmsUri)

        // Parts: text and SMIL keep their text in the TEXT column; binary parts get their bytes
        // written to the freshly-inserted part's output stream (as the provider expects).
        val partUri = Uri.parse("content://mms/$mmsId/part")
        mms.parts.forEach { part ->
            val partValues = ContentValues().apply {
                put(Telephony.Mms.Part.MSG_ID, mmsId)
                put(Telephony.Mms.Part.CONTENT_TYPE, part.type)
                if (part.seq != -1) put(Telephony.Mms.Part.SEQ, part.seq)
                part.name?.let { name ->
                    put(Telephony.Mms.Part.NAME, name)
                    put(Telephony.Mms.Part.CONTENT_LOCATION, name)
                    put(Telephony.Mms.Part.CONTENT_ID, "<$name>")
                }
                if (part.dataFile == null) {
                    put(Telephony.Mms.Part.TEXT, part.text ?: "")
                }
            }
            val insertedPart = context.contentResolver.insert(partUri, partValues) ?: return@forEach
            part.dataFile?.let { dataFile ->
                streamAttachmentInto(attachmentsDir, dataFile, insertedPart)
            }
        }

        // Address rows: replayed verbatim so the sender (FROM) and recipients (TO) are preserved.
        val addrUri = Uri.parse("content://mms/$mmsId/addr")
        mms.addresses.forEach { addr ->
            val addrValues = ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, addr.address)
                put(Telephony.Mms.Addr.TYPE, addr.type)
                put(Telephony.Mms.Addr.CHARSET, addr.charset)
            }
            context.contentResolver.insert(addrUri, addrValues)
        }
    }

    /**
     * Streams one backed-up attachment file straight into the freshly-inserted MMS part's output
     * stream, without holding it in memory. A missing or unreadable attachment is skipped (the part is
     * simply left without its binary) rather than failing the whole restore.
     */
    @SuppressLint("Recycle") // streams closed by use{}
    private fun streamAttachmentInto(attachmentsDir: DocumentFile?, fileName: String, partUri: Uri) {
        val file = attachmentsDir?.findFile(fileName) ?: return
        try {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                context.contentResolver.openOutputStream(partUri)?.use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Timber.w(e)
        }
    }

    override fun getRestoreProgress(): Observable<BackupRepository.Progress> = restoreProgress

    override fun stopRestore() {
        stopFlag = true
    }

    private fun isBackupOrRestoreRunning(): Boolean {
        return backupProgress.blockingFirst().running || restoreProgress.blockingFirst().running
    }

    // region Backup management (list / rename / delete)

    override fun getManagedBackups(): List<BackupItem> {
        val tree = getBackupDocumentTree()
        return try {
            when {
                tree != null -> listSafBackups(tree)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> listMediaStoreBackups()
                else -> listLegacyBackups()
            }.sortedByDescending { backup -> backup.date }
        } catch (e: Exception) {
            Timber.w(e)
            emptyList()
        }
    }

    override fun renameBackup(item: BackupItem, newName: String): Boolean {
        val safeName = newName.trim().replace('/', '-')
        if (safeName.isEmpty() || safeName == item.name) return false
        val tree = getBackupDocumentTree()
        return try {
            when {
                tree != null -> renameSafBackup(tree, item, safeName)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> renameMediaStoreBackup(item, safeName)
                else -> renameLegacyBackup(item, safeName)
            }
        } catch (e: Exception) {
            Timber.w(e)
            false
        }
    }

    override fun deleteBackup(item: BackupItem): Boolean {
        val tree = getBackupDocumentTree()
        return try {
            when {
                tree != null -> deleteSafBackup(tree, item)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> deleteMediaStoreBackup(item)
                else -> deleteLegacyBackup(item)
            }
        } catch (e: Exception) {
            Timber.w(e)
            false
        }
    }

    /** Parses the default yyyy-MM-dd_HH-mm-ss backup name into a timestamp; 0 once it has been renamed. */
    private fun dateFromName(name: String): Long =
            try {
                SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).parse(name)?.time ?: 0
            } catch (e: Exception) {
                0
            }

    private fun childName(item: BackupItem): String = if (item.isZip) "${item.name}.zip" else item.name

    // SAF (the user's custom folder)

    private fun listSafBackups(tree: DocumentFile): List<BackupItem> =
            tree.listFiles().mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                when {
                    file.isDirectory && file.findFile("manifest.json") != null ->
                            BackupItem(name, dateFromName(name), false)
                    !file.isDirectory && name.endsWith(".zip", ignoreCase = true) ->
                            name.dropLast(4).let { base -> BackupItem(base, dateFromName(base), true) }
                    else -> null
                }
            }

    private fun renameSafBackup(tree: DocumentFile, item: BackupItem, newName: String): Boolean {
        val newTarget = if (item.isZip) "$newName.zip" else newName
        return tree.findFile(childName(item))?.renameTo(newTarget) ?: false
    }

    private fun deleteSafBackup(tree: DocumentFile, item: BackupItem): Boolean =
            tree.findFile(childName(item))?.delete() ?: false

    // MediaStore (the default Documents/OpenMessages location on Android 10+)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreCollection() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun listMediaStoreBackups(): List<BackupItem> {
        val basePath = "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR"
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH)
        val items = mutableListOf<BackupItem>()
        val seenFolders = mutableSetOf<String>()
        context.contentResolver.query(mediaStoreCollection(), projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$basePath/%"), null)?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val path = (cursor.getString(pathCol) ?: "").trimEnd('/')
                when {
                    // A .zip sitting directly in the OpenMessages folder is one backup
                    name.endsWith(".zip", ignoreCase = true) && path.endsWith(BACKUP_DIR) ->
                            name.dropLast(4).let { base -> items.add(BackupItem(base, dateFromName(base), true)) }
                    // A manifest.json marks a folder backup; the parent folder name is the backup name
                    name == "manifest.json" -> {
                        val folder = path.substringAfterLast('/')
                        if (folder != BACKUP_DIR && seenFolders.add(folder)) {
                            items.add(BackupItem(folder, dateFromName(folder), false))
                        }
                    }
                }
            }
        }
        return items
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteMediaStoreBackup(item: BackupItem): Boolean {
        val basePath = "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR"
        return if (item.isZip) {
            context.contentResolver.delete(mediaStoreCollection(),
                    "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf("$basePath/", "${item.name}.zip")) > 0
        } else {
            context.contentResolver.delete(mediaStoreCollection(),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$basePath/${item.name}/%")) > 0
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun renameMediaStoreBackup(item: BackupItem, newName: String): Boolean {
        val basePath = "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR"
        if (item.isZip) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "$newName.zip") }
            return context.contentResolver.update(mediaStoreCollection(), values,
                    "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf("$basePath/", "${item.name}.zip")) > 0
        }
        // Folder rename: move every file from .../<old>/... to .../<new>/... via its RELATIVE_PATH
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH)
        var moved = 0
        context.contentResolver.query(mediaStoreCollection(), projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$basePath/${item.name}/%"), null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getString(pathCol) ?: continue
                val newPath = path.replaceFirst("$basePath/${item.name}", "$basePath/$newName")
                val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, newPath) }
                try {
                    if (context.contentResolver.update(
                                    ContentUris.withAppendedId(mediaStoreCollection(), id), values, null, null) > 0) {
                        moved++
                    }
                } catch (e: Exception) {
                    Timber.w(e)
                }
            }
        }
        return moved > 0
    }

    // Legacy public storage (Android 9 and below)

    @Suppress("DEPRECATION")
    private fun legacyBackupDir() =
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), BACKUP_DIR)

    private fun listLegacyBackups(): List<BackupItem> =
            legacyBackupDir().listFiles()?.mapNotNull { file ->
                when {
                    file.isDirectory && File(file, "manifest.json").exists() ->
                            BackupItem(file.name, dateFromName(file.name), false)
                    file.isFile && file.name.endsWith(".zip", ignoreCase = true) ->
                            file.name.dropLast(4).let { base -> BackupItem(base, dateFromName(base), true) }
                    else -> null
                }
            } ?: emptyList()

    private fun renameLegacyBackup(item: BackupItem, newName: String): Boolean {
        val old = File(legacyBackupDir(), childName(item))
        val new = File(legacyBackupDir(), if (item.isZip) "$newName.zip" else newName)
        return old.exists() && !new.exists() && old.renameTo(new)
    }

    private fun deleteLegacyBackup(item: BackupItem): Boolean =
            File(legacyBackupDir(), childName(item)).deleteRecursively()

    // endregion

}
