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
package io.openmessages.feature.settings.autodelete

import android.app.Activity
import android.content.DialogInterface
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import io.openmessages.R
import io.openmessages.databinding.SettingsAutoDeleteDialogBinding

class AutoDeleteDialog(
    context: Activity,
    private val themeColor: Int,
    listener: (Int) -> Unit
) : AlertDialog(context) {

    private val layout = SettingsAutoDeleteDialogBinding.inflate(LayoutInflater.from(context))

    init {
        setView(layout.root)
        setTitle(R.string.settings_auto_delete)
        setMessage(context.getString(R.string.settings_auto_delete_dialog_message))
        setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.button_cancel)) { _, _ -> }
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(R.string.settings_auto_delete_never)) { _, _ -> listener(0) }
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(R.string.button_save)) { _, _ ->
            listener(layout.field.text.toString().toIntOrNull() ?: 0)
        }
    }

    override fun onStart() {
        super.onStart()
        // Buttons exist once shown — colour them with the user's theme color.
        getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(themeColor)
        getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(themeColor)
        getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(themeColor)
    }

    fun setExpiry(days: Int): AutoDeleteDialog {
        when (days) {
            0 -> layout.field.text = null
            else -> layout.field.setText(days.toString())
        }
        return this
    }

}
