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

import io.openmessages.common.base.QkView
import io.reactivex.Observable

interface TemplatesView : QkView<TemplatesState> {

    /** A template was tapped — use it (open the composer pre-filled with its body). */
    val templateClicksIntent: Observable<Template>

    /** A template was long-pressed — open the editor for it. */
    val templateLongClicksIntent: Observable<Template>

    /** The "add" button was tapped. */
    val addTemplateIntent: Observable<*>

    /** The editor was saved: (template id or null for a new one, title, body). */
    val saveTemplateIntent: Observable<Triple<Long?, String, String>>

    /** The editor's delete button was tapped, carrying the template id. */
    val deleteTemplateIntent: Observable<Long>

    val backPressedIntent: Observable<Unit>

    fun showEditor(template: Template?)
    fun returnTemplate(body: String)

    /** Open the recipient picker for a tapped template; the composer opens once a recipient is chosen. */
    fun pickRecipientForTemplate(body: String)

    fun finishActivity()

}
