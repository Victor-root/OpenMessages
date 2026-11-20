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
package io.openmessages.feature.conversationmedia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import io.openmessages.R
import io.openmessages.common.base.BaseController
import io.openmessages.databinding.ConversationMediaControllerBinding
import io.openmessages.feature.conversationmedia.injection.ConversationMediaModule
import io.openmessages.injection.appComponent
import io.reactivex.rxjava3.core.Observable
import javax.inject.Inject

/** Every photo, video and file shared in a conversation, as a grid */
class ConversationMediaController(
    val threadId: Long = 0
) : BaseController<ConversationMediaControllerBinding, ConversationMediaView, ConversationMediaState, ConversationMediaPresenter>(), ConversationMediaView {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): ConversationMediaControllerBinding =
        ConversationMediaControllerBinding.inflate(inflater, container, false)

    @Inject override lateinit var presenter: ConversationMediaPresenter
    @Inject lateinit var adapter: ConversationMediaAdapter

    init {
        appComponent
                .conversationMediaBuilder()
                .conversationMediaModule(ConversationMediaModule(this))
                .build()
                .inject(this)
    }

    override fun onViewCreated() {
        adapter.emptyView = binding.empty
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(GridSpacingItemDecoration(activity!!))
        binding.recyclerView.layoutManager = GridLayoutManager(activity, GridSpacingItemDecoration.SPAN_COUNT)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.media_title)
        showBackButton(true)
    }

    override fun render(state: ConversationMediaState) {
        adapter.data = state.media
    }

    override fun mediaClicks(): Observable<Long> = adapter.mediaClicks

}
