/*
 * Copyright (C) 2025
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
import com.squareup.moshi.Moshi
import io.openmessages.database.AppDatabase
import io.openmessages.database.MessageWithRelations
import io.openmessages.manager.KeyManager
import io.openmessages.model.EmojiReaction
import io.openmessages.model.Message
import io.openmessages.util.EmojiPatternStrings
import timber.log.Timber
import javax.inject.Inject

class EmojiReactionRepositoryImpl @Inject constructor(
    private val context: Context,
    private val db: AppDatabase,
    private val keyManager: KeyManager,
    private val moshi: Moshi,
) : EmojiReactionRepository {
    private val messageDao = db.messageDao()
    private val reactionDao = db.emojiReactionDao()

    // We use an ordered map to make sure we can test tapback regexes before generic ones
    private val reactionPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?> = linkedMapOf(
        Regex( // Google Messages
            "(?s)^\u200a[^\u200b\u200a]*\u200b([^\u200b]*)\u200b[^\u200b\u200a]*\u200a(.*)\u200a[^\u200b\u200a]*\u200a\\Z"
        ) to { match ->
            ParsedEmojiReaction(
            match.groupValues[1], match.groupValues[2]
            )
        }
    )
    private val removalPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?> = linkedMapOf(
        Regex( // Google Messages
            "(?s)^\u200a[^\u200c\u200a]*\u200c([^\u200c]*)\u200c[^\u200c\u200a]*\u200a(.*)\u200a[^\u200c\u200a]*\u200a\\Z"
        ) to { match ->
            ParsedEmojiReaction(
                match.groupValues[1], match.groupValues[2], isRemoval = true
            )
        }
    )

    init {
        val assetEntries = loadEmojiPatternEntriesFromAssets()
        assetEntries.forEach { (localeTag, strings) ->
            try {
                addPatternsForLocaleStrings(localeTag, strings, reactionPatterns, removalPatterns)
            } catch (e: Exception) {
                Timber.w(e, "Failed to load asset patterns for locale: $localeTag")
            }
        }
        Timber.i("Loaded emoji reaction patterns for locales: ${assetEntries.map { it.first }}")
    }

    private fun addPatternsForLocaleStrings(
        localeTag: String,
        strings: EmojiPatternStrings,
        reactionPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?>,
        removalPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?>
    ) {
        // iOS tapbacks (important to add these before generic emoji patterns as the regexes may overlap)
        listOf(
            Triple("❤️", strings.iosHeartAdded, strings.iosHeartRemoved),
            Triple("👍", strings.iosLikeAdded, strings.iosLikeRemoved),
            Triple("👎", strings.iosDislikeAdded, strings.iosDislikeRemoved),
            Triple("😂", strings.iosLaughAdded, strings.iosLaughRemoved),
            Triple("‼️", strings.iosExclamationAdded, strings.iosExclamationRemoved),
            Triple("❓", strings.iosQuestionMarkAdded, strings.iosQuestionMarkRemoved)
        ).forEach { (emoji, added, removed) ->
            added?.let {
                reactionPatterns[Regex(it)] =
                    { match -> ParsedEmojiReaction(emoji, match.groupValues[1]) }
            }
            removed?.let {
                removalPatterns[Regex(it)] =
                    { match -> ParsedEmojiReaction(emoji, match.groupValues[1], isRemoval = true) }
            }
        }

        // Generic iOS emoji patterns
        strings.iosGenericAdded?.let { pattern ->
            reactionPatterns[Regex(pattern)] = { match ->
                if (match.groupValues.getOrNull(1) == "with a sticker") null // TODO: localize "with a sticker"
                else ParsedEmojiReaction(match.groupValues[1], match.groupValues[2])
            }
        }
        strings.iosGenericRemoved?.let { pattern ->
            removalPatterns[Regex(pattern)] = { match ->
                ParsedEmojiReaction(match.groupValues[1], match.groupValues[2], isRemoval = true)
            }
        }

        Timber.d("Loaded emoji regex patterns for $localeTag from assets")
    }

    private fun loadEmojiPatternEntriesFromAssets(): List<Pair<String, EmojiPatternStrings>> {
        val dir = "emojis"
        val files = context.assets.list(dir) ?: emptyArray()
        return files.filter { it.endsWith(".json", ignoreCase = true) }
            .mapNotNull { filename ->
                val localeTag = filename.removeSuffix(".json")
                try {
                    val json = context.assets.open("$dir/$filename")
                        .bufferedReader().use {
                            it.readText()
                        }
                    val data = parseEmojiPatternsJson(json)
                    localeTag to data
                } catch (e: Exception) {
                    Timber.w(e, "Failed parsing emoji patterns asset: $filename")
                    null
                }
            }
    }

    private fun parseEmojiPatternsJson(json: String): EmojiPatternStrings {
        val adapter = moshi.adapter(EmojiPatternStrings::class.java)
        return requireNotNull(adapter.fromJson(json)) { "Invalid emoji patterns JSON" }
    }

    override fun parseEmojiReaction(body: String): ParsedEmojiReaction? {
        val removal = parseRemoval(body)
        if (removal != null) return removal

        for ((pattern, parser) in reactionPatterns) {
            val match = pattern.find(body) ?: continue
            val result = parser(match) ?: continue

            Timber.d("Reaction found with ${result.emoji}")
            return result
        }

        return null
    }

    private fun parseRemoval(body: String): ParsedEmojiReaction? {
        for ((pattern, parser) in removalPatterns) {
            val match = pattern.find(body) ?: continue
            val result = parser(match) ?: continue

            Timber.d("Removal found with ${result.emoji}")
            return result
        }

        return null
    }

    private fun parseTruncatedMessages(originalMessageText: String): Regex {
        val reactionText = originalMessageText.trim()

        val delimiter = "\u2026"
        val index = reactionText.lastIndexOf(delimiter)
        val regexPattern = if (index == -1) {
            Regex.escape(reactionText)
        } else {
            val before = reactionText.take(index)
            Regex.escape(before) + ".*"
        }
        return Regex("^$regexPattern$", RegexOption.DOT_MATCHES_ALL)
    }

    /**
     * Search for messages in the same thread with matching text content
     * We'll search recent messages first
     */
    override fun findTargetMessage(threadId: Long, originalMessageText: String): Message? {
        val startTime = System.currentTimeMillis()
        val messages = getMessagesNewestFirst(threadId)
        val endTime = System.currentTimeMillis()
        Timber.d("Found ${messages.size} messages as potential emoji targets in ${endTime - startTime}ms")

        return findTargetMessage(messages, originalMessageText)
    }

    private fun getMessagesNewestFirst(threadId: Long): List<Message> =
        messageDao.getMessagesNewestFirst(threadId).map(MessageWithRelations::toModel)

    private fun findTargetMessage(messages: List<Message>, originalMessageText: String): Message? {
        val originalMessageRegex = parseTruncatedMessages(originalMessageText)
        val match = messages.find { message ->
            originalMessageRegex.matches(message.getText(false).trim())
        }
        if (match != null) {
            Timber.d("Found match for reaction target: message ID ${match.id}")
            return match
        }

        Timber.w("No target message found for reaction text: '$originalMessageText'")
        return null
    }

    private fun markEmojiReaction(reactionMessage: Message) {
        reactionMessage.isEmojiReaction = true
        messageDao.setEmojiReaction(reactionMessage.id, true)
    }

    private fun removeEmojiReaction(
        reactionMessage: Message,
        reaction: ParsedEmojiReaction,
        targetMessage: Message?,
    ) {
        if (targetMessage == null) {
            Timber.w("Cannot remove emoji reaction '${reaction.emoji}': no target message found")
            return
        }

        val existingReaction = reactionDao.getReactionsTo(targetMessage.id).find { candidate ->
            candidate.senderAddress == reactionMessage.address && candidate.emoji == reaction.emoji
        }

        if (existingReaction != null) {
            reactionDao.delete(listOf(existingReaction.id))
            Timber.d("Removed emoji reaction: ${reaction.emoji} to message ${targetMessage.id}")
        } else {
            Timber.w("No existing emoji reaction found to remove: ${reaction.emoji} to message ${targetMessage.id}")
        }

        markEmojiReaction(reactionMessage)
    }

    override fun saveEmojiReaction(
        reactionMessage: Message,
        parsedReaction: ParsedEmojiReaction,
        targetMessage: Message?,
    ) = db.runInTransaction {
        if (parsedReaction.isRemoval) {
            removeEmojiReaction(reactionMessage, parsedReaction, targetMessage)
            return@runInTransaction
        }

        // Overwrite any previous reaction from this sender for this target
        val priorFromSender = targetMessage
            ?.let { reactionDao.getReactionsTo(it.id) }
            ?.filter { it.senderAddress == reactionMessage.address }
            .orEmpty()

        val reaction = EmojiReaction(
            id = keyManager.newId(),
            reactionMessageId = reactionMessage.id,
            targetMessageId = targetMessage?.id,
            senderAddress = reactionMessage.address,
            emoji = parsedReaction.emoji,
            originalMessageText = parsedReaction.originalMessage,
            threadId = reactionMessage.threadId
        )
        reactionDao.insert(reaction)

        if (targetMessage != null) {
            markEmojiReaction(reactionMessage)
            reactionDao.delete(priorFromSender.map { it.id })

            Timber.i("Saved emoji reaction: ${reaction.emoji} to message ${targetMessage.id}")
        } else {
            Timber.w("No target message, cannot save emoji reaction: ${reaction.emoji}")
        }
    }

    override fun deleteAndReparseAllEmojiReactions(onProgress: (SyncRepository.SyncProgress) -> Unit) {
        val startTime = System.currentTimeMillis()

        reactionDao.deleteAll()
        messageDao.clearEmojiReactionFlags()

        val allMessages = messageDao.getReactionCandidates().map(MessageWithRelations::toModel)

        val max = allMessages.size
        var progress = 0

        // The messages a reaction can point at, read once for each conversation that has reactions
        val messagesByThread = HashMap<Long, List<Message>>()

        allMessages.forEach { message ->
            val text = message.getText(false)
            val parsedReaction = parseEmojiReaction(text)
            if (parsedReaction != null) {
                val targetMessage = findTargetMessage(
                    messagesByThread.getOrPut(message.threadId) { getMessagesNewestFirst(message.threadId) },
                    parsedReaction.originalMessage
                )
                saveEmojiReaction(
                    message,
                    parsedReaction,
                    targetMessage,
                )
                progress++
                // Update the progress every 25 messages, and then at completion
                // that way we don't spam the UI
                if (progress % 25 == 0 || progress == max) {
                    onProgress(
                        SyncRepository.SyncProgress.ParsingEmojis(
                            max = max,
                            progress = progress,
                            indeterminate = false
                        )
                    )
                }
            }
        }

        val endTime = System.currentTimeMillis()
        Timber.d("Deleted and reparsed all emoji reactions in ${endTime - startTime}ms")
    }

}
