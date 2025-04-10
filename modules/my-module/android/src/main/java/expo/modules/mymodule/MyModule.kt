package expo.modules.mymodule

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

  val TAG = "MyModule"

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
    // TODO: this is the other side of the bridge!
    AsyncFunction("setValueAsync") { value: String ->
      // Send an event to JavaScript.

      val eventObject = Json.decodeFromString<List<JsRescheduleConfirmationObject>>(value)

      Log.i(TAG, eventObject.toString())
      // TOOD: setup an intent here to send the ids for the main app to consume

      sendEvent("onChange", mapOf(
        "value" to value
      ))
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
