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
package io.openmessages.feature.compose.part

import android.content.Context
import io.openmessages.R
import io.openmessages.common.base.QkViewHolder
import io.openmessages.common.util.Colors
import io.openmessages.common.util.extensions.setVisible
import io.openmessages.common.widget.BubbleImageView
import io.openmessages.databinding.MmsImagePreviewListItemBinding
import io.openmessages.extensions.isImage
import io.openmessages.extensions.isVideo
import io.openmessages.model.Message
import io.openmessages.model.MmsPart
import io.openmessages.util.GlideApp
import io.openmessages.util.tryOrNull
import javax.inject.Inject

class ImageBinder @Inject constructor(colors: Colors, private val context: Context) : PartBinder() {

    override val partLayout = R.layout.mms_image_preview_list_item
    override var theme = colors.theme()

    override fun canBindPart(part: MmsPart) = part.isImage() || part.isVideo()

    override fun bindPart(
        holder: QkViewHolder,
        part: MmsPart,
        message: Message,
        canGroupWithPrevious: Boolean,
        canGroupWithNext: Boolean
    ) {
        val binding = MmsImagePreviewListItemBinding.bind(holder.itemView)
        binding.video.setVisible(part.isVideo())
        holder.itemView.setOnClickListener { clicks.onNext(part.id) }

        binding.thumbnail.bubbleStyle = when {
            !canGroupWithPrevious && canGroupWithNext -> if (message.isMe()) BubbleImageView.Style.OUT_FIRST else BubbleImageView.Style.IN_FIRST
            canGroupWithPrevious && canGroupWithNext -> if (message.isMe()) BubbleImageView.Style.OUT_MIDDLE else BubbleImageView.Style.IN_MIDDLE
            canGroupWithPrevious && !canGroupWithNext -> if (message.isMe()) BubbleImageView.Style.OUT_LAST else BubbleImageView.Style.IN_LAST
            else -> BubbleImageView.Style.ONLY
        }

        tryOrNull(true) {
            GlideApp.with(context).load(part.getUri()).fitCenter().into(binding.thumbnail)
        }
    }

}