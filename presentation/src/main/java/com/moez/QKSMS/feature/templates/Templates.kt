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

import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared (de)serialisation for the saved templates preference, so every reader/writer agrees on the
 * JSON shape. Used by [TemplatesViewModel] and by the composer's "insert template" action.
 */
object Templates {

    fun parse(json: String): List<Template> =
        try {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                Template(obj.getLong("id"), obj.optString("title", ""), obj.getString("body"))
            }
        } catch (e: Exception) {
            emptyList()
        }

    fun serialize(templates: List<Template>): String {
        val array = JSONArray()
        templates.forEach { template ->
            array.put(
                JSONObject()
                    .put("id", template.id)
                    .put("title", template.title)
                    .put("body", template.body)
            )
        }
        return array.toString()
    }

}
