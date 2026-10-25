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
package io.openmessages.feature.main

import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.ItemTouchHelper
import autodispose2.androidx.lifecycle.scope
import autodispose2.autoDispose
import io.openmessages.R
import io.openmessages.common.Navigator
import io.openmessages.common.base.QkViewModel
import io.openmessages.extensions.mapNotNull
import io.openmessages.interactor.DeleteConversations
import io.openmessages.interactor.MarkAllSeen
import io.openmessages.interactor.MarkArchived
import io.openmessages.interactor.MarkPinned
import io.openmessages.interactor.MarkRead
import io.openmessages.interactor.MarkUnarchived
import io.openmessages.interactor.MarkUnpinned
import io.openmessages.interactor.MarkUnread
import io.openmessages.interactor.MigratePreferences
import io.openmessages.interactor.SpeakThreads
import io.openmessages.interactor.SyncContacts
import io.openmessages.interactor.SyncMessages
import io.openmessages.listener.ContactAddedListener
import io.openmessages.manager.ChangelogManager
import io.openmessages.manager.PermissionManager
import io.openmessages.manager.RatingManager
import io.openmessages.model.Conversation
import io.openmessages.repository.ConversationRepository
import io.openmessages.repository.MessageRepository
import io.openmessages.repository.ScheduledMessageRepository
import io.openmessages.repository.SyncRepository
import io.openmessages.util.Preferences
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.kotlin.Observables
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.withLatestFrom
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MainViewModel @Inject constructor(
    contactAddedListener: ContactAddedListener,
    markAllSeen: MarkAllSeen,
    migratePreferences: MigratePreferences,
    syncRepository: SyncRepository,
    private val changelogManager: ChangelogManager,
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository,
    private val deleteConversations: DeleteConversations,
    private val markArchived: MarkArchived,
    private val markPinned: MarkPinned,
    private val markRead: MarkRead,
    private val markUnarchived: MarkUnarchived,
    private val markUnpinned: MarkUnpinned,
    private val markUnread: MarkUnread,
    private val scheduledMessageRepo: ScheduledMessageRepository,
    private val speakThreads: SpeakThreads,
    private val navigator: Navigator,
    private val permissionManager: PermissionManager,
    private val prefs: Preferences,
    private val ratingManager: RatingManager,
    private val syncContacts: SyncContacts,
    private val syncMessages: SyncMessages
) : QkViewModel<MainView, MainState>(MainState()) {
    private var lastArchivedThreadIds = listOf<Long>(0)

    /**
     * Whether the conversation list shows the archived conversations rather than the inbox. Every
     * value starts the list over, so that a page shown again is filled again.
     */
    private val archivedShown: Subject<Boolean> = BehaviorSubject.createDefault(false)

    init {
        disposables += deleteConversations
        disposables += markAllSeen
        disposables += markArchived
        disposables += markUnarchived
        disposables += migratePreferences
        disposables += syncContacts
        disposables += syncMessages

        // Show the syncing UI
        disposables += syncRepository.syncProgress
                .sample(16, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .subscribe { syncing -> newState { copy(syncing = syncing) } }

        // Show the rating UI
        disposables += ratingManager.shouldShowRating
                .subscribe { show -> newState { copy(showRating = show) } }

        // Fill the inbox or the archive, whichever is shown, and keep it up to date
        disposables += Observables
            .combineLatest(archivedShown, prefs.unreadAtTop.asObservable().distinctUntilChanged())
            .switchMap { (archived, unreadAtTop) ->
                conversationRepo.getConversations(unreadAtTop, archived)
                    .map { conversations -> archived to conversations }
            }
            .subscribe { (archived, conversations) ->
                newState {
                    when {
                        !archived && page is Inbox -> copy(page = page.copy(data = conversations))
                        archived && page is Archived -> copy(page = page.copy(data = conversations))
                        else -> this
                    }
                }
            }

        // Fetch scheduled messages and assign it to hasScheduledMessage
        disposables += scheduledMessageRepo.getScheduledMessages()
            .map { messages ->
                messages.map { it.conversationId }.toSet()
            }
            .subscribe { ids ->
                newState { copy(scheduledConversationIds = ids) }
            }

        // Migrate the preferences from 2.7.3
        migratePreferences.execute(Unit)


        disposables += Completable
            .fromAction {
                // If we have all permissions and we've never run a sync, run a sync. This will be
                // the case when upgrading from 2.7.3, or if the app's data was cleared
                if (!syncRepository.hasSyncedMessages() && permissionManager.isDefaultSms() &&
                    permissionManager.hasReadSms() && permissionManager.hasContacts()) {
                    syncMessages.execute(Unit)
                }

                // This is only used when we update to a version that newly supports emoji reactions
                syncRepository.reparseEmojiReactionsIfNeeded()
            }
            .subscribeOn(Schedulers.io())
            .subscribe()

        // Sync contacts when we detect a change
        if (permissionManager.hasContacts()) {
            disposables += contactAddedListener.listen()
                    .debounce(1, TimeUnit.SECONDS)
                    .subscribeOn(Schedulers.io())
                    .subscribe { syncContacts.execute(Unit) }
        }

        ratingManager.addSession()
        markAllSeen.execute(Unit)
    }

    override fun bindView(view: MainView) {
        super.bindView(view)

        when {
            !permissionManager.isDefaultSms() -> view.requestDefaultSms()
            !permissionManager.hasReadSms() || !permissionManager.hasContacts() -> view.requestPermissions()
        }


        // If the default SMS state changes, reflect it in the State
        view.activityResumedIntent
            .filter { resumed -> resumed }
            .observeOn(Schedulers.io())
            .map { permissionManager.isDefaultSms() }
            .distinctUntilChanged()
            .doOnNext { defaultSms -> newState { copy(defaultSms = defaultSms) } }
            .autoDispose(view.scope())
            .subscribe()

        // If the SMS Permission state changes, reflect it in the State
        view.activityResumedIntent
            .filter { resumed -> resumed }
            .observeOn(Schedulers.io())
            .map { permissionManager.hasReadSms() }
            .distinctUntilChanged()
            .doOnNext { smsPermission -> newState { copy(smsPermission = smsPermission) } }
            .autoDispose(view.scope())
            .subscribe()

        // If the Contacts Permission state changes, reflect it in the State
        view.activityResumedIntent
            .filter { resumed -> resumed }
            .observeOn(Schedulers.io())
            .map { permissionManager.hasContacts() }
            .distinctUntilChanged()
            .doOnNext { contactPermission -> newState { copy(contactPermission = contactPermission) } }
            .autoDispose(view.scope())
            .subscribe()

        // If the Notifications Permission state changes, reflect it in the State
        view.activityResumedIntent
            .filter { resumed -> resumed }
            .observeOn(Schedulers.io())
            .map { permissionManager.hasNotifications() }
            .distinctUntilChanged()
            .doOnNext { notificationPermission -> newState { copy(notificationPermission = notificationPermission) } }
            .autoDispose(view.scope())
            .subscribe()

        // If we go from not having all SMS permissions to having them, sync messages
        view.activityResumedIntent
            .filter { resumed -> resumed }
            .observeOn(Schedulers.io())
            .map { permissionManager.isDefaultSms() && permissionManager.hasReadSms() && permissionManager.hasContacts() }
            .distinctUntilChanged()
            .skip(1)
            .filter { hasAllPermissions -> hasAllPermissions }
            .autoDispose(view.scope())
            .subscribe { syncMessages.execute(Unit) }

        // Launch screen from intent
        view.onNewIntentIntent
                .autoDispose(view.scope())
                .subscribe { intent ->
                    when (intent.getStringExtra("screen")) {
                        "compose" -> navigator.showConversation(intent.getLongExtra("threadId", 0))
                        "blocking" -> navigator.showBlockedConversations()
                    }
                }

        // Show changelog
        if (changelogManager.didUpdate()) {
            if (Locale.getDefault().language.startsWith("en")) {
                viewModelScope.launch {
                    val changelog = changelogManager.getChangelog()
                    changelogManager.markChangelogSeen()
                    // Nothing recorded for this version means nothing to say. The dialog would
                    // otherwise open empty on every update until entries are written for it.
                    val hasEntries = changelog.added.isNotEmpty() || changelog.improved.isNotEmpty() ||
                            changelog.removed.isNotEmpty() || changelog.fixed.isNotEmpty()
                    if (hasEntries) view.showChangelog(changelog)
                }
            } else {
                changelogManager.markChangelogSeen()
            }
        } else {
            changelogManager.markChangelogSeen()
        }

        view.changelogMoreIntent
                .autoDispose(view.scope())
                .subscribe { navigator.showChangelog() }

        view.queryChangedIntent
                .debounce(200, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .map { query -> query.trim() }
                .withLatestFrom(state) { query, state ->
                    if (query.isEmpty() && state.page is Searching) {
                        showConversations(archived = false)
                    }
                    query
                }
                .filter { query -> query.length >= 2 }
                .distinctUntilChanged()
                .doOnNext {
                    newState {
                        val page = (page as? Searching) ?: Searching()
                        copy(page = page.copy(loading = true))
                    }
                }
                .observeOn(Schedulers.io())
                .map(conversationRepo::searchConversations)
                .autoDispose(view.scope())
                .subscribe { data -> newState { copy(page = Searching(loading = false, data = data)) } }

        view.activityResumedIntent
                .filter { resumed -> !resumed }
                .switchMap {
                    // Take until the activity is resumed
                    prefs.keyChanges
                            .filter { key -> key.contains("theme") }
                            .map { true }
                            .mergeWith(prefs.autoColor.asObservable().skip(1))
                            .doOnNext { view.themeChanged() }
                            .takeUntil(view.activityResumedIntent.filter { resumed -> resumed })
                }
                .autoDispose(view.scope())
                .subscribe()

        view.composeIntent
                .autoDispose(view.scope())
                .subscribe { navigator.showCompose() }

        view.homeIntent
                .withLatestFrom(state) { _, state ->
                    when {
                        state.page is Searching -> view.clearSearch()
                        state.page is Inbox && state.page.selected > 0 -> view.clearSelection()
                        state.page is Archived && state.page.selected > 0 -> view.clearSelection()

                        else -> newState { copy(drawerOpen = true) }
                    }
                }
                .autoDispose(view.scope())
                .subscribe()

        view.drawerToggledIntent
            .autoDispose(view.scope())
            .subscribe { open ->
                newState { copy(drawerOpen = open) }
                view.drawerToggled(open)
            }

        view.navigationIntent
                .withLatestFrom(state) { drawerItem, state ->
                    newState { copy(drawerOpen = false) }
                    when (drawerItem) {
                        NavItem.BACK -> when {
                            state.drawerOpen -> Unit
                            state.page is Searching -> view.clearSearch()
                            state.page is Inbox && state.page.selected > 0 -> view.clearSelection()
                            state.page is Archived && state.page.selected > 0 -> view.clearSelection()
                            state.page !is Inbox -> showConversations(archived = false)
                            else -> newState { copy(hasError = true) }
                        }
                        NavItem.BACKUP -> navigator.showBackup()
                        NavItem.SCHEDULED -> navigator.showScheduled(null)
                        NavItem.BLOCKING -> navigator.showBlockedConversations()
                        NavItem.MESSAGE_UTILS -> navigator.showMessageUtils()
                        NavItem.TEMPLATES -> navigator.showTemplates()
                        NavItem.SETTINGS -> navigator.showSettings()
                        NavItem.ABOUT -> navigator.showAbout()
//                        NavItem.HELP -> navigator.showSupport()
                        NavItem.INVITE -> navigator.showInvite()
                        else -> Unit
                    }
                    drawerItem
                }
                .distinctUntilChanged()
                .doOnNext { drawerItem ->
                    when (drawerItem) {
                        NavItem.INBOX -> showConversations(archived = false)
                        NavItem.ARCHIVED -> showConversations(archived = true)
                        else -> Unit
                    }
                }
                .autoDispose(view.scope())
                .subscribe()

        view.optionsItemIntent
            .filter { itemId -> itemId == R.id.select_all }
            .autoDispose(view.scope())
            .subscribe { view.toggleSelectAll() }

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.archive }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    markArchived.execute(conversations)
                    lastArchivedThreadIds = conversations.toList()
                    view.showArchivedSnackbar(lastArchivedThreadIds.count(), true)
                    view.clearSelection()
                }
                .autoDispose(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.unarchive }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    markUnarchived.execute(conversations.toList())
                    view.showArchivedSnackbar(conversations.count(), false)
                    view.clearSelection()
                }
                .autoDispose(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.delete }
                .filter { permissionManager.isDefaultSms().also { if (!it) view.requestDefaultSms() } }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    newState { copy(dialog = MainDialog.DeleteConversations(conversations)) }
                }
                .autoDispose(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.add }
                .withLatestFrom(view.conversationsSelectedIntent, state) { _, selection, state ->
                    state.conversations(selection)
                }
                .doOnNext { view.clearSelection() }
                .filter { conversations -> conversations.size == 1 }
                .map { conversations -> conversations.first().recipients }
                .mapNotNull { recipients -> recipients[0].address.takeIf { recipients.size == 1 } }
                .doOnNext(navigator::addContact)
                .autoDispose(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.pin }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    markPinned.execute(conversations.toList())
                    view.clearSelection()
                }
                .autoDispose(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.unpin }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    markUnpinned.execute(conversations.toList())
                    view.clearSelection()
                }
                .autoDispose(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.read }
                .filter { permissionManager.isDefaultSms().also { if (!it) view.requestDefaultSms() } }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    markRead.execute(conversations.toList())
                    view.clearSelection()
                }
                .autoDispose(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.unread }
                .filter { permissionManager.isDefaultSms().also { if (!it) view.requestDefaultSms() } }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    markUnread.execute(conversations.toList())
                    view.clearSelection()
                }
                .autoDispose(view.scope())
                .subscribe()

        view.optionsItemIntent
                .filter { itemId -> itemId == R.id.block }
                .withLatestFrom(view.conversationsSelectedIntent) { _, conversations ->
                    view.showBlockingDialog(conversations.toList(), true)
                    view.clearSelection()
                }
                .autoDispose(view.scope())
                .subscribe()

        view.optionsItemIntent
            .filter { itemId -> itemId == R.id.rename }
            .withLatestFrom(view.conversationsSelectedIntent, state) { _, selection, state ->
                state.conversations(selection.take(1))
            }
            .mapNotNull { conversations -> conversations.firstOrNull() }
            .autoDispose(view.scope())
            .subscribe { conversation ->
                newState { copy(dialog = MainDialog.RenameConversation(conversation.id, conversation.name)) }
            }

        view.rateIntent
                .autoDispose(view.scope())
                .subscribe {
                    navigator.showRating()
                    ratingManager.rate()
                }

        view.dismissRatingIntent
                .autoDispose(view.scope())
                .subscribe { ratingManager.dismiss() }

        view.conversationsSelectedIntent
                .withLatestFrom(state) { selection, state ->
                    val conversations = state.conversations(selection)
                    val add = conversations.firstOrNull()
                            ?.takeIf { conversations.size == 1 }
                            ?.takeIf { conversation -> conversation.recipients.size == 1 }
                            ?.recipients?.first()
                            ?.takeIf { recipient -> recipient.contact == null } != null
                    val pin = conversations.map { if (it.pinned) -1 else 1 }.sum() >= 0
                    val read = when (conversations.size) {
                        0    -> false
                        1    -> conversations[0].unread
                        else -> true
                    }
                    val selected = selection.size

                    // The page is copied as it stands by then, so that a list arriving meanwhile is kept
                    newState {
                        when (page) {
                            is Inbox -> copy(page = page.copy(addContact = add, markPinned = pin, markRead = read, selected = selected))
                            is Archived -> copy(page = page.copy(addContact = add, markPinned = pin, markRead = read, selected = selected))
                            is Searching -> this
                        }
                    }
                }
                .autoDispose(view.scope())
                .subscribe()

        // Clear the dialog once it is gone, unless it has already been replaced by another one.
        view.dialogDismissedIntent
                .autoDispose(view.scope())
                .subscribe { dismissed ->
                    newState { if (dialog == dismissed) copy(dialog = null) else this }
                }

        // Delete the conversation
        view.confirmDeleteIntent
                .autoDispose(view.scope())
                .subscribe { conversations ->
                    deleteConversations.execute(conversations.toList())
                    view.clearSelection()
                }

        // The conversation comes from the dialog, not from the list's selection: that selection is
        // gone if the screen was rebuilt while the dialog was up, and renaming would rename nothing.
        view.renameConversationIntent
            .doOnNext { view.clearSelection() }
            .map { (conversationId, newConversationName) ->
                conversationRepo.setConversationName(conversationId, newConversationName)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
            }
            .flatMapCompletable { it }
            .autoDispose(view.scope())
            .subscribe()

        view.swipeConversationIntent
                .withLatestFrom(state) { swipe, state -> swipe to state }
                .autoDispose(view.scope())
                .subscribe { (swipe, state) ->
                    val (threadId, direction) = swipe
                    val action =
                        if (direction == ItemTouchHelper.RIGHT) prefs.swipeRight.get()
                        else prefs.swipeLeft.get()
                    when (action) {
                        Preferences.SWIPE_ACTION_ARCHIVE ->
                            markArchived.execute(listOf(threadId)) {
                                lastArchivedThreadIds = listOf(threadId)
                                view.showArchivedSnackbar(1, true)
                            }
                        Preferences.SWIPE_ACTION_DELETE ->
                            newState { copy(dialog = MainDialog.DeleteConversations(listOf(threadId))) }
                        Preferences.SWIPE_ACTION_BLOCK ->
                            view.showBlockingDialog(listOf(threadId), true)
                        Preferences.SWIPE_ACTION_CALL ->
                            Maybe
                                .fromCallable<String> {
                                    messageRepo.getMessagesSync(threadId).lastOrNull { !it.isMe() }
                                        ?.address // most recent non-me msg address
                                        ?: state.conversations(listOf(threadId)).firstOrNull()
                                            ?.recipients?.firstOrNull()?.address  // first recipient in convo
                                }
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .autoDispose(view.scope())
                                .subscribe(navigator::makePhoneCall)
                        Preferences.SWIPE_ACTION_READ -> markRead.execute(listOf(threadId))
                        Preferences.SWIPE_ACTION_UNREAD -> markUnread.execute(listOf(threadId))
                        Preferences.SWIPE_ACTION_TOGGLE_READ ->
                            if (state.conversations(listOf(threadId)).firstOrNull()?.unread == true) {
                                markRead.execute(listOf(threadId))
                            } else {
                                markUnread.execute(listOf(threadId))
                            }
                        Preferences.SWIPE_ACTION_SPEAK -> speakThreads.execute(listOf(threadId))
                    }
                }

        view.undoArchiveIntent
                .autoDispose(view.scope())
                .subscribe {
                    markUnarchived.execute(lastArchivedThreadIds.toList())
                    lastArchivedThreadIds = listOf()
                }

        view.snackbarButtonIntent
                .withLatestFrom(state) { _, state ->
                    when {
                        !state.defaultSms -> view.requestDefaultSms()
                        !state.smsPermission -> view.requestPermissions()
                        !state.contactPermission -> view.requestPermissions()
                        !state.notificationPermission -> when {
                            !prefs.hasAskedForNotificationPermission.get() -> {
                                prefs.hasAskedForNotificationPermission.set(true)
                                view.requestPermissions()
                            }
                            view.shouldShowNotificationRationale() -> view.requestPermissions()
                            else -> navigator.showAppNotificationSettings()
                        }
                    }
                }
                .autoDispose(view.scope())
                .subscribe()
    }

    private fun showConversations(archived: Boolean) {
        newState { copy(page = if (archived) Archived() else Inbox()) }
        archivedShown.onNext(archived)
    }

    /**
     * The conversations of the list on screen with these ids, which are the ones it can select
     */
    private fun MainState.conversations(ids: Collection<Long>): List<Conversation> {
        val conversations = when (page) {
            is Inbox -> page.data
            is Archived -> page.data
            is Searching -> null
        }
        return conversations.orEmpty().filter { conversation -> conversation.id in ids }
    }

}
