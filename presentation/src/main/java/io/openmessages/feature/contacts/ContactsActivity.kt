/*
 * Copyright (C) 2019 Moez Bhatti <moez.bhatti@gmail.com>
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
package io.openmessages.feature.contacts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.rxbinding4.view.clicks
import com.jakewharton.rxbinding4.widget.editorActions
import com.jakewharton.rxbinding4.widget.textChanges
import dagger.android.AndroidInjection
import io.openmessages.R
import io.openmessages.common.ViewModelFactory
import io.openmessages.common.base.ThemedActivity
import io.openmessages.common.util.extensions.finishWithoutAnimation
import io.openmessages.common.util.extensions.hideKeyboard
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.common.util.extensions.showKeyboard
import io.openmessages.common.widget.GroupHeadingDecoration
import io.openmessages.common.widget.ListDialog
import io.openmessages.databinding.ContactsActivityBinding
import io.openmessages.extensions.Optional
import io.openmessages.feature.compose.editing.ComposeItem
import io.openmessages.feature.compose.editing.ComposeItemAdapter
import io.openmessages.feature.compose.editing.PhoneNumberAction
import io.openmessages.feature.compose.editing.PhoneNumberPickerAdapter
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import javax.inject.Inject

class ContactsActivity : ThemedActivity(), ContactsContract {

    companion object {
        const val SHARING_KEY = "sharing"
        const val CHIPS_KEY = "chips"
        // Set when the picker is opened directly (e.g. from Templates) rather than by the composer, so
        // finish() knows how to animate: a direct pick hands off to the composer next, a direct cancel
        // slides normally back to the caller.
        const val STANDALONE_KEY = "standalone"

        /**
         * The chips [intent] carries under [CHIPS_KEY]: the address of each, with the lookup key of
         * its contact
         */
        fun getChips(intent: Intent?): HashMap<String, String?> {
            val chips = intent?.let { IntentCompat.getSerializableExtra(it, CHIPS_KEY, HashMap::class.java) }
            return chips.orEmpty().entries
                .mapNotNull { (address, lookupKey) -> (address as? String)?.let { it to lookupKey as? String } }
                .toMap(HashMap())
        }
    }

    private lateinit var binding: ContactsActivityBinding

    @Inject lateinit var contactsAdapter: ComposeItemAdapter
    @Inject lateinit var phoneNumberAdapter: PhoneNumberPickerAdapter
    @Inject lateinit var viewModelFactory: ViewModelFactory

    override val queryChangedIntent: Observable<CharSequence> by lazy { binding.search.textChanges() }
    override val queryClearedIntent: Observable<*> by lazy { binding.cancel.clicks() }
    override val queryEditorActionIntent: Observable<Int> by lazy { binding.search.editorActions() }
    override val composeItemPressedIntent: Subject<ComposeItem> by lazy { contactsAdapter.clicks }
    override val composeItemLongPressedIntent: Subject<ComposeItem> by lazy { contactsAdapter.longClicks }
    override val phoneNumberSelectedIntent: Subject<Optional<Long>> by lazy { phoneNumberAdapter.selectedItemChanges }
    override val phoneNumberActionIntent: Subject<PhoneNumberAction> = PublishSubject.create()

    private val viewModel by lazy { ViewModelProvider(this, viewModelFactory)[ContactsViewModel::class.java] }

    private val phoneNumberDialog by lazy {
        ListDialog(this).apply {
            titleRes = R.string.compose_number_picker_title
            adapter = phoneNumberAdapter
            positiveButton = R.string.compose_number_picker_always
            positiveButtonListener = { phoneNumberActionIntent.onNext(PhoneNumberAction.ALWAYS) }
            negativeButton = R.string.compose_number_picker_once
            negativeButtonListener = { phoneNumberActionIntent.onNext(PhoneNumberAction.JUST_ONCE) }
            cancelListener = { phoneNumberActionIntent.onNext(PhoneNumberAction.CANCEL) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = ContactsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showBackButton(true)
        viewModel.bindView(this)

        binding.contacts.adapter = contactsAdapter
        setUpLetterRail()

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Cancel the picker with an empty result. The composer closes itself when no recipient
                // was chosen and returns to whatever opened it, instead of jumping to the main screen.
                this@ContactsActivity.finish(hashMapOf<String, String?>())
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun render(state: ContactsState) {
        binding.cancel.isVisible = state.query.length > 1

        contactsAdapter.data = state.composeItems
        binding.letterRail.letters = contactsAdapter.initials().keys.toList()
        updateRailSelection()

        if (state.selectedContact != null && !phoneNumberDialog.isShowing) {
            phoneNumberAdapter.data = state.selectedContact.numbers
            phoneNumberDialog.subtitle = state.selectedContact.name
            phoneNumberDialog.show()
        } else if (state.selectedContact == null && phoneNumberDialog.isShowing) {
            phoneNumberDialog.dismiss()
        }
    }

    /**
     * The contacts grouped under their initials, as in AukDialer: the initials in the theme colour,
     * the one in view staying at the top, and a rail down the side to jump between them
     */
    private fun setUpLetterRail() {
        val accent = colors.theme().theme
        val onAccent = colors.contentColorOnTheme(accent)
        binding.contacts.addItemDecoration(GroupHeadingDecoration(binding.contacts,
            headingColor = colors.readableOnBackground(this, accent), sticky = true))

        binding.letterRail.accent = accent
        binding.letterRail.onAccent = onAccent
        binding.letterBubble.setBackgroundTint(accent)
        binding.letterBubble.setTextColor(onAccent)

        binding.letterRail.onLetterTouched = { letter ->
            binding.letterBubble.isVisible = letter != null
            if (letter != null) {
                binding.letterBubble.text = letter
                binding.letterRail.selected = letter
                contactsAdapter.initials()[letter]?.let { position ->
                    (binding.contacts.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
                }
            } else {
                updateRailSelection()
            }
        }

        binding.contacts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = updateRailSelection()
        })
    }

    /** Sets the rail on the initial of the first contact in view, or none above the contacts */
    private fun updateRailSelection() {
        // Left to the finger while it is on the rail
        if (binding.letterBubble.isVisible) return
        val first = (binding.contacts.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        binding.letterRail.selected = contactsAdapter.rowGroups.groupLabel(first)
            ?.takeIf { label -> label in binding.letterRail.letters }
    }

    override fun clearQuery() {
        binding.search.text = null
    }

    override fun openKeyboard() {
        binding.search.postDelayed({
            binding.search.showKeyboard()
        }, 200)
    }

    override fun finish(result: HashMap<String, String?>) {
        binding.search.hideKeyboard()
        val intent = Intent().putExtra(CHIPS_KEY, result)
        setResult(Activity.RESULT_OK, intent)
        val animate = if (getIntent().getBooleanExtra(STANDALONE_KEY, false)) {
            // Opened directly (e.g. from Templates): a pick hands straight off to the composer, so
            // skip the flash back to the caller; a cancel slides normally back to it.
            result.isEmpty()
        } else {
            // Opened by the composer: on cancel the composer closes itself right after, so skip this
            // exit animation to avoid a doubled/janky back transition (a real pick reveals it normally).
            result.isNotEmpty()
        }
        if (animate) finish() else finishWithoutAnimation()
    }

}
