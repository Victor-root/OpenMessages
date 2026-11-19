package io.openmessages.feature.conversationmedia

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import io.openmessages.common.base.BaseAdapter
import io.openmessages.common.base.BaseViewHolder
import io.openmessages.databinding.ConversationMediaListItemBinding
import io.openmessages.extensions.isVideo
import io.openmessages.model.MmsPart
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import javax.inject.Inject

class ConversationMediaAdapter @Inject constructor(
    private val context: Context
) : BaseAdapter<MmsPart, BaseViewHolder>() {

    val mediaClicks: Subject<Long> = PublishSubject.create()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val binding = ConversationMediaListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BaseViewHolder(binding.root).apply {
            itemView.setOnClickListener {
                getItemOrNull(bindingAdapterPosition)?.id?.run(mediaClicks::onNext)
            }
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        val part = getItemOrNull(position) ?: return
        val binding = ConversationMediaListItemBinding.bind(holder.itemView)

        Glide.with(context)
                .load(part.getUri())
                .fitCenter()
                .into(binding.thumbnail)

        binding.video.isVisible = part.isVideo()
    }

    override fun areItemsTheSame(old: MmsPart, new: MmsPart) = old.id == new.id

}
