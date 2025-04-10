package com.github.quarck.calnotify.dismissedeventsstorage

enum class EventDismissResult(val code: Int) {
    Success(0),
    EventNotFound(1),
    DatabaseError(2),
    InvalidEvent(3),
    NotificationError(4),
    StorageError(5);

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }
} 