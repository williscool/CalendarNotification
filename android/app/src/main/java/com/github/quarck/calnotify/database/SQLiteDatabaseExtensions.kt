package com.github.quarck.calnotify.database
import io.requery.android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.database.SQLiteOpenHelper
import com.github.quarck.calnotify.logs.DevLog


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

  // Singleton flag to detect if we're running in a test environment
  var isTestEnvironment: Boolean? = null
  
  fun isInTestEnvironment(): Boolean {
    if (isTestEnvironment == null) {
      isTestEnvironment = try {
        // Check for Robolectric
        Class.forName("org.robolectric.RuntimeEnvironment")
        true
      } catch (e: ClassNotFoundException) {
        // Check for JUnit test runner
        try {
          Class.forName("org.junit.runner.JUnitCore")
          true
        } catch (e: ClassNotFoundException) {
          false
        }
      }
      DevLog.info("SQLiteDatabaseExtensions", "Detected test environment: $isTestEnvironment")
    }
    return isTestEnvironment ?: false
  }

  // TODO: I just recognized we don't even use the db val we setup in 
  // val db = this.writableDatabase
  // I forgot why we even need this. I think its becasue the regular use doesn't call crsql_finalize
  // like it should but we should investigate getting rid of this if possible
  fun <T, R> T.classCustomUse(block: (T) -> R): R {
    // Add detailed logging to help diagnose type issues
    val className = this!!::class.java.name
    val isMockKMock = className.contains("$") && className.contains("Subclass")
    val isSQLiteOpenHelper = this is SQLiteOpenHelper
    val inTestEnvironment = isInTestEnvironment()
    
    DevLog.info("SQLiteDatabaseExtensions", 
      "classCustomUse called with type: $className, " +
      "isMockKMock: $isMockKMock, " +
      "isSQLiteOpenHelper: $isSQLiteOpenHelper, " +
      "inTestEnvironment: $inTestEnvironment"
    )

    // If this is a SQLiteOpenHelper AND we're not in a test environment
    if (isSQLiteOpenHelper && !inTestEnvironment) {
      val helper = this as SQLiteOpenHelper
      try {
        // Get the database but only in non-test environment
        val db = helper.writableDatabase
        // Pass the helper itself to the block, maintaining original behavior
        return block(this)
      } finally {
        // db.customUse { }
        // Original code didn't close/finalize here, maintaining that.
        // Consider lifecycle management if issues arise.
      }
    }

    // For all other cases (including test environment or mocks), just call the block
    return block(this)
  }
}
