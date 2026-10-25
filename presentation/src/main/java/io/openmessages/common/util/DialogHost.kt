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
package io.openmessages.common.util

import android.app.Dialog

/**
 * Keeps the dialog on screen in step with the one a screen's state asks for, at most one at a time.
 *
 * A screen is destroyed and rebuilt on a rotation while its view model is not. A dialog opened as a
 * one-off command therefore disappears, and Android reports its window as leaked. Described by the
 * state instead, it is shown again on the rebuilt screen and on the same subject, since [Spec]
 * carries what the dialog is about rather than the dialog holding it.
 *
 * [build] turns a spec into a dialog; [onClosed] reports the spec the user closed so the state can
 * drop it. Specs must compare by value: equality is what decides whether a dialog is left alone or
 * rebuilt.
 */
class DialogHost<Spec : Any>(
    private val build: (Spec) -> Dialog,
    private val onClosed: (Spec) -> Unit
) {

    private var shown: Spec? = null
    private var dialog: Dialog? = null

    /** Shows, replaces or closes dialogs so that [spec] is what ends up on screen. */
    fun render(spec: Spec?) {
        if (spec == shown) return
        shown = spec
        close()
        dialog = spec?.let { current ->
            build(current).apply {
                setOnDismissListener { onClosed(current) }
                show()
            }
        }
    }

    /**
     * Closes whatever is showing without reporting it.
     *
     * Call it from the screen's onDestroy: the dialog's window would otherwise outlive the screen,
     * and reporting the close would clear the very state that brings the dialog back afterwards.
     */
    fun close() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }
}
