package expo.modules.mymodule

import kotlinx.serialization.Serializable

@Serializable
data class JsDismissEventObject(
  val ids: List<Long>
)
