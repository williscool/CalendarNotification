//
//   Calendar Notifications Plus
//   Copyright (C) 2017 Sergey Parshin (s.parshin.sc@gmail.com)
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

package com.github.quarck.calnotify.calendareditor.storage

import android.content.Context
import io.requery.android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.database.SQLiteOpenHelper
import com.github.quarck.calnotify.calendareditor.CalendarChangeRequest
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.customUse
import com.github.quarck.calnotify.logs.DevLog
import java.io.Closeable

class CalendarChangeRequestsStorage(val context: Context)
    : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION)
        , Closeable
        , CalendarChangeRequestsStorageInterface {

    private var impl: CalendarChangeRequestsStorageImplInterface

    init {
        impl = CalendarChangeRequestsStorageImplV3();
    }

    override fun onCreate(db: SQLiteDatabase)
            = impl.createDb(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        DevLog.info(LOG_TAG, "onUpgrade $oldVersion -> $newVersion")

        if (oldVersion == newVersion)
            return

        if (newVersion != DATABASE_VERSION_V3)
            throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")

        impl.dropAll(db)
        impl.createDb(db)
    }

    override fun add(req: CalendarChangeRequest)
            = synchronized(CalendarChangeRequestsStorage::class.java) { writableDatabase.customUse { impl.addImpl(it, req) } }

    override fun deleteForEventId(eventId: Long)
            = synchronized(CalendarChangeRequestsStorage::class.java) { writableDatabase.customUse { impl.deleteForEventIdImpl(it, eventId) } }

    override fun delete(req: CalendarChangeRequest)
            = synchronized(CalendarChangeRequestsStorage::class.java) { writableDatabase.customUse { impl.deleteImpl(it, req) } }

    override fun deleteMany(requests: List<CalendarChangeRequest>)
            = synchronized(CalendarChangeRequestsStorage::class.java) { writableDatabase.customUse { impl.deleteManyImpl(it, requests) } }

    override fun update(req: CalendarChangeRequest)
            = synchronized(CalendarChangeRequestsStorage::class.java) { writableDatabase.customUse { impl.updateImpl(it, req) } }

    override fun updateMany(requests: List<CalendarChangeRequest>)
            = synchronized(CalendarChangeRequestsStorage::class.java) { writableDatabase.customUse { impl.updateManyImpl(it, requests) } }


    override val requests: List<CalendarChangeRequest>
        get() = synchronized(CalendarChangeRequestsStorage::class.java) { readableDatabase.customUse { impl.getImpl(it) } }

    override fun close() = super.close()

    companion object {
        private val LOG_TAG = "CalendarChangeRequestsStorage"

        private const val DATABASE_VERSION_V3 = 3
        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V3

        private const val DATABASE_NAME = "calEditReqs"
    }
}
