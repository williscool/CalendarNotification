# Multi-Select Events for Batch Operations

## Overview

Add the ability to select multiple events from the Active events list and perform batch operations (snooze, dismiss) on an arbitrary subset of events.

**GitHub Issue:** [#182](https://github.com/williscool/CalendarNotification/issues/182)

**Prior Art:** [quarck/CalendarNotification#350](https://github.com/quarck/CalendarNotification/issues/350)

## Motivation

Currently, users can either:
- Snooze/dismiss events one at a time
- Use "Snooze All" to affect all events
- Use search/filters to narrow down events and then snooze matching events

This feature adds a middle ground: selecting an arbitrary subset of events for batch operations. For example:
- Select 4 of 10 events and snooze them for 10 minutes
- Select the remaining 6 and snooze for 30 minutes

This avoids the tedious workflow of snoozing all for one time, then individually re-snoozing the others.

## Requirements

### Functional Requirements

1. **Selection Mode Entry:** Long-press on any event card enters selection mode
2. **Selection Persistence:** Selected events remain selected while:
   - Applying/removing search filters
   - Applying/removing chip filters (calendar, status, time)
   - Rotating the device
3. **Filter Interaction:** 
   - Selection mode works independently of search/filters
   - Selected events that become hidden by a filter remain selected (but not visible)
   - "Select All" only selects currently visible events
   - Selection count shows "X of Y selected" where Y is visible count
4. **Batch Actions:** Snooze and dismiss operations on selected events
5. **Clear Exit:** Selection mode exits cleanly after batch action or cancel

### Non-Functional Requirements

1. **Performance:** Selection state changes should be instant (no network/DB calls until action)
2. **Discoverability:** Long-press is standard Android pattern for selection
3. **Accessibility:** Selection state must be announced to screen readers

## Design

### UX Flow

#### Entering Selection Mode

1. User long-presses on any event card
2. The long-pressed event becomes the first selected item
3. UI transitions to selection mode:
   - Checkboxes appear on all visible event cards
   - Contextual action bar (CAB) appears at top
   - Bottom action bar appears with Snooze/Dismiss buttons
   - FAB (add event) hides
   - Swipe-to-dismiss is disabled

#### Selection Mode UI

```
┌─────────────────────────────────────────┐
│ ✕  3 selected          SELECT ALL      │  ← Contextual Action Bar
├─────────────────────────────────────────┤
│ [Calendar ▼] [Status ▼] [Time ▼]       │  ← Filter chips (still functional)
├─────────────────────────────────────────┤
│ ☑ █ Meeting with Bob                   │
│     Today 2:00 PM - 3:00 PM            │
├─────────────────────────────────────────┤
│ ☐ █ Code Review                        │
│     Today 3:30 PM - 4:00 PM            │
├─────────────────────────────────────────┤
│ ☑ █ Standup                            │
│     Snoozed until 2:15 PM              │
├─────────────────────────────────────────┤
│         (more events...)                │
├─────────────────────────────────────────┤
│    [SNOOZE SELECTED]  [DISMISS]        │  ← Bottom Action Bar
└─────────────────────────────────────────┘
```

#### Selecting/Deselecting

- **Tap on event card:** Toggle selection for that event
- **Tap checkbox:** Toggle selection for that event  
- **"Select All":** Select all currently visible events (filtered list)
- **Tap ✕ or back button:** Exit selection mode, clear selections

#### Batch Actions

**Snooze Selected:**
1. Opens snooze dialog (simplified SnoozeAllActivity or bottom sheet)
2. Shows "Snooze X events" as title
3. User selects snooze preset or custom time
4. All selected events are snoozed
5. Selection mode exits
6. Toast confirms: "Snoozed X events until [time]"

**Dismiss Selected:**
1. Shows confirmation dialog: "Dismiss X events?"
2. User confirms
3. All selected events are dismissed
4. Selection mode exits
5. Snackbar with undo option appears

#### Filter Interaction Details

| Scenario | Behavior |
|----------|----------|
| Selection mode active, user applies calendar filter | Hidden events stay selected but aren't visible. Count updates to "X of Y selected (Z hidden)" |
| Selection mode active, user applies search | Same as above |
| User clears filter | Previously hidden selected events reappear with selection intact |
| "Select All" pressed | Only visible (filtered) events are selected |
| All selected events become hidden by filter | Show message "All selected events are hidden by current filters" |

### Technical Design

#### Selection State Management

Selection state lives in `EventListAdapter` and is tracked by `EventAlertRecordKey` (eventId + instanceStartTime) to survive data reloads.

```kotlin
// In EventListAdapter.kt
class EventListAdapter(...) {
    
    // Selection state
    private var _selectionMode = false
    val selectionMode: Boolean get() = _selectionMode
    
    private val selectedKeys = mutableSetOf<EventAlertRecordKey>()
    
    // Selection mode callbacks
    var selectionModeCallback: SelectionModeCallback? = null
    
    interface SelectionModeCallback {
        fun onSelectionModeChanged(active: Boolean)
        fun onSelectionCountChanged(selected: Int, visible: Int, hiddenSelected: Int)
    }
    
    // Selection operations
    fun enterSelectionMode(firstEvent: EventAlertRecord) {
        _selectionMode = true
        selectedKeys.add(firstEvent.key)
        notifyDataSetChanged()
        selectionModeCallback?.onSelectionModeChanged(true)
        updateSelectionCount()
    }
    
    fun exitSelectionMode() {
        _selectionMode = false
        selectedKeys.clear()
        notifyDataSetChanged()
        selectionModeCallback?.onSelectionModeChanged(false)
    }
    
    fun toggleSelection(event: EventAlertRecord) {
        val key = event.key
        if (selectedKeys.contains(key)) {
            selectedKeys.remove(key)
        } else {
            selectedKeys.add(key)
        }
        notifyItemChanged(events.indexOf(event))
        updateSelectionCount()
    }
    
    fun selectAllVisible() {
        events.forEach { selectedKeys.add(it.key) }
        notifyDataSetChanged()
        updateSelectionCount()
    }
    
    fun getSelectedEvents(): List<EventAlertRecord> {
        // Return selected events from allEvents (includes filtered-out ones)
        return allEvents.filter { selectedKeys.contains(it.key) }
    }
    
    fun getVisibleSelectedCount(): Int = events.count { selectedKeys.contains(it.key) }
    
    fun getHiddenSelectedCount(): Int = selectedKeys.size - getVisibleSelectedCount()
    
    private fun updateSelectionCount() {
        selectionModeCallback?.onSelectionCountChanged(
            selected = selectedKeys.size,
            visible = getVisibleSelectedCount(),
            hiddenSelected = getHiddenSelectedCount()
        )
    }
}
```

#### UI Components

**Contextual Action Bar (in fragment_event_list.xml):**
```xml
<LinearLayout
    android:id="@+id/selection_action_bar"
    android:layout_width="match_parent"
    android:layout_height="?attr/actionBarSize"
    android:background="@color/primary"
    android:visibility="gone"
    android:orientation="horizontal"
    android:gravity="center_vertical">
    
    <ImageButton
        android:id="@+id/btn_close_selection"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@drawable/ic_close_white_24dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/cancel_selection"/>
    
    <TextView
        android:id="@+id/selection_count_text"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textColor="@color/white"
        android:textSize="18sp"/>
    
    <TextView
        android:id="@+id/btn_select_all"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/select_all"
        android:textColor="@color/white"
        android:padding="16dp"
        android:background="?attr/selectableItemBackground"/>
</LinearLayout>
```

**Bottom Action Bar:**
```xml
<LinearLayout
    android:id="@+id/selection_bottom_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/cardview_light_background"
    android:elevation="8dp"
    android:visibility="gone"
    android:orientation="horizontal"
    android:padding="8dp">
    
    <Button
        android:id="@+id/btn_snooze_selected"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="@string/snooze_selected"
        style="@style/Widget.MaterialComponents.Button.OutlinedButton"/>
    
    <Button
        android:id="@+id/btn_dismiss_selected"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="@string/dismiss"
        style="@style/Widget.MaterialComponents.Button.OutlinedButton"
        android:layout_marginStart="8dp"/>
</LinearLayout>
```

**Checkbox in event_card_compact.xml:**
```xml
<CheckBox
    android:id="@+id/selection_checkbox"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_alignParentStart="true"
    android:layout_centerVertical="true"
    android:layout_marginStart="4dp"
    android:visibility="gone"
    android:clickable="false"
    android:focusable="false"/>
```

#### ApplicationController Changes

Add methods for batch operations on specific events:

```kotlin
// In ApplicationController.kt

fun snoozeSelectedEvents(
    context: Context,
    events: Collection<EventAlertRecord>,
    snoozeDelay: Long,
    isChange: Boolean
): SnoozeResult? {
    if (events.isEmpty()) return null
    
    val currentTime = clock.currentTimeMillis()
    var snoozedUntil = 0L
    var allSuccess = true
    
    getEventsStorage(context).use { db ->
        var snoozeAdjust = 0
        
        for (event in events) {
            if (event.isSpecial) continue
            
            val newSnoozeUntil = currentTime + snoozeDelay + snoozeAdjust
            val shouldSnooze = isChange || event.snoozedUntil == 0L || event.snoozedUntil < newSnoozeUntil
            
            if (shouldSnooze) {
                val (success, _) = db.updateEvent(
                    event,
                    snoozedUntil = newSnoozeUntil,
                    lastStatusChangeTime = currentTime
                )
                allSuccess = allSuccess && success
                ++snoozeAdjust
                snoozedUntil = newSnoozeUntil
            }
        }
    }
    
    if (allSuccess && snoozedUntil != 0L) {
        notificationManager.onAllEventsSnoozed(context)
        alarmScheduler.rescheduleAlarms(context, getSettings(context), getQuietHoursManager(context))
        
        val silentUntil = getQuietHoursManager(context).getSilentUntil(getSettings(context), snoozedUntil)
        return SnoozeResult(SnoozeType.Snoozed, snoozedUntil, silentUntil)
    }
    
    return null
}

fun dismissSelectedEvents(
    context: Context,
    events: Collection<EventAlertRecord>,
    dismissType: EventDismissType
) {
    getEventsStorage(context).use { db ->
        val validEvents = events.filter { !it.isSpecial }
        dismissEvents(context, db, validEvents, dismissType, true)
    }
}
```

#### Fragment Changes

`ActiveEventsFragment` needs to:
1. Implement `SelectionModeCallback`
2. Handle long-press via adapter callback
3. Show/hide selection UI
4. Coordinate with `MainActivityModern` to hide/show toolbar elements
5. Handle back press to exit selection mode

```kotlin
// In ActiveEventsFragment.kt

class ActiveEventsFragment : Fragment(), EventListCallback, SearchableFragment, 
    EventListAdapter.SelectionModeCallback {
    
    private var selectionActionBar: View? = null
    private var selectionBottomBar: View? = null
    private var selectionCountText: TextView? = null
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // ... existing setup ...
        
        adapter.selectionModeCallback = this
        
        selectionActionBar = view.findViewById(R.id.selection_action_bar)
        selectionBottomBar = view.findViewById(R.id.selection_bottom_bar)
        selectionCountText = view.findViewById(R.id.selection_count_text)
        
        view.findViewById<View>(R.id.btn_close_selection)?.setOnClickListener {
            adapter.exitSelectionMode()
        }
        
        view.findViewById<View>(R.id.btn_select_all)?.setOnClickListener {
            adapter.selectAllVisible()
        }
        
        view.findViewById<View>(R.id.btn_snooze_selected)?.setOnClickListener {
            showSnoozeSelectedDialog()
        }
        
        view.findViewById<View>(R.id.btn_dismiss_selected)?.setOnClickListener {
            showDismissSelectedConfirmation()
        }
    }
    
    override fun onItemLongClick(v: View, position: Int, eventId: Long): Boolean {
        val event = adapter.getEventAtPosition(position, eventId) ?: return false
        if (event.isSpecial) return false
        
        adapter.enterSelectionMode(event)
        return true
    }
    
    override fun onSelectionModeChanged(active: Boolean) {
        selectionActionBar?.visibility = if (active) View.VISIBLE else View.GONE
        selectionBottomBar?.visibility = if (active) View.VISIBLE else View.GONE
        
        // Notify activity to hide/show its toolbar
        (activity as? MainActivityModern)?.onSelectionModeChanged(active)
    }
    
    override fun onSelectionCountChanged(selected: Int, visible: Int, hiddenSelected: Int) {
        val text = if (hiddenSelected > 0) {
            getString(R.string.selection_count_with_hidden, selected, hiddenSelected)
        } else {
            resources.getQuantityString(R.plurals.selection_count, selected, selected)
        }
        selectionCountText?.text = text
    }
    
    // Handle back press
    fun onBackPressed(): Boolean {
        if (adapter.selectionMode) {
            adapter.exitSelectionMode()
            return true
        }
        return false
    }
    
    private fun showSnoozeSelectedDialog() {
        val selectedEvents = adapter.getSelectedEvents()
        if (selectedEvents.isEmpty()) return
        
        // Launch snooze dialog/activity with selected events
        // Option 1: Bottom sheet with snooze presets
        // Option 2: Reuse SnoozeAllActivity with event IDs passed via intent
    }
    
    private fun showDismissSelectedConfirmation() {
        val selectedEvents = adapter.getSelectedEvents()
        if (selectedEvents.isEmpty()) return
        
        AlertDialog.Builder(requireContext())
            .setMessage(resources.getQuantityString(
                R.plurals.dismiss_selected_confirmation, 
                selectedEvents.size, 
                selectedEvents.size
            ))
            .setPositiveButton(android.R.string.yes) { _, _ ->
                ApplicationController.dismissSelectedEvents(
                    requireContext(),
                    selectedEvents,
                    EventDismissType.ManuallyDismissedFromActivity
                )
                adapter.exitSelectionMode()
                loadEvents()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
```

### Implementation Phases

#### Phase 1: Core Selection Infrastructure
- [ ] Add selection state to `EventListAdapter`
- [ ] Add `SelectionModeCallback` interface
- [ ] Add checkbox to `event_card_compact.xml`
- [ ] Update `onBindViewHolder` for selection state
- [ ] Add long-press handling to adapter

#### Phase 2: Selection Mode UI
- [ ] Add contextual action bar to `fragment_event_list.xml`
- [ ] Add bottom action bar to `fragment_event_list.xml`
- [ ] Implement show/hide logic in `ActiveEventsFragment`
- [ ] Add coordination with `MainActivityModern` (hide toolbar, FAB)
- [ ] Handle back press to exit selection mode

#### Phase 3: Filter/Search Integration
- [ ] Ensure selection persists through filter changes
- [ ] Update selection count to show hidden count
- [ ] Test "Select All" with various filter combinations

#### Phase 4: Batch Actions
- [ ] Add `snoozeSelectedEvents` to `ApplicationController`
- [ ] Add `dismissSelectedEvents` to `ApplicationController`
- [ ] Create snooze dialog for selected events (bottom sheet or activity)
- [ ] Implement dismiss confirmation and action
- [ ] Add undo support for batch dismiss

#### Phase 5: Polish & Testing
- [ ] Add accessibility announcements for selection state
- [ ] Add string resources for all new UI text
- [ ] Save/restore selection state on rotation
- [ ] Write unit tests for selection state management
- [ ] Write UI tests for selection mode flows

### String Resources Needed

```xml
<string name="cancel_selection">Cancel selection</string>
<string name="select_all">Select all</string>
<string name="snooze_selected">Snooze selected</string>

<plurals name="selection_count">
    <item quantity="one">%d selected</item>
    <item quantity="other">%d selected</item>
</plurals>

<string name="selection_count_with_hidden">%1$d selected (%2$d hidden by filters)</string>

<plurals name="dismiss_selected_confirmation">
    <item quantity="one">Dismiss %d event?</item>
    <item quantity="other">Dismiss %d events?</item>
</plurals>

<plurals name="snooze_selected_title">
    <item quantity="one">Snooze %d event</item>
    <item quantity="other">Snooze %d events</item>
</plurals>

<plurals name="events_snoozed">
    <item quantity="one">%d event snoozed</item>
    <item quantity="other">%d events snoozed</item>
</plurals>

<plurals name="events_dismissed">
    <item quantity="one">%d event dismissed</item>
    <item quantity="other">%d events dismissed</item>
</plurals>
```

### Testing Strategy

#### Unit Tests
- `EventListAdapter` selection state management
- Selection persistence through `setEventsToDisplay()` calls
- `getSelectedEvents()` returns correct events including filtered-out ones
- Selection count calculations (visible, hidden)

#### Robolectric Tests
- Long-press enters selection mode
- Tap toggles selection
- Select All selects visible events only
- Exit selection mode clears state
- Back press handling

#### Instrumentation Tests (if needed)
- End-to-end batch snooze flow
- End-to-end batch dismiss flow
- Selection + filter interaction

### Open Questions

1. **Snooze UI:** Should we reuse `SnoozeAllActivity` with selected event IDs, or create a new bottom sheet? Bottom sheet might be cleaner UX.

2. **Upcoming/Dismissed tabs:** Should multi-select work on those tabs too? 
   - Upcoming: Could allow pre-snooze or pre-dismiss
   - Dismissed: Could allow batch restore
   - Recommendation: Start with Active only, extend later if needed

3. **Maximum selection:** Should there be a limit? Probably not needed given typical event counts.

4. **Selection indicator:** Checkbox vs. circle/check overlay? Checkbox is more standard for Android.

### Related Files

- `EventListAdapter.kt` - Main adapter changes
- `ActiveEventsFragment.kt` - Selection mode UI coordination
- `MainActivityModern.kt` - Toolbar/FAB hiding during selection
- `ApplicationController.kt` - Batch operation methods
- `event_card_compact.xml` - Checkbox addition
- `fragment_event_list.xml` - Action bars
- `strings.xml` - New string resources
