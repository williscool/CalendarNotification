package com.github.quarck.calnotify.calendar

data class CalendarBackupInfo(
    val originalCalendarId: Long,
    val owner: String,
    val accountName: String,
    val accountType: String,
    val displayName: String,
    val name: String
) 