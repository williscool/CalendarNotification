package expo.modules.mymodule

import kotlinx.serialization.Serializable

@Serializable
data class JsRescheduleConfirmationObject(
    val event_id: Long,
    val calendar_id: Long,
    val original_instance_start_time: Long,
    val title: String,
    val new_instance_start_time: Long?,
    val is_in_future: Boolean,
    val meta: String? = null,
    val created_at: String,
    val updated_at: String
)
