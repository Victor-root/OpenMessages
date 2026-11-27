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
package io.openmessages.feature.compose

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.jakewharton.rxbinding4.view.clicks
import io.openmessages.common.AppMediaPlayer
import io.openmessages.R
import io.openmessages.common.base.BaseAdapter
import io.openmessages.common.base.BaseViewHolder
import io.openmessages.common.util.Colors
import io.openmessages.common.util.DateFormatter
import io.openmessages.common.util.TextViewStyler
import io.openmessages.common.util.extensions.dpToPx
import io.openmessages.common.util.extensions.resolveThemeAttribute
import io.openmessages.common.util.extensions.resolveThemeColor
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.common.util.extensions.setPadding
import io.openmessages.common.util.extensions.setTint
import io.openmessages.common.util.extensions.setVisible
import io.openmessages.common.util.extensions.withAlpha
import io.openmessages.common.widget.MessageBodyView
import io.openmessages.compat.SubscriptionManagerCompat
import io.openmessages.extensions.isSmil
import io.openmessages.extensions.isText
import io.openmessages.extensions.joinTo
import io.openmessages.extensions.truncateWithEllipses
import io.openmessages.feature.compose.BubbleUtils.canGroup
import io.openmessages.feature.compose.BubbleUtils.getBubble
import io.openmessages.feature.compose.part.PartsAdapter
import io.openmessages.feature.extensions.isEmojiOnly
import io.openmessages.model.Conversation
import io.openmessages.model.Message
import io.openmessages.model.Recipient
import io.openmessages.databinding.MessageListItemInBinding
import io.openmessages.databinding.MessageListItemOutBinding
import io.openmessages.util.DisplayNames
import io.openmessages.util.PhoneNumberUtils
import io.openmessages.util.Preferences
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import java.util.*
import javax.inject.Inject
import javax.inject.Provider

