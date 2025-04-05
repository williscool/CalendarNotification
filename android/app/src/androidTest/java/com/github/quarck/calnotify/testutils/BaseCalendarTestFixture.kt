package com.github.quarck.calnotify.testutils

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusTestClock
import io.mockk.MockKAnnotations
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import java.util.concurrent.ScheduledExecutorService
import org.junit.Assert.*
import com.github.quarck.calnotify.logs.DevLog

/**
 * Base fixture for Calendar Notification Plus tests
 *
 * This class provides a reusable test infrastructure for calendar-related tests,
 * reducing duplication and simplifying test setup.
 */
class BaseCalendarTestFixture private constructor(builder: Builder) {
    
    private val LOG_TAG = "BaseCalendarTestFixture"
    
    // Core components
    val contextProvider: MockContextProvider
    val timeProvider: MockTimeProvider
    val calendarProvider: MockCalendarProvider
    val applicationComponents: MockApplicationComponents
    
    // Test state
    var testCalendarId: Long = 0
    var testEventId: Long = 0
    var eventStartTime: Long = 0
    var reminderTime: Long = 0
    
    init {
        DevLog.info(LOG_TAG, "Initializing BaseCalendarTestFixture")
        
        // Initialize components in the correct order
        timeProvider = builder.timeProvider ?: MockTimeProvider()
        contextProvider = builder.contextProvider ?: MockContextProvider(timeProvider)
        calendarProvider = builder.calendarProvider ?: MockCalendarProvider(contextProvider, timeProvider)
        applicationComponents = builder.applicationComponents ?: MockApplicationComponents(
            contextProvider, 
            timeProvider, 
            calendarProvider
        )
        
        // Perform setup
        setup()
    }
    
    /**
     * Performs the basic setup for the test fixture
     */
    private fun setup() {
        DevLog.info(LOG_TAG, "Setting up test fixture")
        MockKAnnotations.init(this)
        
        // Initialize the context and other components
        contextProvider.setup()
        timeProvider.setup()
        calendarProvider.setup()
        applicationComponents.setup()
        
        // Clear any existing data
        clearStorages()
    }
    
    /**
     * Clears all storage databases to ensure a clean test state
     */
    fun clearStorages() {
        DevLog.info(LOG_TAG, "Clearing storages")
        calendarProvider.clearStorages(contextProvider.fakeContext)
    }
    
    /**
     * Creates a test calendar and sets up basic test state
     */
    fun setupTestCalendar(
        displayName: String = "Test Calendar",
        accountName: String = "test@local",
        ownerAccount: String = "test@local"
    ): Long {
        DevLog.info(LOG_TAG, "Setting up test calendar")
        testCalendarId = calendarProvider.createTestCalendar(
            contextProvider.fakeContext,
            displayName,
            accountName,
            ownerAccount
        )
        return testCalendarId
    }
    
    /**
     * Advances the mock time by the specified duration
     */
    fun advanceTime(milliseconds: Long) {
        DevLog.info(LOG_TAG, "Advancing time by $milliseconds ms")
        timeProvider.advanceTime(milliseconds)
    }
    
    /**
     * Cleans up all mocks and resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test fixture")
        clearStorages()
        contextProvider.cleanup()
        timeProvider.cleanup()
        calendarProvider.cleanup()
        applicationComponents.cleanup()
        unmockkAll()
    }
    
    /**
     * Builder for BaseCalendarTestFixture
     * Allows flexible configuration of the test fixture
     */
    class Builder {
        var contextProvider: MockContextProvider? = null
            private set
        var timeProvider: MockTimeProvider? = null
            private set
        var calendarProvider: MockCalendarProvider? = null
            private set
        var applicationComponents: MockApplicationComponents? = null 
            private set
            
        fun withContextProvider(provider: MockContextProvider): Builder {
            contextProvider = provider
            return this
        }
        
        fun withTimeProvider(provider: MockTimeProvider): Builder {
            timeProvider = provider
            return this
        }
        
        fun withCalendarProvider(provider: MockCalendarProvider): Builder {
            calendarProvider = provider
            return this
        }
        
        fun withApplicationComponents(components: MockApplicationComponents): Builder {
            applicationComponents = components
            return this
        }
        
        fun build(): BaseCalendarTestFixture {
            return BaseCalendarTestFixture(this)
        }
    }
} 