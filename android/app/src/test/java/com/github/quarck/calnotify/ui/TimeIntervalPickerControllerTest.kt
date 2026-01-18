package com.github.quarck.calnotify.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.NumberPicker
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import org.junit.Assert.assertEquals
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
    private lateinit var spinner: Spinner

    private enum class UnitType {
        Seconds,
        Minutes,
        Hours,
        Days
    }

    private data class IndexCase(
        val allowSubMinuteIntervals: Boolean,
        val expectedSecondsIndex: Int,
        val expectedMinutesIndex: Int,
        val expectedHoursIndex: Int,
        val expectedDaysIndex: Int
    )

    private data class IntervalCase(
        val allowSubMinuteIntervals: Boolean,
        val value: Int,
        val unit: UnitType,
        val expectedSeconds: Int
    )

    private data class SetSecondsCase(
        val inputSeconds: Int,
        val expectedUnit: UnitType,
        val expectedValue: Int
    )

    private data class MaxIntervalCase(
        val maxIntervalMilliseconds: Long,
        val unit: UnitType,
        val expectedMaxValue: Int
    )

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        view = LayoutInflater.from(context).inflate(R.layout.dialog_interval_picker, null)
        numberPicker = view.findViewById(R.id.numberPickerTimeInterval)
        spinner = view.findViewById(R.id.spinnerTimeIntervalUnit)
    }

    private fun createController(
        allowSubMinuteIntervals: Boolean,
        maxIntervalMilliseconds: Long = 0L
    ): TimeIntervalPickerController {
        return TimeIntervalPickerController(
            view = view,
            titleId = null,
            maxIntervalMilliseconds = maxIntervalMilliseconds,
            allowSubMinuteIntervals = allowSubMinuteIntervals
        )
    }

    private fun unitIndex(controller: TimeIntervalPickerController, unit: UnitType): Int {
        return when (unit) {
            UnitType.Seconds -> controller.SecondsIndex
            UnitType.Minutes -> controller.MinutesIndex
            UnitType.Hours -> controller.HoursIndex
            UnitType.Days -> controller.DaysIndex
        }
    }

    @Test
    fun `controller indices reflect sub-minute configuration`() {
        val cases = listOf(
            IndexCase(false, -1, 0, 1, 2),
            IndexCase(true, 0, 1, 2, 3)
        )

        for (case in cases) {
            val controller = createController(allowSubMinuteIntervals = case.allowSubMinuteIntervals)

            assertEquals(case.expectedSecondsIndex, controller.SecondsIndex)
            assertEquals(case.expectedMinutesIndex, controller.MinutesIndex)
            assertEquals(case.expectedHoursIndex, controller.HoursIndex)
            assertEquals(case.expectedDaysIndex, controller.DaysIndex)
        }
    }

    @Test
    fun `controller defaults set picker bounds and selection`() {
        val controller = createController(allowSubMinuteIntervals = false)

        assertEquals(1, numberPicker.minValue)
        assertEquals(100, numberPicker.maxValue)
        assertEquals(controller.MinutesIndex, spinner.selectedItemPosition)
    }

    @Test
    fun `intervalSeconds getter converts selected unit`() {
        val cases = listOf(
            IntervalCase(false, 30, UnitType.Minutes, 30 * 60),
            IntervalCase(false, 2, UnitType.Hours, 2 * 60 * 60),
            IntervalCase(false, 1, UnitType.Days, 24 * 60 * 60),
            IntervalCase(true, 45, UnitType.Seconds, 45)
        )

        for (case in cases) {
            val controller = createController(allowSubMinuteIntervals = case.allowSubMinuteIntervals)
            numberPicker.value = case.value
            spinner.setSelection(unitIndex(controller, case.unit))

            assertEquals(case.expectedSeconds, controller.intervalSeconds)
        }
    }

    @Test
    fun `intervalSeconds setter chooses best unit without sub-minute`() {
        val cases = listOf(
            SetSecondsCase(30 * 60, UnitType.Minutes, 30),
            SetSecondsCase(2 * 60 * 60, UnitType.Hours, 2),
            SetSecondsCase(2 * 24 * 60 * 60, UnitType.Days, 2),
            SetSecondsCase(90 * 60, UnitType.Minutes, 90)
        )

        for (case in cases) {
            val controller = createController(allowSubMinuteIntervals = false)
            controller.intervalSeconds = case.inputSeconds

            assertEquals(unitIndex(controller, case.expectedUnit), spinner.selectedItemPosition)
            assertEquals(case.expectedValue, numberPicker.value)
        }
    }

    @Test
    fun `intervalSeconds setter respects sub-minute intervals`() {
        val cases = listOf(
            SetSecondsCase(45, UnitType.Seconds, 45),
            SetSecondsCase(5 * 60, UnitType.Minutes, 5)
        )

        for (case in cases) {
            val controller = createController(allowSubMinuteIntervals = true)
            controller.intervalSeconds = case.inputSeconds

            assertEquals(unitIndex(controller, case.expectedUnit), spinner.selectedItemPosition)
            assertEquals(case.expectedValue, numberPicker.value)
        }
    }

    @Test
    fun `intervalMilliseconds getter and setter convert through seconds`() {
        val controller = createController(allowSubMinuteIntervals = false)
        numberPicker.value = 15
        spinner.setSelection(controller.MinutesIndex)

        assertEquals(15 * 60 * 1000L, controller.intervalMilliseconds)

        controller.intervalMilliseconds = Consts.HOUR_IN_MILLISECONDS
        assertEquals(controller.HoursIndex, spinner.selectedItemPosition)
        assertEquals(1, numberPicker.value)
    }

    @Test
    fun `intervalMinutes getter and setter convert through seconds`() {
        val getterController = createController(allowSubMinuteIntervals = false)
        numberPicker.value = 2
        spinner.setSelection(getterController.HoursIndex)

        assertEquals(120, getterController.intervalMinutes)

        val setterController = createController(allowSubMinuteIntervals = false)
        setterController.intervalMinutes = 60

        assertEquals(setterController.HoursIndex, spinner.selectedItemPosition)
        assertEquals(1, numberPicker.value)
    }

    @Test
    fun `maxValue clamps to max interval`() {
        val cases = listOf(
            MaxIntervalCase(
                maxIntervalMilliseconds = 2 * Consts.HOUR_IN_MILLISECONDS,
                unit = UnitType.Minutes,
                expectedMaxValue = 100
            ),
            MaxIntervalCase(
                maxIntervalMilliseconds = 5 * Consts.HOUR_IN_MILLISECONDS,
                unit = UnitType.Hours,
                expectedMaxValue = 5
            )
        )

        for (case in cases) {
            val controller = createController(
                allowSubMinuteIntervals = false,
                maxIntervalMilliseconds = case.maxIntervalMilliseconds
            )

            spinner.setSelection(unitIndex(controller, case.unit))
            controller.onItemSelected(null, null, spinner.selectedItemPosition, 0)

            assertEquals(case.expectedMaxValue, numberPicker.maxValue)
        }
    }

    @Test
    fun `setting and getting intervalSeconds preserves value`() {
        val controller = createController(allowSubMinuteIntervals = false)
        val testValues = listOf(
            60,
            300,
            3600,
            7200,
            86400,
            172800
        )

        for (value in testValues) {
            controller.intervalSeconds = value
            assertEquals("Round trip failed for $value seconds", value, controller.intervalSeconds)
        }
    }

    @Test
    fun `setting and getting intervalMilliseconds preserves value`() {
        val controller = createController(allowSubMinuteIntervals = false)
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
