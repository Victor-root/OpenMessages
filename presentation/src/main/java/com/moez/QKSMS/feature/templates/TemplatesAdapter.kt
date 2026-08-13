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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import io.openmessages.common.base.QkAdapter
import io.openmessages.databinding.TemplateListItemBinding
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class TemplatesAdapter @Inject constructor() : QkAdapter<Template, TemplatesAdapter.ViewHolder>() {

    val clicks: Subject<Template> = PublishSubject.create()
    val longClicks: Subject<Template> = PublishSubject.create()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = TemplateListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val template = getItem(position)
        // The title is just a label for the list; show it only when set.
        holder.binding.title.text = template.title
        holder.binding.title.isVisible = template.title.isNotBlank()
        holder.binding.body.text = template.body
        holder.binding.root.setOnClickListener { clicks.onNext(template) }
        holder.binding.root.setOnLongClickListener {
            longClicks.onNext(template)
            true
        }
    }

    override fun areItemsTheSame(old: Template, new: Template) = old.id == new.id

    inner class ViewHolder(val binding: TemplateListItemBinding) :
        RecyclerView.ViewHolder(binding.root)

}
