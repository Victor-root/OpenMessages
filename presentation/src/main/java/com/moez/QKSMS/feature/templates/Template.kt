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

/**
 * A reusable, pre-written message. [title] is an optional label shown only in the list to help the
 * user find a template — it is never part of the sent message. The list of templates is persisted as
 * a JSON array in preferences (see [io.openmessages.util.Preferences.templates]); serialisation is
 * done with org.json in the view model rather than Moshi, whose Kotlin codegen is outdated here.
 */
data class Template(
    val id: Long,
    val title: String,
    val body: String
)
