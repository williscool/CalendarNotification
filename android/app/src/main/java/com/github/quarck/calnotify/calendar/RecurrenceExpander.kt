package com.github.quarck.calnotify.calendar

import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock
import java.util.*

/**
 * Utility class that expands recurring events based on their recurrence rules.
 */
class RecurrenceExpander(private val clock: CNPlusClockInterface = CNPlusSystemClock()) {
    /**
     * Expands a recurring event into multiple EventAlertRecord instances based on its recurrence rule.
     *
     * @param event The base event to expand
     * @param maxInstances Maximum number of instances to generate (default 100)
     * @return List of EventAlertRecord instances generated from the recurring event
     */
    fun expandRecurringEvent(
        event: EventRecord, 
        maxInstances: Int = 100
    ): List<EventAlertRecord> {
        return expandRecurringEvent(event, maxInstances, clock.currentTimeMillis())
    }
    
    /**
     * Expands a recurring event into multiple EventAlertRecord instances based on its recurrence rule.
     *
     * @param event The base event to expand
     * @param maxInstances Maximum number of instances to generate (default 100)
     * @param currentTimeMillis Current time in milliseconds (for setting timestamps)
     * @return List of EventAlertRecord instances generated from the recurring event
     */
    fun expandRecurringEvent(
        event: EventRecord, 
        maxInstances: Int = 100,
        currentTimeMillis: Long
    ): List<EventAlertRecord> {
        val rule = event.repeatingRule
        
        if (rule.isEmpty()) {
            return emptyList()
        }
        
        // Parse the rule
        val frequency: String
        val count: Int?
        val until: Long?
        val interval: Int
        
        when {
            rule.contains("FREQ=DAILY") -> frequency = "DAILY"
            rule.contains("FREQ=WEEKLY") -> frequency = "WEEKLY"
            rule.contains("FREQ=MONTHLY") -> frequency = "MONTHLY"
            rule.contains("FREQ=YEARLY") -> frequency = "YEARLY"
            else -> return emptyList() // Unsupported frequency
        }
        
        // Parse COUNT if present
        val countMatch = Regex("COUNT=(\\d+)").find(rule)
        count = countMatch?.groupValues?.get(1)?.toIntOrNull()
        
        // Parse INTERVAL if present (default is 1)
        val intervalMatch = Regex("INTERVAL=(\\d+)").find(rule)
        interval = intervalMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        
        // Parse UNTIL if present
        val untilMatch = Regex("UNTIL=(\\d+)T").find(rule)
        until = untilMatch?.groupValues?.get(1)?.toLongOrNull()
        
        // Generate instances based on the rule
        val instances = mutableListOf<EventAlertRecord>()
        val baseStartTime = event.startTime
        val duration = event.endTime - event.startTime
        val millisecondsPerDay = 24 * 60 * 60 * 1000L
        
        var instanceCount = 0
        var currentInstanceStart = baseStartTime
        
        // Extract reminders to calculate alert times
        val reminders = event.details.reminders
        val reminderMilliseconds = if (reminders.isNotEmpty()) 
            reminders.first().millisecondsBefore 
        else 
            15 * 60 * 1000L // Default 15 minute reminder
        
        while (instanceCount < (count ?: maxInstances)) {
            // Check UNTIL limit if specified
            if (until != null && currentInstanceStart > until) {
                break
            }
            
            // Create an instance
            instances.add(
                EventAlertRecord(
                    calendarId = event.calendarId,
                    eventId = event.eventId,
                    isAllDay = event.isAllDay,
                    isRepeating = true,
                    alertTime = currentInstanceStart - reminderMilliseconds,
                    notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM + instanceCount,
                    title = event.title,
                    desc = event.desc,
                    startTime = currentInstanceStart,
                    endTime = currentInstanceStart + duration,
                    instanceStartTime = currentInstanceStart,
                    instanceEndTime = currentInstanceStart + duration,
                    location = event.location,
                    lastStatusChangeTime = currentTimeMillis,
                    displayStatus = EventDisplayStatus.Hidden,
                    color = event.color,
                    origin = EventOrigin.FullManual,
                    timeFirstSeen = currentTimeMillis,
                    eventStatus = event.eventStatus,
                    attendanceStatus = event.attendanceStatus,
                    flags = 0
                )
            )
            
            instanceCount++
            
            // Calculate next instance time based on frequency
            when (frequency) {
                "DAILY" -> currentInstanceStart += millisecondsPerDay * interval
                "WEEKLY" -> currentInstanceStart += millisecondsPerDay * 7 * interval
                "MONTHLY" -> {
                    // Calendar-accurate for different month lengths
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = currentInstanceStart
                    calendar.add(Calendar.MONTH, interval)
                    currentInstanceStart = calendar.timeInMillis
                }
                "YEARLY" -> {
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = currentInstanceStart
                    calendar.add(Calendar.YEAR, interval)
                    currentInstanceStart = calendar.timeInMillis
                }
            }
        }
        
        return instances
    }
} 