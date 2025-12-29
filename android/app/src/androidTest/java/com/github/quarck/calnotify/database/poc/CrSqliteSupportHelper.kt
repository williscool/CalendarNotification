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

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteTransactionListener
import android.os.CancellationSignal
import android.util.Pair
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import com.github.quarck.calnotify.logs.DevLog
import io.requery.android.database.sqlite.SQLiteCustomExtension
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabase.OpenFlags
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import io.requery.android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

/**
 * SupportSQLiteOpenHelper implementation that uses requery's SQLite with cr-sqlite extension.
 * 
 * This bridges Room's SupportSQLite interface with requery's implementation,
 * allowing Room to use cr-sqlite for CRDT functionality.
 */
class CrSqliteSupportHelper(
    private val configuration: SupportSQLiteOpenHelper.Configuration
) : SupportSQLiteOpenHelper {

    companion object {
        private const val LOG_TAG = "CrSqliteSupportHelper"
    }

    /**
     * Exposes the underlying requery SQLiteDatabase for cr-sqlite specific operations.
     * This bypasses Room's connection management and goes directly to the database
     * where the cr-sqlite extension is loaded.
     * 
     * Use this for testing cr-sqlite functions like crsql_db_version(), crsql_site_id(), etc.
     */
    val underlyingDatabase: SQLiteDatabase
        get() = requeryHelper.writableDatabase

    private val requeryHelper: RequeryOpenHelper by lazy {
        RequeryOpenHelper(
            configuration.context,
            configuration.name,
            configuration.callback.version,
            configuration.callback
        )
    }

    override val databaseName: String?
        get() = configuration.name

    override val writableDatabase: SupportSQLiteDatabase
        get() = RequeryDatabaseWrapper(requeryHelper.writableDatabase)

    override val readableDatabase: SupportSQLiteDatabase
        get() = RequeryDatabaseWrapper(requeryHelper.readableDatabase)

    override fun close() {
        // Call crsql_finalize before closing
        try {
            requeryHelper.writableDatabase.rawQuery("SELECT crsql_finalize()", null).use { cursor ->
                cursor.moveToFirst()
            }
            DevLog.info(LOG_TAG, "Called crsql_finalize() before close")
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Error calling crsql_finalize: ${e.message}")
        }
        requeryHelper.close()
    }

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        requeryHelper.setWriteAheadLoggingEnabled(enabled)
    }

    /**
     * Inner requery-based SQLiteOpenHelper that loads cr-sqlite extension.
     */
    private class RequeryOpenHelper(
        private val context: android.content.Context,
        name: String?,
        version: Int,
        private val callback: SupportSQLiteOpenHelper.Callback
    ) : SQLiteOpenHelper(context, name, null, version, null) {

        override fun createConfiguration(
            path: String?,
            @OpenFlags openFlags: Int
        ): SQLiteDatabaseConfiguration {
            DevLog.info(LOG_TAG, "createConfiguration called! path=$path, openFlags=$openFlags")
            val config = super.createConfiguration(path, openFlags)
            // Load cr-sqlite extension
            config.customExtensions.add(SQLiteCustomExtension("crsqlite_requery", "sqlite3_crsqlite_init"))
            DevLog.info(LOG_TAG, "Added cr-sqlite extension to configuration. Extensions count: ${config.customExtensions.size}")
            return config
        }

        override fun onCreate(db: SQLiteDatabase) {
            DevLog.info(LOG_TAG, "onCreate called")
            callback.onCreate(RequeryDatabaseWrapper(db))
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            DevLog.info(LOG_TAG, "onUpgrade called: $oldVersion -> $newVersion")
            callback.onUpgrade(RequeryDatabaseWrapper(db), oldVersion, newVersion)
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            DevLog.info(LOG_TAG, "onDowngrade called: $oldVersion -> $newVersion")
            callback.onDowngrade(RequeryDatabaseWrapper(db), oldVersion, newVersion)
        }

        override fun onOpen(db: SQLiteDatabase) {
            DevLog.info(LOG_TAG, "onOpen called")
            callback.onOpen(RequeryDatabaseWrapper(db))
        }

        override fun onConfigure(db: SQLiteDatabase) {
            DevLog.info(LOG_TAG, "onConfigure called")
            
            // Log native library info for debugging
            try {
                val nativeLibDir = context.applicationInfo.nativeLibraryDir
                val libDir = java.io.File(nativeLibDir)
                val libs = libDir.listFiles()?.map { it.name } ?: emptyList()
                DevLog.info(LOG_TAG, "Native lib dir: $nativeLibDir")
                DevLog.info(LOG_TAG, "Available libs: $libs")
            } catch (e: Exception) {
                DevLog.error(LOG_TAG, "Failed to list native libs: ${e.message}")
            }
            
            // Option B: Let SQLiteCustomExtension handle loading (configured in createConfiguration)
            // Don't call load_extension() explicitly - requery should load it via nativeLoadExtension
            
            // Just verify if cr-sqlite functions are available after requery loads the extension
            try {
                db.rawQuery("SELECT crsql_db_version()", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        DevLog.info(LOG_TAG, "cr-sqlite loaded via SQLiteCustomExtension! db_version = ${cursor.getString(0)}")
                    }
                }
            } catch (e: Exception) {
                DevLog.warn(LOG_TAG, "cr-sqlite not available via SQLiteCustomExtension: ${e.message}")
                
                // Try explicit loading as fallback with more debug info
                try {
                    db.execSQL("PRAGMA enable_load_extension = 1;")
                    DevLog.info(LOG_TAG, "Enabled extension loading, attempting explicit load...")
                    
                    // Get full path to the library
                    val libPath = "${context.applicationInfo.nativeLibraryDir}/crsqlite_requery.so"
                    DevLog.info(LOG_TAG, "Attempting to load from: $libPath")
                    DevLog.info(LOG_TAG, "File exists: ${java.io.File(libPath).exists()}")
                    
                    // Try with full path
                    db.execSQL("SELECT load_extension('$libPath', 'sqlite3_crsqlite_init');")
                    DevLog.info(LOG_TAG, "Extension loaded successfully via full path")
                } catch (e2: Exception) {
                    DevLog.error(LOG_TAG, "Explicit load also failed: ${e2.message}")
                    // Try without entry point to see if we get a different error
                    try {
                        val libPath = "${context.applicationInfo.nativeLibraryDir}/crsqlite_requery.so"
                        db.execSQL("SELECT load_extension('$libPath');")
                    } catch (e3: Exception) {
                        DevLog.error(LOG_TAG, "Load without entry point: ${e3.message}")
                    }
                }
            }
            
            callback.onConfigure(RequeryDatabaseWrapper(db))
        }

        companion object {
            private const val LOG_TAG = "RequeryOpenHelper"
        }
    }

    /**
     * Wraps requery's SQLiteDatabase to implement Room's SupportSQLiteDatabase interface.
     */
    private class RequeryDatabaseWrapper(
        private val delegate: SQLiteDatabase
    ) : SupportSQLiteDatabase {

        override val isOpen: Boolean
            get() = delegate.isOpen

        override val path: String?
            get() = delegate.path

        override val isReadOnly: Boolean
            get() = delegate.isReadOnly

        override val isWriteAheadLoggingEnabled: Boolean
            get() = delegate.isWriteAheadLoggingEnabled

        override val isDbLockedByCurrentThread: Boolean
            get() = delegate.isDbLockedByCurrentThread

        override var version: Int
            get() = delegate.version
            set(value) { delegate.version = value }

        override val maximumSize: Long
            get() = delegate.maximumSize

        override var pageSize: Long
            get() = delegate.pageSize
            set(value) { delegate.pageSize = value }

        override val attachedDbs: List<Pair<String, String>>?
            get() = delegate.attachedDbs

        override val isDatabaseIntegrityOk: Boolean
            get() = delegate.isDatabaseIntegrityOk

        override fun close() {
            delegate.close()
        }

        override fun compileStatement(sql: String): SupportSQLiteStatement {
            return RequeryStatementWrapper(delegate.compileStatement(sql))
        }

        override fun beginTransaction() {
            delegate.beginTransaction()
        }

        override fun beginTransactionNonExclusive() {
            delegate.beginTransactionNonExclusive()
        }

        override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener) {
            delegate.beginTransactionWithListener(transactionListener)
        }

        override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLiteTransactionListener) {
            delegate.beginTransactionWithListenerNonExclusive(transactionListener)
        }

        override fun endTransaction() {
            delegate.endTransaction()
        }

        override fun setTransactionSuccessful() {
            delegate.setTransactionSuccessful()
        }

        override fun inTransaction(): Boolean {
            return delegate.inTransaction()
        }

        override fun yieldIfContendedSafely(): Boolean {
            return delegate.yieldIfContendedSafely()
        }

        override fun yieldIfContendedSafely(sleepAfterYieldDelayMillis: Long): Boolean {
            return delegate.yieldIfContendedSafely(sleepAfterYieldDelayMillis)
        }

        override fun enableWriteAheadLogging(): Boolean {
            return delegate.enableWriteAheadLogging()
        }

        override fun disableWriteAheadLogging() {
            delegate.disableWriteAheadLogging()
        }

        override fun needUpgrade(newVersion: Int): Boolean {
            return delegate.needUpgrade(newVersion)
        }

        override fun setMaxSqlCacheSize(cacheSize: Int) {
            delegate.setMaxSqlCacheSize(cacheSize)
        }

        override fun setForeignKeyConstraintsEnabled(enabled: Boolean) {
            delegate.setForeignKeyConstraintsEnabled(enabled)
        }

        override fun setLocale(locale: Locale) {
            delegate.setLocale(locale)
        }

        override fun setMaximumSize(numBytes: Long): Long {
            return delegate.setMaximumSize(numBytes)
        }

        override fun query(query: String): Cursor {
            return delegate.rawQuery(query, null)
        }

        override fun query(query: String, bindArgs: Array<out Any?>): Cursor {
            return delegate.rawQuery(query, bindArgs.map { it?.toString() }.toTypedArray())
        }

        override fun query(query: SupportSQLiteQuery): Cursor {
            return query(query, null)
        }

        override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
            val bindArgs = mutableListOf<Any?>()
            query.bindTo(object : SupportSQLiteProgram {
                override fun bindNull(index: Int) { bindArgs.add(null) }
                override fun bindLong(index: Int, value: Long) { bindArgs.add(value) }
                override fun bindDouble(index: Int, value: Double) { bindArgs.add(value) }
                override fun bindString(index: Int, value: String) { bindArgs.add(value) }
                override fun bindBlob(index: Int, value: ByteArray) { bindArgs.add(value) }
                override fun clearBindings() { bindArgs.clear() }
                override fun close() {}
            })
            // Convert android.os.CancellationSignal to androidx.core.os.CancellationSignal for requery
            val axCancellationSignal = cancellationSignal?.let {
                androidx.core.os.CancellationSignal().also { axSignal ->
                    it.setOnCancelListener { axSignal.cancel() }
                }
            }
            return if (axCancellationSignal != null) {
                delegate.rawQueryWithFactory(null, query.sql, bindArgs.map { it?.toString() }.toTypedArray(), null, axCancellationSignal)
            } else {
                delegate.rawQuery(query.sql, bindArgs.map { it?.toString() }.toTypedArray())
            }
        }

        override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long {
            return delegate.insertWithOnConflict(table, null, values, conflictAlgorithm)
        }

        override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int {
            val args = whereArgs?.map { it?.toString() }?.toTypedArray()
            return delegate.delete(table, whereClause, args)
        }

        override fun update(
            table: String,
            conflictAlgorithm: Int,
            values: ContentValues,
            whereClause: String?,
            whereArgs: Array<out Any?>?
        ): Int {
            val args = whereArgs?.map { it?.toString() }?.toTypedArray()
            return delegate.updateWithOnConflict(table, values, whereClause, args, conflictAlgorithm)
        }

        override fun execSQL(sql: String) {
            delegate.execSQL(sql)
        }

        override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
            delegate.execSQL(sql, bindArgs)
        }

        /**
         * Helper interface for binding query parameters.
         */
        interface SupportSQLiteProgram : androidx.sqlite.db.SupportSQLiteProgram
    }

    /**
     * Wraps requery's SQLiteStatement to implement Room's SupportSQLiteStatement interface.
     */
    private class RequeryStatementWrapper(
        private val delegate: io.requery.android.database.sqlite.SQLiteStatement
    ) : SupportSQLiteStatement {

        override fun bindNull(index: Int) {
            delegate.bindNull(index)
        }

        override fun bindLong(index: Int, value: Long) {
            delegate.bindLong(index, value)
        }

        override fun bindDouble(index: Int, value: Double) {
            delegate.bindDouble(index, value)
        }

        override fun bindString(index: Int, value: String) {
            delegate.bindString(index, value)
        }

        override fun bindBlob(index: Int, value: ByteArray) {
            delegate.bindBlob(index, value)
        }

        override fun clearBindings() {
            delegate.clearBindings()
        }

        override fun close() {
            delegate.close()
        }

        override fun execute() {
            delegate.execute()
        }

        override fun executeUpdateDelete(): Int {
            return delegate.executeUpdateDelete()
        }

        override fun executeInsert(): Long {
            return delegate.executeInsert()
        }

        override fun simpleQueryForLong(): Long {
            return delegate.simpleQueryForLong()
        }

        override fun simpleQueryForString(): String? {
            return delegate.simpleQueryForString()
        }
    }
}

