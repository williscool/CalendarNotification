# Database Schema Reference

Column-by-column reference for the app's SQLite databases. Column names are abbreviated for historical reasons (the 2016 schema used short names to save space), so this table is the map between the on-disk name and what it actually means.

Authoritative definitions live in the Room entities; this doc is the human-readable index:

- `eventsstorage/EventAlertEntity.kt`
- `dismissedeventsstorage/DismissedEventEntity.kt`
- `monitorstorage/MonitorAlertEntity.kt`

## Databases

| Database | Room file | Legacy file | Table | Primary key |
|---|---|---|---|---|
| Events (active/snoozed) | `RoomEvents` | `Events` | `eventsV9` | `(id, istart)` |
| Portable event identity *(planned)* | `RoomEventIdentity` | — (new) | `eventIdentityV1` | `(eventId, instanceStart)` |
| Dismissed events | `RoomDismissedEvents` | `DismissedEvents` | `dismissedEventsV2` | `(eventId, instanceStart)` |
| Calendar monitor | `RoomCalendarMonitor` | `CalendarMonitor` | `manualAlertsV1` | `(eventId, alertTime, instanceStart)` |

These are **separate database files**. There are no foreign keys between them and cross-database transactions are not possible — code that must stay consistent across two of them does manual rollback (see `ApplicationController.unsnoozeToUpcoming`).

Note what the shared `(eventId, instanceStart)` key implies: changing an event's `eventId` requires re-keying its rows in *every* one of these databases, with no transaction spanning them.

## `eventsV9` — active and snoozed events

The short column names here are the ones most likely to confuse. Note especially `attsts`/`oattsts`, which are easy to read backwards.

| Column | Field | Type | Notes |
|---|---|---|---|
| `cid` | `calendarId` | Long | CalendarContract `Calendars._ID`. **Device-local.** `-1` = unknown, treated as handled (fail-open) |
| `id` | `eventId` | Long | CalendarContract `Events._ID`. **Device-local.** PK part 1 |
| `istart` | `instanceStartTime` | Long | PK part 2. Distinguishes occurrences of a recurring event |
| `iend` | `instanceEndTime` | Long | |
| `estart` | `startTime` | Long | The *event's* start, vs the *instance's* start above |
| `eend` | `endTime` | Long | |
| `altm` | `alertTime` | Long | When the reminder fires |
| `nid` | `notificationId` | Int | Android notification ID. Independent of `id` |
| `ttl` | `title` | String | |
| `s1` | `description` | String | A string column that is *not* reserved despite the name |
| `loc` | `location` | String | |
| `snz` | `snoozedUntil` | Long | `0` = not snoozed |
| `ls` | `lastStatusChangeTime` | Long | |
| `dsts` | `displayStatus` | Int | `EventDisplayStatus` enum |
| `clr` | `color` | Int | |
| `rep` | `isRepeating` | Int | 0/1 |
| `alld` | `isAllDay` | Int | 0/1 |
| `ogn` | `origin` | Int | `EventOrigin` enum |
| `fsn` | `timeFirstSeen` | Long | |
| `attsts` | **`eventStatus`** | Int | `EventStatus` enum — confirmed/tentative/canceled |
| `oattsts` | **`attendanceStatus`** | Int | `AttendanceStatus` enum — the user's RSVP |
| `i1` | `flags` | Long | Bitfield: `IS_MUTED=1`, `IS_TASK=2`, `IS_ALARM=4`, `IS_PINNED=8` |
| `i2`–`i8` | *reserved* | Long | Unused; written as `0` |
| `s2` | *reserved* | String | Unused; written as `""` — the only spare text column in this table |

## `dismissedEventsV2` — dismissal history

Mirrors `eventsV9` but uses **long column names**, so don't copy-paste column lists between the two.

Same-meaning columns with different names: `eventStart`/`eventEnd` (vs `estart`/`eend`), `instanceStart`/`instanceEnd` (vs `istart`/`iend`), `snoozeUntil` (vs `snz`), `displayStatus` (vs `dsts`), `title`/`location`/`color`/`isRepeating`/`allDay` spelled out. `s1` is again `description`.

