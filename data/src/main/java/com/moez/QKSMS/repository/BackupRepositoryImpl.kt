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
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.core.content.contentValuesOf
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import io.openmessages.common.util.extensions.now
import io.openmessages.compat.TelephonyCompat
import io.openmessages.manager.AlarmManager
import io.openmessages.model.AllowedNumber
import io.openmessages.model.BackupCategory
import io.openmessages.model.BackupFolder
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Timer
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
        /** Bumped when the on-disk backup format changes. Version 3 added MMS messages and attachments. */
        private const val BACKUP_FORMAT_VERSION = 3

        /** MMS protocol version stamped on restored messages (matches the app's own send path). */
        private const val MMS_VERSION = 18

        /** Backups are written automatically under this folder inside the shared Documents directory. */
        private const val BACKUP_DIR = "OpenMessages"

        /**
         * Preference keys that are device-specific or internal and must never be carried across a
         * restore (e.g. the SAF tree Uri of the backup folder, which is meaningless on another
         * device/install).
         */
        private val SETTINGS_DENYLIST = setOf("backupDirectory")
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
     * them); the telephony provider wants seconds, so restore divides by 1000. Binary parts carry their
     * bytes base64-encoded in [BackupMmsPart.data]; text and SMIL parts keep their text inline instead.
     * [recipients] is the conversation's recipient list, used to re-resolve the thread on restore.
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
        val data: String? = null
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

    override fun performBackup(categories: Set<BackupCategory>) {
        // If a backup or restore is already running, or nothing was selected, don't do anything
        if (isBackupOrRestoreRunning() || categories.isEmpty()) return

        val createdAt = now()
        val folderName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(createdAt)

        // Build a payload per selected category before touching the filesystem. Each backup lives in
        // its own date-and-time sub-folder, holding one plainly-named file per category and a manifest.
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
                    messageToBackupMms(message, realm)
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

        try {
            val manifest = BackupManifest(
                    version = BACKUP_FORMAT_VERSION,
                    createdAt = createdAt,
                    app = "OpenMessages",
                    files = payloads.entries.associate { (category, payload) -> category.name to payload.first },
                    counts = counts)

            // Assemble every file of this backup set (one per category, plus the manifest).
            val backupFiles = linkedMapOf<String, ByteArray>()
            payloads.values.forEach { (fileName, bytes) -> backupFiles[fileName] = bytes }
            backupFiles["manifest.json"] =
                    moshi.adapter(BackupManifest::class.java).indent("\t").toJson(manifest).toByteArray()

            // Write to the user's custom folder if they picked one, otherwise straight to the default
            // Documents/OpenMessages (no folder to pick).
            val overrideTree = getBackupDocumentTree()
            if (overrideTree != null) {
                writeBackupSetSaf(overrideTree, folderName, backupFiles)
            } else {
                writeBackupSet(folderName, backupFiles)
            }
        } catch (e: Exception) {
            Timber.w(e)
        }

        // Mark the task finished, and set it as Idle a second later
        backupProgress.onNext(BackupRepository.Progress.Finished())
        Timer().schedule(1000) { backupProgress.onNext(BackupRepository.Progress.Idle()) }
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
     * Writes all files of a backup set into a date-and-time sub-folder of Documents/OpenMessages,
     * creating the folders as needed. No folder needs to be picked by the user.
     */
    private fun writeBackupSet(folderName: String, files: Map<String, ByteArray>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeBackupSetMediaStore(folderName, files)
        } else {
            writeBackupSetLegacy(folderName, files)
        }
    }

    /**
     * Android 10+: write through MediaStore so nothing has to be picked and no storage permission is
     * required - the app owns the files it creates in the shared Documents collection.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeBackupSetMediaStore(folderName: String, files: Map<String, ByteArray>) {
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR/$folderName"
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        files.forEach { (fileName, bytes) ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val uri = context.contentResolver.insert(collection, values)
                    ?: throw Exception("Failed to create $fileName via MediaStore")
            (context.contentResolver.openOutputStream(uri)
                    ?: throw Exception("Failed to open output stream for $fileName")).use { it.write(bytes) }
        }
    }

    /** Android 9 and below: write straight to the public Documents directory (legacy storage). */
    @Suppress("DEPRECATION")
    private fun writeBackupSetLegacy(folderName: String, files: Map<String, ByteArray>) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "$BACKUP_DIR/$folderName")
        if (!dir.exists() && !dir.mkdirs()) throw Exception("Failed to create backup directory $dir")

        files.forEach { (fileName, bytes) ->
            File(dir, fileName).outputStream().use { it.write(bytes) }
        }
    }

    /** Writes a backup set into a date-and-time sub-folder of a folder the user picked via SAF. */
    private fun writeBackupSetSaf(tree: DocumentFile, folderName: String, files: Map<String, ByteArray>) {
        val backupDir = tree.createDirectory(folderName)
                ?: throw Exception("Failed to create backup sub-folder")
        files.forEach { (fileName, bytes) -> writeJson(backupDir, fileName, bytes) }
    }

    private fun writeJson(folder: DocumentFile, fileName: String, bytes: ByteArray) {
        val file = folder.createFile("application/json", fileName)
                ?: throw Exception("Failed to create $fileName")
        (context.contentResolver.openOutputStream(file.uri)
                ?: throw Exception("Failed to open output stream for $fileName")).use { it.write(bytes) }
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
     * restore), the raw address rows from the provider, and every part. Binary parts have their bytes
     * read from the provider and base64-encoded; text and SMIL parts keep their text inline.
     */
    private fun messageToBackupMms(message: Message, realm: Realm): BackupMmsMessage {
        val recipients = realm.where(Conversation::class.java)
                .equalTo("id", message.threadId)
                .findFirst()
                ?.recipients
                ?.mapNotNull { recipient -> recipient.address.takeIf { it.isNotBlank() } }
                ?: emptyList()

        val parts = message.parts.map { part ->
            val isText = part.type.startsWith("text/", ignoreCase = true) ||
                    part.type.equals("application/smil", ignoreCase = true)
            BackupMmsPart(
                    type = part.type,
                    seq = part.seq,
                    name = part.name,
                    text = if (isText) part.text else null,
                    data = if (isText) null else readPartData(part))
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

    /** Reads one binary MMS part's bytes from the provider and base64-encodes them. */
    @SuppressLint("Recycle") // InputStream closed by use{}
    private fun readPartData(part: MmsPart): String? {
        return try {
            context.contentResolver.openInputStream(part.getUri())?.use { input ->
                Base64.encodeToString(input.readBytes(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Timber.w(e)
            null
        }
    }

    override fun getBackupProgress(): Observable<BackupRepository.Progress> = backupProgress

    override fun listBackups(folder: Uri): List<BackupFolder> {
        return try {
            val tree = DocumentFile.fromTreeUri(context, folder) ?: return emptyList()
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

        val tree = DocumentFile.fromTreeUri(context, folder)
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

        // Never let a failure in one category leave the progress stuck (the screen would show
        // "Parsing…" forever). Log it and still mark the restore finished below.
        try {
            // Settings first (instant); it seeds templates and every toggle before messages come in.
            if (BackupCategory.SETTINGS in categories) {
                manifest.files[BackupCategory.SETTINGS.name]?.let { fileName -> restoreSettings(backupDir, fileName) }
            }

            if (BackupCategory.BLOCKING in categories) {
                manifest.files[BackupCategory.BLOCKING.name]?.let { fileName -> restoreBlocking(backupDir, fileName) }
            }

            if (BackupCategory.MESSAGES in categories) {
                manifest.files[BackupCategory.MESSAGES.name]?.let { fileName ->
                    if (!restoreMessages(backupDir, fileName)) {
                        // The user cancelled mid-restore
                        restoreProgress.onNext(BackupRepository.Progress.Idle())
                        return
                    }
                }
            }

            // Conversation overlay last: it matches by address against the (now synced) conversations.
            if (BackupCategory.CONVERSATIONS in categories) {
                manifest.files[BackupCategory.CONVERSATIONS.name]?.let { fileName -> restoreConversations(backupDir, fileName) }
            }

            if (BackupCategory.SCHEDULED in categories) {
                manifest.files[BackupCategory.SCHEDULED.name]?.let { fileName -> restoreScheduled(backupDir, fileName) }
            }
        } catch (e: Exception) {
            Timber.w(e)
        }

        // Mark the task finished, and set it as Idle a second later
        restoreProgress.onNext(BackupRepository.Progress.Finished())
        Timer().schedule(1000) { restoreProgress.onNext(BackupRepository.Progress.Idle()) }
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

                context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
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
                restoreMms(mms)
            } catch (e: Exception) {
                Timber.w(e)
                errorCount++
            }
        }

        if (errorCount > 0) {
            Timber.w(Exception("Failed to restore $errorCount/$total messages"))
        }

        // Sync the messages
        restoreProgress.onNext(BackupRepository.Progress.Syncing())
        syncRepo.syncMessages()

        return true
    }

    /**
     * Re-inserts one MMS into the system provider, mirroring the app's own send path: create the
     * message row, then its parts (text inline, binary written to the part's output stream), then its
     * address rows. The thread is re-resolved from the recipient list, and dates go back to seconds.
     */
    private fun restoreMms(mms: BackupMmsMessage) {
        val recipients = mms.recipients.ifEmpty { mms.addresses.map { it.address }.distinct() }
        if (recipients.isEmpty()) return

        val threadId = TelephonyCompat.getOrCreateThreadId(context, recipients)

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

        val mmsUri = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, values) ?: return
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
                if (part.data == null) {
                    put(Telephony.Mms.Part.TEXT, part.text ?: "")
                }
            }
            val insertedPart = context.contentResolver.insert(partUri, partValues) ?: return@forEach
            part.data?.let { encoded ->
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                context.contentResolver.openOutputStream(insertedPart)?.use { it.write(bytes) }
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

    override fun getRestoreProgress(): Observable<BackupRepository.Progress> = restoreProgress

    override fun stopRestore() {
        stopFlag = true
    }

    private fun isBackupOrRestoreRunning(): Boolean {
        return backupProgress.blockingFirst().running || restoreProgress.blockingFirst().running
    }

}
