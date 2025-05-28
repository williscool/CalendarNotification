package com.github.quarck.calnotify.testutils

import android.content.Context
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorInterface
import com.github.quarck.calnotify.logs.DevLog
import io.mockk.*

/**
 * Provides calendar-related mock functionality for Robolectric tests
 *
 * This class creates minimal calendar mocks needed for Robolectric tests
 */
class MockCalendarProvider(
    private val contextProvider: MockContextProvider,
    private val timeProvider: MockTimeProvider
) {
    private val LOG_TAG = "MockCalendarProvider"
    
    // Core components
    lateinit var mockCalendarMonitor: CalendarMonitorInterface
        private set
    
    // Track initialization state
    private var isInitialized = false
    
    /**
     * Sets up the mock calendar provider and related components
     */
    fun setup() {
        if (isInitialized) {
            DevLog.info(LOG_TAG, "MockCalendarProvider already initialized, skipping setup")
            return
        }
        
        DevLog.info(LOG_TAG, "Setting up MockCalendarProvider")
        
        // Create minimal mocks for Robolectric tests
        setupCalendarProvider()
        setupMockCalendarMonitor()
        
        isInitialized = true
    }
    
    /**
     * Creates and configures the mock calendar provider
     */
    private fun setupCalendarProvider() {
        DevLog.info(LOG_TAG, "Setting up CalendarProvider delegation")
        
        // Mock the CalendarProvider object but delegate to real implementation by default
        mockkObject(CalendarProvider)
        
        // Just ensure the basic methods work as expected
        every { 
            CalendarProvider.getEventReminders(any(), any<Long>()) 
        } returns emptyList()
        
        every { 
            CalendarProvider.isRepeatingEvent(any(), any<Long>()) 
        } returns false
    }
    
    /**
     * Sets up a mock calendar monitor
     */
    private fun setupMockCalendarMonitor() {
        DevLog.info(LOG_TAG, "Setting up mock calendar monitor")
        
        // Create a minimal mock monitor
        mockCalendarMonitor = mockk<CalendarMonitorInterface>(relaxed = true)
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up MockCalendarProvider")
        isInitialized = false
    }
} 
