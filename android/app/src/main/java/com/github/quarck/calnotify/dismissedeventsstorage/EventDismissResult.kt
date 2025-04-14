package com.github.quarck.calnotify.dismissedeventsstorage

/**
 * Represents the result of attempting to dismiss an event.
 * Success is determined by whether the event was properly stored in the dismissed events storage.
 * Deletion from the main events storage is considered a warning rather than a failure.
 */
enum class EventDismissResult(val code: Int) {
    Success(0),                    // Event was found and stored in dismissed events storage
    EventNotFound(1),             // Event was not found in the database
    DatabaseError(2),             // Error occurred deleting from main storage
    InvalidEvent(3),              // Event is invalid
    NotificationError(4),         // Error occurred during notification handling
    StorageError(5),              // Failed to store in dismissed events storage
    DeletionWarning(6);           // Event was stored but failed to delete from main storage

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }
} 