Additional columns:

| Column | Field | Notes |
|---|---|---|
| `dismissTime` | `dismissTime` | |
| `dismissType` | `dismissType` | `EventDismissType` enum |
| `lastSeen` | `lastStatusChangeTime` | Same field as `ls` in `eventsV9`, different name |

Reserved: `i2`–`i9`, `s2`, `s3`. Does **not** carry `eventStatus`/`attendanceStatus`/`timeFirstSeen`/`origin`.

## `manualAlertsV1` — calendar monitor scan state

Short-lived bookkeeping for alerts the app discovered by scanning, rebuilt from the provider.

| Column | Field | Notes |
|---|---|---|
| `calendarId` | `calendarId` | **Written but never read** — `toAlertEntry()` drops it, and `MonitorEventAlertEntry` has no such field |
| `eventId` | `eventId` | PK part 1 |
| `alertTime` | `alertTime` | PK part 2 |
| `instanceStart` | `instanceStartTime` | PK part 3 |
| `instanceEnd` | `instanceEndTime` | |
| `allDay` | `isAllDay` | |
| `alertCreatedByUs` | `alertCreatedByUs` | Distinguishes our synthetic alerts from provider ones |
| `wasHandled` | `wasHandled` | Whether we've already fired for this alert |
| `i1`, `i2` | *reserved* | |

## On the reserved columns

Every table carries spare `iN`/`sN` columns from the original 2016 schema. They are written as `0`/`""` and read by nothing.

New claims apply to the **Room entities only** — the legacy `*Impl*` classes are deprecated and scheduled for removal (`../dev_todo/deprecated_features.md`, item 5), so they should not gain new field handling.

**Spend them sparingly.** There is exactly one spare text column per table and claiming one is effectively irreversible once rows are written. Reserve them for data that genuinely *must* live in the row: read on the hot path, needed in the same query as the event, or required to travel with the row through the PowerSync/Supabase pipeline. Anything that is merely *associated* with an event — derived metadata, bookkeeping, anything read only on rare paths — belongs in its own table or database, joined by `(eventId, instanceStart)`.

A worked example of choosing the latter: [portable_event_identity.md](../dev_todo/portable_event_identity.md) initially planned to use `eventsV9.s2` and deliberately moved to a separate Room database instead.

They exist so a field can be added **without a schema migration** — no Room version bump, no new legacy `EventsStorageImplV10`, and no change to the Supabase table (`supabase/migrations/20250301213237_events.sql` already mirrors every column, reserved ones included), which keeps the PowerSync payload working untouched.

The cost is that the name stops describing the content: a column called `i1` holding a flags bitfield is opaque to anyone reading a raw DB dump. So the rule is:

> **When you claim a reserved column, rename the constant to describe its meaning, document it in this file, and leave a comment at the entity declaration. Never leave a live column named "reserved".**

Note the precedent: `i1` was already claimed for `flags`, and `s1` for `description`. Both are documented above rather than left as mysteries.

### Currently claimed

| Table | Column | Claimed by | Status |
|---|---|---|---|
| `eventsV9` | `i1` | `flags` bitfield | in use |
| `eventsV9` | `s1` | `description` | in use |
| `dismissedEventsV2` | `i1` | `flags` bitfield | in use |
| `dismissedEventsV2` | `s1` | `description` | in use |

## Identity caveat

`cid` and `id` are both **device-local autoincrement row IDs** assigned by that device's Calendar Provider. They are meaningless on any other device, which is why a restored database cannot re-associate with calendars without extra stored identity. See [portable_event_identity.md](../dev_todo/portable_event_identity.md).

## Related

- [domain_model.md](./domain_model.md) — the domain types these rows map to
- [storage_lifecycle.md](./storage_lifecycle.md) — Room vs legacy, and the `.use {}` pattern
- [../dev_completed/room_database_migration.md](../dev_completed/room_database_migration.md) — the copy-based migration strategy
