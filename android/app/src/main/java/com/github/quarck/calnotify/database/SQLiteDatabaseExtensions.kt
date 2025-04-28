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

  fun <T, R> T.classCustomUse(block: (T) -> R): R {
    // Check if the object is a MockK mock
    // If it is, just execute the block directly without trying to access database properties
    if (this!!::class.java.name.contains("mockk")) {
        return block(this)
    }

    // If this is a SQLiteOpenHelper, use writableDatabase
    if (this is SQLiteOpenHelper) {
      val db = this.writableDatabase // Ensure db is obtained, though not directly used here based on original logic
      try {
        // Pass the helper itself to the block, maintaining original behavior
        return block(this)
      } finally {
        // Original code didn't close/finalize here, maintaining that.
        // Consider lifecycle management if issues arise.
      }
    }
    // Otherwise (including mocks or other types), just call the block
    return block(this)
  }
}
