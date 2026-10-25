/*
 * Copyright (C) 2019 Moez Bhatti <moez.bhatti@gmail.com>
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
package io.openmessages.feature.backup

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import io.openmessages.R
import io.openmessages.common.base.QkAdapter
import io.openmessages.common.base.QkBindingViewHolder
import io.openmessages.databinding.BackupListItemBinding
import io.openmessages.model.BackupItem
import java.util.Date

/**
 * Lists the existing backups in the manager dialog. Each row shows a folder/zip icon, a readable
 * date (or the custom name once the backup has been renamed) and its type, with inline rename and
 * delete actions handled by the host controller.
 */
class BackupsAdapter(
    private val onRename: (BackupItem) -> Unit,
    private val onDelete: (BackupItem) -> Unit
) : QkAdapter<BackupItem, QkBindingViewHolder<BackupListItemBinding>>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<BackupListItemBinding> {
        val binding = BackupListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QkBindingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<BackupListItemBinding>, position: Int) {
        val item = getItem(position)
        val context = holder.binding.root.context

        holder.binding.icon.setImageResource(
                if (item.isZip) R.drawable.ic_archive_white_24dp else R.drawable.ic_baseline_folder_24)

        // A default-named backup carries a real date; a renamed one (date == 0) shows the name the user gave it
        holder.binding.title.text = when {
            item.date > 0 -> "${DateFormat.getMediumDateFormat(context).format(Date(item.date))}, " +
                    DateFormat.getTimeFormat(context).format(Date(item.date))
            else -> item.name
        }

        holder.binding.subtitle.setText(
                if (item.isZip) R.string.backup_manage_type_zip else R.string.backup_manage_type_folder)

        holder.binding.rename.setOnClickListener { onRename(item) }
        holder.binding.delete.setOnClickListener { onDelete(item) }
    }

    override fun areItemsTheSame(old: BackupItem, new: BackupItem): Boolean =
            old.isZip == new.isZip && old.name == new.name

}
