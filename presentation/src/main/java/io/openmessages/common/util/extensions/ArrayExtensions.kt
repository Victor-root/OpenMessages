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
package io.openmessages.common.util.extensions

/**
 * The label for a setting whose stored value is a position in this array of choices, falling back to
 * the label for [default] when the value names a position that does not exist.
 *
 * Preference values reach the settings screens unchecked: restoring a backup copies every number it
 * holds as-is, so a value written by another version of the app can point outside the list. Indexing
 * it directly throws while the screen is being built, which closes the app on every attempt to open
 * that screen, and Settings has no way back since the backup screen sits behind it. Treating an
 * unrecognised value as unset is what the preference itself does with a missing key.
 */
fun Array<String>.labelFor(value: Int, default: Int): String =
        getOrNull(value) ?: getOrNull(default) ?: first()

/**
 * As above, for the settings whose positions in the list carry no meaning of their own and whose
 * stored values are therefore declared separately in [ids].
 */
fun Array<String>.labelFor(value: Int, default: Int, ids: IntArray): String =
        labelFor(ids.indexOf(value), ids.indexOf(default))
