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
package io.openmessages.util

import android.content.SharedPreferences
import androidx.core.content.edit
import io.reactivex.rxjava3.core.Observable

/**
 * [SharedPreferences] read and written through [Preference]s, each of which can be watched for
 * the values it takes
 */
class RxSharedPreferences(private val preferences: SharedPreferences) {

    /**
     * The key of each preference as it is written
     */
    val keyChanges: Observable<String> = Observable.create<String> { emitter ->
        // Held by the subscription, since the preferences only keep a weak reference to it
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null) emitter.onNext(key)
        }
        emitter.setCancellable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }.share()

    fun getBoolean(key: String, defaultValue: Boolean = false): Preference<Boolean> =
        preference(key, defaultValue, SharedPreferences::getBoolean, SharedPreferences.Editor::putBoolean)

    fun getFloat(key: String, defaultValue: Float = 0f): Preference<Float> =
        preference(key, defaultValue, SharedPreferences::getFloat, SharedPreferences.Editor::putFloat)

    fun getInteger(key: String, defaultValue: Int = 0): Preference<Int> =
        preference(key, defaultValue, SharedPreferences::getInt, SharedPreferences.Editor::putInt)

    fun getString(key: String, defaultValue: String = ""): Preference<String> =
        preference(key, defaultValue, { name, default -> getString(name, default) ?: default }, SharedPreferences.Editor::putString)

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Preference<Set<String>> =
        preference(key, defaultValue, { name, default -> getStringSet(name, default) ?: default }, SharedPreferences.Editor::putStringSet)

    /**
     * A preference stored as the text [converter] turns its value into
     */
    fun <T : Any> getObject(key: String, defaultValue: T, converter: Preference.Converter<T>): Preference<T> =
        preference(
            key,
            defaultValue,
            { name, default -> getString(name, null)?.let(converter::deserialize) ?: default },
            { name, value -> putString(name, converter.serialize(value)) }
        )

    private fun <T : Any> preference(
        key: String,
        defaultValue: T,
        read: SharedPreferences.(String, T) -> T,
        write: SharedPreferences.Editor.(String, T) -> SharedPreferences.Editor
    ) = Preference(preferences, key, defaultValue, read, write, keyChanges)

}

/**
 * One value of the [SharedPreferences], which reads as [defaultValue] for as long as it is not set
 */
class Preference<T : Any> internal constructor(
    private val preferences: SharedPreferences,
    val key: String,
    val defaultValue: T,
    private val read: SharedPreferences.(String, T) -> T,
    private val write: SharedPreferences.Editor.(String, T) -> SharedPreferences.Editor,
    keyChanges: Observable<String>
) {

    /**
     * Turns a value into the text it is stored as, and back
     */
    interface Converter<T : Any> {
        fun deserialize(serialized: String): T
        fun serialize(value: T): String
    }

    private val values: Observable<T> = keyChanges
        .filter { changedKey -> changedKey == key }
        .startWithItem(key)
        .map { get() }

    val isSet: Boolean
        get() = preferences.contains(key)

    fun get(): T = if (isSet) preferences.read(key, defaultValue) else defaultValue

    fun set(value: T) = preferences.edit { write(key, value) }

    fun delete() = preferences.edit { remove(key) }

    /**
     * The value, followed by each value it is set to
     */
    fun asObservable(): Observable<T> = values

}
