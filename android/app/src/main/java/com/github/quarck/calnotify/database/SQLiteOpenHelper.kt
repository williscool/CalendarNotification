package com.github.quarck.calnotify.database

import android.content.Context
import io.requery.android.database.sqlite.SQLiteOpenHelper

import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.DatabaseErrorHandler
import io.requery.android.database.sqlite.SQLiteCustomExtension
import io.requery.android.database.sqlite.SQLiteDatabase.OpenFlags
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration


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
  protected val soPathcontext: Context = context

  override fun createConfiguration(
    path: String?,
    @OpenFlags openFlags: Int
  ): SQLiteDatabaseConfiguration {
    val configuration = super.createConfiguration(path, openFlags)


    val soPath = soPathcontext.applicationInfo.dataDir + "/lib/x86_64/crsqlite"

    configuration.customExtensions.add(SQLiteCustomExtension(soPath, "sqlite3_crsqlite_init"))
    return configuration
  }

}
