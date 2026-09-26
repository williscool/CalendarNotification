# Portable Event Identity

How the app keeps snoozed and dismissed events associated with the right calendars after a database restore onto a new phone.

The system-design *history* — why this exists, what was tried and reverted, what got measured — lives in [`docs/dev_todo/portable_event_identity.md`](../dev_todo/portable_event_identity.md). Read that when you want to know *why* something is shaped a particular way. This doc explains *what is there and how the pieces fit* right now.

## The problem in one paragraph

`eventsV9` keys events by `cid` and `id`, which are row numbers this device's Calendar Provider assigned when it first synced the account. Restore the database onto another phone and both numbers point at unrelated events, or at nothing. Tapping a notification opens the wrong event, filter pills mis-count, and the per-calendar "handled" flag silently defaults back to `true`. The fix is to capture identifiers that mean something on any device — the calendar's account tuple and the event's server-assigned `_SYNC_ID` — while the device-local ids still resolve, and re-derive `cid` from that identity after a restore.

## What lives where

### `identitystorage/` package

The whole subsystem is one package.

| File | Purpose |
|---|---|
| `EventIdentityEntity.kt` | The Room row. Fifteen columns per stored event: the primary key `(eventId, instanceStartTime)`, five calendar-account fields, `eventSyncId` + `eventUid`, `originalCalendarId` + `originalEventId` (kept as the staleness anchor), `capturedAtTime`, and `resolutionAttemptCount` + `lastResolutionAttemptTime` for retry pacing. |
| `EventIdentityDao.kt` | Room queries. Notably three narrow projections: `getAll()` reads whole rows (used only by the resolver), `getAllSyncIds()` returns just `(eventId, instanceStartTime, eventSyncId)`, and `getFullyCapturedKeys()` returns the primary key of rows that also have an account recorded. |
| `EventIdentityDatabase.kt` | Room database version 1, name `RoomEventIdentity`. Opened through `CrSqliteRoomFactory` like every other Room database in the app. |
| `EventIdentityStorage.kt` | Read/write facade. Swallows `SQLException` — capture is best-effort. |
| `EventIdentityCheck.kt` | The rule the whole system turns on. `checkEventIdentity(storedSyncId, providerSyncId)` → `CURRENT` / `STALE` / `UNKNOWN`. Pure function; no context, no storage. |
| `IdentityCapturePlan.kt` | Given a batch of events, decides which get identity written, which are stale, which have nothing to record. Pure. |
| `CalendarIdentityMatcher.kt` | Given a captured identity, decides which calendar on *this* device it refers to. Returns `Matched(id, strength)` / `Ambiguous(candidates)` / `NotFound`. Pure. |
| `CalendarResolutionPlan.kt` | Given all captured identities and all device calendars, produces a per-event decision: change / already-current / calendar-not-found / ambiguous. Pure. |
| `CalendarResolutionApplier.kt` | Turns a resolution plan into edited event copies plus a settings remap. Pure. |

Only two files reach out of the package:

- `app/ApplicationController.kt` owns the entry points `captureEventIdentities()` and `resolveEventCalendars()`. They open the databases and query the Calendar Provider; the pure classes above do everything else.
- `calendarmonitor/CalendarMonitorService.kt` calls both, in order, at the tail of the periodic-rescan pass.

### Nothing else touches this system on the notification path

Identity is never urgent — it matters at restore time, which is months away — so it deliberately runs nowhere near `EVENT_REMINDER` or `registerNewEvent`. It rides along with the wake-locked background rescan that the app already schedules every 30 minutes.

## What runs each rescan

```
CalendarMonitorService.onHandleIntent   (partial wake lock held)
    │
    ├─► existing rescan / reload logic
    │
    ├─► ApplicationController.captureEventIdentities()
    │      │
    │      ├─ read identityStorage.getAllSyncIds()                (projection)
    │      ├─ read identityStorage.getFullyCapturedKeys()         (projection)
    │      ├─ read every event from EventsStorage (active list)
    │      ├─ read the dismissed events that aren't already fully captured
    │      │      (getAllKeys projection, then getByEventIds for the missing ones)
    │      │
    │      └─► IdentityCapturePlan.compute(...)                   (pure)
    │             │    provider + captured sync id per event
    │             │
    │             ├─ CURRENT / UNKNOWN → capture (refresh identity)
    │             └─ STALE            → skip, leave for the resolver
    │
    └─► ApplicationController.resolveEventCalendars()
           │
           ├─ read identityStorage.getAll()                       (full rows)
           ├─ read calendarProvider.getCalendars()
           ├─ read every event from EventsStorage
           │
           ├─► CalendarResolutionPlan.compute(...)                (pure)
           │      │    matches each identity to a device calendar
           │      │    caches match results per unique account tuple
           │      │
           │      ├─ Matched, and event's live cid == matched id → alreadyCurrent
           │      ├─ Matched, and cid differs                    → changes
           │      ├─ Ambiguous                                    → left for a later pass
           │      └─ NotFound                                     → left for a later pass
           │
           └─► CalendarResolutionApplier.computeEdits(...)        (pure)
                  │
                  ├─ events whose row still exists      → updatedEvents (copy(calendarId = new))
                  └─ calendars with an explicit setting → handledSettingsToMove + toClear

           EventsStorage.updateEvents(updatedEvents)              (Room @Update, keyed on PK,
                                                                   transactional rollback built in)

           Settings.setCalendarIsHandled(newId, ...)              (per calendar, only if set)
           Settings.clearCalendarIsHandled(oldId)
```

