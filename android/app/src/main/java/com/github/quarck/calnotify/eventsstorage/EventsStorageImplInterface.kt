//
//   Calendar Notifications Plus
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.eventsstorage

import android.content.Context
import io.requery.android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.calendar.EventAlertRecord

interface EventsStorageImplInterface {
    fun createDb(db: SQLiteDatabase)

    fun addEventImpl(db: SQLiteDatabase, event: EventAlertRecord): Boolean

    fun addEventsImpl(db: SQLiteDatabase, events: List<EventAlertRecord>): Boolean

    fun updateEventImpl(db: SQLiteDatabase, event: EventAlertRecord): Boolean

    fun updateEventsImpl(db: SQLiteDatabase, events: List<EventAlertRecord>): Boolean

    fun updateEventAndInstanceTimesImpl(db: SQLiteDatabase, event: EventAlertRecord, instanceStart: Long, instanceEnd: Long): Boolean

    fun updateEventsAndInstanceTimesImpl(db: SQLiteDatabase, events: Collection<EventWithNewInstanceTime>): Boolean

    fun getEventImpl(db: SQLiteDatabase, eventId: Long, instanceStartTime: Long): EventAlertRecord?

    fun getEventsImpl(db: SQLiteDatabase): List<EventAlertRecord>

    fun getEventInstancesImpl(db: SQLiteDatabase, eventId: Long): List<EventAlertRecord>

    fun deleteEventImpl(db: SQLiteDatabase, eventId: Long, instanceStartTime: Long): Boolean

    fun deleteEventsImpl(db: SQLiteDatabase, events: Collection<EventAlertRecord>): Int

    fun deleteAllEventsImpl(db: SQLiteDatabase): Boolean

    fun dropAll(db: SQLiteDatabase): Boolean


}
