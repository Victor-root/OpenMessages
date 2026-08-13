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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.openmessages.common.util.extensions.dpToPx
import io.openmessages.common.util.extensions.setPadding
import io.openmessages.databinding.QkDialogActionsBinding
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

    /** Optional view shown between the list and the Cancel/OK row, only meaningful alongside
     *  [confirmWithButtons]. */
    var extraView: View? = null

    val confirmClicks: Subject<Int> = PublishSubject.create()

    init {
        appComponent.inject(this)
    }

    fun show(activity: Activity) {
        val recyclerView = RecyclerView(activity)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        recyclerView.setPadding(top = 8.dpToPx(context), bottom = 8.dpToPx(context))

        val builder = AlertDialog.Builder(activity).setTitle(title)

        var lastClicked = adapter.selectedItem

        // A custom action row instead of the stock AlertDialog buttons, which are tinted from the
        // app's static theme and don't follow the user's chosen theme color (see TextInputDialog).
        val actions = if (confirmWithButtons) QkDialogActionsBinding.inflate(LayoutInflater.from(activity)) else null

        // extraView may be a view kept alive across multiple show() calls (e.g. a widget backed by
        // a lazily-created binding in the caller), so detach it from any previous dialog first: a
        // view can only have one parent, and dismissing an AlertDialog doesn't clear that itself.
        extraView?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }

        if (actions != null) {
            builder.setView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(recyclerView)
                extraView?.let(::addView)
                addView(actions.root)
            })
        } else {
            builder.setView(recyclerView)
        }

        val dialog = builder.create()

        actions?.cancel?.setOnClickListener { dialog.dismiss() }
        actions?.ok?.setOnClickListener {
            lastClicked?.let(confirmClicks::onNext)
            dialog.dismiss()
        }

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