Total per healthy-device pass: a handful of projection reads and one `getCalendars()`. No writes. The `updatedEvents` list is empty, `handledSettingsToMove` is empty, both entry points return without touching anything.

## The self-check

This is the rule the design turns on and worth understanding on its own. It is a pure function of two strings.

| Provider returns for the stored id | Stored sync id | Verdict | What capture does |
|---|---|---|---|
| an event | matches | `CURRENT` | refresh the identity row (in case the calendar was renamed) |
| an event | differs | `STALE` | **skip** — leave the row for the resolver |
| nothing (id gone) | any | `STALE` | **skip** |
| anything | absent / blank | `UNKNOWN` | capture for the first time |

Blank counts as absent on both sides — provider columns come back as `""` as readily as `null`, and comparing two blanks as equal would report `CURRENT` for two unrelated events.

The load-bearing property is that this asks about **one row** and answers about **one row**. Earlier attempts to infer a device-wide "is this restored?" from indirect evidence (an install fingerprint, then a validation sample over a handful of events) both mistook *legitimately absent* for *wrong device* and disabled capture on healthy phones. See the plan doc's *Why device-level restore detection was abandoned* section.

## Capture, in detail

`ApplicationController.captureEventIdentities()` — the entry point — is small: read the two projections, gather events from both stores, hand them to `IdentityCapturePlan.compute()`, write the resulting rows.

The compute call is where the decisions live:

```kotlin
IdentityCapturePlan.compute(
    events           = deduplicated event list,
    storedSyncIdOf   = { syncId this event was captured with, or null },
    providerEventOf  = { what the provider now returns for this event id },
    backupInfoOf     = { calendar account tuple for a given cid },
    capturedAtTime   = clock.now()
)
```

Each argument except `events` is a function so the pure compute never touches storage. Tests hand it lambdas over plain maps.

### Both stores, deduplicated

Active events live in `eventsV9`, dismissed events in `dismissedEventsV2`. Both are walked. The concatenation is deduplicated on `(eventId, instanceStartTime)` with the active row winning — dismiss-then-restore can leave the same key in both stores, and the active row is the authoritative one.

Active events are re-read on every pass — that list is small and its events genuinely move (reschedules, edits, calendar renames), so refreshing their identity is worth the queries. Dismissed events are skipped once they're already **fully captured**: `getFullyCapturedKeys()` returns the primary key of rows whose account columns are non-empty, and capture reads only the dismissed rows *not* in that set. Steady state: zero dismissed rows are read.

That distinction — *fully* captured, not just captured — matters. A row that has a sync id but no account (its calendar was gone when capture ran) has to keep being retried, or the calendar coming back does nothing.

### Per-verdict outcomes

- `CURRENT` — the calendar tuple is refreshed on the identity row. Cheap; keeps display names up to date if the user renames a calendar.
- `UNKNOWN` — the row is captured for the first time.
- `STALE` — nothing is written. Capturing from a stale id would record the wrong event's identity, which is worse than not capturing at all.

### Where a null calendar goes

A stored event whose `cid` no longer resolves to a real calendar produces `backupInfoOf(...) == null`. Two branches:

- If the provider also knows nothing about the event → the row is classified `gone` and left alone.
- If the provider *does* know it, and there's a sync id to compare → the row is captured with a sync id and blank account fields. `getFullyCapturedKeys()` filters it out, so the next pass will try again.

## Resolution, in detail

`ApplicationController.resolveEventCalendars()` — the second entry point — reads every identity row, every calendar, every event, hands them to `CalendarResolutionPlan.compute()`, and applies the resulting edits.

### Matching a calendar

Three fields together identify a calendar independently of any device:

| Field | Example | What it is |
|---|---|---|
| `accountName` | `you@gmail.com` | the account the calendar syncs under |
| `accountType` | `com.google` | which sync adapter owns it |
| `ownerAccount` | `c_9tnin89@group.calendar.google.com` | who the calendar *belongs to* |

`ownerAccount` is the field doing the work, and it is easy to assume redundant. It is not: Google gives every *secondary* calendar its own address as owner, so only your primary has `owner == accountName`. On a device with 16 calendars over 2 accounts, `accountName` + `accountType` alone leaves 9 and 7 candidates; adding `ownerAccount` makes all 16 unique. It also keeps a **shared** calendar distinct — subscribe to one owned by another account and `accountName` is yours, `ownerAccount` is theirs.

If several calendars still share all three fields, the search is **narrowed** by `name`, then `displayName`. Never widened. Name and display name are never searched on their own — two calendars called `William H - uws` exist on different accounts on the measured device, so a name-only match would cross an account boundary.

