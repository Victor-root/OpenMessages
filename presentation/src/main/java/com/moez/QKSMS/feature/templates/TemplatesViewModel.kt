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

import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.openmessages.common.Navigator
import io.openmessages.common.base.QkViewModel
import io.openmessages.util.Preferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Named

class TemplatesViewModel @Inject constructor(
    @Named("pick") private val pick: Boolean,
    private val navigator: Navigator,
    private val prefs: Preferences
) : QkViewModel<TemplatesView, TemplatesState>(TemplatesState()) {

    init {
        // Keep the list in sync with the persisted preference so add/edit/delete reflect immediately.
        disposables += prefs.templates.asObservable()
            .map(::parse)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { templates -> newState { copy(templates = templates) } }
    }

    override fun bindView(view: TemplatesView) {
        super.bindView(view)

        // Add a new template
        view.addTemplateIntent
            .autoDisposable(view.scope())
            .subscribe { view.showEditor(null) }

        // Long-press to edit an existing template
        view.templateLongClicksIntent
            .autoDisposable(view.scope())
            .subscribe { template -> view.showEditor(template) }

        // Tap to use a template. From the picker (compose's "+") return it to the conversation;
        // otherwise open the composer pre-filled and let the user pick the recipient.
        view.templateClicksIntent
            .autoDisposable(view.scope())
            .subscribe { template ->
                if (pick) view.returnTemplate(template.body)
                else navigator.showCompose(body = template.body)
            }

        // Save from the editor (new or edited)
        view.saveTemplateIntent
            .autoDisposable(view.scope())
            .subscribe { (id, title, body) -> saveTemplate(id, title, body) }

        // Delete from the editor
        view.deleteTemplateIntent
            .autoDisposable(view.scope())
            .subscribe { id -> deleteTemplate(id) }

        view.backPressedIntent
            .autoDisposable(view.scope())
            .subscribe { view.finishActivity() }
    }

    private fun parse(json: String): List<Template> = Templates.parse(json)

    private fun persist(templates: List<Template>) = prefs.templates.set(Templates.serialize(templates))

    private fun saveTemplate(id: Long?, title: String, body: String) {
        val trimmedBody = body.trim()
        if (trimmedBody.isEmpty()) return
        val trimmedTitle = title.trim()
        val current = parse(prefs.templates.get())
        val updated = when (id) {
            null -> current + Template(System.currentTimeMillis(), trimmedTitle, trimmedBody)
            else -> current.map {
                if (it.id == id) it.copy(title = trimmedTitle, body = trimmedBody) else it
            }
        }
        persist(updated)
    }

    private fun deleteTemplate(id: Long) {
        persist(parse(prefs.templates.get()).filterNot { it.id == id })
    }

}
