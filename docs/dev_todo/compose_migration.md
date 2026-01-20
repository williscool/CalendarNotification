# Jetpack Compose Migration Plan

**GitHub Issue:** TBD  
**Status:** Planning  
**Last Updated:** January 2026

## Overview

This document outlines a plan to migrate the Calendar Notifications Plus modern UI from the traditional View system (XML layouts + Activities/Fragments) to Jetpack Compose.

## Current State (v9.15.0)

### UI Component Inventory

| Component | Type | Lines | Complexity | Notes |
|-----------|------|-------|------------|-------|
| `MainActivityModern.kt` | Activity | 636 | High | Navigation, filter chips, menus |
| `ViewEventActivityNoRecents.kt` | Activity | 945 | Very High | Single event view, snooze UI, dialogs |
| `SnoozeAllActivity.kt` | Activity | 606 | High | Bulk snooze with presets |
| `EventListAdapter.kt` | RecyclerView.Adapter | 539 | High | Swipe gestures, undo, ViewHolder |
| `PreActionActivity.kt` | Activity | 437 | Medium | Pre-mute/snooze/dismiss |
| `ActiveEventsFragment.kt` | Fragment | 325 | Medium | Active events list |
| `UpcomingEventsFragment.kt` | Fragment | 310 | Medium | Upcoming events list |
| `DismissedEventsFragment.kt` | Fragment | 293 | Medium | Dismissed events list |
| `CalendarFilterBottomSheet.kt` | BottomSheetDialogFragment | 283 | Medium | Multi-select with search |
| `FilterState.kt` | Data class | 200 | Low | Filter state management |
| `TimeFilterBottomSheet.kt` | BottomSheetDialogFragment | 132 | Low | Radio selection |
| `DismissedEventListAdapter.kt` | RecyclerView.Adapter | ~250 | Medium | Similar to EventListAdapter |
| Other UI files | Various | ~1500 | Mixed | Settings, about, etc. |

**Total: 30 UI source files, 62 XML layout files**

### Current Architecture

- **Navigation:** `NavHostFragment` + XML nav graph + `BottomNavigationView`
- **Lists:** `RecyclerView` with custom `ItemTouchHelper` for swipe-to-dismiss
- **State:** `FilterState` data class with Bundle serialization
- **Dialogs:** Custom dialog builders with View inflation
- **Bottom Sheets:** `BottomSheetDialogFragment` subclasses

### Test Coverage

- ~70+ UI tests (Espresso/Ultron instrumented tests)
- Robolectric unit tests for fragments and activities
- Tests use View-based matchers

## Migration Strategy

### Recommended Approach: Incremental Migration

Google's recommended approach - migrate components incrementally while maintaining interoperability. This allows:
- Continued feature development during migration
- Risk mitigation (can rollback individual components)
- Test suite updates in parallel
- Learning curve spread over time

### Why Not Full Rewrite?

- High risk of breaking existing functionality
- Test suite would need complete rewrite
- Extended feature freeze
- Not recommended by Google

---

## Phase 1: Infrastructure Setup

**Goal:** Get Compose building and establish patterns without changing visible UI.

### 1.1 Build Configuration

Add to `android/app/build.gradle`:

```kotlin
android {
    buildFeatures {
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"  // Match Kotlin 1.9.22
    }
}

dependencies {
    // Compose BOM for version alignment
    def composeBom = platform('androidx.compose:compose-bom:2024.02.00')
    implementation composeBom
    androidTestImplementation composeBom
    
    // Core Compose
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
    
    // Integration with existing Views
    implementation 'androidx.activity:activity-compose:1.8.2'
    implementation 'androidx.lifecycle:lifecycle-runtime-compose:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'
    
    // Navigation (for eventual full migration)
    implementation 'androidx.navigation:navigation-compose:2.7.7'
    
    // Testing
    androidTestImplementation 'androidx.compose.ui:ui-test-junit4'
    debugImplementation 'androidx.compose.ui:ui-tooling'
    debugImplementation 'androidx.compose.ui:ui-test-manifest'
}
```

### 1.2 Theme Bridge