The matcher returns:

- `Matched(id, MatchStrength)` — exactly one calendar. `strength` is `UNIQUE_ACCOUNT`, `ACCOUNT_PLUS_CALENDAR_NAME`, or `ACCOUNT_PLUS_DISPLAY_NAME` for logging.
- `Ambiguous(candidateIds)` — several matched and none could be narrowed. Reported, never guessed. The rows keep their stale `cid` and are retried on later passes, by which time a sync may have made the calendars distinguishable.
- `NotFound` — the account is on no calendar here. Not signed in yet, not synced yet, or genuinely gone.

### From matches to a plan

`CalendarResolutionPlan.compute()` walks every stored identity and sorts them into four buckets: `changes`, `alreadyCurrent`, `calendarNotFound`, `ambiguous`.

Two details worth knowing:

- It matches per **unique calendar tuple**, not per event. Hundreds of events typically share a handful of calendars, so the matcher runs a handful of times and the results are cached.
- It compares each event against the *live* `cid` (via a `currentCalendarIdOf` callback), not the identity row's `originalCalendarId`. That field records where the event was captured on the *old* device; treating it as current would reschedule the same write forever.

### From a plan to edits

`CalendarResolutionApplier.computeEdits()` turns a plan into two things:

- `updatedEvents: List<EventAlertRecord>` — event rows with their `calendarId` corrected, ready to hand to `EventsStorage.updateEvents`. Guards: the event still has to exist (dismissed or deleted between planning and applying → skipped), and its live `calendarId` still has to differ from the target (already correct → skipped, keeps rescans idempotent).
- `handledSettingsToMove: Map<Long, Boolean>` + `handledSettingsToClear: Set<Long>` — the per-calendar `calendar_handled_.<id>` prefs, collapsed from the per-event changes. **Only the calendars the user explicitly set** are moved. `getCalendarIsHandled` defaults to `true`, so writing the default through would invent a setting the user never made *and* make later `hasCalendarIsHandledSetting()` checks answer `yes` from then on.

Only after both pieces are computed does the controller do any writing. Events first — the settings move is a repair of what the event rows say, so it should never run ahead of them succeeding.

## Scope

**In this system, right now:**

- Rewriting `cid` on `eventsV9` rows after a restore.
- Moving `calendar_handled_.<id>` preferences with the events.
- Retrying anything that can't be resolved yet (ambiguous, calendar-not-found), on every subsequent rescan.

**Not in this system:**

- Rewriting event `id`. That is *half* the primary key of `eventsV9`, so changing it means delete + re-insert across four databases (`eventsV9`, `RoomEventIdentity`, `dismissedEventsV2`, `manualAlertsV1`) with manual rollback. Separate future layer.
- The old-device fallback for `_SYNC_ID`, `UID_2445`. Measured on a real Google-synced device: `_SYNC_ID` populated and unique for 100% of 4761 events, `UID_2445` null for every one. Capture reads and stores both, but only `_SYNC_ID` drives resolution today.
- Backfilling identity from a snapshot at install time. Capture on the old device happens every rescan; a device installing this build fresh gains identity on its first rescan.

## When each PR fits

| PR | Layer | Landed on the resolver as… |
|---|---|---|
| [#277](https://github.com/williscool/CalendarNotification/pull/277), [#279](https://github.com/williscool/CalendarNotification/pull/279) | Identity storage — the Room database, entity, DAO, facade | The `identitystorage/` package skeleton |
| [#280](https://github.com/williscool/CalendarNotification/pull/280) | Self-check, dismissed capture, bulk query optimisations | `EventIdentityCheck`, `IdentityCapturePlan`, the two DAO projections |
| [#281](https://github.com/williscool/CalendarNotification/pull/281) | Matcher + plan (lookup only, no writes) | `CalendarIdentityMatcher`, `CalendarResolutionPlan` |
| [#283](https://github.com/williscool/CalendarNotification/pull/283) | `cid` write-back | `CalendarResolutionApplier`, `resolveEventCalendars`, the service call |
| [#284](https://github.com/williscool/CalendarNotification/pull/284) | Per-calendar setting remap | The applier's settings edits, `hasCalendarIsHandledSetting` / `clearCalendarIsHandled`, `resetSettings` for tests |

## For further reading

- Design history, measurements, and abandoned approaches: [`docs/dev_todo/portable_event_identity.md`](../dev_todo/portable_event_identity.md).
- How the rescan service reaches these entry points: [`calendar_monitoring.md`](calendar_monitoring.md).
- Room/legacy storage split (both stores this system reads through): [`storage_lifecycle.md`](storage_lifecycle.md). Batch-size reasoning for the dismissed-event read is in the KDoc of `MAX_EVENTS_PER_BATCH` in `RoomDismissedEventsStorage.kt`; the summary is that Android's 2 MB `CursorWindow` sets the ceiling, not SQLite's parameter cap.
- Schema of the identity table (columns and their SQL types): [`database_schema_reference.md`](database_schema_reference.md).
