package com.github.quarck.calnotify.database

import io.requery.android.database.sqlite.SQLiteDatabase

object SQLiteDatabaseExtensions {
    /**
     * Extension for SQLiteDatabase that calls crsql_finalize() before closing.
     * 
     * Required for cr-sqlite: must finalize before every connection close.
     * Similar to CloseableKt.use but with cr-sqlite finalization.
     */
    inline fun <R> SQLiteDatabase.customUse(block: SQLiteDatabase.(SQLiteDatabase) -> R): R {
        var exception: Throwable? = null
        try {
            return block(this)
        } catch (e: Throwable) {
            exception = e
            throw e
        } finally {
            try {
                // Execute finalization query before closing the database.
                rawQuery("SELECT crsql_finalize()", null).use { cursor ->
                    cursor.moveToFirst() // Ensure the query is executed.
                }
            } catch (finalizeEx: Throwable) {
                exception?.addSuppressed(finalizeEx) ?: throw finalizeEx
            } finally {
                close()
            }
        }
    }
}
