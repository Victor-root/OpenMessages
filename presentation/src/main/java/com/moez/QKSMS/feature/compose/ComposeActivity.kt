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

import android.Manifest
import android.animation.LayoutTransition
import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.format.DateFormat
import android.view.ContextMenu
import android.view.ContextThemeWrapper
import android.view.DragEvent.ACTION_DRAG_ENDED
import android.view.DragEvent.ACTION_DRAG_EXITED
import android.view.DragEvent.ACTION_DROP
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ConcatAdapter
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import io.openmessages.common.QkMediaPlayer
import com.uber.autodispose.ObservableSubscribeProxy
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dagger.android.AndroidInjection
import io.openmessages.BuildConfig
import io.openmessages.R
import io.openmessages.common.Navigator
import io.openmessages.common.base.QkThemedActivity
import io.openmessages.common.util.DateFormatter
import io.openmessages.common.util.DialogHost
import io.openmessages.common.util.extensions.autoScrollToStart
import io.openmessages.common.util.extensions.dpToPx
import io.openmessages.common.util.extensions.hideKeyboard
import io.openmessages.common.util.extensions.makeToast
import io.openmessages.common.util.extensions.scrapViews
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.common.util.extensions.setTint
import io.openmessages.common.util.extensions.setVisible
import io.openmessages.common.util.extensions.showKeyboard
import io.openmessages.common.util.extensions.themeButtons
import io.openmessages.common.widget.MicInputCloudView
import io.openmessages.extensions.mapNotNull
import io.openmessages.feature.blocking.BlockingDialog
import io.openmessages.feature.compose.editing.ChipsAdapter
import io.openmessages.feature.contacts.ContactsActivity
import io.openmessages.feature.templates.TemplatesActivity
import io.openmessages.model.Attachment
import io.openmessages.model.Recipient
import io.openmessages.util.Preferences
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.openmessages.databinding.ComposeActivityBinding
import io.openmessages.databinding.ComposeAttachSheetBinding
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class ComposeActivity : QkThemedActivity(), ComposeView {

    @Inject lateinit var composeAttachmentAdapter: ComposeAttachmentAdapter
    @Inject lateinit var chipsAdapter: ChipsAdapter
    @Inject lateinit var dateFormatter: DateFormatter
    @Inject lateinit var messageAdapter: MessagesAdapter
    @Inject lateinit var scheduledMessagesAdapter: ScheduledMessagesAdapter
    @Inject lateinit var navigator: Navigator
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var blockingDialog: BlockingDialog

    private lateinit var binding: ComposeActivityBinding

    /** Modern attachment picker: a bottom sheet opened by the "+" button. */
    private val attachSheetBinding by lazy { ComposeAttachSheetBinding.inflate(layoutInflater) }
    // Held as the delegate rather than a plain `by lazy` so onDestroy can ask whether the sheet was
    // ever opened: reading it there would otherwise inflate the layout and build a dialog to throw
    // straight away, on every conversation the user closes without touching the "+" button.
    private val attachSheetDelegate = lazy {
        // The app runs on a Theme.AppCompat base; Material's BottomSheetDialog needs a Material
        // context, so wrap the activity the same way the other Material components here do.
        BottomSheetDialog(ContextThemeWrapper(this, R.style.Theme_OpenMessages_Material3Context)).apply {
            setContentView(attachSheetBinding.root)
            // Dragging down closes rather than snapping back to the half-open height, which for a
            // seven-item menu is only ever a truncated version of the same thing.
            behavior.skipCollapsed = true
            setOnShowListener {
                // Drop the default opaque sheet background so our rounded-corner background shows through.
                findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                    ?.setBackgroundResource(android.R.color.transparent)
                // Open at full height. The default is a peek height derived from the screen, which in
                // landscape is shorter than the two rows and cut the options off at the screen edge.
                // Set on every show: BottomSheetDialog.onStart() puts the sheet back to collapsed.
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }
    private val attachSheet by attachSheetDelegate

    override val activityVisibleIntent: Subject<Boolean> = PublishSubject.create()
    override val chipsSelectedIntent: Subject<HashMap<String, String?>> = PublishSubject.create()
    override val chipDeletedIntent: Subject<Recipient> by lazy { chipsAdapter.chipDeleted }
    override val menuReadyIntent: Observable<Unit> = menu.map { }
    override val optionsItemIntent: Subject<Int> = PublishSubject.create()
    override val contextItemIntent: Subject<MenuItem> = PublishSubject.create()
    override val scheduleAction: Subject<Boolean> = PublishSubject.create()
    override val sendAsGroupIntent by lazy { binding.sendAsGroupSwitch.clicks() }
    override val messagePartClickIntent: Subject<Long> by lazy { messageAdapter.partClicks }
    override val messagePartContextMenuRegistrar: Subject<View> by lazy { messageAdapter.partContextMenuRegistrar }
    override val messagesSelectedIntent by lazy { messageAdapter.selectionChanges }
    override val cancelDelayedIntent: Subject<Long> by lazy { messageAdapter.cancelSendingClicks }
    override val sendDelayedNowIntent: Subject<Long> by lazy { messageAdapter.sendNowClicks }
    override val resendIntent: Subject<Long> by lazy { messageAdapter.resendClicks }
    override val attachmentDeletedIntent: Subject<Attachment> by lazy { composeAttachmentAdapter.attachmentDeleted }
    override val textChangedIntent by lazy { binding.message.textChanges() }
    // The attach options live in a bottom sheet now (attachSheet); each item feeds its intent below.
    override val cameraIntent: Subject<Unit> = PublishSubject.create()
    override val attachImageFileIntent: Subject<Unit> = PublishSubject.create()
    override val attachAnyFileIntent: Subject<Unit> = PublishSubject.create()
    override val scheduleIntent: Subject<Unit> = PublishSubject.create()
    override val attachContactIntent: Subject<Unit> = PublishSubject.create()
    override val templateIntent: Subject<Unit> = PublishSubject.create()
    override val flaggedApproveIntent by lazy { binding.flaggedApprove.clicks() }
    override val flaggedBlockIntent by lazy { binding.flaggedBlock.clicks() }
    override val attachAnyFileSelectedIntent: Subject<Uri> = PublishSubject.create()
    override val contactSelectedIntent: Subject<Uri> = PublishSubject.create()
    override val inputContentIntent by lazy { binding.message.inputContentSelected }
    override val scheduleDateSelectedIntent: Subject<Triple<Int, Int, Int>> = PublishSubject.create()
    override val scheduleSelectedIntent: Subject<Long> = PublishSubject.create()
    override val dialogDismissedIntent: Subject<ComposeDialog> = PublishSubject.create()
    override val changeSimIntent by lazy { binding.sim.clicks() }
    override val scheduleCancelIntent by lazy { binding.scheduledCancel.clicks() }
    override val sendIntent by lazy {  Observable.merge(binding.send.clicks(), binding.scheduledSend.clicks()) }
    override val viewQksmsPlusIntent: Subject<Unit> = PublishSubject.create()
    override val backPressedIntent: Subject<Unit> = PublishSubject.create()
    override val confirmDeleteIntent: Subject<List<Long>> = PublishSubject.create()
    override val confirmDeleteConversationIntent: Subject<Long> = PublishSubject.create()
    override val clearCurrentMessageIntent: Subject<Boolean> = PublishSubject.create()
    override val messageLinkAskIntent: Subject<Uri> by lazy { messageAdapter.messageLinkClicks }
    override val reactionClickIntent: Subject<Long> by lazy { messageAdapter.reactionClicks }
    override val scheduledMessageClickIntent: Subject<Long> by lazy { scheduledMessagesAdapter.clicks }
    override val speechRecogniserIntent by lazy { binding.speechToTextIcon.clicks() }
    override val shadeIntent by lazy { binding.shadeBackground.clicks() }
    override val recordAudioStartStopRecording: Subject<Boolean> = PublishSubject.create()
    /** Fed by the input-bar mic and the bottom sheet's "audio" item. */
    override val recordAnAudioMessage: Observable<Unit> by lazy {
        Observable.merge(binding.recordAudioMsg.clicks(), recordAudioFromSheet)
    }
    private val recordAudioFromSheet: Subject<Unit> = PublishSubject.create()
    override val recordAudioAbort by lazy { binding.audioMsgAbort.clicks() }
    override val recordAudioAttach by lazy { binding.audioMsgAttach.clicks() }
    override val recordAudioPlayerPlayPause: Subject<QkMediaPlayer.PlayingState> = PublishSubject.create()
    override val recordAudioPlayerConfigUI: Subject<QkMediaPlayer.PlayingState> = PublishSubject.create()
    override val recordAudioPlayerVisible: Subject<Boolean> = PublishSubject.create()
    override val recordAudioMsgRecordVisible: Subject<Boolean> = PublishSubject.create()
    override val recordAudioChronometer: Subject<Boolean> = PublishSubject.create()
    override val recordAudioRecord: Subject<MicInputCloudView.ViewState> = PublishSubject.create()

    // The date and time pickers are platform dialogs rather than AppCompat ones and were never
    // themed here; leave them as they were.
    private val dialogHost = DialogHost<ComposeDialog>(
        build = { spec -> buildDialog(spec).also { (it as? AlertDialog)?.themeButtons(colors.theme().theme) } },
        onClosed = dialogDismissedIntent::onNext)

    private var seekBarUpdater: Disposable? = null

    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory)[ComposeViewModel::class.java] }

    private var cameraDestination: Uri? = null

    /** Window soft-input mode to restore after closing the full-screen editor. */
    private var savedSoftInputMode = 0

    private fun getSeekBarUpdater(): ObservableSubscribeProxy<Long> {
        return Observable.interval(500, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.single())
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(scope())
    }

    private fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = ComposeActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showBackButton(true)
        viewModel.bindView(this)

        binding.contentView.layoutTransition = LayoutTransition().apply {
            disableTransitionType(LayoutTransition.CHANGING)
        }
            chipsAdapter.view = binding.chips

            binding.chips.itemAnimator = null
            binding.chips.layoutManager = FlexboxLayoutManager(this)

            messageAdapter.autoScrollToStart(binding.messageList)
            messageAdapter.emptyView = binding.messagesEmpty

            binding.messageList.setHasFixedSize(true)
            // Two lists in one: the conversation's messages, then the ones still waiting for their
            // send time. Scheduled messages are always in the future, so appending them keeps them
            // last without anything having to reorder when a message is sent in the meantime.
            binding.messageList.adapter = ConcatAdapter(messageAdapter, scheduledMessagesAdapter)

            binding.messageAttachments.adapter = composeAttachmentAdapter

            binding.message.supportsInputContent = true

            // "+" opens the attachment sheet; each sheet item feeds its existing intent, then closes.
            binding.attach.clicks()
                .autoDisposable(scope())
                .subscribe { attachSheet.show() }
            attachSheetBinding.sheetGallery.setOnClickListener { attachImageFileIntent.onNext(Unit); attachSheet.dismiss() }
            attachSheetBinding.sheetCamera.setOnClickListener { cameraIntent.onNext(Unit); attachSheet.dismiss() }
            attachSheetBinding.sheetFile.setOnClickListener { attachAnyFileIntent.onNext(Unit); attachSheet.dismiss() }
            attachSheetBinding.sheetAudio.setOnClickListener { recordAudioFromSheet.onNext(Unit); attachSheet.dismiss() }
            attachSheetBinding.sheetContact.setOnClickListener { attachContactIntent.onNext(Unit); attachSheet.dismiss() }
            attachSheetBinding.sheetSchedule.setOnClickListener { scheduleIntent.onNext(Unit); attachSheet.dismiss() }
            attachSheetBinding.sheetTemplates.setOnClickListener { templateIntent.onNext(Unit); attachSheet.dismiss() }

            // Full-screen editor: the expand chevron opens it; collapse/send sync back to the bubble.
            savedSoftInputMode = window.attributes.softInputMode
            binding.expand.clicks()
                .autoDisposable(scope())
                .subscribe { showFullscreen() }
            binding.fullscreenCollapse.clicks()
                .autoDisposable(scope())
                .subscribe { collapseFullscreen() }
            binding.fullscreenSend.clicks()
                .autoDisposable(scope())
                .subscribe { sendFromFullscreen() }
            binding.message.textChanges()
                .autoDisposable(scope())
                .subscribe { binding.message.post { updateExpandButton() } }

            theme
                .doOnNext {
                    binding.loading.setTint(it.theme)

                    // Attach button + the bottom-sheet option circles follow the conversation theme.
                    binding.attach.setBackgroundTint(it.theme); binding.attach.setTint(it.textPrimary)
                    listOf(
                        attachSheetBinding.sheetGalleryIcon, attachSheetBinding.sheetCameraIcon,
                        attachSheetBinding.sheetFileIcon, attachSheetBinding.sheetAudioIcon,
                        attachSheetBinding.sheetContactIcon, attachSheetBinding.sheetScheduleIcon,
                        attachSheetBinding.sheetTemplatesIcon
                    ).forEach { icon -> icon.setBackgroundTint(it.theme); icon.setTint(it.textPrimary) }

                    // Send button and the mic (shown instead, before anything is typed) follow the
                    // conversation theme too.
                    binding.send.setBackgroundTint(it.theme); binding.send.setTint(it.textPrimary)
                    binding.scheduledSend.setBackgroundTint(it.theme); binding.scheduledSend.setTint(it.textPrimary)
                    binding.recordAudioMsg.setBackgroundTint(it.theme); binding.recordAudioMsg.setTint(it.textPrimary)

                    // speech to text floating button
                    binding.speechToTextIconBorder.setBackgroundTint(it.theme)
                    binding.speechToTextIcon.setBackgroundTint(it.textPrimary)
                    binding.speechToTextIcon.setTint(it.theme)

                    // audio message recording
                    binding.audioMsgRecord.setColor(it.theme)
                    binding.audioMsgPlayerPlayPause.setTint(it.theme)
                    binding.audioMsgPlayerSeekBar.apply {
                        thumbTintList = ColorStateList.valueOf(it.theme)
                        progressBackgroundTintList = ColorStateList.valueOf(it.theme)
                        progressTintList = ColorStateList.valueOf(it.theme)
                    }

                    messageAdapter.theme = it
                }
                .autoDisposable(scope())
                .subscribe()

            // context menu registration for message parts
            messagePartContextMenuRegistrar
                .mapNotNull { it }
                .autoDisposable(scope())
                .subscribe { registerForContextMenu(it) }

            // drag drop handlers for speech-to-text icon
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                binding.speechToTextIcon.setOnLongClickListener {
                    it.startDragAndDrop(null, View.DragShadowBuilder(binding.speechToTextFrame), null, 0)
                    binding.speechToTextFrame.isVisible = false

                    binding.contentView.setOnDragListener { _, event ->
                        when (event.action) {
                            ACTION_DROP -> {
                                binding.speechToTextFrame.x = (event.x - (binding.speechToTextFrame.width / 2))
                                binding.speechToTextFrame.y = (event.y - (binding.speechToTextFrame.height / 2))

                                // get offset from root view as a percentage of root view for saving
                                prefs.showSttOffsetX.set((binding.speechToTextFrame.x - binding.contentView.x) / binding.contentView.width)
                                prefs.showSttOffsetY.set((binding.speechToTextFrame.y - binding.contentView.y) / binding.contentView.height)
                            }

                            ACTION_DRAG_ENDED, ACTION_DRAG_EXITED -> {
                                binding.speechToTextFrame.isVisible = true
                            }
                        }
                        true
                    }
                    true
                }
            }

            // start/stop audio message recording
            binding.audioMsgRecord.setOnClickListener {
                recordAudioRecord.onNext(binding.audioMsgRecord.getState())
            }

            recordAudioChronometer
                .subscribeOn(AndroidSchedulers.mainThread())
                .distinctUntilChanged()
                .autoDisposable(scope())
                .subscribe {
                    if (it) {
                        binding.audioMsgDuration.base = SystemClock.elapsedRealtime()
                        binding.audioMsgDuration.start()
                    } else {
                        binding.audioMsgDuration.stop()
                    }
                }

            // audio record playback play/pause button
            binding.audioMsgPlayerPlayPause.setOnClickListener {
                recordAudioPlayerPlayPause.onNext(
                    binding.audioMsgPlayerPlayPause.tag as QkMediaPlayer.PlayingState
                )
            }

            recordAudioMsgRecordVisible
                .subscribeOn(AndroidSchedulers.mainThread())
                .distinctUntilChanged()
                .autoDisposable(scope())
                .subscribe {
                    binding.audioMsgRecord.isVisible = it
                    binding.audioMsgDuration.isVisible =
                        it   // chronometer follows record button visibility
                    binding.audioMsgBluetooth.isVisible = !it
                }

            recordAudioPlayerVisible
                .subscribeOn(AndroidSchedulers.mainThread())
                .distinctUntilChanged()
                .autoDisposable(scope())
                .subscribe {
                    binding.audioMsgPlayerBackground.isVisible = it
                    recordAudioPlayerConfigUI.onNext(QkMediaPlayer.PlayingState.Stopped)
                }

            recordAudioPlayerConfigUI
                .subscribeOn(AndroidSchedulers.mainThread())
                .distinctUntilChanged()
                .autoDisposable(scope())
                .subscribe {
                    when (it) {
                        QkMediaPlayer.PlayingState.Playing -> {
                            binding.audioMsgPlayerPlayPause.tag = QkMediaPlayer.PlayingState.Playing
                            QkMediaPlayer.start()
                            binding.audioMsgPlayerPlayPause.setImageResource(R.drawable.exo_icon_pause)
                            seekBarUpdater = getSeekBarUpdater().subscribe {
                                binding.audioMsgPlayerSeekBar.progress = QkMediaPlayer.currentPosition
                                binding.audioMsgPlayerSeekBar.max = QkMediaPlayer.duration
                            }
                            binding.audioMsgPlayerSeekBar.isEnabled = true
                        }

                        QkMediaPlayer.PlayingState.Paused -> {
                            binding.audioMsgPlayerPlayPause.tag = QkMediaPlayer.PlayingState.Paused
                            QkMediaPlayer.pause()
                            binding.audioMsgPlayerPlayPause.setImageResource(R.drawable.exo_icon_play)
                            seekBarUpdater?.dispose()
                        }

                        else -> {
                            binding.audioMsgPlayerPlayPause.tag = QkMediaPlayer.PlayingState.Stopped
                            QkMediaPlayer.reset()
                            binding.audioMsgPlayerPlayPause.setImageResource(R.drawable.exo_icon_play)
                            seekBarUpdater?.dispose()
                            binding.audioMsgPlayerSeekBar.progress = 0
                            binding.audioMsgPlayerSeekBar.isEnabled = false
                        }
                    }
                }
            // audio msg player seek bar handler
            binding.audioMsgPlayerSeekBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                        // if seek was initiated by the user and this part is currently playing
                        if (fromUser)
                            QkMediaPlayer.seekTo(progress)
                    }
                    override fun onStartTrackingTouch(p0: SeekBar?) {}
                    override fun onStopTrackingTouch(p0: SeekBar?) {}
                }
            )

            window.callback = ComposeWindowCallback(window.callback, this)
    }

    override fun onStart() {
        super.onStart()
        activityVisibleIntent.onNext(true)

        // if first time stt icon is shown (since setting reset), pop up an instruction toast
        if (prefs.showStt.get() &&
            (prefs.showSttOffsetX.get() == Float.MIN_VALUE) &&
            (prefs.showSttOffsetX.get() == Float.MIN_VALUE)) {
            makeToast(R.string.compose_toast_drag_stt, Toast.LENGTH_LONG)
            // reset to new flag value that indicates 'not first time through, but not customised'
            prefs.showSttOffsetX.set(Float.MAX_VALUE)
            prefs.showSttOffsetY.set(Float.MAX_VALUE)
        }
    }

    override fun onPause() {
        super.onPause()
        activityVisibleIntent.onNext(false)
    }

    override fun onDestroy() {
        super.onDestroy()

        // stop any playing audio
        QkMediaPlayer.reset()

        seekBarUpdater?.dispose()

        // The attachment sheet is a Dialog attached to this window. Left showing when the activity
        // goes away, most often on a rotation, its window outlives it: Android reports a leaked
        // window and it keeps the dead activity alive behind it.
        if (attachSheetDelegate.isInitialized()) attachSheet.dismiss()

        // Same for the dialog on screen, but without reporting it closed: the state keeps it, and
        // that is what lets the rebuilt screen show it again.
        dialogHost.close()
    }


    override fun render(state: ComposeState) {
        if (state.hasError) {
            finish()
            return
        }

        threadId.onNext(state.threadId)

        dialogHost.render(state.dialog)

        binding.flaggedBanner.isVisible = state.flagged
        if (state.flagged) {
            binding.flaggedReason.text = when {
                state.flagReason.isBlank() -> getString(R.string.compose_flagged_title)
                else -> getString(R.string.compose_flagged_title) + " - " + state.flagReason
            }
        }

        title = when {
            state.selectedMessages > 0 -> getString(R.string.compose_title_selected, state.selectedMessages)
            state.query.isNotEmpty() -> state.query
            else -> state.conversationtitle
        }

        binding.toolbarSubtitle.setVisible(state.query.isNotEmpty())
        binding.toolbarSubtitle.text = getString(R.string.compose_subtitle_results, state.searchSelectionPosition,
            state.searchResults)

        binding.toolbarTitle.setVisible(!state.editingMode)
        binding.chips.setVisible(state.editingMode)
        binding.composeBar.setVisible(!state.loading)
        binding.message.post { updateExpandButton() }

        // Don't set the adapters unless needed
        if (state.editingMode && binding.chips.adapter == null) binding.chips.adapter = chipsAdapter

        binding.toolbar.menu.findItem(R.id.viewScheduledMessages)?.isVisible = !state.editingMode && state.selectedMessages == 0
                && state.query.isEmpty() && state.hasScheduledMessages
        binding.toolbar.menu.findItem(R.id.select_all)?.isVisible = !state.editingMode && (messageAdapter.itemCount > 1) && state.selectedMessages != 0
        binding.toolbar.menu.findItem(R.id.add)?.isVisible = state.editingMode
        binding.toolbar.menu.findItem(R.id.call)?.isVisible = !state.editingMode && state.selectedMessages == 0
                && state.query.isEmpty()
        binding.toolbar.menu.findItem(R.id.headerQuickAction)?.apply {
            val quickAction = prefs.headerQuickAction.get()
            isVisible = quickAction != Preferences.HEADER_ACTION_NONE && !state.editingMode &&
                state.selectedMessages == 0 && state.query.isEmpty()
            if (isVisible) {
                val (iconRes, titleRes) = when (quickAction) {
                    Preferences.HEADER_ACTION_ARCHIVE -> R.drawable.ic_archive_white_24dp to R.string.main_menu_archive
                    Preferences.HEADER_ACTION_UNREAD -> R.drawable.ic_markunread_black_24dp to R.string.main_menu_unread
                    Preferences.HEADER_ACTION_BLOCK -> R.drawable.ic_block_white_24dp to R.string.main_menu_block
                    else -> R.drawable.ic_delete_white_24dp to R.string.main_menu_delete
                }
                // Menu icons are auto-tinted by QkThemedActivity, but only when the shared
                // `menu`/`theme` observable fires; reassigning the drawable here (a fresh instance,
                // since the action can change) needs its own tint so it doesn't render untinted.
                icon = ContextCompat.getDrawable(this@ComposeActivity, iconRes)
                        ?.apply { setTint(toolbarContentColor) }
                title = getString(titleRes)
            }
        }
        binding.toolbar.menu.findItem(R.id.info)?.isVisible = !state.editingMode && state.selectedMessages == 0
                && state.query.isEmpty()
        binding.toolbar.menu.findItem(R.id.copy)?.isVisible =
            !state.editingMode && state.selectedMessages > 0 && state.selectedMessagesHaveText
        binding.toolbar.menu.findItem(R.id.share)?.isVisible =
            !state.editingMode && state.selectedMessages > 0 && state.selectedMessagesHaveText
        binding.toolbar.menu.findItem(R.id.details)?.isVisible = !state.editingMode && state.selectedMessages == 1
        binding.toolbar.menu.findItem(R.id.delete)?.isVisible = !state.editingMode && ((state.selectedMessages > 0) || state.canSend)
        binding.toolbar.menu.findItem(R.id.forward)?.isVisible = !state.editingMode && state.selectedMessages == 1
        binding.toolbar.menu.findItem(R.id.show_status)?.isVisible = !state.editingMode && state.selectedMessages > 0
        binding.toolbar.menu.findItem(R.id.previous)?.isVisible = state.selectedMessages == 0 && state.query.isNotEmpty()
        binding.toolbar.menu.findItem(R.id.next)?.isVisible = state.selectedMessages == 0 && state.query.isNotEmpty()
        binding.toolbar.menu.findItem(R.id.clear)?.isVisible = state.selectedMessages == 0 && state.query.isNotEmpty()

        chipsAdapter.data = state.selectedChips

        binding.loading.setVisible(state.loading)

        binding.sendAsGroup.setVisible(state.recipientCount > 1)
        binding.sendAsGroupSwitch.isChecked = state.sendAsGroup
        binding.sendAsGroupSummary.setText(
            if (binding.sendAsGroupSwitch.isChecked) R.string.compose_send_group_summary_on
            else R.string.compose_send_group_summary_off
        )

        binding.messageList.setVisible(!state.editingMode || state.sendAsGroup || state.selectedChips.size == 1)
        messageAdapter.data = state.messages
        messageAdapter.highlight = state.searchSelectionId
        scheduledMessagesAdapter.updateData(state.scheduledMessages)

        binding.scheduledGroup.isVisible = state.scheduled != 0L
        binding.scheduledTime.text = dateFormatter.getScheduledTimestamp(state.scheduled)

        binding.messageAttachments.setVisible(state.attachments.isNotEmpty())
        composeAttachmentAdapter.data = state.attachments

        // Temporary, chasing the attachment row that shows itself when a message is merely being
        // selected. Read once the layout pass has had its own say about the row, and only worth a
        // line when the row is showing or holding something, which is the whole anomaly.
        if (BuildConfig.DEBUG) {
            binding.messageAttachments.post {
                val visible = binding.messageAttachments.isVisible
                if (visible || composeAttachmentAdapter.itemCount > 0) {
                    Timber.v("attachment row visible=$visible holding " +
                            "${composeAttachmentAdapter.itemCount} item(s) " +
                            "[${state.attachments.joinToString { it.uri.toString() }}], " +
                            "${state.selectedMessages} message(s) selected")
                }
            }
        }

        binding.shadeBackground.apply {
            if (state.audioMsgRecording) {
                visibility = View.VISIBLE
                elevation = 5.dpToPx(context).toFloat() // dim behind the audio recorder
            } else {
                visibility = View.GONE
            }
        }

        // show or hide audio message recording panel and shade background
        binding.audioMsgBackground.isVisible = state.audioMsgRecording

        binding.counter.text = state.remaining
        binding.counter.setVisible(binding.counter.text.isNotBlank())

        binding.sim.setVisible(state.subscription != null)
        binding.sim.contentDescription = getString(R.string.compose_sim_cd, state.subscription?.displayName)
        binding.simIndex.text = state.subscription?.simSlotIndex?.plus(1)?.toString()

        // show either send, audio msg record, or sendScheduled button
        binding.send.visibility = if (state.canSend && !state.loading && state.scheduled == 0L) View.VISIBLE else View.INVISIBLE
        binding.recordAudioMsg.visibility = if (state.canSend && !state.loading) View.INVISIBLE else View.VISIBLE
        binding.scheduledSend.visibility = if (state.canSend && (state.scheduled != 0L) && !state.loading) View.VISIBLE else View.INVISIBLE

        // if not in editing mode, and there are no non-me participants that can be sent to,
        // hide controls that allow constructing a reply and inform user no valid recipients
        if (!state.editingMode && (state.validRecipientNumbers == 0)) {
            binding.composeBar.visibility = View.GONE
            binding.sim.visibility = View.GONE
            binding.recordAudioMsg.visibility = View.GONE
            binding.noValidRecipients.visibility = View.VISIBLE

            // change constraint of messageList to constrain bottom to top of noValidRecipients
            ConstraintSet().apply {
                clone(binding.contentView)
                connect(
                    R.id.messageList,
                    ConstraintSet.BOTTOM,
                    R.id.noValidRecipients,
                    ConstraintSet.TOP,
                    0
                )
                applyTo(binding.contentView)
            }
        }

        // if scheduling mode is set, show schedule dialog
        if (state.scheduling)
            scheduleAction.onNext(true)

        // if stt is available and preference is set to show stt button
        if (isSpeechRecognitionAvailable() && prefs.showStt.get()) {
            binding.speechToTextFrame.isVisible = true

            val xPercent = prefs.showSttOffsetX.get()
            val yPercent = prefs.showSttOffsetY.get()

            // if the stt icon has a custom position, move it
            if ((xPercent != Float.MAX_VALUE) && (yPercent != Float.MAX_VALUE)) {
                binding.speechToTextFrame.x = (binding.contentView.x + (xPercent * binding.contentView.width))
                binding.speechToTextFrame.y = (binding.contentView.y + (yPercent * binding.contentView.height))
            }
        }
    }

    override fun clearSelection() = messageAdapter.clearSelection()

    override fun toggleSelectAll() {
        messageAdapter.toggleSelectAll()
    }

    override fun expandMessages(messageIds: List<Long>, expand: Boolean) {
        messageAdapter.expandMessages(messageIds, expand)
    }

    override fun showBlockingDialog(threadIds: List<Long>, block: Boolean) {
        // Blocking a conversation from the "suspected spam" banner removes it from the inbox, so
        // bounce back to the conversation list once the block has been applied.
        blockingDialog.show(threadIds, block) { finish() }
    }

    override fun requestDefaultSms() {
        navigator.showDefaultSmsDialog(this)
    }

    override fun requestStoragePermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
    }

    override fun requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
    }

    override fun requestSmsPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS), 0)
    }

    @Suppress("DEPRECATION")
    override fun requestContact() {
        val intent = Intent(Intent.ACTION_PICK)
            .setType(ContactsContract.Contacts.CONTENT_TYPE)

        startActivityForResult(Intent.createChooser(intent, null), ComposeView.ATTACH_CONTACT_REQUEST_CODE)
    }

    @Suppress("DEPRECATION")
    override fun showContacts(sharing: Boolean, chips: List<Recipient>) {
        binding.message.hideKeyboard()
        val serialized = HashMap(chips.associate { chip -> chip.address to chip.contact?.lookupKey })
        val intent = Intent(this, ContactsActivity::class.java)
            .putExtra(ContactsActivity.SHARING_KEY, sharing)
            .putExtra(ContactsActivity.CHIPS_KEY, serialized)
        startActivityForResult(intent, ComposeView.SELECT_CONTACT_REQUEST_CODE)
    }

    @Suppress("DEPRECATION")
    override fun startSpeechRecognition() {
        if (isSpeechRecognitionAvailable()) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            try {
                startActivityForResult(intent, ComposeView.SPEECH_RECOGNITION_REQUEST_CODE)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.error_stt_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun themeChanged() {
        binding.messageList.scrapViews()
    }

    override fun showKeyboard() {
        binding.message.postDelayed({
            binding.message.showKeyboard()
        }, 200)
    }

    @Suppress("DEPRECATION")
    override fun requestCamera() {
        cameraDestination = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            .let { timestamp -> ContentValues().apply { put(MediaStore.Images.Media.TITLE, timestamp) } }
            .let { cv -> contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, cameraDestination)
        startActivityForResult(Intent.createChooser(intent, null), ComposeView.TAKE_PHOTOS_REQUEST_CODE)
    }

    @Suppress("DEPRECATION")
    override fun requestGallery(mimeType: String, requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, false)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setType(mimeType)
        startActivityForResult(Intent.createChooser(intent, null), requestCode)
    }

    override fun setDraft(draft: String) {
        binding.message.setText(draft)
        binding.message.setSelection(draft.length)
    }

    /** Opens the full Templates screen in pick mode; the chosen template returns via onActivityResult. */
    override fun showTemplatePicker() {
        startActivityForResult(
            Intent(this, TemplatesActivity::class.java).putExtra(TemplatesActivity.EXTRA_PICK, true),
            ComposeView.TEMPLATE_REQUEST_CODE
        )
    }

    /** Show/hide the bubble's expand chevron — only useful once the message spans multiple lines. */
    private fun updateExpandButton() {
        binding.expand.isVisible = binding.message.isVisible && binding.message.lineCount > 1
    }

    /** Opens the full-screen editor, seeded with the current message text. */
    private fun showFullscreen() {
        binding.fullscreenMessage.setText(binding.message.text)
        binding.fullscreenMessage.setSelection(binding.fullscreenMessage.length())
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        binding.fullscreenEditor.isVisible = true
        binding.fullscreenMessage.requestFocus()
        binding.fullscreenMessage.showKeyboard()
    }

    /** Closes the full-screen editor, syncing its text back into the bubble. */
    private fun collapseFullscreen() {
        binding.message.setText(binding.fullscreenMessage.text)
        binding.message.setSelection(binding.message.length())
        binding.fullscreenEditor.isVisible = false
        window.setSoftInputMode(savedSoftInputMode)
        binding.message.requestFocus()
    }

    /** Syncs the full-screen text into the bubble and triggers the normal send. */
    private fun sendFromFullscreen() {
        binding.message.setText(binding.fullscreenMessage.text)
        binding.fullscreenEditor.isVisible = false
        window.setSoftInputMode(savedSoftInputMode)
        binding.send.performClick()
    }

    /** Inserts [text] into the message field at the cursor, replacing any selection. */
    private fun insertIntoMessage(text: String) {
        val field = binding.message
        val editable = field.text ?: return
        val start = field.selectionStart.coerceIn(0, editable.length)
        val end = field.selectionEnd.coerceIn(0, editable.length)
        editable.replace(minOf(start, end), maxOf(start, end), text)
        field.setSelection((minOf(start, end) + text.length).coerceAtMost(field.length()))
        field.requestFocus()
        field.showKeyboard()
    }

    override fun scrollToMessage(id: Long) {
        messageAdapter.data?.second
            ?.indexOfLast { message -> message.id == id }
            ?.takeIf { position -> position != -1 }
            ?.let(binding.messageList::scrollToPosition)
    }

    override fun showQksmsPlusSnackbar(message: Int) {
        Snackbar.make(binding.contentView, message, Snackbar.LENGTH_LONG).run {
            setAction(R.string.button_more) { viewQksmsPlusIntent.onNext(Unit) }
            setActionTextColor(colors.theme().theme)
            show()
        }
    }

    private fun buildDialog(spec: ComposeDialog): Dialog = when (spec) {
        is ComposeDialog.DeleteMessages -> {
            val count = spec.messageIds.size
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(resources.getQuantityString(R.plurals.dialog_delete_chat, count, count))
                .setPositiveButton(R.string.button_delete) { _, _ -> confirmDeleteIntent.onNext(spec.messageIds) }
                .setNegativeButton(R.string.button_cancel, null)
                .create()
        }

        is ComposeDialog.DeleteConversation -> AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(resources.getQuantityString(R.plurals.dialog_delete_message, 1, 1))
            .setPositiveButton(R.string.button_delete) { _, _ ->
                confirmDeleteConversationIntent.onNext(spec.threadId)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .create()

        ComposeDialog.ClearMessage -> AlertDialog.Builder(this)
            .setTitle(R.string.dialog_clear_compose_title)
            .setMessage(R.string.dialog_clear_compose)
            .setPositiveButton(R.string.button_clear) { _, _ -> clearCurrentMessageIntent.onNext(true) }
            .setNegativeButton(R.string.button_cancel, null)
            .create()

        is ComposeDialog.OpenLink -> AlertDialog.Builder(this)
            .setTitle(R.string.messageLinkHandling_dialog_title)
            .setMessage(getString(R.string.messageLinkHandling_dialog_body, spec.uri.toString()))
            .setPositiveButton(R.string.messageLinkHandling_dialog_positive) { _, _ ->
                ContextCompat.startActivity(this, Intent(Intent.ACTION_VIEW).setData(spec.uri), null)
            }
            .setNegativeButton(R.string.messageLinkHandling_dialog_negative, null)
            .create()

        is ComposeDialog.MessageDetails -> AlertDialog.Builder(this)
            .setTitle(R.string.compose_details_title)
            .setMessage(spec.details)
            .setCancelable(true)
            .create()

        is ComposeDialog.Reactions -> AlertDialog.Builder(this)
            .setTitle(R.string.compose_reactions_title)
            .setMessage(spec.lines.joinToString("\n"))
            .create()

        ComposeDialog.ScheduleDate -> {
            // On some devices, the keyboard can cover the picker
            binding.message.hideKeyboard()
            val now = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                scheduleDateSelectedIntent.onNext(Triple(year, month, day))
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
        }

        is ComposeDialog.ScheduleTime -> {
            val now = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                val scheduled = Calendar.getInstance()
                        .apply { set(spec.year, spec.month, spec.day, hour, minute) }
                scheduleSelectedIntent.onNext(scheduled.timeInMillis)
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), DateFormat.is24HourFormat(this))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.compose, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        optionsItemIntent.onNext(item.itemId)
        return true
    }

    override fun getColoredMenuItems(): List<Int> {
        return super.getColoredMenuItems()
    }

    override fun onCreateContextMenu(
        menu: ContextMenu?,
        v: View?,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.mms_part_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        super.onContextItemSelected(item)
        contextItemIntent.onNext(item)
        return true
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK)
            return

        when (requestCode) {
            ComposeView.SELECT_CONTACT_REQUEST_CODE -> {
                @Suppress("UNCHECKED_CAST")
                chipsSelectedIntent.onNext(data?.getSerializableExtra(ContactsActivity.CHIPS_KEY)
                    ?.let { serializable -> serializable as? HashMap<String, String?> }
                    ?: hashMapOf())
            }

            ComposeView.TAKE_PHOTOS_REQUEST_CODE -> {
                cameraDestination?.let(attachAnyFileSelectedIntent::onNext)
            }

            ComposeView.ATTACH_FILE_REQUEST_CODE -> {
                data?.clipData?.itemCount
                    ?.let { count -> 0 until count }
                    ?.mapNotNull { i -> data.clipData?.getItemAt(i)?.uri }
                    ?.forEach(attachAnyFileSelectedIntent::onNext)
                    ?: data?.data?.let(attachAnyFileSelectedIntent::onNext)
            }

            ComposeView.ATTACH_CONTACT_REQUEST_CODE -> {
                data?.data?.let(contactSelectedIntent::onNext)
            }

            ComposeView.SPEECH_RECOGNITION_REQUEST_CODE -> {
                // check returned results are good
                val match = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if ((match !== null) && (match.size > 0) && (!match[0].isNullOrEmpty())) {
                    // populate message box with data returned by STT, set cursor to end, and focus
                    binding.message.append(match[0])
                    binding.message.setSelection(binding.message.text?.length ?: 0)
                    binding.message.requestFocus()
                }
            }

            ComposeView.TEMPLATE_REQUEST_CODE -> {
                data?.getStringExtra(TemplatesActivity.EXTRA_BODY)?.let(::insertIntoMessage)
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(ComposeView.CAMERA_DESTINATION_KEY, cameraDestination)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        cameraDestination = savedInstanceState.getParcelable(ComposeView.CAMERA_DESTINATION_KEY)
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onBackPressed() {
        if (binding.fullscreenEditor.isVisible) collapseFullscreen()
        else backPressedIntent.onNext(Unit)
    }

    override fun focusMessage() {
        binding.message.requestFocus()
    }
}
