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

  // TODO: I just recognized we don't even use the db val we setup in 
  // val db = this.writableDatabase
  // I forgot why we even need this. I think its becasue the regular use doesn't call crsql_finalize
  // like it should but we should investigate getting rid of this if possible
  fun <T, R> T.classCustomUse(block: (T) -> R): R {
    // If this is a SQLiteOpenHelper, use writableDatabase
    if (this is SQLiteOpenHelper) {
      val db = this.writableDatabase // Ensure db is obtained, though not directly used here based on original logic
      try {
        // Pass the helper itself to the block, maintaining original behavior
        return block(this)
      } finally {
        // db.customUse { }
        // Original code didn't close/finalize here, maintaining that.
        // Consider lifecycle management if issues arise.
      }
    }
    // Otherwise (including mocks or other types), just call the block
    return block(this)
  }
}
