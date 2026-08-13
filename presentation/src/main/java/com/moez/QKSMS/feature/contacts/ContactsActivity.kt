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
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProviders
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.editorActions
import com.jakewharton.rxbinding2.widget.textChanges
import dagger.android.AndroidInjection
import io.openmessages.R
import io.openmessages.common.ViewModelFactory
import io.openmessages.common.base.QkThemedActivity
import io.openmessages.common.util.extensions.hideKeyboard
import io.openmessages.common.util.extensions.showKeyboard
import io.openmessages.common.widget.QkDialog
import io.openmessages.databinding.ContactsActivityBinding
import io.openmessages.extensions.Optional
import io.openmessages.feature.compose.editing.ComposeItem
import io.openmessages.feature.compose.editing.ComposeItemAdapter
import io.openmessages.feature.compose.editing.PhoneNumberAction
import io.openmessages.feature.compose.editing.PhoneNumberPickerAdapter
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class ContactsActivity : QkThemedActivity(), ContactsContract {

    companion object {
        const val SHARING_KEY = "sharing"
        const val CHIPS_KEY = "chips"
        // Set when the picker is opened directly (e.g. from Templates) rather than by the composer, so
        // finish() knows how to animate: a direct pick hands off to the composer next, a direct cancel
        // slides normally back to the caller.
        const val STANDALONE_KEY = "standalone"
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

    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory)[ContactsViewModel::class.java] }

    private val phoneNumberDialog by lazy {
        QkDialog(this).apply {
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

        if (state.selectedContact != null && !phoneNumberDialog.isShowing) {
            phoneNumberAdapter.data = state.selectedContact.numbers
            phoneNumberDialog.subtitle = state.selectedContact.name
            phoneNumberDialog.show()
        } else if (state.selectedContact == null && phoneNumberDialog.isShowing) {
            phoneNumberDialog.dismiss()
        }
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
        finish()
        if (getIntent().getBooleanExtra(STANDALONE_KEY, false)) {
            // Opened directly (e.g. from Templates): a pick hands straight off to the composer, so
            // skip the flash back to the caller; a cancel slides normally back to it.
            if (result.isNotEmpty()) overridePendingTransition(0, 0)
        } else {
            // Opened by the composer: on cancel the composer closes itself right after, so skip this
            // exit animation to avoid a doubled/janky back transition (a real pick reveals it normally).
            if (result.isEmpty()) overridePendingTransition(0, 0)
        }
    }

}
