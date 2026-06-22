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
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.jakewharton.rxbinding2.view.clicks
import dagger.android.AndroidInjection
import io.openmessages.R
import io.openmessages.common.base.QkThemedActivity
import io.openmessages.common.util.extensions.setBackgroundTint
import io.openmessages.common.util.extensions.setTint
import io.openmessages.databinding.TemplateEditorDialogBinding
import io.openmessages.databinding.TemplatesActivityBinding
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class TemplatesActivity : QkThemedActivity(), TemplatesView {

    @Inject lateinit var templatesAdapter: TemplatesAdapter
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: TemplatesActivityBinding

    override val templateClicksIntent by lazy { templatesAdapter.clicks }
    override val templateLongClicksIntent by lazy { templatesAdapter.longClicks }
    override val addTemplateIntent by lazy { binding.add.clicks() }
    override val saveTemplateIntent: Subject<Triple<Long?, String, String>> = PublishSubject.create()
    override val deleteTemplateIntent: Subject<Long> = PublishSubject.create()
    override val backPressedIntent: Subject<Unit> = PublishSubject.create()

    private val viewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory)[TemplatesViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = TemplatesActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(R.string.templates_title)
        showBackButton(true)
        viewModel.bindView(this)

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
        editor.titleField.setText(template?.title.orEmpty())
        editor.field.setText(template?.body.orEmpty())
        editor.field.setSelection(editor.field.length())
        editor.delete.isVisible = template != null

        val dialog = AlertDialog.Builder(this)
            .setView(editor.root)
            .create()

        val themeColor = colors.theme().theme
        editor.save.setTextColor(themeColor)
        editor.delete.setTextColor(themeColor)
        editor.cancel.setTextColor(themeColor)

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

    override fun onBackPressed() = backPressedIntent.onNext(Unit)

    override fun finishActivity() {
        finish()
    }

    override fun returnTemplate(body: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_BODY, body))
        finish()
    }

    companion object {
        /** When true, tapping a template returns it (via setResult) instead of opening the composer. */
        const val EXTRA_PICK = "pick"
        const val EXTRA_BODY = "body"
    }

}