Create `ui/theme/` package with Compose theme that mirrors existing XML theme:

```kotlin
// ui/theme/AppTheme.kt
@Composable
fun CalendarNotifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme(
        primary = Color(0xFF4285F4),  // Match @color/primary
        // ... other colors from colors.xml
    ) else lightColorScheme(
        primary = Color(0xFF4285F4),
        // ...
    )
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,  // Define to match existing text styles
        content = content
    )
}
```

### 1.3 Verification Test

Add a simple Compose test to verify setup works:

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun compose_infrastructure_works() {
    composeTestRule.setContent {
        CalendarNotifyTheme {
            Text("Compose works!")
        }
    }
    composeTestRule.onNodeWithText("Compose works!").assertIsDisplayed()
}
```

**Files to create:**
- `ui/theme/AppTheme.kt`
- `ui/theme/Color.kt`
- `ui/theme/Type.kt`

**Files to modify:**
- `android/app/build.gradle`

---

## Phase 2: Bottom Sheets Migration

**Goal:** Migrate self-contained bottom sheets as proof of concept.

### 2.1 TimeFilterBottomSheet → Compose

The simplest component - single-select radio buttons.

```kotlin
// ui/compose/TimeFilterSheet.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeFilterSheet(
    currentFilter: TimeFilter,
    tabType: TabType,
    onDismiss: () -> Unit,
    onFilterSelected: (TimeFilter) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.filter_time),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            TimeFilter.entries
                .filter { it.isAvailableFor(tabType) }
                .forEach { filter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = filter == currentFilter,
                                onClick = { onFilterSelected(filter) }
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = filter == currentFilter,
                            onClick = null  // Handled by Row
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(filter.getDisplayName(LocalContext.current))
                    }
                }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { onFilterSelected(currentFilter); onDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.apply))
            }
            
            Spacer(modifier = Modifier.height(32.dp))  // Bottom padding for nav bar
        }
    }
}
```

**Integration in MainActivityModern:**

```kotlin
// Add ComposeView to show bottom sheet
private var timeFilterSheetVisible by mutableStateOf(false)

private fun showTimeFilterCompose() {
    val composeView = ComposeView(this).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            CalendarNotifyTheme {
                if (timeFilterSheetVisible) {
                    TimeFilterSheet(
                        currentFilter = filterState.timeFilter,
                        tabType = getCurrentTabType(),
                        onDismiss = { timeFilterSheetVisible = false },
                        onFilterSelected = { filter ->
                            filterState = filterState.copy(timeFilter = filter)
                            timeFilterSheetVisible = false
                            updateFilterChipsForCurrentTab()
                            getCurrentSearchableFragment()?.onFilterChanged()
                        }
                    )
                }
            }
        }
    }
    (window.decorView as ViewGroup).addView(composeView)
    timeFilterSheetVisible = true
}
```

### 2.2 CalendarFilterBottomSheet → Compose

More complex - multi-select with search and color indicators.

```kotlin
// ui/compose/CalendarFilterSheet.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarFilterSheet(
    calendars: List<CalendarRecord>,
    selectedIds: Set<Long>?,  // null = all, empty = none
    maxDisplayItems: Int = 20,
    onDismiss: () -> Unit,
    onApply: (Set<Long>?) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var localSelection by remember { 
        mutableStateOf(selectedIds ?: calendars.map { it.calendarId }.toSet()) 
    }
    
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Text(
                text = stringResource(R.string.filter_calendar),
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_calendars)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // "All Calendars" toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        localSelection = if (localSelection.size == calendars.size) {
                            emptySet()
                        } else {
                            calendars.map { it.calendarId }.toSet()
                        }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = localSelection.size == calendars.size,
                    onCheckedChange = null
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.filter_calendar_all),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            HorizontalDivider()
            
            // Calendar list
            val filtered = calendars.filter {
                searchQuery.isEmpty() || 
                it.displayName.contains(searchQuery, ignoreCase = true)
            }.take(if (searchQuery.isNotEmpty()) 50 else maxDisplayItems)
            
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(filtered, key = { it.calendarId }) { calendar ->
                    CalendarFilterItem(
                        calendar = calendar,
                        isSelected = calendar.calendarId in localSelection,
                        onToggle = {
                            localSelection = if (calendar.calendarId in localSelection) {
                                localSelection - calendar.calendarId
                            } else {
                                localSelection + calendar.calendarId
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    // Return null if all selected (means "no filter")
                    val result = if (localSelection.size == calendars.size) null else localSelection
                    onApply(result)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.apply))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CalendarFilterItem(
    calendar: CalendarRecord,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Calendar color indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = Color(calendar.color),
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Checkbox(
            checked = isSelected,
            onCheckedChange = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = calendar.displayName.ifEmpty { calendar.name },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
```

**Files to create:**
- `ui/compose/TimeFilterSheet.kt`
- `ui/compose/CalendarFilterSheet.kt`

**Files to modify:**
- `MainActivityModern.kt` - Add Compose integration for showing sheets

**Tests to update:**
- Create new Compose tests for bottom sheets
- Keep existing View tests until full migration

---

## Phase 3: Event Card Composable

**Goal:** Create reusable event card with swipe-to-dismiss.

### 3.1 EventCard Composable

```kotlin
// ui/compose/EventCard.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCard(
    event: EventAlertRecord,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    onUndoDismiss: () -> Unit,
    showUndo: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else false
        }
    )
    
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            DismissBackground(dismissState.dismissDirection)
        },
        modifier = modifier
    ) {
        if (showUndo) {
            UndoCard(onUndo = onUndoDismiss)
        } else {
            EventCardContent(event = event, onClick = onClick)
        }
    }
}

