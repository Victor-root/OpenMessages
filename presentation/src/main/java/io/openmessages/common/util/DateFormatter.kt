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
package io.openmessages.common.util

import android.content.Context
import android.text.format.DateFormat
import io.openmessages.R
import io.openmessages.common.util.extensions.isSameDay
import io.openmessages.common.util.extensions.isSameWeek
import io.openmessages.common.util.extensions.isSameYear
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DateFormatter @Inject constructor(val context: Context) {

    /**
     * Formats the [pattern] correctly for the current locale, and replaces 12 hour format with
     * 24 hour format if necessary
     */
    private fun getFormatter(pattern: String): SimpleDateFormat {
        var formattedPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), pattern)

        if (DateFormat.is24HourFormat(context)) {
            formattedPattern = formattedPattern
                    .replace("h", "HH")
                    .replace("K", "HH")
                    .replace("\\s+a".toRegex(), "")
        }

        return SimpleDateFormat(formattedPattern, Locale.getDefault())
    }

    fun getDetailedTimestamp(date: Long): String {
        return getFormatter("M/d/y, h:mm:ss a").format(date)
    }

    fun getTimestamp(date: Long): String {
        return getFormatter("h:mm a").format(date)
    }

    fun getMessageTimestamp(date: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance()
        then.timeInMillis = date

        return when {
            now.isSameDay(then) -> getFormatter("h:mm a")
            now.isSameWeek(then) -> getFormatter("E h:mm a")
            now.isSameYear(then) -> getFormatter("MMM d, h:mm a")
            else -> getFormatter("MMM d yyyy, h:mm a")
        }.format(date)
    }

    fun getConversationTimestamp(date: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance()
        then.timeInMillis = date

        return when {
            now.isSameDay(then) -> getFormatter("h:mm a")
            now.isSameWeek(then) -> getFormatter("E")
            now.isSameYear(then) -> getFormatter("MMM d")
            else -> getFormatter("MM/d/yy")
        }.format(date)
    }

    /**
     * Names the day each date falls on, for the headings of the conversation list: today, yesterday,
     * the day of the week within the last week, then the date, with the year once it is another one.
     *
     * Meant for a whole list at once. Today, yesterday, the week and the year are worked out when it
     * is made, each day is named once however many dates fall on it, and each pattern is resolved
     * once. Working all that out again for every conversation took the main thread a tenth of a
     * second or more on a phone holding a few hundred of them, every time the list changed.
     */
    fun dayLabeller(): (Long) -> String {
        val calendar = Calendar.getInstance()
        fun startOfDay(date: Long): Long = calendar.run {
            timeInMillis = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }
        fun daysFrom(day: Long, days: Int): Long = calendar.run {
            timeInMillis = day
            add(Calendar.DAY_OF_YEAR, days)
            timeInMillis
        }

        val today = startOfDay(System.currentTimeMillis())
        val yesterday = daysFrom(today, -1)
        // The start of the day six days back, so that the whole of each of those days counts
        val weekAgo = daysFrom(today, -6)
        val thisYear = calendar.run {
            timeInMillis = today
            set(Calendar.DAY_OF_YEAR, 1)
            timeInMillis
        }
        val nextYear = calendar.run {
            add(Calendar.YEAR, 1)
            timeInMillis
        }

        val formatters = HashMap<String, SimpleDateFormat>()
        fun format(pattern: String, date: Long) = formatters.getOrPut(pattern) { getFormatter(pattern) }.format(date)

        val labels = HashMap<Long, String>()
        return { date ->
            val day = startOfDay(date)
            labels.getOrPut(day) {
                when {
                    day == today -> context.getString(R.string.conversations_group_today)
                    day == yesterday -> context.getString(R.string.conversations_group_yesterday)
                    day >= weekAgo -> format("EEEE", date)
                    day in thisYear until nextYear -> format("MMMMd", date)
                    else -> format("MMMMdyyyy", date)
                }.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) }
            }
        }
    }

    fun getScheduledTimestamp(date: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance()
        then.timeInMillis = date

        return when {
            now.isSameDay(then) -> getFormatter("h:mm a")
            now.isSameYear(then) -> getFormatter("MMM d h:mm a")
            else -> getFormatter("MMM d yyyy h:mm a")
        }.format(date)
    }

}