class MessagesAdapter @Inject constructor(
    private val displayNames: DisplayNames,
    subscriptionManager: SubscriptionManagerCompat,
    private val context: Context,
    private val colors: Colors,
    private val dateFormatter: DateFormatter,
    private val partsAdapterProvider: Provider<PartsAdapter>,
    private val phoneNumberUtils: PhoneNumberUtils,
    private val prefs: Preferences,
    private val textViewStyler: TextViewStyler,
) : BaseAdapter<Message, BaseViewHolder>() {
    class AudioState(
        var partId: Long = -1,
        var state: AppMediaPlayer.PlayingState = AppMediaPlayer.PlayingState.Stopped,
        var seekBarUpdater: Disposable? = null,
        var viewHolder: BaseViewHolder? = null
    )

    companion object {
        private const val VIEW_TYPE_MESSAGE_IN = 0
        private const val VIEW_TYPE_MESSAGE_OUT = 1

        private const val MAX_MESSAGE_DISPLAY_LENGTH = 5000
        private const val SELECTION_ALPHA = 0x40
    }

    // click events passed back to compose view model
    val partClicks: Subject<Long> = PublishSubject.create()
    val messageLinkClicks: Subject<Uri> = PublishSubject.create()
    val cancelSendingClicks: Subject<Long> = PublishSubject.create()
    val sendNowClicks: Subject<Long> = PublishSubject.create()
    val resendClicks: Subject<Long> = PublishSubject.create()
    val partContextMenuRegistrar: Subject<View> = PublishSubject.create()
    val reactionClicks: Subject<Long> = PublishSubject.create()

    /**
     * The conversation the messages belong to
     */
    var conversation: Conversation? = null
        set(value) {
            if (field === value) return

            field = value
            contactCache.clear()
        }

    /**
     * Mark this message as highlighted
     */
    var highlight: Long = -1L
        set(value) {
            if (field == value) return

            field = value
            notifyDataSetChanged()
        }

    private val contactCache = ContactCache()
    private val expanded = HashMap<Long, Boolean>()
    private val subs = subscriptionManager.activeSubscriptionInfoList

    /** The conversation's colour, which my messages and their controls wear */
    var theme: Colors.Theme = colors.theme()
        set(value) {
            if (field == value) return
            field = value
            selectionTint = selectionTint(value.theme)
            notifyDataSetChanged()
        }

    /**
     * A selected message's row: the conversation's colour, light enough for the bubbles to read on,
     * and lifted on a dark background where a deep colour would hardly show
     */
    private var selectionTint = selectionTint(theme.theme)

    private fun selectionTint(color: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
        intArrayOf(colors.readableOnBackground(context, color).withAlpha(SELECTION_ALPHA), Color.TRANSPARENT))

    private val audioState = AudioState()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        // Use the parent's context to inflate the layout, otherwise link clicks will crash the app
        val inflater = LayoutInflater.from(parent.context)

        val view: View
        val body: TextView
        val parts: View

        if (viewType == VIEW_TYPE_MESSAGE_OUT) {
            val binding = MessageListItemOutBinding.inflate(inflater, parent, false)
            view = binding.root
            body = binding.body
            parts = binding.parts
        } else {
            val binding = MessageListItemInBinding.inflate(inflater, parent, false)
            view = binding.root
            body = binding.body
            parts = binding.parts
        }

        body.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE

        // register recycler view with compose activity for context menus
        partContextMenuRegistrar.onNext(parts)

        // Tinted in the conversation's colour across the whole width while selected, see selectionTint
        view.background = ColorDrawable(Color.WHITE)
        view.foreground = ContextCompat.getDrawable(parent.context,
            parent.context.resolveThemeAttribute(android.R.attr.selectableItemBackground))

        return BaseViewHolder(view).apply {
            view.setOnClickListener {
                getItemOrNull(bindingAdapterPosition)?.let {
                    if (toggleSelection(it.id, false)) view.isActivated = isSelected(it.id)
                }
            }
            view.setOnLongClickListener {
                getItemOrNull(bindingAdapterPosition)?.let {
                    toggleSelection(it.id)
                    view.isActivated = isSelected(it.id)
                }
                true
            }
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        val message = getItemOrNull(position) ?: return
        val previous = if (position == 0) null else getItemOrNull(position - 1)
        val next = if (position == itemCount - 1) null else getItemOrNull(position + 1)

        val theme = when (message.isOutgoingMessage()) {
            true -> this.theme
            false -> colors.theme(contactCache[message.address])
        }

        // Update the selected state
        holder.itemView.backgroundTintList = selectionTint
        holder.itemView.isActivated = isSelected(message.id) || highlight == message.id

        // Get views based on message type
        val isOutgoing = message.isMe()
        val timestamp: TextView
        val simIndex: TextView
        val sim: ImageView
        val body: MessageBodyView
        val parts: androidx.recyclerview.widget.RecyclerView
        val reactions: View
        val reactionText: TextView
        val status: TextView

        if (isOutgoing) {
            val binding = MessageListItemOutBinding.bind(holder.itemView)
            timestamp = binding.timestamp
            simIndex = binding.simIndex
            sim = binding.sim
            body = binding.body
            parts = binding.parts
            reactions = binding.reactions
            reactionText = binding.reactionText
            status = binding.status

            binding.cancel.setTint(theme.theme)
            listOf(binding.cancelIcon, binding.sendNowIcon, binding.resendIcon)
                .forEach { control -> control.setTint(theme.theme) }

            // bind the cancelFrame (cancel button) and send now button
            val isCancellable = message.isSending() && message.date > System.currentTimeMillis()

            if (isCancellable) {
                binding.cancelFrame.visibility = View.VISIBLE
                binding.sendNowIcon.visibility = View.VISIBLE

                binding.cancelFrame.setOnClickListener { cancelSendingClicks.onNext(message.id) }
                binding.sendNowIcon.setOnClickListener { sendNowClicks.onNext(message.id) }

                binding.cancel.progress = 2

                val delay = when (prefs.sendDelay.get()) {
                    Preferences.SEND_DELAY_SHORT -> 3000
                    Preferences.SEND_DELAY_MEDIUM -> 5000
                    Preferences.SEND_DELAY_LONG -> 10000
                    else -> 0
                }
                val progress =
                    (1 - (message.date - System.currentTimeMillis()) / delay.toFloat()) * 100

                ObjectAnimator.ofInt(binding.cancel, "progress", progress.toInt(), 100)
                    .setDuration(message.date - System.currentTimeMillis())
                    .start()
            } else {
                binding.cancelFrame.visibility = View.GONE
                binding.sendNowIcon.visibility = View.GONE

                binding.cancelFrame.setOnClickListener(null)
                binding.sendNowIcon.setOnClickListener(null)
            }

            // bind the resend icon view
            if (message.isFailedMessage()) {
                binding.resendIcon.visibility = View.VISIBLE
                binding.resendIcon.clicks().subscribe {
                    resendClicks.onNext(message.id)
                    binding.resendIcon.visibility = View.GONE
                }
            } else {
                binding.resendIcon.visibility = View.GONE
            }

            body.apply {
                // My bubbles are neutral, so the links in them take the conversation's colour
                setLinkTextColor(colors.readableOnBackground(this.context, theme.theme))
                highlightColor = theme.theme.withAlpha(0x5d)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    textSelectHandle?.setTint(theme.theme.withAlpha(0xad))
                    textSelectHandleLeft?.setTint(theme.theme.withAlpha(0xad))
                    textSelectHandleRight?.setTint(theme.theme.withAlpha(0xad))
                }
            }
        } else {
            val binding = MessageListItemInBinding.bind(holder.itemView)
            timestamp = binding.timestamp
            simIndex = binding.simIndex
            sim = binding.sim
            body = binding.body
            parts = binding.parts
            reactions = binding.reactions
            reactionText = binding.reactionText
            status = binding.status

            // Bind the avatar and bubble colour
            val showAvatar = prefs.showAvatar.get()
            binding.avatar.apply {
                setRecipient(contactCache[message.address])
                // Shown: keep the slot (INVISIBLE) for grouped messages so bubbles stay aligned; the
                // last of a group reveals it. Hidden: drop it (GONE) so the bubble reclaims the width.
                if (showAvatar) setVisible(!canGroup(message, next), View.INVISIBLE)
                else setVisible(false, View.GONE)
            }

            // Keep the status label aligned under the bubble whether or not the avatar takes its slot.
            (binding.status.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val startMargin = (if (showAvatar) 72 else 36).dpToPx(context)
                if (lp.marginStart != startMargin) {
                    lp.marginStart = startMargin
                    binding.status.layoutParams = lp
                }
            }

            body.apply {
                setTextColor(theme.textPrimary)
                setBackgroundTint(theme.theme)
                highlightColor = R.attr.bubbleColor.withAlpha(0x5d)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    textSelectHandle?.setTint(R.attr.bubbleColor.withAlpha(0x7d))
                    textSelectHandleLeft?.setTint(R.attr.bubbleColor.withAlpha(0x7d))
                    textSelectHandleRight?.setTint(R.attr.bubbleColor.withAlpha(0x7d))
                }
            }
        }

        val subject = message.getCleansedSubject()

        var isMsgTextTruncated: Boolean

        // get message text to display, which may need to be truncated
        val displayText = subject.joinTo(message.getText(false), "\n").let {
            isMsgTextTruncated = (it.length > MAX_MESSAGE_DISPLAY_LENGTH)

            // make subject sub-string bold, if subject is not blank
            if (subject.isNotBlank())
                SpannableString(it.truncateWithEllipses(MAX_MESSAGE_DISPLAY_LENGTH)).apply {
                    setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        subject.length,
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE
                    )
                }
            else
                it.truncateWithEllipses(MAX_MESSAGE_DISPLAY_LENGTH)
        }

        // A message without text has no bubble to carry its time, so the line below takes it
        val hasBody = message.isSms() || displayText.isNotBlank()

        // Bind the message status
        bindStatus(status, isMsgTextTruncated, hasBody, message, next)

        // Bind the day, shown above the first message of each one
        val subscription = subs.find { it.subscriptionId == message.subId }
        val day = dayLabel(message.date)

        timestamp.apply {
            text = day
            setVisible(
                    (previous == null || dayLabel(previous.date) != day) ||
                            (message.subId != previous.subId) &&
                            (subscription != null)
            )
        }

        simIndex.text = subscription?.simSlotIndex?.plus(1)?.toString()
        sim.setTint(colors.sim(context, subscription) ?: context.resolveThemeColor(android.R.attr.textColorSecondary))

        ((message.subId != previous?.subId) && (subscription != null) && (subs.size > 1)).also {
            sim.setVisible(it)
            simIndex.setVisible(it)
        }

        // Bind the grouping
        holder.itemView.setPadding(
            bottom = if (canGroup(message, next)) 0 else 16.dpToPx(context)
        )

        // Bind the body text
        val emojiOnly = displayText.isEmojiOnly()

        // The time, and for a message I sent where it stands, in the bubble's corner. A sent bubble
        // is neutral and an emoji alone has none, so both take the screen's own secondary colour.
        body.setFooter(
            time = dateFormatter.getTimestamp(message.date),
            icon = statusIcon(message)?.let { res -> ContextCompat.getDrawable(holder.itemView.context, res) },
            color = when (isOutgoing || emojiOnly) {
                true -> holder.itemView.context.resolveThemeColor(android.R.attr.textColorSecondary)
                false -> theme.textSecondary
            }
        )
        textViewStyler.setTextSize(
            body,
            when (emojiOnly) {
                true -> TextViewStyler.SIZE_EMOJI
                false -> TextViewStyler.SIZE_PRIMARY
            }
        )

        val spanString = SpannableStringBuilder(displayText)

        when (prefs.messageLinkHandling.get()) {
            Preferences.MESSAGE_LINK_HANDLING_BLOCK -> body.autoLinkMask = 0
            Preferences.MESSAGE_LINK_HANDLING_ASK -> {
                //  manually handle link clicks if user has set to ask before opening links
                body.apply {
                    isClickable = false
                    linksClickable = false
                    movementMethod = LinkMovementMethod.getInstance()

                    Linkify.addLinks(spanString, autoLinkMask)
                }

                spanString.apply {
                    for (span in getSpans(0, length, URLSpan::class.java)) {
                        // set handler for when user touches a link into new span
                        setSpan(
                            object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    messageLinkClicks.onNext(span.url.toUri())
                                }
                            },
                            getSpanStart(span),
                            getSpanEnd(span),
                            getSpanFlags(span)
                        )

                        // remove original span
                        removeSpan(span)
                    }
                }
            }
            else -> body.movementMethod = LinkMovementMethod.getInstance()
        }

        body.apply {
            text = spanString
            setVisible(hasBody)

            setBackgroundResource(
                getBubble(
                    emojiOnly = emojiOnly,
                    canGroupWithPrevious = canGroup(message, previous) ||
                            message.parts.any { !it.isSmil() && !it.isText() },
                    canGroupWithNext = canGroup(message, next),
                    isMe = message.isMe()
                )
            )
        }

        // Bind the parts
        parts.adapter = partsAdapterProvider.get().apply {
            this.theme = theme
            setData(message, previous, next, holder, audioState)
            contextMenuValue = message.id
            clicks.subscribe(partClicks)    // part clicks gets passed back to compose view model
        }

        showEmojiReactions(reactions, reactionText, message)
    }

    private fun showEmojiReactions(reactionsContainer: View, reactionTextView: TextView, message: Message) {
        val reactions = message.emojiReactions
        val hasReactions = reactions.isNotEmpty()

        if (hasReactions) {
            val uniqueEmojis = reactions.map { it.emoji }.distinct()
            val totalCount = reactions.size

            // Show unique emojis followed by total count
            val reactionText = if (totalCount == 1) {
                uniqueEmojis.first()
            } else {
                "${uniqueEmojis.joinToString("")}\u00A0$totalCount"
            }

            reactionTextView.text = reactionText
            reactionTextView.setOnClickListener { reactionClicks.onNext(message.id) }
            reactionsContainer.setVisible(true)
        } else {
            reactionsContainer.setVisible(false)
            reactionTextView.setOnClickListener(null)
        }
    }


    /**
     * The line under a bubble. Its time and state already sit in the bubble's corner, so it is kept
     * for what the corner can't say: a failure to retry, text cut short, who wrote in a group, the
     * time of a message with no text, and the full date and state of a message tapped open.
     */
    private fun bindStatus(
        statusView: TextView,
        bodyTextTruncated: Boolean,
        hasBody: Boolean,
        message: Message,
        next: Message?
    ) {
        val incomingInGroup = !message.isMe() && (conversation?.recipients?.size ?: 0) > 1

        statusView.apply {
            text = when {
                message.isSending() -> context.getString(R.string.message_status_sending)
                message.isDelivered() -> context.getString(
                    R.string.message_status_delivered,
                    dateFormatter.getTimestamp(message.dateSent)
                )
                message.isFailedMessage() -> context.getString(R.string.message_status_failed)
                bodyTextTruncated -> context.getString(R.string.message_body_too_long_to_display)
                incomingInGroup -> contactCache[message.address]?.let(displayNames::of)
                !hasBody -> dateFormatter.getTimestamp(message.date)
                else -> dateFormatter.getMessageTimestamp(message.date)
            }

            setVisible(
                when {
                    expanded[message.id] == true -> true
                    message.isFailedMessage() -> true
                    bodyTextTruncated -> true
                    expanded[message.id] == false -> false
                    incomingInGroup && next?.compareSender(message) != true -> true
                    !hasBody -> true
                    else -> false
                }
            )
        }
    }

    /** Where a message I sent stands, as the icon in its bubble's corner; none for one received */
    private fun statusIcon(message: Message): Int? = when {
        !message.isMe() -> null
        message.isFailedMessage() -> R.drawable.ic_error_outline_black_24dp
        message.isSending() -> R.drawable.ic_schedule_black_24dp
        message.isDelivered() -> R.drawable.ic_done_all_black_24dp
        else -> R.drawable.ic_check_white_24dp
    }

    /** A message shown while it was being sent and the same one once written are one bubble */
    override fun areItemsTheSame(old: Message, new: Message) = old.id == new.id || new.settles(old)

    override fun areContentsTheSame(old: Message, new: Message) =
        old == new && old.parts == new.parts && old.emojiReactions == new.emojiReactions

    /**
     * The oldest message the list held. Once older ones are loaded above it, it has a message before
     * it again, which can change whether it shows its time or groups with its sender.
     */
    private var oldestId = -1L

    /** Names the day of a message, for the heading above the first message of each day */
    private var dayLabel = dateFormatter.dayLabeller()

    override fun onDatasetChanged() {
        dayLabel = dateFormatter.dayLabeller()
        val formerOldest = data.indexOfFirst { message -> message.id == oldestId }
        if (formerOldest > 0) notifyItemChanged(formerOldest)
        oldestId = data.firstOrNull()?.id ?: -1L
    }

    override fun getItemId(position: Int): Long {
        return getItemOrNull(position)?.id ?: -1
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItemOrNull(position) ?: return -1
        return when (message.isMe()) {
            true -> VIEW_TYPE_MESSAGE_OUT
            false -> VIEW_TYPE_MESSAGE_IN
        }
    }

    fun expandMessages(messageIds: List<Long>, expand: Boolean) {
        messageIds.forEach { expanded[it] = expand }
        notifyDataSetChanged()
    }

    /**
     * Cache the contacts in a map by the address, because the messages we're binding don't have
     * a reference to the contact.
     */
    private inner class ContactCache : HashMap<String, Recipient?>() {
        override fun get(key: String): Recipient? {
            if (!containsKey(key)) {
                put(key, conversation?.recipients?.firstOrNull {
                    phoneNumberUtils.compare(it.address, key)
                })
            }

            return super.get(key)
        }

    }
}
