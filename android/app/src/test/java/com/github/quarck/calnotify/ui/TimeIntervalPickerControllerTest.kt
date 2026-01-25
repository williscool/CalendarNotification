package com.github.quarck.calnotify.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.NumberPicker
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for TimeIntervalPickerController.
 * 
 * Tests the time interval conversion logic between different units.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TimeIntervalPickerControllerTest {
    
    private lateinit var view: View
    private lateinit var numberPicker: NumberPicker
    private lateinit var timeUnitsDropdown: AutoCompleteTextView
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        view = LayoutInflater.from(context).inflate(R.layout.dialog_interval_picker, null)
        numberPicker = view.findViewById(R.id.numberPickerTimeInterval)
        timeUnitsDropdown = view.findViewById(R.id.spinnerTimeIntervalUnit)
    }

    private fun expectedTimeUnitsArray(controller: TimeIntervalPickerController): Array<String> {
        val ctx = view.context
        return if (controller.SecondsIndex >= 0) {
            ctx.resources.getStringArray(R.array.time_units_plurals_with_seconds)
        } else {
            ctx.resources.getStringArray(R.array.time_units_plurals)
        }
    }

    private fun selectTimeUnit(controller: TimeIntervalPickerController, position: Int) {
        val labels = expectedTimeUnitsArray(controller)
        timeUnitsDropdown.setText(labels[position], false)
        controller.onItemSelected(position)
    }
    
    // === Index Configuration Tests (without sub-minute intervals) ===
    
    @Test
    fun `controller without sub-minute has correct indices`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        assertEquals(-1, controller.SecondsIndex)
        assertEquals(0, controller.MinutesIndex)
        assertEquals(1, controller.HoursIndex)
        assertEquals(2, controller.DaysIndex)
    }
    
    @Test
    fun `controller with sub-minute has shifted indices`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = true
        )
        
        assertEquals(0, controller.SecondsIndex)
        assertEquals(1, controller.MinutesIndex)
        assertEquals(2, controller.HoursIndex)
        assertEquals(3, controller.DaysIndex)
    }
    
    // === Default Value Tests ===
    
    @Test
    fun `numberPicker has default min value of 1`() {
        TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        assertEquals(1, numberPicker.minValue)
    }
    
    @Test
    fun `numberPicker has default max value of 100`() {
        TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        assertEquals(100, numberPicker.maxValue)
    }
    
    @Test
    fun `spinner defaults to minutes selection`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        val labels = expectedTimeUnitsArray(controller)
        assertEquals(labels[controller.MinutesIndex], timeUnitsDropdown.text.toString())
    }
    
    // === Interval Seconds Getter Tests ===
    
    @Test
    fun `intervalSeconds returns correct value for minutes`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        numberPicker.value = 30
        selectTimeUnit(controller, controller.MinutesIndex)
        
        assertEquals(30 * 60, controller.intervalSeconds)
    }
    
    @Test
    fun `intervalSeconds returns correct value for hours`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        numberPicker.value = 2
        selectTimeUnit(controller, controller.HoursIndex)
        
        assertEquals(2 * 60 * 60, controller.intervalSeconds)
    }
    
    @Test
    fun `intervalSeconds returns correct value for days`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        numberPicker.value = 1
        selectTimeUnit(controller, controller.DaysIndex)
        
        assertEquals(24 * 60 * 60, controller.intervalSeconds)
    }
    
    @Test
    fun `intervalSeconds returns correct value for seconds with sub-minute enabled`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = true
        )
        
        numberPicker.value = 45
        selectTimeUnit(controller, controller.SecondsIndex)
        
        assertEquals(45, controller.intervalSeconds)
    }
    
    // === Interval Seconds Setter Tests (without sub-minute) ===
    
    @Test
    fun `setting intervalSeconds converts to optimal unit - minutes`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        controller.intervalSeconds = 30 * 60 // 30 minutes in seconds
        
        val labels = expectedTimeUnitsArray(controller)
        assertEquals(labels[controller.MinutesIndex], timeUnitsDropdown.text.toString())
        assertEquals(30, numberPicker.value)
    }
    
    @Test
    fun `setting intervalSeconds converts to optimal unit - hours`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        controller.intervalSeconds = 2 * 60 * 60 // 2 hours in seconds
        
        val labels = expectedTimeUnitsArray(controller)
        assertEquals(labels[controller.HoursIndex], timeUnitsDropdown.text.toString())
        assertEquals(2, numberPicker.value)
    }
    
    @Test
    fun `setting intervalSeconds converts to optimal unit - days`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        controller.intervalSeconds = 2 * 24 * 60 * 60 // 2 days in seconds
        
        val labels = expectedTimeUnitsArray(controller)
        assertEquals(labels[controller.DaysIndex], timeUnitsDropdown.text.toString())
        assertEquals(2, numberPicker.value)
    }
    
    @Test
    fun `setting intervalSeconds keeps minutes when not evenly divisible by 60`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        // 90 minutes = 1.5 hours, should stay as minutes
        controller.intervalSeconds = 90 * 60
        
        val labels = expectedTimeUnitsArray(controller)
        assertEquals(labels[controller.MinutesIndex], timeUnitsDropdown.text.toString())
        assertEquals(90, numberPicker.value)
    }
    
    // === Interval Seconds Setter Tests (with sub-minute) ===
    
    @Test
    fun `setting intervalSeconds with sub-minute converts to seconds when not divisible by 60`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = true
        )
        
        controller.intervalSeconds = 45 // 45 seconds
        
        val labels = expectedTimeUnitsArray(controller)
        assertEquals(labels[controller.SecondsIndex], timeUnitsDropdown.text.toString())
        assertEquals(45, numberPicker.value)
    }
    
    @Test
    fun `setting intervalSeconds with sub-minute converts to minutes when divisible by 60`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = true
        )
        
        controller.intervalSeconds = 5 * 60 // 5 minutes
        
        val labels = expectedTimeUnitsArray(controller)
        assertEquals(labels[controller.MinutesIndex], timeUnitsDropdown.text.toString())
        assertEquals(5, numberPicker.value)
    }
    
    // === Interval Milliseconds Tests ===
    
    @Test
    fun `intervalMilliseconds getter converts from seconds`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        numberPicker.value = 15
        selectTimeUnit(controller, controller.MinutesIndex)
        
        assertEquals(15 * 60 * 1000L, controller.intervalMilliseconds)
    }
    
    @Test
    fun `intervalMilliseconds setter converts to seconds`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        controller.intervalMilliseconds = Consts.HOUR_IN_MILLISECONDS // 1 hour
        
        val labels = expectedTimeUnitsArray(controller)
        assertEquals(labels[controller.HoursIndex], timeUnitsDropdown.text.toString())
        assertEquals(1, numberPicker.value)
    }
    
    // === Interval Minutes Tests ===
    
    @Test
    fun `intervalMinutes getter converts from seconds`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        numberPicker.value = 2
        selectTimeUnit(controller, controller.HoursIndex)
        
        assertEquals(120, controller.intervalMinutes) // 2 hours = 120 minutes
    }
    
    @Test
    fun `intervalMinutes setter converts to seconds`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        controller.intervalMinutes = 60 // 60 minutes = 1 hour
        
        val labels = expectedTimeUnitsArray(controller)
        assertEquals(labels[controller.HoursIndex], timeUnitsDropdown.text.toString())
        assertEquals(1, numberPicker.value)
    }
    
    // === Max Interval Clamping Tests ===
    
    @Test
    fun `maxValue is clamped when max interval is set`() {
        val maxIntervalMs = 2 * Consts.HOUR_IN_MILLISECONDS // 2 hours max
        
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = maxIntervalMs,
            allowSubMinuteIntervals = false
        )
        
        // Trigger onItemSelected by changing selection
        selectTimeUnit(controller, controller.MinutesIndex)
        
        // Max should be 120 minutes (but capped at 100)
        assertEquals(100, numberPicker.maxValue)
    }
    
    @Test
    fun `maxValue adjusts for hours unit with max interval`() {
        val maxIntervalMs = 5 * Consts.HOUR_IN_MILLISECONDS // 5 hours max
        
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = maxIntervalMs,
            allowSubMinuteIntervals = false
        )
        
        selectTimeUnit(controller, controller.HoursIndex)
        
        assertEquals(5, numberPicker.maxValue)
    }
    
    // === Round Trip Tests ===
    
    @Test
    fun `setting and getting intervalSeconds preserves value`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        val testValues = listOf(
            60,      // 1 minute
            300,     // 5 minutes
            3600,    // 1 hour
            7200,    // 2 hours
            86400,   // 1 day
            172800   // 2 days
        )
        
        for (value in testValues) {
            controller.intervalSeconds = value
            assertEquals("Round trip failed for $value seconds", value, controller.intervalSeconds)
        }
    }
    
    @Test
    fun `setting and getting intervalMilliseconds preserves value`() {
        val controller = TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = 0L,
            allowSubMinuteIntervals = false
        )
        
        val testValues = listOf(
            Consts.MINUTE_IN_MILLISECONDS,
            5 * Consts.MINUTE_IN_MILLISECONDS,
            Consts.HOUR_IN_MILLISECONDS,
            Consts.DAY_IN_MILLISECONDS
        )
        
        for (value in testValues) {
            controller.intervalMilliseconds = value
            assertEquals("Round trip failed for $value ms", value, controller.intervalMilliseconds)
        }
    }
}