@Composable
private fun EventCardContent(
    event: EventAlertRecord,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 8.dp)
        ) {
            // Calendar color bar
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .padding(start = 2.dp, end = 5.dp)
                    .background(
                        color = Color(event.color.adjustCalendarColor().takeIf { it != 0 }
                            ?: MaterialTheme.colorScheme.primary.toArgb())
                    )
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                // Title
                Text(
                    text = event.titleAsOneLine,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Date/time
                val formatter = remember { EventFormatter(LocalContext.current) }
                Text(
                    text = formatter.formatDateTimeOneLine(event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Snoozed until (if applicable)
                if (event.snoozedUntil > 0) {
                    Text(
                        text = stringResource(R.string.snoozed_until_string) + " " + 
                               formatter.formatSnoozedUntil(event),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            
            // Status icons
            Row(
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (event.isMuted) {
                    Icon(
                        imageVector = Icons.Default.VolumeOff,
                        contentDescription = stringResource(R.string.notification_is_muted),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (event.isTask) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (event.isAlarm) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
    
    HorizontalDivider(thickness = 0.25.dp)
}

@Composable
private fun DismissBackground(direction: SwipeToDismissBoxValue?) {
    val color = MaterialTheme.colorScheme.error
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Icon(
            imageVector = Icons.Default.Clear,
            contentDescription = stringResource(R.string.dismiss),
            tint = Color.White
        )
    }
}

@Composable
private fun UndoCard(onUndo: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.error
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dismissed),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onUndo) {
                Text(
                    text = stringResource(R.string.undo),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
```

### 3.2 Hybrid Adapter Approach

Use Compose cards within existing RecyclerView during transition:

```kotlin
// In EventListAdapter.kt - add ComposeView support
override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    // Option: Use ComposeView as the item view
    val composeView = ComposeView(parent.context).apply {
        setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
    }
    return ViewHolder(composeView)
}

override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val event = events[position]
    (holder.itemView as ComposeView).setContent {
        CalendarNotifyTheme {
            EventCard(
                event = event,
                onClick = { callback.onItemClick(holder.itemView, position, event.eventId) },
                onDismiss = { callback.onItemRemoved(event) },
                onUndoDismiss = { callback.onItemRestored(event) },
                showUndo = eventsPendingRemoval.contains(event.key)
            )
        }
    }
}
```

**Files to create:**
- `ui/compose/EventCard.kt`
- `ui/compose/DismissedEventCard.kt` (similar, without undo)

**Files to modify:**
- `EventListAdapter.kt` - Option to use Compose cards
- `DismissedEventListAdapter.kt` - Same

---

## Phase 4: Event List Screen

**Goal:** Replace fragments with full Compose screens.

### 4.1 ViewModel for State Management

```kotlin
// ui/viewmodel/EventsViewModel.kt
class EventsViewModel(
    private val eventsStorage: EventsStorageInterface,
    private val clock: CNPlusClockInterface
) : ViewModel() {
    
    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()
    
    private val _events = MutableStateFlow<List<EventAlertRecord>>(emptyList())
    val events: StateFlow<List<EventAlertRecord>> = _events.asStateFlow()
    
    private val _pendingRemovals = MutableStateFlow<Set<EventAlertRecordKey>>(emptySet())
    val pendingRemovals: StateFlow<Set<EventAlertRecordKey>> = _pendingRemovals.asStateFlow()
    
    fun loadEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val allEvents = eventsStorage.eventsForDisplay
            val now = clock.currentTimeMillis()
            _events.value = filterState.value.filterEvents(allEvents, now).toList()
        }
    }
    
    fun updateFilter(newState: FilterState) {
        _filterState.value = newState
        loadEvents()
    }
    
    fun dismissEvent(event: EventAlertRecord) {
        _pendingRemovals.value = _pendingRemovals.value + event.key
        // Schedule actual removal after undo timeout
    }
    
    fun undoDismiss(event: EventAlertRecord) {
        _pendingRemovals.value = _pendingRemovals.value - event.key
    }
}
```

### 4.2 ActiveEventsScreen Composable

```kotlin
// ui/compose/ActiveEventsScreen.kt
@Composable
fun ActiveEventsScreen(
    viewModel: EventsViewModel = viewModel(),
    onEventClick: (EventAlertRecord) -> Unit,
    onEventDismissed: (EventAlertRecord) -> Unit
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val pendingRemovals by viewModel.pendingRemovals.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    
    val pullRefreshState = rememberPullToRefreshState()
    
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.loadEvents()
            pullRefreshState.endRefresh()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
        if (events.isEmpty()) {
            EmptyState(message = stringResource(R.string.empty_active))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 72.dp)  // FAB space
            ) {
                items(
                    items = events,
                    key = { it.key.hashCode() }
                ) { event ->
                    EventCard(
                        event = event,
                        onClick = { onEventClick(event) },
                        onDismiss = {
                            viewModel.dismissEvent(event)
                            onEventDismissed(event)
                        },
                        onUndoDismiss = { viewModel.undoDismiss(event) },
                        showUndo = event.key in pendingRemovals
                    )
                }
            }
        }
        
        PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

### 4.3 Fragment Wrapper (Transitional)

Keep fragment as wrapper during migration:

```kotlin
// ActiveEventsFragment.kt - modified to use Compose
class ActiveEventsFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                CalendarNotifyTheme {
                    ActiveEventsScreen(
                        onEventClick = { event -> navigateToEventDetail(event) },
                        onEventDismissed = { event -> handleDismiss(event) }
                    )
                }
            }
        }
    }
    
    private fun navigateToEventDetail(event: EventAlertRecord) {
        startActivity(
            Intent(requireContext(), ViewEventActivity::class.java)
                .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
        )
    }
}
```

**Files to create:**
- `ui/viewmodel/EventsViewModel.kt`
- `ui/viewmodel/UpcomingEventsViewModel.kt`
- `ui/viewmodel/DismissedEventsViewModel.kt`
- `ui/compose/ActiveEventsScreen.kt`
- `ui/compose/UpcomingEventsScreen.kt`
- `ui/compose/DismissedEventsScreen.kt`
- `ui/compose/EmptyState.kt`

**Files to modify:**
- `ActiveEventsFragment.kt`
- `UpcomingEventsFragment.kt`
- `DismissedEventsFragment.kt`

---

## Phase 5: Main Scaffold & Navigation

**Goal:** Replace MainActivityModern's layout with Compose scaffold.

### 5.1 Main Scaffold

```kotlin
// ui/compose/MainScaffold.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    filterState: FilterState,
    onFilterStateChanged: (FilterState) -> Unit,
    onAddEventClick: () -> Unit,
    onSettingsClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(currentTab.title) },
                    actions = {
                        IconButton(onClick = { /* search */ }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.MoreVert, "Menu")
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                FilterChipsRow(
                    currentTab = currentTab,
                    filterState = filterState,
                    onFilterStateChanged = onFilterStateChanged
                )
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, tab.title) },
                        selected = currentTab == tab,
                        onClick = { onTabSelected(tab) },
                        label = null  // Icon-only
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEventClick) {
                Icon(Icons.Default.Add, "Add event")
            }
        },
        content = content
    )
}

enum class Tab(val title: String, val icon: ImageVector) {
    ACTIVE("Active", Icons.Default.Notifications),
    UPCOMING("Upcoming", Icons.Default.Schedule),
    DISMISSED("Dismissed", Icons.Default.Delete)
}
```

### 5.2 Filter Chips Row

```kotlin
// ui/compose/FilterChipsRow.kt
@Composable
fun FilterChipsRow(
    currentTab: Tab,
    filterState: FilterState,
    onFilterStateChanged: (FilterState) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCalendarSheet by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showTimeSheet by remember { mutableStateOf(false) }
    
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Calendar chip (all tabs)
        item {
            FilterChip(
                selected = filterState.selectedCalendarIds != null,
                onClick = { showCalendarSheet = true },
                label = { Text(getCalendarChipText(filterState)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
            )
        }
        
        // Status chip (Active and Upcoming only)
        if (currentTab != Tab.DISMISSED) {
            item {
                FilterChip(
                    selected = filterState.statusFilters.isNotEmpty(),
                    onClick = { showStatusMenu = true },
                    label = { Text(getStatusChipText(filterState)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                )
            }
        }
        
        // Time chip (Active and Dismissed only)
        if (currentTab != Tab.UPCOMING) {
            item {
                FilterChip(
                    selected = filterState.timeFilter != TimeFilter.ALL,
                    onClick = { showTimeSheet = true },
                    label = { Text(getTimeChipText(filterState)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                )
            }
        }
    }
    
    // Bottom sheets
    if (showCalendarSheet) {
        CalendarFilterSheet(/* ... */)
    }
    if (showTimeSheet) {
        TimeFilterSheet(/* ... */)
    }
    // Status popup menu handled separately
}
```

**Files to create:**
- `ui/compose/MainScaffold.kt`
- `ui/compose/FilterChipsRow.kt`
- `ui/compose/StatusFilterMenu.kt`

**Files to modify:**
- `MainActivityModern.kt` - Use ComposeView for entire content

---

## Phase 6: ViewEventActivity Migration

**Goal:** Migrate the complex single-event view/snooze screen.

This is the most complex migration due to:
- Custom time/date picker dialogs
- Snooze preset grid
- State management for multi-step dialogs
- Integration with calendar intents

### 6.1 Break Into Components

```kotlin
// ui/compose/event/EventDetailScreen.kt
// ui/compose/event/SnoozePresetsGrid.kt
// ui/compose/event/EventHeader.kt
// ui/compose/event/RescheduleSection.kt
// ui/compose/dialog/CustomSnoozeDialog.kt
// ui/compose/dialog/SnoozeUntilDialog.kt
```

### 6.2 State Machine for Dialogs

```kotlin
sealed class EventDetailState {
    data class Viewing(val event: EventAlertRecord) : EventDetailState()
    data class CustomSnooze(val event: EventAlertRecord, val duration: Long) : EventDetailState()
    data class SnoozeUntil(val event: EventAlertRecord, val date: LocalDate, val time: LocalTime?) : EventDetailState()
}
```

**Estimated scope:** 500-700 lines of Compose code, significant testing effort.

---

## Phase 7: Remaining Screens

**Goal:** Complete migration of auxiliary screens.

| Screen | Complexity | Notes |
|--------|------------|-------|
| `SnoozeAllActivity` | Medium | Reuse snooze preset components |
| `PreActionActivity` | Medium | Pre-mute/snooze/dismiss options |
| `EditEventActivity` | Medium | Form inputs |
| `SettingsActivityX` | Low | Use AndroidX Preference or Compose settings |
| `AboutActivity` | Low | Static content |
| `PrivacyPolicyActivity` | Low | WebView or text |

---

## Testing Strategy

### Compose-Specific Testing

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun eventCard_displaysEventTitle() {
    val event = createTestEvent(title = "Team Meeting")
    
    composeTestRule.setContent {
        CalendarNotifyTheme {
            EventCard(event = event, onClick = {}, onDismiss = {}, onUndoDismiss = {})
        }
    }
    
    composeTestRule.onNodeWithText("Team Meeting").assertIsDisplayed()
}

@Test
fun eventCard_swipeToDismiss_callsCallback() {
    var dismissed = false
    val event = createTestEvent()
    
    composeTestRule.setContent {
        CalendarNotifyTheme {
            EventCard(
                event = event, 
                onClick = {}, 
                onDismiss = { dismissed = true }, 
                onUndoDismiss = {}
            )
        }
    }
    
    composeTestRule.onNodeWithText(event.title)
        .performTouchInput { swipeLeft() }
    
    assertTrue(dismissed)
}
```

### Hybrid Testing During Migration

- Keep existing Espresso/Ultron tests for View components
- Add Compose tests for new Compose components
- Use `createAndroidComposeRule<Activity>()` for Activity-level tests with Compose content

---

## Migration Checklist

### Phase 1: Infrastructure
- [ ] Add Compose dependencies to build.gradle
- [ ] Create theme files (AppTheme.kt, Color.kt, Type.kt)
- [ ] Verify Compose builds and test infrastructure works
- [ ] Create first Compose test

### Phase 2: Bottom Sheets
- [ ] Implement TimeFilterSheet in Compose
- [ ] Implement CalendarFilterSheet in Compose
- [ ] Integrate with MainActivityModern
- [ ] Update/add tests

### Phase 3: Event Card
- [ ] Create EventCard composable
- [ ] Implement swipe-to-dismiss
- [ ] Implement undo state
- [ ] Create DismissedEventCard
- [ ] Test hybrid adapter approach

### Phase 4: Event List Screens
- [ ] Create EventsViewModel
- [ ] Create ActiveEventsScreen
- [ ] Create UpcomingEventsScreen
- [ ] Create DismissedEventsScreen
- [ ] Update fragments to use Compose
- [ ] Migrate SearchableFragment interface

### Phase 5: Main Scaffold
- [ ] Create MainScaffold with navigation
- [ ] Create FilterChipsRow
- [ ] Migrate menu handling
- [ ] Update MainActivityModern

### Phase 6: ViewEventActivity
- [ ] Design state machine for dialogs
- [ ] Create EventDetailScreen
- [ ] Create snooze components
- [ ] Create custom dialogs in Compose
- [ ] Extensive testing

### Phase 7: Remaining Screens
- [ ] SnoozeAllActivity
- [ ] PreActionActivity
- [ ] EditEventActivity
- [ ] Settings screens
- [ ] About/Privacy screens

### Cleanup
- [ ] Remove unused XML layouts
- [ ] Remove View-based code
- [ ] Update documentation
- [ ] Final test pass

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Test suite breaks | Maintain hybrid during migration, update tests incrementally |
| Performance regression | Profile LazyColumn vs RecyclerView, optimize recomposition |
| Functionality regression | Feature flags for Compose vs View paths |
| Extended timeline | Each phase is independently shippable |
| Theme inconsistency | Bridge XML theme to Compose early |

---

## Dependencies

- Kotlin 1.9.22+ (current: 1.9.22 ✓)
- Compose BOM 2024.02.00+
- AGP 8.0+ (current: 8.x ✓)
- minSdk 23 (current: 23 ✓) - Compose supports API 21+

---

## References

- [Jetpack Compose Migration Guide](https://developer.android.com/jetpack/compose/migrate)
- [Compose and Views Interop](https://developer.android.com/jetpack/compose/migrate/interoperability-apis)
- [State Management in Compose](https://developer.android.com/jetpack/compose/state)
- [Testing in Compose](https://developer.android.com/jetpack/compose/testing)
