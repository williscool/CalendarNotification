package com.github.quarck.calnotify.database
import io.requery.android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.database.SQLiteOpenHelper

object  SQLiteDatabaseExtensions {
  // had to overwrite this to call crsql finalize before every connection close
  // because you can't extend SQLiteDatabase because its final
  // bascially CloseableKt.use but only for SQLiteDatabase and with crsql finalize
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

  fun <T : SQLiteOpenHelper, R> T.classCustomUse(block: (T) -> R): R {
    val db = this.writableDatabase
    try {
      return block(this)
    } finally {
//      db.customUse {  }
    }
  }
}
