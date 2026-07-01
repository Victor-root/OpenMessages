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
package io.openmessages.manager

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.openmessages.receiver.SendScheduledMessageReceiver
import javax.inject.Inject

class AlarmManagerImpl @Inject constructor(private val context: Context) : AlarmManager {

    override fun getScheduledMessageIntent(id: Long): PendingIntent {
        val intent = Intent(context, SendScheduledMessageReceiver::class.java).putExtra("id", id)
        return PendingIntent.getBroadcast(context, id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun setAlarm(date: Long, intent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

        // On Android 12+ exact alarms require SCHEDULE_EXACT_ALARM; if it isn't granted,
        // setExactAndAllowWhileIdle throws a SecurityException. Fall back to an inexact wake-up so the
        // scheduled message still fires (a little late) instead of crashing the caller (e.g. restore).
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, date, intent)
            } else {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, date, intent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, date, intent)
        }
    }

}