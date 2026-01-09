package expo.modules.mymodule

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import kotlinx.serialization.json.Json


class MyModule : Module() {
  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.

  companion object {
    private const val TAG = "MyModule"
    
    // SharedPreferences for cross-module communication (not backed up - see backup_rules.xml)
    // CONTRACT: These constants must match EventsStorageState.kt in the main app
    // See EventsStorageState for documentation on the contract
    const val STORAGE_PREFS_NAME = "events_storage_state"
    const val PREF_ACTIVE_DB_NAME = "active_db_name"
    const val PREF_IS_USING_ROOM = "is_using_room"
    
    // Database name constants (must match EventsDatabase.kt)
    const val ROOM_DATABASE_NAME = "RoomEvents"
    const val LEGACY_DATABASE_NAME = "Events"
  }

  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('MyModule')` in JavaScript.
    Name(TAG)

    // Sets constant properties on the module. Can take a dictionary or a closure that returns a dictionary.
    Constants(
      "PI" to 100
    )

    // Defines event names that the module can send to JavaScript.
    Events("onChange")

    // Defines a JavaScript synchronous function that runs the native code on the JavaScript thread.
    Function("hello") {
      "Hello world! 👋"
    }

    // Defines a JavaScript function that always returns a Promise and whose native code
    // is by default dispatched on the different thread than the JavaScript runtime runs on.
    AsyncFunction("setValueAsync") { value: String ->
      // Send an event to JavaScript.
      sendEvent("onChange", mapOf(
        "value" to value
      ))
    }

    AsyncFunction("sendRescheduleConfirmations") { value: String ->
      val rescheduleConfirmations = Json.decodeFromString<List<JsRescheduleConfirmationObject>>(value)

      Log.i(TAG, rescheduleConfirmations.take(3).toString())
      
      // Send intent with reschedule confirmations data
      val intent = Intent("com.github.quarck.calnotify.RESCHEDULE_CONFIRMATIONS_RECEIVED")
      intent.putExtra("reschedule_confirmations", value)
      appContext.reactContext?.sendBroadcast(intent)

      sendEvent("onChange", mapOf(
        "value" to value
      ))
    }

    // Returns the active events database name for sync feature to use
    // Room is the primary implementation; legacy is only used if Room migration fails
    // Reads from SharedPreferences written by EventsStorage in main app
    Function("getActiveEventsDbName") {
      val context = appContext.reactContext
      if (context == null) {
        Log.w(TAG, "getActiveEventsDbName: context is null, defaulting to Room")
        return@Function ROOM_DATABASE_NAME
      }
      
      val prefs = context.getSharedPreferences(STORAGE_PREFS_NAME, Context.MODE_PRIVATE)
      // Default to Room (primary implementation) - legacy is only used if migration failed
      val dbName = prefs.getString(PREF_ACTIVE_DB_NAME, ROOM_DATABASE_NAME) ?: ROOM_DATABASE_NAME
      val isRoom = prefs.getBoolean(PREF_IS_USING_ROOM, true)
      
      Log.i(TAG, "getActiveEventsDbName: returning '$dbName' (isUsingRoom=$isRoom)")
      dbName
    }

    // Returns whether Room storage is being used (vs legacy fallback)
    // Reads from SharedPreferences written by EventsStorage in main app
    Function("isUsingRoomStorage") {
      val context = appContext.reactContext
      if (context == null) {
        Log.w(TAG, "isUsingRoomStorage: context is null, defaulting to true (Room)")
        return@Function true
      }
      
      val prefs = context.getSharedPreferences(STORAGE_PREFS_NAME, Context.MODE_PRIVATE)
      // Default to true (Room is primary) - only false if migration explicitly failed
      val isRoom = prefs.getBoolean(PREF_IS_USING_ROOM, true)
      
      Log.i(TAG, "isUsingRoomStorage: $isRoom")
      isRoom
    }

    // Enables the module to be used as a native view. Definition components that are accepted as part of
    // the view definition: Prop, Events.
    View(MyModuleView::class) {
      // Defines a setter for the `name` prop.
      Prop("name") { view: MyModuleView, prop: String ->
        Log.i(TAG, prop)
      }
    }
  }
}
