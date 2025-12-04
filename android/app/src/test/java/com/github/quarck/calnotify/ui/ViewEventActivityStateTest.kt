package com.github.quarck.calnotify.ui

import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for ViewEventActivityState and ViewEventActivityStateCode.
 * 
 * Tests Bundle serialization/deserialization and enum mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ViewEventActivityStateTest {
    
    // === ViewEventActivityStateCode tests ===
    
    @Test
    fun `fromInt returns Normal for code 0`() {
        val code = ViewEventActivityStateCode.fromInt(0)
        assertEquals(ViewEventActivityStateCode.Normal, code)
    }
    
    @Test
    fun `fromInt returns CustomSnoozeOpened for code 1`() {
        val code = ViewEventActivityStateCode.fromInt(1)
        assertEquals(ViewEventActivityStateCode.CustomSnoozeOpened, code)
    }
    
    @Test
    fun `fromInt returns SnoozeUntilOpenedDatePicker for code 2`() {
        val code = ViewEventActivityStateCode.fromInt(2)
        assertEquals(ViewEventActivityStateCode.SnoozeUntilOpenedDatePicker, code)
    }
    
    @Test
    fun `fromInt returns SnoozeUntilOpenedTimePicker for code 3`() {
        val code = ViewEventActivityStateCode.fromInt(3)
        assertEquals(ViewEventActivityStateCode.SnoozeUntilOpenedTimePicker, code)
    }
    
    @Test
    fun `state codes have correct int values`() {
        assertEquals(0, ViewEventActivityStateCode.Normal.code)
        assertEquals(1, ViewEventActivityStateCode.CustomSnoozeOpened.code)
        assertEquals(2, ViewEventActivityStateCode.SnoozeUntilOpenedDatePicker.code)
        assertEquals(3, ViewEventActivityStateCode.SnoozeUntilOpenedTimePicker.code)
    }
    
    // === ViewEventActivityState default values ===
    
    @Test
    fun `default state has Normal code`() {
        val state = ViewEventActivityState()
        assertEquals(ViewEventActivityStateCode.Normal, state.state)
    }
    
    @Test
    fun `default state has zero time values`() {
        val state = ViewEventActivityState()
        assertEquals(0L, state.timeAMillis)
        assertEquals(0L, state.timeBMillis)
    }
    
    // === Bundle serialization round-trip tests ===
    
    @Test
    fun `toBundle and fromBundle roundtrip preserves Normal state`() {
        val original = ViewEventActivityState(
            state = ViewEventActivityStateCode.Normal,
            timeAMillis = 0L,
            timeBMillis = 0L
        )
        
        val bundle = Bundle()
        original.toBundle(bundle)
        val restored = ViewEventActivityState.fromBundle(bundle)
        
        assertEquals(original.state, restored.state)
        assertEquals(original.timeAMillis, restored.timeAMillis)
        assertEquals(original.timeBMillis, restored.timeBMillis)
    }
    
    @Test
    fun `toBundle and fromBundle roundtrip preserves CustomSnoozeOpened state`() {
        val original = ViewEventActivityState(
            state = ViewEventActivityStateCode.CustomSnoozeOpened,
            timeAMillis = 1234567890L,
            timeBMillis = 9876543210L
        )
        
        val bundle = Bundle()
        original.toBundle(bundle)
        val restored = ViewEventActivityState.fromBundle(bundle)
        
        assertEquals(original.state, restored.state)
        assertEquals(original.timeAMillis, restored.timeAMillis)
        assertEquals(original.timeBMillis, restored.timeBMillis)
    }
    
    @Test
    fun `toBundle and fromBundle roundtrip preserves DatePicker state`() {
        val original = ViewEventActivityState(
            state = ViewEventActivityStateCode.SnoozeUntilOpenedDatePicker,
            timeAMillis = System.currentTimeMillis(),
            timeBMillis = System.currentTimeMillis() + 3600000
        )
        
        val bundle = Bundle()
        original.toBundle(bundle)
        val restored = ViewEventActivityState.fromBundle(bundle)
        
        assertEquals(original.state, restored.state)
        assertEquals(original.timeAMillis, restored.timeAMillis)
        assertEquals(original.timeBMillis, restored.timeBMillis)
    }
    
    @Test
    fun `toBundle and fromBundle roundtrip preserves TimePicker state`() {
        val original = ViewEventActivityState(
            state = ViewEventActivityStateCode.SnoozeUntilOpenedTimePicker,
            timeAMillis = 999999999L,
            timeBMillis = 888888888L
        )
        
        val bundle = Bundle()
        original.toBundle(bundle)
        val restored = ViewEventActivityState.fromBundle(bundle)
        
        assertEquals(original.state, restored.state)
        assertEquals(original.timeAMillis, restored.timeAMillis)
        assertEquals(original.timeBMillis, restored.timeBMillis)
    }
    
    // === fromBundle with missing/default values ===
    
    @Test
    fun `fromBundle with empty bundle returns default state`() {
        val bundle = Bundle()
        val state = ViewEventActivityState.fromBundle(bundle)
        
        assertEquals(ViewEventActivityStateCode.Normal, state.state)
        assertEquals(0L, state.timeAMillis)
        assertEquals(0L, state.timeBMillis)
    }
    
    @Test
    fun `fromBundle with partial bundle uses defaults for missing values`() {
        val bundle = Bundle().apply {
            putInt("code", 2) // Only set state code
        }
        val state = ViewEventActivityState.fromBundle(bundle)
        
        assertEquals(ViewEventActivityStateCode.SnoozeUntilOpenedDatePicker, state.state)
        assertEquals(0L, state.timeAMillis)
        assertEquals(0L, state.timeBMillis)
    }
    
    // === Bundle key constants ===
    
    @Test
    fun `bundle uses correct key constants`() {
        assertEquals("code", ViewEventActivityState.KEY_STATE_CODE)
        assertEquals("timeA", ViewEventActivityState.KEY_TIME_A)
        assertEquals("timeB", ViewEventActivityState.KEY_TIME_B)
    }
    
    // === Data class equality ===
    
    @Test
    fun `data class equality works correctly`() {
        val state1 = ViewEventActivityState(
            state = ViewEventActivityStateCode.CustomSnoozeOpened,
            timeAMillis = 100L,
            timeBMillis = 200L
        )
        val state2 = ViewEventActivityState(
            state = ViewEventActivityStateCode.CustomSnoozeOpened,
            timeAMillis = 100L,
            timeBMillis = 200L
        )
        
        assertEquals(state1, state2)
    }
    
    @Test
    fun `data class inequality works correctly`() {
        val state1 = ViewEventActivityState(
            state = ViewEventActivityStateCode.Normal,
            timeAMillis = 100L,
            timeBMillis = 200L
        )
        val state2 = ViewEventActivityState(
            state = ViewEventActivityStateCode.CustomSnoozeOpened,
            timeAMillis = 100L,
            timeBMillis = 200L
        )
        
        assertNotEquals(state1, state2)
    }
}

