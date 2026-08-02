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
package io.openmessages.common

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.openmessages.common.util.extensions.dpToPx
import io.openmessages.common.util.extensions.setPadding
import io.openmessages.injection.appComponent
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

/**
 * Wrapper around AlertDialog which makes it easier to display lists that use our UI
 */
class QkDialog @Inject constructor(private val context: Context, val adapter: MenuItemAdapter) {

    var title: String? = null

    /**
     * When true, tapping an item previews/highlights it instead of dismissing the dialog, and
     * Cancel/OK buttons are shown instead; [confirmClicks] then fires once, with the last tapped
     * item, when OK is pressed. When false (the default), tapping any item selects it and
     * dismisses the dialog immediately.
     */
    var confirmWithButtons: Boolean = false

    val confirmClicks: Subject<Int> = PublishSubject.create()

    init {
        appComponent.inject(this)
    }

    fun show(activity: Activity) {
        val recyclerView = RecyclerView(activity)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        recyclerView.setPadding(top = 8.dpToPx(context), bottom = 8.dpToPx(context))

        val builder = AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(recyclerView)

        var lastClicked = adapter.selectedItem

        if (confirmWithButtons) {
            builder.setPositiveButton(android.R.string.ok) { _, _ -> lastClicked?.let(confirmClicks::onNext) }
            builder.setNegativeButton(android.R.string.cancel, null)
        }

        val dialog = builder.create()

        val clicks = adapter.menuItemClicks
                .subscribe { id ->
                    lastClicked = id
                    when (confirmWithButtons) {
                        true -> adapter.selectedItem = id
                        false -> dialog.dismiss()
                    }
                }

        dialog.setOnDismissListener {
            clicks.dispose()
        }

        dialog.show()
    }

    fun setTitle(@StringRes title: Int) {
        this.title = context.getString(title)
    }

}