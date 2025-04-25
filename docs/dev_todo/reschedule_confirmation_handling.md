# Reschedule Confirmation Handling

## Overview

This document summarizes the findings and considerations around handling reschedule confirmations in the Calendar Notifications app. The primary focus is on safely dismissing events based on reschedule confirmations and displaying accurate rescheduled time information to users.

## Current Implementation

- A new `safeDismissEventsFromRescheduleConfirmations` method has been added to `ApplicationController` to handle reschedule confirmations
- `JsRescheduleConfirmationObject` contains information about the original event and the new scheduled time
- When events are dismissed due to reschedule confirmation, they use the `AutoDismissedDueToRescheduleConfirmation` dismiss type

## Challenge: Displaying the New Time

Currently, when an event is rescheduled, the UI displays the time when the dismissal happened (the `dismissTime` field), not the actual new time the event was rescheduled to.

The `DismissedEventAlertRecord` class can be updated with a `rescheduleConfirmation` field that contains the `JsRescheduleConfirmationObject` with the actual new time (`new_instance_start_time`). However, this data is not persistent beyond the current session.

### Storage Considerations

Several options were considered for making the reschedule time persistent:

1. **Add a dedicated column to the database**:
   - Requires creating a new implementation and database upgrade path
   - Most robust but most complex solution

2. **Repurpose an existing unused column**:
   - Several reserved columns exist in the schema (`KEY_RESERVED_INT2`, `KEY_RESERVED_STR2`, etc.)
   - Would change the select queries and semantics of these fields
   - Lacks tests for database reading and writing
   - Not to mention worrying about serializing the confirmation properly though we could probably use the same `JsRescheduleConfirmationObject` we already have

3. **Create a separate reschedule confirmations database**:
   - Would require additional infrastructure
   - Increases complexity for a relatively simple feature

4. **Keep the current transient approach**:
   - Display the accurate new time only during the current session
   - Fall back to showing the dismissal time after app restart

## Current Decision

For now, we've implemented a simpler approach:
- Added the `safeDismissEventsFromRescheduleConfirmations` method to safely handle reschedule confirmations
- This method provides better error handling and logging than the previous implementation
- The accurate display of the new scheduled time remains an enhancement to be addressed in a future update

## Future Work

1. **Database Schema Update**:
   - Plan a proper database schema update to store the rescheduled time
   - Add tests for database reading and writing before making such changes

2. **UI Enhancements**:
   - Update the UI to clearly distinguish between:
     - Events manually dismissed by users
     - Events auto-dismissed due to rescheduling
     - Events moved using the app

3. **Testing**:
   - Add end-to-end tests for the reschedule confirmation flow
   - Ensure proper error handling for edge cases

## Implementation Notes

When eventually implementing persistent storage of reschedule confirmation data, we should:

1. Create a new database version (`DATABASE_VERSION_V3`)
2. Add a migration path from V2 to V3
3. Add a dedicated field for storing the rescheduled time
4. Update the UI to display this time
5. Add comprehensive tests for the feature

## References

- `DismissedEventAlertRecord` class
- `DismissedEventsStorageImplV2.kt`
- `ApplicationController.kt` - `safeDismissEventsFromRescheduleConfirmations` method
- `DismissedEventListAdapter.kt` - `formatReason` method
- `JsRescheduleConfirmationObject` class 