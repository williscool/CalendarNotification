package com.github.quarck.calnotify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.prefs.PreferenceUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for Settings logic - snooze presets and reminder intervals
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class SettingsRobolectricTest {

    private lateinit var context: Context
    private lateinit var settings: Settings

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settings = Settings(context)
    }

    // === Snooze Presets Tests ===

    @Test
    fun testSnoozePresetsDefaultValues() {
        // Default snooze presets should not be empty
        val presets = settings.snoozePresets
        assertTrue("Snooze presets should not be empty", presets.isNotEmpty())
    }

    @Test
    fun testParseSnoozePresetsMinutes() {
        val parsed = PreferenceUtils.parseSnoozePresets("5m, 10m, 15m")
        assertNotNull(parsed)
        assertEquals(3, parsed!!.size)
        assertEquals(5 * 60 * 1000L, parsed[0])
        assertEquals(10 * 60 * 1000L, parsed[1])
        assertEquals(15 * 60 * 1000L, parsed[2])
    }

    @Test
    fun testParseSnoozePresetsHours() {
        val parsed = PreferenceUtils.parseSnoozePresets("1h, 2h")
        assertNotNull(parsed)
        assertEquals(2, parsed!!.size)
        assertEquals(1 * 60 * 60 * 1000L, parsed[0])
        assertEquals(2 * 60 * 60 * 1000L, parsed[1])
    }

    @Test
    fun testParseSnoozePresetsDays() {
        val parsed = PreferenceUtils.parseSnoozePresets("1d")
        assertNotNull(parsed)
        assertEquals(1, parsed!!.size)
        assertEquals(24 * 60 * 60 * 1000L, parsed[0])
    }

    @Test
    fun testParseSnoozePresetsMixed() {
        val parsed = PreferenceUtils.parseSnoozePresets("30m, 1h, 1d")
        assertNotNull(parsed)
        assertEquals(3, parsed!!.size)
        assertEquals(30 * 60 * 1000L, parsed[0])
        assertEquals(60 * 60 * 1000L, parsed[1])
        assertEquals(24 * 60 * 60 * 1000L, parsed[2])
    }

    @Test
    fun testParseSnoozePresetsInvalidReturnsNull() {
        val parsed = PreferenceUtils.parseSnoozePresets("invalid")
        assertNull(parsed)
    }

    @Test
    fun testParseSnoozePresetsEmptyReturnsNull() {
        val parsed = PreferenceUtils.parseSnoozePresets("")
        assertTrue(parsed == null || parsed.isEmpty())
    }

    // === Reminder Interval Tests ===

    @Test
    fun testReminderIntervalMillisPatternDefault() {
        // Default pattern should return at least one interval
        val pattern = settings.remindersIntervalMillisPattern
        assertTrue("Reminder pattern should have at least one value", pattern.isNotEmpty())
    }

    @Test
    fun testReminderIntervalMillisForIndexBasic() {
        // Test that the interval respects minimum
        val interval = settings.reminderIntervalMillisForIndex(0)
        assertTrue("Interval should be >= MIN_REMINDER_INTERVAL", 
            interval >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
    }

    @Test
    fun testReminderIntervalMillisForIndexWrapping() {
        // Test index wrapping - should not throw for large indices
        val interval0 = settings.reminderIntervalMillisForIndex(0)
        val interval100 = settings.reminderIntervalMillisForIndex(100)
        // Both should be valid (not throw, and respect minimum)
        assertTrue(interval0 >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
        assertTrue(interval100 >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
    }

    @Test
    fun testCurrentAndNextReminderIntervalsMillis() {
        val (current, next) = settings.currentAndNextReminderIntervalsMillis(0)
        
        // Both should respect minimum interval
        assertTrue("Current interval should be >= MIN_REMINDER_INTERVAL",
            current >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
        assertTrue("Next interval should be >= MIN_REMINDER_INTERVAL",
            next >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
    }

    @Test
    fun testCurrentAndNextReminderIntervalsWrapping() {
        // Test with large index - should wrap without exception
        val (current, next) = settings.currentAndNextReminderIntervalsMillis(1000)
        
        assertTrue(current >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
        assertTrue(next >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
    }

    @Test
    fun testCurrentAndNextReminderIntervalsDifferentIndices() {
        // Verify that consecutive calls return consistent results
        val (_, next0) = settings.currentAndNextReminderIntervalsMillis(0)
        val (current1, _) = settings.currentAndNextReminderIntervalsMillis(1)
        
        // next at index 0 should equal current at index 1
        assertEquals("Next at index 0 should equal current at index 1", next0, current1)
    }

    // === PreferenceUtils formatting tests ===

    @Test
    fun testFormatSnoozePresetMinutes() {
        assertEquals("5m", PreferenceUtils.formatSnoozePreset(5 * 60 * 1000L))
        assertEquals("30m", PreferenceUtils.formatSnoozePreset(30 * 60 * 1000L))
    }

    @Test
    fun testFormatSnoozePresetHours() {
        assertEquals("1h", PreferenceUtils.formatSnoozePreset(60 * 60 * 1000L))
        assertEquals("2h", PreferenceUtils.formatSnoozePreset(2 * 60 * 60 * 1000L))
    }

    @Test
    fun testFormatSnoozePresetDays() {
        assertEquals("1d", PreferenceUtils.formatSnoozePreset(24 * 60 * 60 * 1000L))
    }

    @Test
    fun testFormatSnoozePresetSeconds() {
        assertEquals("45s", PreferenceUtils.formatSnoozePreset(45 * 1000L))
    }

    @Test
    fun testFormatSnoozePresetWeeks() {
        assertEquals("1w", PreferenceUtils.formatSnoozePreset(7 * 24 * 60 * 60 * 1000L))
        assertEquals("2w", PreferenceUtils.formatSnoozePreset(14 * 24 * 60 * 60 * 1000L))
    }

    @Test
    fun testParseSnoozePresetsWeeks() {
        val parsed = PreferenceUtils.parseSnoozePresets("1w, 2w")
        assertNotNull(parsed)
        assertEquals(2, parsed!!.size)
        assertEquals(Consts.WEEK_IN_MILLISECONDS, parsed[0])
        assertEquals(2 * Consts.WEEK_IN_MILLISECONDS, parsed[1])
    }

    @Test
    fun testFormatPresetHumanReadable() {
        assertEquals("4 hours", PreferenceUtils.formatPresetHumanReadable(4 * Consts.HOUR_IN_MILLISECONDS))
        assertEquals("1 hour", PreferenceUtils.formatPresetHumanReadable(Consts.HOUR_IN_MILLISECONDS))
        assertEquals("1 day", PreferenceUtils.formatPresetHumanReadable(Consts.DAY_IN_MILLISECONDS))
        assertEquals("3 days", PreferenceUtils.formatPresetHumanReadable(3 * Consts.DAY_IN_MILLISECONDS))
        assertEquals("1 week", PreferenceUtils.formatPresetHumanReadable(Consts.WEEK_IN_MILLISECONDS))
        assertEquals("2 weeks", PreferenceUtils.formatPresetHumanReadable(2 * Consts.WEEK_IN_MILLISECONDS))
        assertEquals("30 minutes", PreferenceUtils.formatPresetHumanReadable(30 * Consts.MINUTE_IN_MILLISECONDS))
        assertEquals("1 minute", PreferenceUtils.formatPresetHumanReadable(Consts.MINUTE_IN_MILLISECONDS))
    }

    @Test
    fun testRoundtripWeekPresets() {
        val original = "4h, 8h, 1d, 3d, 1w"
        val parsed = PreferenceUtils.parseSnoozePresets(original)
        assertNotNull(parsed)
        val formatted = PreferenceUtils.formatPattern(parsed!!)
        assertEquals(original, formatted)
    }

    // === Upcoming Time Presets Tests ===

    @Test
    fun testUpcomingTimePresetsDefault() {
        val presets = settings.upcomingTimePresets
        assertTrue("Default upcoming presets should not be empty", presets.isNotEmpty())
        assertEquals(5, presets.size)
        assertEquals(4 * Consts.HOUR_IN_MILLISECONDS, presets[0])
        assertEquals(8 * Consts.HOUR_IN_MILLISECONDS, presets[1])
        assertEquals(Consts.DAY_IN_MILLISECONDS, presets[2])
        assertEquals(3 * Consts.DAY_IN_MILLISECONDS, presets[3])
        assertEquals(Consts.WEEK_IN_MILLISECONDS, presets[4])
    }

    @Test
    fun testUpcomingTimePresetsFiltersNegatives() {
        val parsed = PreferenceUtils.parseSnoozePresets("-5m, 4h, 1d")
        assertNotNull(parsed)
        val filtered = parsed!!.filter { it > 0 && it <= Settings.MAX_LOOKAHEAD_MILLIS }.toLongArray()
        assertEquals(2, filtered.size)
        assertEquals(4 * Consts.HOUR_IN_MILLISECONDS, filtered[0])
        assertEquals(Consts.DAY_IN_MILLISECONDS, filtered[1])
    }

    @Test
    fun testUpcomingTimePresetsFiltersExceedingMax() {
        val parsed = PreferenceUtils.parseSnoozePresets("1d, 5w, 1w")
        assertNotNull(parsed)
        val filtered = parsed!!.filter { it > 0 && it <= Settings.MAX_LOOKAHEAD_MILLIS }.toLongArray()
        assertEquals("5w (35d) exceeds 30d max, should be filtered out", 2, filtered.size)
        assertEquals(Consts.DAY_IN_MILLISECONDS, filtered[0])
        assertEquals(Consts.WEEK_IN_MILLISECONDS, filtered[1])
    }

    // === Fixed Lookahead Millis Tests ===

    @Test
    fun testFixedLookaheadMillisDefault_fallsBackToLegacyHours() {
        settings.upcomingEventsFixedHours = 8
        val millis = settings.upcomingEventsFixedLookaheadMillis
        assertEquals(8 * Consts.HOUR_IN_MILLISECONDS, millis)
    }

    @Test
    fun testFixedLookaheadMillis_setAndGet() {
        val threeDays = 3 * Consts.DAY_IN_MILLISECONDS
        settings.upcomingEventsFixedLookaheadMillis = threeDays
        assertEquals(threeDays, settings.upcomingEventsFixedLookaheadMillis)
    }

    @Test
    fun testFixedLookaheadMillis_clampedToMax() {
        val tooLarge = 60 * Consts.DAY_IN_MILLISECONDS
        settings.upcomingEventsFixedLookaheadMillis = tooLarge
        assertEquals(Settings.MAX_LOOKAHEAD_MILLIS, settings.upcomingEventsFixedLookaheadMillis)
    }

    @Test
    fun testMaxLookaheadConstants() {
        assertEquals(30L, Settings.MAX_LOOKAHEAD_DAYS)
        assertEquals(30L * Consts.DAY_IN_MILLISECONDS, Settings.MAX_LOOKAHEAD_MILLIS)
    }

    // === Display Next Alert Settings Tests ===

    @Test
    fun testDisplayNextGCalReminderDefaultValue() {
        // The default value should be true
        assertTrue("displayNextGCalReminder should default to true", settings.displayNextGCalReminder)
    }

    @Test
    fun testDisplayNextAppAlertDefaultValue() {
        // The default value should be false
        assertFalse("displayNextAppAlert should default to false", settings.displayNextAppAlert)
    }

    // === Keep History Settings Tests ===

    @Test
    fun testKeepHistoryDaysDefaultValue() {
        // Default should be 14 days
        assertEquals(14, settings.keepHistoryDays)
    }

    @Test
    fun testKeepHistoryMillisDefaultValue() {
        // 14 days in milliseconds
        val expected = 14L * Consts.DAY_IN_MILLISECONDS
        assertEquals(expected, settings.keepHistoryMillis)
    }

    @Test
    fun testKeepHistoryMillisCalculation() {
        // With default of 14 days, should be 14 * DAY_IN_MILLISECONDS
        val days = settings.keepHistoryDays
        val expectedMillis = days.toLong() * Consts.DAY_IN_MILLISECONDS
        assertEquals(expectedMillis, settings.keepHistoryMillis)
    }
}

