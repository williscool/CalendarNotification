package com.github.quarck.calnotify.calendar

data class CalendarBackupInfo(
    val calendarId: Long,
    val accountName: String,
    val accountType: String,
    val ownerAccount: String,
    val displayName: String,
    val name: String
) 