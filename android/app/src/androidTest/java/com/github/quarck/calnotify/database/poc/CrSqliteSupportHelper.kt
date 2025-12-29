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

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.github.quarck.calnotify.logs.DevLog
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCustomExtension
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration

/**
 * SupportSQLiteOpenHelper.Factory that uses requery with cr-sqlite extension.
 * 
 * Uses RequerySQLiteOpenHelperFactory's ConfigurationOptions to add cr-sqlite,
 * and wraps the helper to ensure crsql_finalize() is called on close.
 * 
 * See: https://github.com/requery/sqlite-android#support-library-compatibility
 * See: docs/testing/crsqlite_room_testing.md
 */
class CrSqliteRoomFactory : SupportSQLiteOpenHelper.Factory {
    
    companion object {
        private const val LOG_TAG = "CrSqliteRoomFactory"
    }

    // ConfigurationOptions that adds cr-sqlite extension
    private val crSqliteOptions = object : RequerySQLiteOpenHelperFactory.ConfigurationOptions {
        override fun apply(config: SQLiteDatabaseConfiguration): SQLiteDatabaseConfiguration {
            config.customExtensions.add(
                SQLiteCustomExtension("crsqlite_requery", "sqlite3_crsqlite_init")
            )
            DevLog.info(LOG_TAG, "Added cr-sqlite extension to configuration")
            return config
        }
    }

    // Requery's factory with our cr-sqlite configuration
    private val requeryFactory = RequerySQLiteOpenHelperFactory(listOf(crSqliteOptions))

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val requeryHelper = requeryFactory.create(configuration)
        return CrSqliteFinalizeWrapper(requeryHelper)
    }
}

/**
 * Wrapper that ensures crsql_finalize() is called before close.
 * 
 * This is required by cr-sqlite to properly flush CRDT metadata.
 * All other methods delegate directly to the underlying helper.
 */
class CrSqliteFinalizeWrapper(
    private val delegate: SupportSQLiteOpenHelper
) : SupportSQLiteOpenHelper {
    
    companion object {
        private const val LOG_TAG = "CrSqliteFinalizeWrapper"
    }

    override val databaseName: String? 
        get() = delegate.databaseName

    override val writableDatabase: SupportSQLiteDatabase 
        get() = delegate.writableDatabase

    override val readableDatabase: SupportSQLiteDatabase 
        get() = delegate.readableDatabase

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) = 
        delegate.setWriteAheadLoggingEnabled(enabled)

    override fun close() {
        // REQUIRED: Call crsql_finalize() before closing
        try {
            writableDatabase.query("SELECT crsql_finalize()").use { it.moveToFirst() }
            DevLog.info(LOG_TAG, "Called crsql_finalize() before close")
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Error calling crsql_finalize: ${e.message}")
        }
        delegate.close()
    }

    /**
     * Access underlying requery database for cr-sqlite operations.
     * Use for: crsql_db_version(), crsql_site_id(), crsql_changes(), etc.
     */
    val underlyingDatabase: SQLiteDatabase
        get() = writableDatabase as SQLiteDatabase
}
