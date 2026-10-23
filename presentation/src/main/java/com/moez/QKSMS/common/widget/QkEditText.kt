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

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import com.google.android.mms.ContentType
import io.openmessages.common.util.TextViewStyler
import io.openmessages.injection.appComponent
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import javax.inject.Inject


/**
 * Custom implementation of EditText to allow for dynamic text colors
 *
 * Beware of updating to extend AppCompatTextView, as this inexplicably breaks the view in
 * the contacts chip view
 */
class QkEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null)
    : AppCompatEditText(context, attrs) {

    companion object {
        private val INPUT_CONTENT_MIME_TYPES = arrayOf(
                ContentType.IMAGE_JPEG,
                ContentType.IMAGE_JPG,
                ContentType.IMAGE_PNG,
                ContentType.IMAGE_GIF)
    }

    @Inject lateinit var textViewStyler: TextViewStyler

    val backspaces: Subject<Unit> = PublishSubject.create()
    /** The images taken in from the keyboard or the clipboard */
    val inputContentSelected: Subject<Uri> = PublishSubject.create()

    /** Whether images are taken in, as attachments to the message */
    var supportsInputContent: Boolean = false
        set(value) {
            field = value
            if (value) {
                ViewCompat.setOnReceiveContentListener(this, INPUT_CONTENT_MIME_TYPES,
                        OnReceiveContentListener { _, payload -> receiveContent(payload) })
            } else {
                ViewCompat.setOnReceiveContentListener(this, null, null)
            }
        }

    init {
        if (!isInEditMode) {
            appComponent.inject(this)
            textViewStyler.applyAttributes(this, attrs)
        } else {
            TextViewStyler.applyEditModeAttributes(this, attrs)
        }
    }

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection {
        return object : InputConnectionWrapper(super.onCreateInputConnection(editorInfo), true) {
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL) {
                    backspaces.onNext(Unit)
                }
                return super.sendKeyEvent(event)
            }


            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength == 1 && afterLength == 0) {
                    backspaces.onNext(Unit)
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
    }

    /**
     * Takes the images out of [payload], leaving the rest, like text, for the field to insert
     */
    private fun receiveContent(payload: ContentInfoCompat): ContentInfoCompat? {
        if (INPUT_CONTENT_MIME_TYPES.none(payload.clip.description::hasMimeType)) return payload

        val split = payload.partition { item -> item.uri != null }
        split.first?.clip?.let { clip ->
            (0 until clip.itemCount).mapNotNull { i -> clip.getItemAt(i).uri }.forEach(inputContentSelected::onNext)
        }
        return split.second
    }

}
