# Show Upcoming Event Count for Unhandled Calendars

**Origin:** User restored Android backup which had a calendar marked as unhandled. Didn't realize they were missing events from that calendar.

## Status: 📋 PLANNING

---

## Problem Summary

When a calendar is unchecked in "Handled Calendars", users have no visibility into what events they're missing. This can lead to:
- Accidentally missing important events after backup/restore
- Not realizing a calendar has relevant content
- Confusion about why certain events aren't showing notifications

**Proposed solution:** Show a count of upcoming events next to unhandled calendars, e.g. "(5 events this week)"

## Technical Feasibility

✅ **This is possible.** The calendar provider contains all event data regardless of whether our app "handles" the calendar. We can query `CalendarContract.Instances` for any calendar.

## Implementation Approach

### Query Upcoming Events Per Calendar

```kotlin
fun getUpcomingEventCountsByCalendar(context: Context, daysAhead: Int = 7): Map<Long, Int> {
    val now = System.currentTimeMillis()
    val endTime = now + daysAhead * 24 * 60 * 60 * 1000L
    
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(now.toString())
        .appendPath(endTime.toString())
        .build()
    
    val projection = arrayOf(CalendarContract.Events.CALENDAR_ID)
    
    val counts = mutableMapOf<Long, Int>()
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        while (cursor.moveToNext()) {
            val calendarId = cursor.getLong(0)
            counts[calendarId] = (counts[calendarId] ?: 0) + 1
        }
    }
    return counts
}
```

### Update Calendar List UI

In `CalendarsActivity.loadCalendars()`:

```kotlin
private fun loadCalendars() {
    val calendars = CalendarProvider.getCalendars(this).toTypedArray()
    
    // Get upcoming event counts for all calendars
    val eventCounts = getUpcomingEventCountsByCalendar(this)
    
    // ... existing grouping logic ...
    
    // When creating CalendarListEntry for unhandled calendars,
    // include the event count
}
```

### UI Options

**Option A: Subtitle text**
```
☐ Work Calendar
   (12 events this week)
```

**Option B: Badge/chip**
```
☐ Work Calendar                    [12]
```

**Option C: Only show if count > 0**
```
☐ Work Calendar (12 upcoming)
☑ Personal Calendar
```

**Recommendation:** Option C - subtle, only shows when relevant.

## Affected Files

- `CalendarsActivity.kt` - Add query, pass count to adapter
- `CalendarListAdapter` / `CalendarListEntry` - Add optional count field
- `calendar_view.xml` - Add TextView for count (or reuse existing)
- `strings.xml` - Add format string like `"%d upcoming"`

## Performance Considerations

- Query runs on background thread (already in `loadCalendars()`)
- Single query for all calendars, not per-calendar
- 7-day window is reasonable; could make configurable
- For users with many events, count operation is O(n) cursor iteration

**Estimated impact:** Minimal - one additional query during calendar list load.

## Testing

### Robolectric Tests
- Test `getUpcomingEventCountsByCalendar` returns correct counts
- Test UI shows count for unhandled calendars
- Test UI hides count for handled calendars (or shows nothing)

### Manual Smoke Test
- Create events in multiple calendars
- Uncheck a calendar
- Verify count appears
- Check calendar, verify count disappears (or changes)

## Open Questions

1. **Time window:** 7 days? 30 days? Configurable?
2. **Show for handled calendars too?** Probably not - clutters UI
3. **What if count is 0?** Hide the indicator entirely
4. **Wording:** "12 upcoming" vs "12 events this week" vs "12 events"

## Future Enhancements

- Tap on count to preview event titles
- Show count breakdown (e.g., "3 today, 9 this week")
- Option to bulk-enable calendars with events

## Related

- `calendar_sync_refresh.md` - Same screen, complementary feature
