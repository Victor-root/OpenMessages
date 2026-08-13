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
package io.openmessages.model

/**
 * A category of data that can be independently included in or excluded from a backup. Each category
 * is written to its own timestamp-prefixed file directly in the chosen backup folder, so the user can
 * pick exactly what to back up and what to restore. A per-backup manifest records which file holds
 * which category.
 *
 * New categories are added here as they gain full round-trip (backup + restore) support; the backup
 * and restore UIs enumerate this list, so they stay in sync automatically.
 */
enum class BackupCategory {
    MESSAGES,
    SETTINGS,
    BLOCKING,
    CONVERSATIONS,
    SCHEDULED
}
