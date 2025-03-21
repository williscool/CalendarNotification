package com.github.quarck.calnotify.database

import android.content.Context
import com.github.quarck.calnotify.logs.DevLog
import io.requery.android.database.sqlite.SQLiteOpenHelper

import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.DatabaseErrorHandler
import io.requery.android.database.sqlite.SQLiteCustomExtension
import io.requery.android.database.sqlite.SQLiteDatabase.OpenFlags
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import com.github.quarck.calnotify.database.SQLiteDatabaseExtensions.customUse
import io.requery.android.database.sqlite.SQLiteClosable
import java.io.Closeable

abstract class SQLiteOpenHelper @JvmOverloads constructor(
  context: Context, name: String?,
  factory: SQLiteDatabase.CursorFactory?,
  version: Int,
  errorHandler: DatabaseErrorHandler? = null
) :
  SQLiteOpenHelper(  context , name ,
  factory,
version,
errorHandler) {

  // Store context as a property so it can be accessed in other methods
  //  protected val soPathcontext: Context = context

//  private var closable: CrSQLClosable? = null
//
//  override val writableDatabase: SQLiteDatabase
//    get() = super.writableDatabase.apply {
//      // Create and track closable instance
//      closable = CrSQLClosable(this).apply {
//        acquireReference()  // Start reference tracking
//      }
//    }



  override fun createConfiguration(
    path: String?,
    @OpenFlags openFlags: Int
  ): SQLiteDatabaseConfiguration {
    val configuration = super.createConfiguration(path, openFlags)

    configuration.customExtensions.add(SQLiteCustomExtension("crsqlite", "sqlite3_crsqlite_init"))
    return configuration
  }

  fun dbQueryAndDebugLog(db: SQLiteDatabase, query: String) {
    val queryResponse = db.query(query)
    val results = mutableListOf<String>()

    if (queryResponse.moveToFirst()) {
        do {
            for (i in 0 until queryResponse.columnCount) {
                results.add(queryResponse.getString(i))
            }
        } while (queryResponse.moveToNext())
    }

    DevLog.info(LOG_TAG, "Sqlite $query response: $results")
    
  }

  inline fun <R> customUse(block: (SQLiteDatabase) -> R): R {
    return writableDatabase.customUse { block(it) }
  }

  override fun close() {
//    closable?.releaseReference()  // Triggers custom close logic
    // now just calling custom use will do the finalize
    this.writableDatabase.customUse {}

    super.close()
  }

  companion object {
    private const val LOG_TAG = "Overridden SQLiteOpenHelper"
  }
}


