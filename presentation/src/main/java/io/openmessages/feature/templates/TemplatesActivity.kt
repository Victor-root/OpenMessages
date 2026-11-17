/*
 * Copyright (C) 2026 OpenMessages contributors
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
package io.openmessages.feature.templates

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.rxbinding4.view.clicks
import dagger.android.AndroidInjection
import io.openmessages.R
import io.openmessages.common.Navigator
import io.openmessages.common.ViewModelFactory
import io.openmessages.common.base.ThemedActivity
import io.openmessages.common.util.extensions.dpToPx
import io.openmessages.common.util.extensions.resolveThemeColor
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.common.util.extensions.setTint
import io.openmessages.databinding.TemplateEditorDialogBinding
import io.openmessages.feature.contacts.ContactsActivity
import io.openmessages.databinding.TemplatesActivityBinding
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import javax.inject.Inject

class TemplatesActivity : ThemedActivity(), TemplatesView {

    @Inject lateinit var templatesAdapter: TemplatesAdapter
    @Inject lateinit var viewModelFactory: ViewModelFactory
    @Inject lateinit var navigator: Navigator

    private lateinit var binding: TemplatesActivityBinding

    /** Body of the template awaiting a recipient, held between the picker launch and its result. */
    private var pendingTemplateBody: String? = null

    private val recipientPicker = registerForActivityResult(StartActivityForResult()) { result ->
        val body = pendingTemplateBody
        pendingTemplateBody = null
        // The picker always returns RESULT_OK; a cancel just carries an empty chip set, in which
        // case we stay on Templates. A real pick hands off to the composer with the recipient(s).
        val addresses = ContactsActivity.getChips(result.data).keys.toList()
        if (result.resultCode == Activity.RESULT_OK && addresses.isNotEmpty()) {
            navigator.showCompose(body = body, addresses = addresses)
        }
    }

    override val templateClicksIntent by lazy { templatesAdapter.clicks }
    override val templateLongClicksIntent by lazy { templatesAdapter.longClicks }
    override val addTemplateIntent by lazy { binding.add.clicks() }
    override val saveTemplateIntent: Subject<Triple<Long?, String, String>> = PublishSubject.create()
    override val deleteTemplateIntent: Subject<Long> = PublishSubject.create()
    override val backPressedIntent: Subject<Unit> = PublishSubject.create()

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[TemplatesViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = TemplatesActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(R.string.templates_title)
        showBackButton(true)
        viewModel.bindView(this)
        onBackPressedDispatcher.addCallback(this) { backPressedIntent.onNext(Unit) }

        templatesAdapter.emptyView = binding.empty
        binding.templates.adapter = templatesAdapter

        colors.theme().let { theme ->
            binding.add.setBackgroundTint(theme.theme)
            binding.add.setTint(theme.textPrimary)
        }
    }

    override fun render(state: TemplatesState) {
        templatesAdapter.data = state.templates
    }

    override fun showEditor(template: Template?) {
        val editor = TemplateEditorDialogBinding.inflate(layoutInflater)
        editor.heading.setText(if (template == null) R.string.template_new else R.string.template_edit)
        editor.titleField.setText(template?.title.orEmpty())
        editor.field.setText(template?.body.orEmpty())
        editor.field.setSelection(editor.field.length())
        editor.delete.isVisible = template != null

        val dialog = MaterialAlertDialogBuilder(ContextThemeWrapper(this, R.style.Theme_OpenMessages_Material3Context))
            .setView(editor.root)
            .create()

        val themeColor = colors.theme().theme
        val readable = colors.readableOnBackground(this, themeColor)
        listOf(editor.titleField, editor.field).forEach { field -> field.background = outlinedField(readable) }
        editor.save.setBackgroundTint(themeColor)
        editor.save.setTextColor(colors.contentColorOnTheme(themeColor))
        editor.delete.setTextColor(readable)
        editor.cancel.setTextColor(readable)

        editor.save.setOnClickListener {
            saveTemplateIntent.onNext(
                Triple(template?.id, editor.titleField.text.toString(), editor.field.text.toString())
            )
            dialog.dismiss()
        }
        editor.delete.setOnClickListener {
            template?.let { deleteTemplateIntent.onNext(it.id) }
            dialog.dismiss()
        }
        editor.cancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    override fun finishActivity() {
        finish()
    }

    override fun returnTemplate(body: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_BODY, body))
        finish()
    }

    override fun pickRecipientForTemplate(body: String) {
        pendingTemplateBody = body
        val intent = Intent(this, ContactsActivity::class.java)
            .putExtra(ContactsActivity.SHARING_KEY, false)
            .putExtra(ContactsActivity.CHIPS_KEY, hashMapOf<String, String?>())
            .putExtra(ContactsActivity.STANDALONE_KEY, true)
        recipientPicker.launch(intent)
    }

    companion object {
        /** When true, tapping a template returns it (via setResult) instead of opening the composer. */
        const val EXTRA_PICK = "pick"
        const val EXTRA_BODY = "body"
    }


    /** A Material 3 outlined field: a thin outline, in [accent] and thicker while it has the focus */
    private fun outlinedField(accent: Int) = StateListDrawable().apply {
        fun outline(width: Int, color: Int) = GradientDrawable().apply {
            cornerRadius = 12.dpToPx(this@TemplatesActivity).toFloat()
            setStroke(width.dpToPx(this@TemplatesActivity), color)
        }
        addState(intArrayOf(android.R.attr.state_focused), outline(2, accent))
        addState(intArrayOf(), outline(1, resolveThemeColor(android.R.attr.textColorTertiary)))
    }

}
