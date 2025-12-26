//
//   Calendar Notifications Plus
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

package com.github.quarck.calnotify.database.poc

import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.github.quarck.calnotify.logs.DevLog

/**
 * Custom factory for Room that uses requery's SQLite with cr-sqlite extension.
 * 
 * This is a POC to verify Room can work with the existing cr-sqlite infrastructure
 * before committing to full database migration.
 * 
 * See: docs/dev_todo/database_modernization_plan.md
 */
class CrSqliteRoomFactory : SupportSQLiteOpenHelper.Factory {
    
    companion object {
        private const val LOG_TAG = "CrSqliteRoomFactory"
    }
    
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        DevLog.info(LOG_TAG, "Creating CrSqliteSupportHelper for database: ${configuration.name}")
        return CrSqliteSupportHelper(configuration)
    }
}

