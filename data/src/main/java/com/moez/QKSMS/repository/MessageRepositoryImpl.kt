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

import io.openmessages.manager.QkTransaction
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import android.telephony.SmsManager
import android.webkit.MimeTypeMap
import androidx.core.content.contentValuesOf
import com.google.android.mms.pdu_alt.ContentType
import com.klinker.android.send_message.SmsManagerFactory
import io.openmessages.common.util.extensions.now
import io.openmessages.compat.TelephonyCompat
import io.openmessages.database.AppDatabase
import io.openmessages.database.MessageDao
import io.openmessages.database.MessageWithRelations
import io.openmessages.database.PartWithMessages
import io.openmessages.extensions.Optional
import io.openmessages.extensions.isImage
import io.openmessages.extensions.isVideo
import io.openmessages.extensions.map
import io.openmessages.extensions.resourceExists
import io.openmessages.manager.ActiveConversationManager
import io.openmessages.manager.KeyManager
import io.openmessages.mapper.CursorToMessage
import io.openmessages.mapper.CursorToPart
import io.openmessages.model.Attachment
import io.openmessages.model.Message
import io.openmessages.model.Message.Companion.TYPE_MMS
import io.openmessages.model.Message.Companion.TYPE_SMS
import io.openmessages.model.MmsPart
import io.openmessages.receiver.MessageDeliveredReceiver
import io.openmessages.receiver.MessageSentReceiver
import io.openmessages.receiver.SendDelayedMessageReceiver
import io.openmessages.receiver.SendDelayedMessageReceiver.Companion.MESSAGE_ID_EXTRA
import io.openmessages.util.ImageUtils
import io.openmessages.util.PhoneNumberUtils
import io.openmessages.util.Preferences
import io.openmessages.util.VideoUtils
import io.openmessages.util.sha256
import io.openmessages.util.tryOrNull
import io.openmessages.data.BuildConfig
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
open class MessageRepositoryImpl @Inject constructor(
    private val activeConversationManager: ActiveConversationManager,
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val db: AppDatabase,
    private val messageIds: KeyManager,
    private val phoneNumberUtils: PhoneNumberUtils,
    private val prefs: Preferences,
    private val syncRepository: SyncRepository,
    private val reactions: EmojiReactionRepository,
    private val cursorToMessage: CursorToMessage,
    private val cursorToPart: CursorToPart,
) : MessageRepository {

    private val dao = db.messageDao()

    override val deduplicationProgress: Subject<MessageRepository.DeduplicationProgress> =
        BehaviorSubject.createDefault(MessageRepository.DeduplicationProgress.Idle)

    companion object {
        const val TELEPHONY_UPDATE_CHUNK_SIZE = 200

        /** Enough for the estimate below to settle; a stop in case it never does. */
        private const val MAX_COMPRESSION_ATTEMPTS = 6

        /**
         * What an image that has to be shrunk is encoded at.
         *
         * Under the default because an image losing most of its pixels to fit cannot also afford to
         * spend what it has left on detail nobody will make out at that size. Every byte not spent
         * on it is a pixel kept, and pixels are what was actually missing.
         */
        private const val COMPRESSED_IMAGE_QUALITY = 75
    }

    /**
     * Keeps the messages whose text holds [query], all of them when it is empty. Matched here
     * rather than in the query, where SQLite only ignores the case of ASCII letters.
     */
    private fun List<Message>.matching(query: String) = when (query.isEmpty()) {
        true -> this
        false -> filter { message ->
            message.body.contains(query, ignoreCase = true) ||
                    message.parts.any { part -> part.text?.contains(query, ignoreCase = true) == true }
        }
    }

    override fun getMessages(threadId: Long, query: String): Observable<List<Message>> =
        dao.observeMessages(threadId)
            .toObservable()
            .map { messages -> messages.map(MessageWithRelations::toModel).matching(query) }

    override fun getMessagesSync(threadId: Long, query: String): List<Message> =
        dao.getMessages(threadId).map(MessageWithRelations::toModel).matching(query)

    override fun getMessage(messageId: Long) = dao.get(messageId)?.toModel()

    override fun getMessages(messageIds: Collection<Long>): List<Message> =
        dao.get(messageIds).map(MessageWithRelations::toModel)

    override fun getMessageForPart(id: Long) = dao.getMessageForPart(id)?.toModel()

    override fun observeLastIncomingMessage(threadId: Long): Observable<Optional<Message>> =
        dao.observeLatestInBoxes(
            threadId,
            smsBoxes = listOf(Sms.MESSAGE_TYPE_INBOX, Sms.MESSAGE_TYPE_ALL),
            mmsBoxes = listOf(Mms.MESSAGE_BOX_INBOX, Mms.MESSAGE_BOX_ALL)
        )
            .toObservable()
            .map { messages -> Optional(messages.firstOrNull()?.toModel()) }

    override fun getUnreadCount() = db.conversationDao().getUnreadCount()

    override fun getPart(id: Long) = dao.getPart(id)?.toModel()

    override fun getPartsForConversation(threadId: Long): Observable<List<MmsPart>> =
        dao.observePartsForConversation(threadId)
            .toObservable()
            .map { parts -> parts.map(PartWithMessages::toModel) }

    override fun savePart(id: Long): Uri? {
        val part = getPart(id) ?: return null

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(part.type)
            ?: return null
        // fileDateAndTime is divided by 1000 in order to remove the extra 0's after date and time
        // This way the file name isn't so long.
        val fileDateAndTime = part.messages.firstOrNull()?.date?.div(1000)
        val fileName = "OpenMessages_${part.type.split("/").last()}_$fileDateAndTime.$extension"

        val values = contentValuesOf(
            MediaStore.MediaColumns.DISPLAY_NAME to fileName,
            MediaStore.MediaColumns.MIME_TYPE to part.type,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            values.put(
                MediaStore.MediaColumns.RELATIVE_PATH, when {
                    part.isImage() -> "${Environment.DIRECTORY_PICTURES}/OpenMessages"
                    part.isVideo() -> "${Environment.DIRECTORY_MOVIES}/OpenMessages"
                    else -> "${Environment.DIRECTORY_DOWNLOADS}/OpenMessages"
                }
            )
        }

        val contentUri = when {
            part.isImage() -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            part.isVideo() -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Downloads.EXTERNAL_CONTENT_URI

            else -> MediaStore.Files.getContentUri("external")
        }

        val uri = context.contentResolver.insert(contentUri, values)
        Timber.v("Saving $fileName (${part.type}) to $uri")

        uri?.let {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                context.contentResolver.openInputStream(part.getUri())?.use { inputStream ->
                    inputStream.copyTo(outputStream, 1024)
                }
            }
            Timber.v("Saved $fileName (${part.type}) to $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(
                    uri,
                    contentValuesOf(MediaStore.MediaColumns.IS_PENDING to 0),
                    null,
                    null
                )
                Timber.v("Marked $uri as not pending")
            }
        }

        return uri
    }

    override fun getUnreadUnseenMessages(threadId: Long): List<Message> =
        dao.getUnreadUnseen(threadId).map(MessageWithRelations::toModel)

    override fun getUnreadMessages(threadId: Long): List<Message> =
        dao.getUnread(threadId).map(MessageWithRelations::toModel)

    override fun observeUnreadMessages(threadId: Long): Observable<List<Message>> =
        dao.observeUnread(threadId)
            .toObservable()
            .map { messages -> messages.map(MessageWithRelations::toModel) }

    // marks all messages in threads as read and/or seen in the native provider
    private fun telephonyMarkSeenRead(
        seen: Boolean?,
        read: Boolean?,
        threadIds: Collection<Long>,
    ): Int {
        if (((seen == null) && (read == null)) || threadIds.isEmpty())
            return -1

        var countUpdated = 0

        // 'read' can be modified at the conversation level which updates all messages
        read?.let {
            tryOrNull(true) {
                // chunked so where clause doesn't get too long if there are many threads
                threadIds.forEach {
                    countUpdated += context.contentResolver.update(
                        ContentUris.withAppendedId(
                            Telephony.MmsSms.CONTENT_CONVERSATIONS_URI,
                            it
                        ),
                        contentValuesOf(Sms.READ to read),
                        "${Sms.READ} = ${if (read) 0 else 1}",
                        null
                    )
                }
            }
        }

        seen?.let {
            // 'seen' has to be modified at the messages level
            threadIds.chunked(TELEPHONY_UPDATE_CHUNK_SIZE).forEach {
                // chunked for smaller where clause size
                val values = contentValuesOf(Sms.SEEN to seen)
                val whereClause = "${Sms.SEEN} = ${if (seen) 0 else 1} " +
                        "and ${Sms.THREAD_ID} in (${it.joinToString(",")})"

                // sms messages
                tryOrNull(true) {
                    countUpdated += context.contentResolver.update(
                        Sms.CONTENT_URI, values, whereClause, null
                    )
                }

                // mms messages
                tryOrNull(true) {
                    countUpdated += context.contentResolver.update(
                        Mms.CONTENT_URI, values, whereClause, null
                    )
                }
            }
        }

        return countUpdated  // a mix of convo and message updates, so not overly useful. meh
    }

    override fun markAllSeen(): Int {
        val threadIds = db.runInTransaction<List<Long>> {
            dao.getUnseenThreadIds().also { dao.markAllSeen() }
        }

        return telephonyMarkSeenRead(true, null, threadIds)
    }

    override fun markSeen(threadIds: Collection<Long>): Int {
        dao.markSeen(threadIds)

        return telephonyMarkSeenRead(true, null, threadIds)
    }

    override fun markRead(threadIds: Collection<Long>): Int {
        if (threadIds.isEmpty()) return 0

        dao.markRead(threadIds)

        return telephonyMarkSeenRead(seen = true, read = true, threadIds = threadIds)
    }

    override fun markUnread(threadIds: Collection<Long>): Int {
        if (threadIds.isEmpty()) return 0

        dao.markLastMessagesUnread(threadIds)

        return telephonyMarkSeenRead(null, false, threadIds)
    }

    private fun syncProviderMessage(uri: Uri, sendAsGroup: Boolean): Message? {
        // if uri doesn't have valid type
        val type = when {
            uri.toString().contains(TYPE_MMS) -> TYPE_MMS
            uri.toString().contains(TYPE_SMS) -> TYPE_SMS
            // Returning null here loses the message to the app while leaving it in the provider,
            // so it still goes out and simply never appears. Worth saying out loud.
            else -> {
                if (BuildConfig.DEBUG) Timber.w("sync back: $uri names neither sms nor mms")
                return null
            }
        }

        // if uri doesn't have a valid id, fail
        val contentId = tryOrNull(false) { ContentUris.parseId(uri) } ?: run {
            if (BuildConfig.DEBUG) Timber.w("sync back: no id to read in $uri")
            return null
        }

        val stableUri = when (type) {
            TYPE_MMS -> ContentUris.withAppendedId(Mms.CONTENT_URI, contentId)
            else -> ContentUris.withAppendedId(Sms.CONTENT_URI, contentId)
        }

        return context.contentResolver.query(
            stableUri, null, null, null, null
        )?.use { cursor ->
            // if there are no rows, return null. else, move to the first row
            if (!cursor.moveToFirst()) {
                if (BuildConfig.DEBUG)
                    Timber.w("sync back: $stableUri was written but reads back with no row")
                return null
            }

            cursorToMessage.map(Pair(cursor, CursorToMessage.MessageColumns(cursor))).apply {
                this.sendAsGroup = sendAsGroup

                if (isMms()) {
                    parts = cursorToPart.getPartsCursor(contentId)
                        ?.map { cursorToPart.map(it) }
                        .orEmpty()
                }

                if (BuildConfig.DEBUG)
                    Timber.v("sync back: $stableUri is message id $id on thread $threadId")

                // The provider decides which thread a message ends up on, and it is free to pick
                // one other than the thread the conversation was opened against: the addresses are
                // resolved once to open the conversation and again, normalised, to write the
                // message, and the two need not agree. Whichever thread it chose, the message is on
                // it now, so the conversation for that thread has to exist. Without this the
                // refresh that follows a send looks for a conversation that was never created,
                // quietly does nothing, and the conversation stays out of the list for good while
                // the message itself goes out. The sync path has always done this; sending had not.
                conversationRepo.getOrCreateConversation(threadId)

                dao.upsert(this)
            }
        } ?: run {
            if (BuildConfig.DEBUG) Timber.w("sync back: $stableUri was written but cannot be queried")
            null
        }
    }

    override fun sendNewMessages(
        subId: Int, threadId: Long, toAddresses: Collection<String>, body: String,
        attachments: Collection<Attachment>, sendAsGroup: Boolean, delayMs: Int
    ): Collection<Message> {
        Timber.v("sending message(s)")

        val parts = mutableListOf<com.google.android.mms.MMSPart>()

        if (attachments.isNotEmpty()) {
            Timber.v("has attachments")
            val smsManager = SmsManagerFactory.createSmsManager(context, subId)

            // A carrier config answers 0 for anything it doesn't carry, which is what a device
            // gets when the config never loaded or the carrier is not one it knows. Zero is not a
            // limit: as a size it scales the image away to nothing, which Glide refuses outright,
            // and as a budget it leaves no room for the message at all. Only a positive number
            // states a limit, so anything else is read as none being known, which is the same
            // position the app is in when the user set the size themselves.
            val maxWidth = smsManager.carrierConfigValues
                .getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH)
                .takeIf { prefs.mmsSize.get() == -1 && it > 0 }
                ?: Int.MAX_VALUE

            val maxHeight = smsManager.carrierConfigValues
                .getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT)
                .takeIf { prefs.mmsSize.get() == -1 && it > 0 }
                ?: Int.MAX_VALUE

            var remainingBytes = when (prefs.mmsSize.get()) {
                -1 -> smsManager.carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE)
                    .takeIf { it > 0 }
                    ?: (300 * 1024) // what Android settles on when a carrier states no maximum
                0 -> Int.MAX_VALUE
                else -> prefs.mmsSize.get() * 1024
            } * 0.9 // Ugly, but buys us a bit of wiggle room

            remainingBytes -= body.takeIf { it.isNotEmpty() }?.toByteArray()?.size ?: 0

            // What the message is allowed to weigh and what is being asked to fit in it. Only
            // images and videos are ever shrunk, so an attachment reported as anything else goes
            // in whole however large it is, and the type below is what decides that.
            if (BuildConfig.DEBUG) Timber.v("mms budget ${remainingBytes.toInt()} bytes (size setting " +
                    "${prefs.mmsSize.get()}), attachments: " +
                    attachments.joinToString { attachment ->
                        "${attachment.getType(context)} ${attachment.getSize(context)} bytes" +
                                if (attachment.isImage(context) || attachment.isVideo(context)) " (shrinkable)"
                                else " (as is)"
                    })

            // Attach those that can't be compressed (ie. everything but images and videos)
            parts += attachments
                .filter { !it.isImage(context) && !it.isVideo(context) }
                // filter in only items that exist (user may have deleted the file)
                .filter { it.uri.resourceExists(context) }
                .map {
                    remainingBytes -= it.getResourceBytes(context).size
                    val part = com.google.android.mms.MMSPart().apply {
                        MimeType = it.getType(context)
                        Name = it.getName(context)
                        Data = it.getResourceBytes(context)
                    }

                    // release the attachment hold on the image bytes so the GC can reclaim
                    it.releaseResourceBytes()

                    part
                }

            val images = attachments
                // filter in images only
                .filter { it.isImage(context) }
                // filter in only items that exist (user may have deleted the file)
                .filter { it.uri.resourceExists(context) }

            // Each video gets an even share of what is left, alongside the images, which then
            // shrink to whatever the videos didn't take. One already within its share goes as it is.
            val videos = attachments
                .filter { it.isVideo(context) }
                .filter { it.uri.resourceExists(context) }
            val videoShare = remainingBytes.toLong() / (videos.size + images.size).coerceAtLeast(1)
            parts += videos.map { video ->
                val size = video.getSize(context)
                val scaled = if (size in 0..videoShare) null
                    else VideoUtils.getScaledVideo(context, video.uri, videoShare)
                val bytes = scaled ?: video.getResourceBytes(context)
                remainingBytes -= bytes.size
                video.releaseResourceBytes()

                com.google.android.mms.MMSPart().apply {
                    MimeType = if (scaled != null) ContentType.VIDEO_MP4 else video.getType(context)
                    Name = video.getName(context)
                        .let { name -> if (scaled != null) name.substringBeforeLast('.') + ".mp4" else name }
                    Data = bytes
                }
            }

            // Encoding an image at full size is the slowest thing here, and the result is thrown
            // away whenever it lands over budget, which for a photo off a phone camera it always
            // does. A file already heavier on its own than the whole message may weigh cannot come
            // out under it, so that much is settled from the file and the encoding is not done:
            // the shrinking below starts from the image's dimensions, which is where it was headed
            // anyway.
            val imageBytesByAttachment = images
                .filter { attachment -> attachment.getSize(context) <= remainingBytes }
                .associateWith {
                    when (it.getType(context) == "image/gif") {
                        true -> ImageUtils.getScaledGif(context, it.uri, maxWidth, maxHeight)
                        false -> ImageUtils.getScaledImage(context, it.uri, maxWidth, maxHeight)
                    }
                }
                .toMutableMap()

            // What each image counts as while the budget is divided between them: what it encoded
            // to where that was worth finding out, the size of the file itself where it was not.
            val imageSizes = images.associateWith { attachment ->
                imageBytesByAttachment[attachment]?.size?.toLong() ?: attachment.getSize(context)
            }

            val imageByteCount = imageSizes.values.sum()
            if (imageByteCount > remainingBytes) {
                images.forEach { attachment ->
                    val uri = attachment.uri
                    val originalSize = imageSizes.getValue(attachment)
                    val maxBytes = originalSize / imageByteCount.toFloat() * remainingBytes

                    // Get the image dimensions
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(
                        context.contentResolver.openInputStream(uri),
                        null,
                        options
                    )
                    val width = options.outWidth
                    val height = options.outHeight
                    val aspectRatio = width.toFloat() / height.toFloat()

                    var attempts = 0

                    // Null where the image was never encoded, which is the whole point above: it
                    // has nothing to compare against yet, so the first pass below is taken.
                    var scaledBytes = imageBytesByAttachment[attachment]

                    // The area to try next, as a fraction of the original. The opening guess takes
                    // a file's weight to follow its area, which is near enough to start from and
                    // not near enough to land on, so each attempt corrects it by what it actually
                    // produced. Stepping blindly down a fixed ladder instead, as this did, took
                    // five attempts to fit a photo and finished at half the size allowed: slow, and
                    // paid for in the picture. Aiming a little under each time settles it just
                    // below the mark rather than circling it.
                    var scale = maxBytes / originalSize * 0.9

                    while (scaledBytes == null || scaledBytes.size > maxBytes) {
                        if (scale <= 0 || attempts >= MAX_COMPRESSION_ATTEMPTS) {
                            Timber.w(
                                "Failed to compress ${
                                    originalSize / 1024
                                }Kb to ${maxBytes.toInt() / 1024}Kb"
                            )

                            // Give up on shrinking it, but not on sending it: an image that was
                            // never encoded has no bytes to fall back on yet, and leaving it out
                            // would drop it from the message without a word.
                            if (scaledBytes == null) {
                                imageBytesByAttachment[attachment] =
                                    when (attachment.getType(context) == "image/gif") {
                                        true -> ImageUtils.getScaledGif(
                                            context, uri, maxWidth, maxHeight
                                        )

                                        false -> ImageUtils.getScaledImage(
                                            context, uri, maxWidth, maxHeight,
                                            COMPRESSED_IMAGE_QUALITY
                                        )
                                    }
                            }

                            return@forEach
                        }

                        val newArea = scale * width * height
                        val newWidth = sqrt(newArea * aspectRatio).toInt()
                        val newHeight = (newWidth / aspectRatio).toInt()

                        // Nothing to ask for below a pixel, and asking is an error rather than a
                        // small image.
                        if (newWidth < 1 || newHeight < 1) {
                            scale = 0.0
                            continue
                        }

                        attempts++
                        scaledBytes = when (attachment.getType(context) == "image/gif") {
                            true -> ImageUtils.getScaledGif(
                                context, attachment.uri, newWidth, newHeight
                            )

                            false -> ImageUtils.getScaledImage(
                                context, attachment.uri, newWidth, newHeight,
                                COMPRESSED_IMAGE_QUALITY
                            )
                        }

                        Timber.d(
                            "Compression attempt $attempts: ${
                                scaledBytes.size / 1024
                            }/${maxBytes.toInt() / 1024}Kb ($width*$height -> $newWidth*${
                                newHeight
                            })"
                        )

                        // How far off it landed is how far the next guess has to move.
                        scale *= maxBytes / scaledBytes.size * 0.9

                        // release the attachment hold on the image bytes so the GC can reclaim
                        attachment.releaseResourceBytes()
                    }

                    val compressedBytes = scaledBytes

                    Timber.v(
                        "Compressed ${originalSize / 1024}Kb to ${
                            compressedBytes.size / 1024
                        }Kb with a target size of ${
                            maxBytes.toInt() / 1024
                        }Kb in $attempts attempts"
                    )
                    imageBytesByAttachment[attachment] = compressedBytes
                }
            }

            imageBytesByAttachment.forEach { (attachment, bytes) ->
                parts += com.google.android.mms.MMSPart().apply {
                    MimeType =
                        if (attachment.getType(context) == "image/gif") ContentType.IMAGE_GIF
                        else ContentType.IMAGE_JPEG
                    Name = attachment.getName(context)
                    Data = bytes
                }
            }
        }

        // What actually goes out, after any shrinking. Against the budget logged above, this says
        // whether the message was brought down to size or simply never was.
        if (BuildConfig.DEBUG) Timber.v("mms parts ready: ${parts.size} part(s), " +
                "${parts.sumOf { part -> part.Data?.size ?: 0 }} bytes")

        Timber.v("create os provider message")

        // 3 stage sending process - stage 1, create records in os provider
        val group = (sendAsGroup && (toAddresses.size > 1))
        val messageUri = QkTransaction.createMessage(
            context, subId, threadId, body, prefs.signature.get(),
            toAddresses.map(phoneNumberUtils::normalizeNumber).toTypedArray(),
            parts, group, prefs.longAsMms.get(), prefs.unicode.get()
        )

        if (messageUri == Uri.EMPTY) {
            Timber.v("create os provider message failed")
            return listOf()
        }

        val message = syncProviderMessage(messageUri, group)
        if (message == null) {
            Timber.v("sync message failed for uri $messageUri")
            return listOf()
        }

        Timber.v("created message id ${message.id} from uri $messageUri")

        if (delayMs > 0) {  // if delaying
            val sendTime = (now() + delayMs)

            // set delay time on the db message
            message.date = sendTime
            dao.setDate(message.id, sendTime)

            // create alarm that will trigger sending the message
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, sendTime, getIntentForDelayedSms(message.id)
                )

            Timber.v("set ${delayMs}ms delay for message id ${message.id}")

            return listOf(message)
        }

        // send now (message will be exploded, as required, and all sent)
        return sendMessage(message)
    }

    override fun sendMessage(message: Message): Collection<Message> {
        val retVal = mutableListOf<Message>()

        tryOrNull(true) {
            // explode message if needed
            val explodedMessages = QkTransaction.explodeMessage(
                context, message.getUri(), message.sendAsGroup
            ).filter { explodedMessageUri -> (explodedMessageUri != Uri.EMPTY) }

            // if multiple messages to send, create each and recurse to send
            if (explodedMessages.size > 1) {
                explodedMessages.forEach { explodedMessageUri ->
                    val childMessage = syncProviderMessage(explodedMessageUri, message.sendAsGroup)
                    if (childMessage != null) {
                        Timber.v("created message id ${childMessage.id} from uri $explodedMessageUri")
                        retVal.addAll(sendMessage(childMessage))
                    }
                    else
                        Timber.e("sync failed for uri $explodedMessageUri")
                }

                // mark original message as sent
                markSent(message.id)
            } else {
                markSending(message.id)

                // individual message to send, send it
                val sentIntent = Intent(context, MessageSentReceiver::class.java)
                    .putExtra(MessageSentReceiver.EXTRA_OPENMESSAGES_MESSAGE_ID, message.id)

                val deliveryIntent =
                    if (prefs.delivery.get())
                        Intent(context, MessageDeliveredReceiver::class.java)
                            .putExtra(MessageDeliveredReceiver.EXTRA_OPENMESSAGES_MESSAGE_ID, message.id)
                    else null

                // use values from os provider to resend the message, except subId
                if (!QkTransaction.sendMessage(context, message.getUri(), sentIntent, deliveryIntent))
                    Timber.e("message id ${message.id} not sent by smsmms")
            }

            retVal.add(message)
        }

        return retVal
    }

    override fun sendMessage(messageId: Long): Collection<Message> {
        val message = getMessage(messageId) ?: return listOf()

        // Retrying used to hand the stored record back to the provider untouched, so a message it
        // had already refused could only be refused again, however many times it was tried. That is
        // what an MMS too large for the carrier does: nothing about it changes between attempts, and
        // shrinking only ever happens while a message is being built. Build it again from its own
        // attachments and let it through the same shrinking a first send gets, so a retry is one.
        if (message.isMms() && message.isFailedMessage()) {
            val attachments = message.parts
                .filter { part -> part.type.startsWith("image/") || part.type.startsWith("video/") }
                .map { part -> Attachment(context, part.getUri()) }

            val addresses = conversationRepo.getConversation(message.threadId)
                ?.recipients?.map { recipient -> recipient.address }
                .orEmpty()

            if (attachments.isNotEmpty() && addresses.isNotEmpty()) {
                if (BuildConfig.DEBUG) Timber.v("rebuilding failed message $messageId from " +
                        "${attachments.size} attachment(s) before retrying")

                val rebuilt = sendNewMessages(message.subId, message.threadId, addresses,
                    message.getText(withSubject = false), attachments, message.sendAsGroup)

                // Only once the replacement exists, so a rebuild that comes to nothing leaves the
                // message where it was rather than losing it.
                if (rebuilt.isNotEmpty()) {
                    deleteMessages(listOf(messageId))
                    return rebuilt
                }

                if (BuildConfig.DEBUG)
                    Timber.w("rebuilding message $messageId produced nothing, sending it as it stands")
            }
        }

        return sendMessage(message)
    }

    override fun cancelDelayedSmsAlarm(messageId: Long) =
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(getIntentForDelayedSms(messageId))

    private fun getIntentForDelayedSms(messageId: Long) =
        PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            Intent(context, SendDelayedMessageReceiver::class.java)
                .putExtra(MESSAGE_ID_EXTRA, messageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    override fun insertReceivedSms(subId: Int, address: String, body: String, sentTime: Long)
    : Message {
        val threadId = TelephonyCompat.getOrCreateThreadId(context, address)

        // One reception time, written to both stores. Left out of the values below, the provider
        // stamps its own System.currentTimeMillis() at insert, while the database copy read another one
        // a few milliseconds later: the same message then carried two different dates until the next
        // full sync rebuilt the database from the provider. Anything comparing the two, the
        // restore's already-present check for one, could never match them up.
        val receivedTime = System.currentTimeMillis()

        // insert the message to the native content provider
        val values = contentValuesOf(
            Sms.ADDRESS to address,
            Sms.BODY to body,
            Sms.DATE to receivedTime,
            Sms.DATE_SENT to sentTime,
            Sms.THREAD_ID to threadId
        )

        if (prefs.canUseSubId.get())
            values.put(Sms.SUBSCRIPTION_ID, subId)

        val providerContentId = context.contentResolver.insert(Sms.Inbox.CONTENT_URI, values)
            ?.let { insertedUri -> ContentUris.parseId(insertedUri) }
            ?: 0

        // insert the message to the database
        val message = Message().apply {
            id = messageIds.newId()

            this.address = address
            this.body = body
            this.dateSent = sentTime
            this.threadId = threadId
            this.subId = subId

            date = receivedTime

            contentId = providerContentId
            boxId = Sms.MESSAGE_TYPE_INBOX
            type = TYPE_SMS
            read = (activeConversationManager.getActiveConversation() == threadId)
        }

        dao.upsert(message)

        val parsedReaction = reactions.parseEmojiReaction(body)
        if (parsedReaction != null) {
            val targetMessage = reactions.findTargetMessage(message.threadId, parsedReaction.originalMessage)
            reactions.saveEmojiReaction(message, parsedReaction, targetMessage)
        }

        return message
    }

    override fun markAsSendingNow(messageId: Long) = dao.setDate(messageId, System.currentTimeMillis())

    /**
     * Marks the message as sending, in case we need to retry sending it
     */
    override fun markSending(messageId: Long) {
        val message = getMessage(messageId) ?: return

        // Update the message in the database
        dao.setBoxId(message.id, when (message.isSms()) {
            true -> Sms.MESSAGE_TYPE_OUTBOX
            false -> Mms.MESSAGE_BOX_OUTBOX
        })

        // Update the message in the native ContentProvider
        context.contentResolver.update(
            message.getUri(),
            when (message.isSms()) {
                true -> contentValuesOf(Sms.TYPE to Sms.MESSAGE_TYPE_OUTBOX)
                false -> contentValuesOf(Mms.MESSAGE_BOX to Mms.MESSAGE_BOX_OUTBOX)
            },
            null,
            null
        )
    }

    override fun markSent(messageId: Long) {
        Timber.v("mark message id $messageId as sent")

        val message = getMessage(messageId) ?: return

        if (message.isSms()) {
            // update the message in the database
            dao.setBoxId(message.id, Sms.MESSAGE_TYPE_SENT)

            // Update the message in the native ContentProvider
            context.contentResolver.update(
                message.getUri(),
                contentValuesOf(Sms.TYPE to Sms.MESSAGE_TYPE_SENT),
                null,
                null
            )
        } else {
            // update the message in the database
            dao.setBoxId(message.id, Mms.MESSAGE_BOX_SENT)

            // Update the message in the native ContentProvider
            context.contentResolver.update(
                message.getUri(),
                contentValuesOf(Mms.MESSAGE_BOX to Mms.MESSAGE_BOX_SENT),
                null,
                null
            )
        }
    }

    override fun markFailed(messageId: Long, resultCode: Int): Boolean {
        Timber.v("mark message id $messageId as failed. code $resultCode")

        val message = getMessage(messageId) ?: return false

        if (message.isSms()) {
            if (message.boxId == Sms.MESSAGE_TYPE_FAILED) return false

            // Update the message in the database
            dao.setFailed(message.id, Sms.MESSAGE_TYPE_FAILED, resultCode)

            // Update the message in the native ContentProvider
            context.contentResolver.update(
                message.getUri(),
                contentValuesOf(
                    Sms.TYPE to Sms.MESSAGE_TYPE_FAILED,
                    Sms.ERROR_CODE to resultCode,
                ),
                null,
                null
            )
        } else {  // mms
            if (message.boxId == Mms.MESSAGE_BOX_FAILED) return false

            // Update the message in the database
            dao.setFailed(message.id, Mms.MESSAGE_BOX_FAILED, resultCode)

            // Update the message in the native ContentProvider
            context.contentResolver.update(
                message.getUri(),
                contentValuesOf(
                    Mms.MESSAGE_BOX to Mms.MESSAGE_BOX_FAILED
                ),
                null,
                null
            )

            // TODO this query isn't able to find any results
            // Need to figure out why the message isn't appearing in the PendingMessages Uri,
            // so that we can properly assign the error type
            context.contentResolver.update(
                Telephony.MmsSms.PendingMessages.CONTENT_URI,
                contentValuesOf(
                    Telephony.MmsSms.PendingMessages.ERROR_TYPE to Telephony.MmsSms.ERR_TYPE_GENERIC_PERMANENT
                ),
                "${Telephony.MmsSms.PendingMessages.MSG_ID} = ?",
                arrayOf(message.id.toString())
            )
        }

        return true
    }

    override fun markDelivered(messageId: Long) {
        Timber.v("mark message id $messageId as delivered")

        val message = getMessage(messageId) ?: return

        // Read the clock once: the two stores must record the same instant, not two
        // readings a few milliseconds apart (see insertReceivedSms).
        val deliveredTime = System.currentTimeMillis()

        // Update the message in the database
        dao.setDelivered(message.id, Sms.STATUS_COMPLETE, deliveredTime)

        // Update the message in the native ContentProvider
        context.contentResolver.update(
            message.getUri(),
            contentValuesOf(
                Sms.STATUS to Sms.STATUS_COMPLETE,
                Sms.DATE_SENT to deliveredTime,
                Sms.READ to true,
            ),
            null,
            null
        )
    }

    override fun markDeliveryFailed(messageId: Long, resultCode: Int) {
        Timber.v("mark message id $messageId as delivery failed result code $resultCode")

        val message = getMessage(messageId) ?: return

        // Same single clock reading as markDelivered above.
        val failedTime = System.currentTimeMillis()

        // Update the message in the database
        dao.setDeliveryFailed(message.id, Sms.STATUS_FAILED, failedTime, resultCode)

        // Update the message in the native ContentProvider
        context.contentResolver.update(
            message.getUri(),
            contentValuesOf(
                Sms.STATUS to Sms.STATUS_FAILED,
                Sms.DATE_SENT to failedTime,
                Sms.READ to true,
                Sms.ERROR_CODE to resultCode,
            ),
            null,
            null
        )
    }

    override fun deleteMessages(messageIds: Collection<Long>) {
        val messages = dao.get(messageIds)

        messages.forEach { message ->
            val uri = message.message.getUri()
            if (uri != Uri.EMPTY)
                context.contentResolver.delete(uri, null, null)
        }

        dao.delete(messages.map { message -> message.message.id })
    }

    override fun getOldMessageCounts(maxAgeDays: Int) =
        dao.getOldMessageCounts(now() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong()))
            .associate { count -> count.threadId to count.count }

    override fun deleteOldMessages(maxAgeDays: Int) {
        val messages = dao.getOlderThan(now() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong()))

        val uris = messages.map { it.getUri() }

        dao.delete(messages.map { message -> message.id })

        uris.forEach {
            uri -> context.contentResolver.delete(uri, null, null)
        }
    }

    override fun deduplicateMessages(): Flowable<MessageRepository.DeduplicationResult> =
        Flowable.fromCallable {
            val duplicateIds = findDuplicateMessages()
            if (duplicateIds.isEmpty()) {
                MessageRepository.DeduplicationResult.NoDuplicates
            } else {
                deduplicationProgress.onNext(MessageRepository.DeduplicationProgress.Running(0, 0, true))
                deleteMessages(duplicateIds)
                MessageRepository.DeduplicationResult.Success
            }
        }
        .onErrorReturn { MessageRepository.DeduplicationResult.Failure(it) }
        .doFinally {
            deduplicationProgress.onNext(MessageRepository.DeduplicationProgress.Idle)
        }

    private fun findDuplicateMessages(): List<Long> {
        val seenSignatures = HashSet<String>()
        val duplicateIds = ArrayList<Long>()

        val allMessages = dao.getSignatures()

        val max = allMessages.size
        var progress = 0

        allMessages.forEach { message ->
            ++progress
            tryOrNull {
                if (progress % 100 == 0 || progress == max) {
                    deduplicationProgress.onNext(
                        MessageRepository.DeduplicationProgress.Running(max, progress, false)
                    )
                }
                val signature = messageHash(message)
                if (!seenSignatures.add(signature)) {
                    duplicateIds.add(message.id)
                }
            }
        }
        return duplicateIds
    }

    private fun messageHash(message: MessageDao.MessageSignature): String {
        val signatureString= buildString {
            append(message.address).append('|')
            append(message.dateSent).append('|')
            append(message.boxId).append('|')
            append(message.body).append('|')
            append(message.attachmentTypeString)
        }
        return sha256(signatureString)
    }
}
