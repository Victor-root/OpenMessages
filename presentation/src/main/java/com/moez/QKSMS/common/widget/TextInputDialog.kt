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
package io.openmessages.common.widget

import android.app.Activity
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import io.openmessages.databinding.TextInputDialogBinding

class TextInputDialog(
    context: Activity,
    private val themeColor: Int,
    hint: String,
    listener: (String) -> Unit
) : AlertDialog(context) {

    private val layout = TextInputDialogBinding.inflate(LayoutInflater.from(context))

    init {
        layout.field.hint = hint

        // Custom action row (see text_input_dialog.xml) instead of the stock AlertDialog buttons,
        // which stack into an ugly column when the localized labels are too wide to fit in a row.
        setView(layout.root)
        layout.save.setOnClickListener {
            listener(layout.field.text.toString())
            dismiss()
        }
        layout.delete.setOnClickListener {
            listener("")
            dismiss()
        }
        layout.cancel.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        // Colour the action labels with the user's theme color.
        layout.save.setTextColor(themeColor)
        layout.delete.setTextColor(themeColor)
        layout.cancel.setTextColor(themeColor)
    }

    fun setText(text: String): TextInputDialog {
        layout.field.setText(text)
        return this
    }

}
