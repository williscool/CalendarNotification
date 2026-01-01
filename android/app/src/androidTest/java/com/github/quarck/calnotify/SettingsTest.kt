package com.github.quarck.calnotify

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.prefs.PreferenceUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for Settings logic - snooze presets and reminder intervals
 */
@RunWith(AndroidJUnit4::class)
class SettingsTest {
    private val LOG_TAG = "SettingsTest"
    private lateinit var settings: Settings

    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up SettingsTest")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        settings = Settings(context)
    }

    // === Snooze Presets Tests ===

    @Test
    fun testSnoozePresetsDefaultValues() {
        DevLog.info(LOG_TAG, "Running testSnoozePresetsDefaultValues")
        val presets = settings.snoozePresets
        assertTrue("Snooze presets should not be empty", presets.isNotEmpty())
    }

    @Test
    fun testParseSnoozePresetsMinutes() {
        DevLog.info(LOG_TAG, "Running testParseSnoozePresetsMinutes")
        val parsed = PreferenceUtils.parseSnoozePresets("5m, 10m, 15m")
        assertNotNull(parsed)
        assertEquals(3, parsed!!.size)
        assertEquals(5 * 60 * 1000L, parsed[0])
        assertEquals(10 * 60 * 1000L, parsed[1])
        assertEquals(15 * 60 * 1000L, parsed[2])
    }

    @Test
    fun testParseSnoozePresetsHours() {
        DevLog.info(LOG_TAG, "Running testParseSnoozePresetsHours")
        val parsed = PreferenceUtils.parseSnoozePresets("1h, 2h")
        assertNotNull(parsed)
        assertEquals(2, parsed!!.size)
        assertEquals(1 * 60 * 60 * 1000L, parsed[0])
        assertEquals(2 * 60 * 60 * 1000L, parsed[1])
    }

    @Test
    fun testParseSnoozePresetsDays() {
        DevLog.info(LOG_TAG, "Running testParseSnoozePresetsDays")
        val parsed = PreferenceUtils.parseSnoozePresets("1d")
        assertNotNull(parsed)
        assertEquals(1, parsed!!.size)
        assertEquals(24 * 60 * 60 * 1000L, parsed[0])
    }

    @Test
    fun testParseSnoozePresetsMixed() {
        DevLog.info(LOG_TAG, "Running testParseSnoozePresetsMixed")
        val parsed = PreferenceUtils.parseSnoozePresets("30m, 1h, 1d")
        assertNotNull(parsed)
        assertEquals(3, parsed!!.size)
        assertEquals(30 * 60 * 1000L, parsed[0])
        assertEquals(60 * 60 * 1000L, parsed[1])
        assertEquals(24 * 60 * 60 * 1000L, parsed[2])
    }

    @Test
    fun testParseSnoozePresetsInvalidReturnsNull() {
        DevLog.info(LOG_TAG, "Running testParseSnoozePresetsInvalidReturnsNull")
        val parsed = PreferenceUtils.parseSnoozePresets("invalid")
        assertNull(parsed)
    }

    @Test
    fun testParseSnoozePresetsEmptyReturnsNull() {
        DevLog.info(LOG_TAG, "Running testParseSnoozePresetsEmptyReturnsNull")
        val parsed = PreferenceUtils.parseSnoozePresets("")
        assertTrue(parsed == null || parsed.isEmpty())
    }

    // === Reminder Interval Tests ===

    @Test
    fun testReminderIntervalMillisPatternDefault() {
        DevLog.info(LOG_TAG, "Running testReminderIntervalMillisPatternDefault")
        val pattern = settings.remindersIntervalMillisPattern
        assertTrue("Reminder pattern should have at least one value", pattern.isNotEmpty())
    }

    @Test
    fun testReminderIntervalMillisForIndexBasic() {
        DevLog.info(LOG_TAG, "Running testReminderIntervalMillisForIndexBasic")
        val interval = settings.reminderIntervalMillisForIndex(0)
        assertTrue("Interval should be >= MIN_REMINDER_INTERVAL",
            interval >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
    }

    @Test
    fun testReminderIntervalMillisForIndexWrapping() {
        DevLog.info(LOG_TAG, "Running testReminderIntervalMillisForIndexWrapping")
        val interval0 = settings.reminderIntervalMillisForIndex(0)
        val interval100 = settings.reminderIntervalMillisForIndex(100)
        assertTrue(interval0 >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
        assertTrue(interval100 >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
    }

    @Test
    fun testCurrentAndNextReminderIntervalsMillis() {
        DevLog.info(LOG_TAG, "Running testCurrentAndNextReminderIntervalsMillis")
        val (current, next) = settings.currentAndNextReminderIntervalsMillis(0)

        assertTrue("Current interval should be >= MIN_REMINDER_INTERVAL",
            current >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
        assertTrue("Next interval should be >= MIN_REMINDER_INTERVAL",
            next >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
    }

    @Test
    fun testCurrentAndNextReminderIntervalsWrapping() {
        DevLog.info(LOG_TAG, "Running testCurrentAndNextReminderIntervalsWrapping")
        val (current, next) = settings.currentAndNextReminderIntervalsMillis(1000)

        assertTrue(current >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
        assertTrue(next >= Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
    }

    @Test
    fun testCurrentAndNextReminderIntervalsDifferentIndices() {
        DevLog.info(LOG_TAG, "Running testCurrentAndNextReminderIntervalsDifferentIndices")
        val (_, next0) = settings.currentAndNextReminderIntervalsMillis(0)
        val (current1, _) = settings.currentAndNextReminderIntervalsMillis(1)

        assertEquals("Next at index 0 should equal current at index 1", next0, current1)
    }

    // === PreferenceUtils formatting tests ===

    @Test
    fun testFormatSnoozePresetMinutes() {
        DevLog.info(LOG_TAG, "Running testFormatSnoozePresetMinutes")
        assertEquals("5m", PreferenceUtils.formatSnoozePreset(5 * 60 * 1000L))
        assertEquals("30m", PreferenceUtils.formatSnoozePreset(30 * 60 * 1000L))
    }

    @Test
    fun testFormatSnoozePresetHours() {
        DevLog.info(LOG_TAG, "Running testFormatSnoozePresetHours")
        assertEquals("1h", PreferenceUtils.formatSnoozePreset(60 * 60 * 1000L))
        assertEquals("2h", PreferenceUtils.formatSnoozePreset(2 * 60 * 60 * 1000L))
    }

    @Test
    fun testFormatSnoozePresetDays() {
        DevLog.info(LOG_TAG, "Running testFormatSnoozePresetDays")
        assertEquals("1d", PreferenceUtils.formatSnoozePreset(24 * 60 * 60 * 1000L))
    }

    @Test
    fun testFormatSnoozePresetSeconds() {
        DevLog.info(LOG_TAG, "Running testFormatSnoozePresetSeconds")
        assertEquals("45s", PreferenceUtils.formatSnoozePreset(45 * 1000L))
    }

    // === Display Next Alert Settings Tests ===

    @Test
    fun testDisplayNextGCalReminderDefaultValue() {
        DevLog.info(LOG_TAG, "Running testDisplayNextGCalReminderDefaultValue")
        // The default value should be true
        assertTrue("displayNextGCalReminder should default to true", settings.displayNextGCalReminder)
    }

    @Test
    fun testDisplayNextAppAlertDefaultValue() {
        DevLog.info(LOG_TAG, "Running testDisplayNextAppAlertDefaultValue")
        // The default value should be false
        assertFalse("displayNextAppAlert should default to false", settings.displayNextAppAlert)
    }
}

