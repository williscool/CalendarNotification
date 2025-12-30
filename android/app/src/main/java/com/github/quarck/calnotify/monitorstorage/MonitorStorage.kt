//
//   Calendar Notifications Plus
//   Copyright (C) 2017 Sergey Parshin (s.parshin.sc@gmail.com)
//   Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
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

package com.github.quarck.calnotify.monitorstorage

import android.content.Context
import java.io.Closeable

/**
 * MonitorStorage - tracks calendar alerts being monitored.
 * 
 * Now backed by Room database with cr-sqlite support.
 * Maintains API compatibility with existing callers via delegation.
 * 
 * Note: This class previously extended SQLiteOpenHelper with:
 * - DATABASE_NAME = "CalendarMonitor" → now in MonitorDatabase.DATABASE_NAME
 * - DATABASE_VERSION = 1 → now in @Database(version = 1) on MonitorDatabase
 * Room handles version tracking automatically via room_master_table.
 */
class MonitorStorage private constructor(
    private val delegate: RoomMonitorStorage
) : MonitorStorageInterface by delegate, Closeable by delegate {
    
    constructor(context: Context) : this(RoomMonitorStorage(context))

    companion object {
        @Suppress("unused")
        private const val LOG_TAG = "MonitorStorage"
    }
}
