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
package io.openmessages.feature.qkreply

import android.telephony.SmsMessage
import autodispose2.androidx.lifecycle.scope
import autodispose2.autoDispose
import io.openmessages.R
import io.openmessages.common.Navigator
import io.openmessages.common.base.QkViewModel
import io.openmessages.compat.SubscriptionManagerCompat
import io.openmessages.extensions.mapNotNull
import io.openmessages.interactor.DeleteMessages
import io.openmessages.interactor.MarkRead
import io.openmessages.interactor.SendNewMessage
import io.openmessages.model.Conversation
import io.openmessages.model.Message
import io.openmessages.repository.ConversationRepository
import io.openmessages.repository.MessageRepository
import io.openmessages.util.ActiveSubscriptionObservable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.functions.Function3
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.withLatestFrom
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

class QkReplyViewModel @Inject constructor(
    @Named("threadId") private val threadId: Long,
    private val conversationRepo: ConversationRepository,
    private val deleteMessages: DeleteMessages,
    private val markRead: MarkRead,
    private val messageRepo: MessageRepository,
    private val navigator: Navigator,
    private val sendNewMessage: SendNewMessage,
    private val subscriptionManager: SubscriptionManagerCompat
) : QkViewModel<QkReplyView, QkReplyState>(QkReplyState(threadId = threadId)) {

    private val conversation: Subject<Conversation> = BehaviorSubject.create()

    /**
     * Whether every message of the conversation is shown, rather than the unread ones only
     */
    private val expanded: Subject<Boolean> = BehaviorSubject.createDefault(false)

    private val messages: Subject<List<Message>> = BehaviorSubject.create()

    init {
        disposables += markRead
        disposables += sendNewMessage

        // Handed on from the main thread, where the view can be told about it
        disposables += conversationRepo.observeConversation(threadId)
                .mapNotNull { conversation -> conversation.value }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(conversation::onNext)

        disposables += expanded
                .switchMap { expanded ->
                    when (expanded) {
                        true -> messageRepo.getMessages(threadId)
                        false -> messageRepo.observeUnreadMessages(threadId)
                    }
                }
                .subscribe(messages::onNext)

        // When the set of messages changes, update the state
        // If we're ever showing an empty set of messages, then it's time to shut down to activity
        disposables += Observable
                .combineLatest(messages, conversation) { messages, conversation ->
                    newState { copy(data = Pair(conversation, messages)) }
                    messages
                }
                .filter { it.isEmpty() }
                .subscribe { newState { copy(hasError = true) } }

        disposables += conversation
                .map { conversation -> conversation.getTitle() }
                .distinctUntilChanged()
                .subscribe { title -> newState { copy(title = title) } }

        val latestSubId = messages
                .map { messages -> messages.lastOrNull()?.subId ?: -1 }
                .distinctUntilChanged()

        val subscriptions = ActiveSubscriptionObservable(subscriptionManager)
        disposables += Observable.combineLatest(latestSubId, subscriptions) { subId, subs ->
            val sub = if (subs.size > 1) subs.firstOrNull { it.subscriptionId == subId } ?: subs[0] else null
            newState { copy(subscription = sub) }
        }.subscribe()
    }

    override fun bindView(view: QkReplyView) {
        super.bindView(view)

        conversation
            .take(1)  // only update saved draft to ui once
            .map { conversation -> conversation.draft }
            .distinctUntilChanged()
            .autoDispose(view.scope())
            .subscribe { draft -> view.setDraft(draft) }

        // Mark read
        view.menuItemIntent
                .filter { id -> id == R.id.read }
                .autoDispose(view.scope())
                .subscribe {
                    markRead.execute(listOf(threadId)) { newState { copy(hasError = true) } }
                }

        // Call
        view.menuItemIntent
            .filter { id -> id == R.id.call }
            .withLatestFrom(state, conversation)
            .mapNotNull { (_, state, conversation) ->
                state.data?.second?.lastOrNull { !it.isMe() }?.address // most recent non-me msg address
                    ?: conversation.recipients.firstOrNull()?.address  // first recipient in convo
            }
            .doOnNext { navigator.makePhoneCall(it) }
            .autoDispose(view.scope())
            .subscribe { newState { copy(hasError = true) } }

        // Show all messages
        view.menuItemIntent
                .filter { id -> id == R.id.expand }
                .autoDispose(view.scope())
                .subscribe {
                    expanded.onNext(true)
                    newState { copy(expanded = true) }
                }

        // Show unread messages only
        view.menuItemIntent
                .filter { id -> id == R.id.collapse }
                .autoDispose(view.scope())
                .subscribe {
                    expanded.onNext(false)
                    newState { copy(expanded = false) }
                }

        // Delete new messages
        view.menuItemIntent
                .filter { id -> id == R.id.delete }
                .observeOn(Schedulers.io())
                .map { messageRepo.getUnreadMessages(threadId).map { it.id } }
                .map { messages -> DeleteMessages.Params(messages, threadId) }
                .autoDispose(view.scope())
                .subscribe { deleteMessages.execute(it) { newState { copy(hasError = true) } } }

        // View conversation
        view.menuItemIntent
                .filter { id -> id == R.id.view }
                .doOnNext { navigator.showConversation(threadId) }
                .autoDispose(view.scope())
                .subscribe { newState { copy(hasError = true) } }

        // Enable the send button when there is text input into the new message body or there's
        // an attachment, disable otherwise
        view.textChangedIntent
                .map { text -> text.isNotBlank() }
                .autoDispose(view.scope())
                .subscribe { canSend -> newState { copy(canSend = canSend) } }

        // Show the remaining character counter when necessary
        view.textChangedIntent
                .observeOn(Schedulers.computation())
                .map { draft -> SmsMessage.calculateLength(draft, false) }
                .map { array ->
                    val messages = array[0]
                    val remaining = array[2]

                    when {
                        messages <= 1 && remaining > 10 -> ""
                        messages <= 1 && remaining <= 10 -> "$remaining"
                        else -> "$remaining / $messages"
                    }
                }
                .distinctUntilChanged()
                .autoDispose(view.scope())
                .subscribe { remaining -> newState { copy(remaining = remaining) } }

        // Update the draft whenever the text is changed
        view.textChangedIntent
                .debounce(100, TimeUnit.MILLISECONDS)
                .map { draft -> draft.toString() }
                .observeOn(Schedulers.io())
                .autoDispose(view.scope())
                .subscribe { draft -> conversationRepo.saveDraft(threadId, draft) }

        // Toggle to the next sim slot
        view.changeSimIntent
                .withLatestFrom(state, BiFunction { _, state ->
                    val subs = subscriptionManager.activeSubscriptionInfoList
                    val subIndex = subs.indexOfFirst { it.subscriptionId == state.subscription?.subscriptionId }
                    val subscription = when {
                        subIndex == -1 -> null
                        subIndex < subs.size - 1 -> subs[subIndex + 1]
                        else -> subs[0]
                    }
                    newState { copy(subscription = subscription) }
                })
                .autoDispose(view.scope())
                .subscribe()

        // Send a message when the send button is clicked, and disable editing mode if it's enabled
        view.sendIntent
                .withLatestFrom(view.textChangedIntent, BiFunction { _, body -> body })
                .map { body -> body.toString() }
                .withLatestFrom(state, conversation, Function3 { body, state, conversation ->
                    sendNewMessage.execute(SendNewMessage.Params(
                        state.subscription?.subscriptionId ?: -1, 0,
                        conversation.recipients.map { it.address }, body, conversation.sendAsGroup
                    ))
                    view.setDraft("")
                })
                .doOnNext {
                    markRead.execute(listOf(threadId)) { newState { copy(hasError = true) } }
                }
                .autoDispose(view.scope())
                .subscribe()
    }

}
