package com.github.quarck.calnotify.ui

import android.graphics.Bitmap
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Root
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.UITestFixture
import io.mockk.*
import org.hamcrest.Description
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Espresso UI tests for DismissedEventsActivity.
 * 
 * Tests the dismissed events list display and restore functionality.
 */
@RunWith(AndroidJUnit4::class)
class DismissedEventsActivityTest {
    
    private lateinit var fixture: UITestFixture
    
    /**
     * Takes a screenshot and saves it to external storage for debugging.
     * Uses UiAutomation API which doesn't require external storage permissions.
     */
    private fun takeDebugScreenshot(name: String) {
        try {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val uiAutomation = instrumentation.uiAutomation
            
            val bitmap = uiAutomation.takeScreenshot()
            
            if (bitmap != null) {
                val screenshotDir = File("/sdcard/Pictures/espresso_screenshots")
                screenshotDir.mkdirs()
                
                val screenshotFile = File(screenshotDir, "${name}.png")
                FileOutputStream(screenshotFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                
                DevLog.info(LOG_TAG, "Screenshot saved: ${screenshotFile.absolutePath}")
            } else {
                DevLog.error(LOG_TAG, "Screenshot bitmap was null")
            }
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Failed to save screenshot: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Custom root matcher that finds a suitable root without requiring window focus.
     * Based on: https://github.com/open-tool/ultron/wiki/Avoiding-nontrivial-element-lookup-exceptions
     * 
     * This handles cases where views might be attached to application context or
     * where async operations prevent the window from immediately gaining focus.
     */
    private fun anySuitableRoot(): org.hamcrest.Matcher<Root> {
        return object : TypeSafeMatcher<Root>() {
            override fun describeTo(description: Description) {
                description.appendText("any suitable root (focus not required)")
            }
            override fun matchesSafely(root: Root): Boolean {
                // Accept any root that is not a dialog or popup
                // This allows testing even when window focus is delayed
                return root.decorView != null
            }
        }
    }
    
    @Before
    fun setup() {
        fixture = UITestFixture.create()
        fixture.setup()
    }
    
    @After
    fun cleanup() {
        fixture.cleanup()
    }
    
    // === Activity Launch Tests ===
    
    @Test
    fun dismissedEventsActivity_launches_successfully() {
        val scenario = fixture.launchDismissedEventsActivity()
        
        scenario.onActivity { activity ->
            assert(activity != null)
        }
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsActivity_shows_toolbar() {
        val scenario = fixture.launchDismissedEventsActivity()
        
        onView(withId(R.id.toolbar))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsActivity_has_back_navigation() {
        val scenario = fixture.launchDismissedEventsActivity()
        
        // The activity should have a back button/up navigation
        onView(withContentDescription("Navigate up"))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Empty State Tests ===
    
    @Test
    fun dismissedEventsActivity_shows_recycler_view() {
        val scenario = fixture.launchDismissedEventsActivity()
        
        onView(withId(R.id.list_events))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Dismissed Events Display Tests ===
    
    @Test
    fun dismissedEventsActivity_displays_dismissed_events() {
        // Create dismissed events
        fixture.createDismissedEvent(title = "Dismissed Meeting")
        fixture.createDismissedEvent(title = "Dismissed Appointment")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Give time for data to load
        Thread.sleep(500)
        
        // RecyclerView should be visible
        onView(withId(R.id.list_events))
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun dismissedEventsActivity_displays_event_title() {
        fixture.createDismissedEvent(title = "Important Dismissed Event")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Wait for recycler to be ready
        onView(withId(R.id.list_events))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        
        onView(withText("Important Dismissed Event"))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Click to Show Menu Tests ===
    
    @Test
    fun clicking_dismissed_event_shows_popup_menu() {
        fixture.createDismissedEvent(title = "Clickable Dismissed Event")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Wait for recycler to be ready
        onView(withId(R.id.list_events))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        
        // Click on the event
        onView(withText("Clickable Dismissed Event"))
            .inRoot(anySuitableRoot())
            .perform(click())
        
        // Popup menu with Restore option should appear
        onView(withText(R.string.restore))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Restore Action Tests ===
    
    @Test
    fun clicking_restore_triggers_restore_event() {
        fixture.mockApplicationController()
        every { ApplicationController.restoreEvent(any(), any()) } returns Unit
        
        fixture.createDismissedEvent(title = "Event to Restore")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Wait for recycler to be ready
        onView(withId(R.id.list_events))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        
        // Click on the event
        onView(withText("Event to Restore"))
            .inRoot(anySuitableRoot())
            .perform(click())
        
        // Click Restore in popup menu
        onView(withText(R.string.restore))
            .inRoot(anySuitableRoot())
            .perform(click())
        
        // Verify restore was called
        verify(timeout = 2000) { 
            ApplicationController.restoreEvent(any(), any()) 
        }
        
        scenario.close()
    }
    
    // === Options Menu Tests ===
    
    @Test
    fun dismissedEventsActivity_has_remove_all_option() {
        fixture.createDismissedEvent(title = "Test Event")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Take screenshot before first interaction
        takeDebugScreenshot("dismissed_before_toolbar_check")
        
        // First check that toolbar loaded (forces Espresso to wait)
        try {
            onView(withId(R.id.toolbar))
                .inRoot(anySuitableRoot())
                .check(matches(isDisplayed()))
        } catch (e: Exception) {
            takeDebugScreenshot("dismissed_toolbar_check_failed")
            throw e
        }
        
        takeDebugScreenshot("dismissed_after_toolbar_check")
        
        // Open overflow menu
        try {
            openActionBarOverflowOrOptionsMenu(
                InstrumentationRegistry.getInstrumentation().targetContext
            )
        } catch (e: Exception) {
            takeDebugScreenshot("dismissed_menu_open_failed")
            throw e
        }
        
        takeDebugScreenshot("dismissed_menu_opened")
        
        // Remove All option should be visible
        onView(withText(R.string.remove_all))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    @Test
    fun clicking_remove_all_shows_confirmation_dialog() {
        fixture.createDismissedEvent(title = "Test Event")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Wait for toolbar to be ready
        onView(withId(R.id.toolbar))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        
        // Open overflow menu
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        
        // Click Remove All
        onView(withText(R.string.remove_all))
            .inRoot(anySuitableRoot())
            .perform(click())
        
        // Confirmation dialog should appear
        onView(withText(R.string.remove_all_confirmation))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    // === Multiple Dismissed Events Tests ===
    
    @Test
    fun dismissedEventsActivity_displays_multiple_events() {
        fixture.createDismissedEvent(title = "First Dismissed")
        fixture.createDismissedEvent(title = "Second Dismissed")
        fixture.createDismissedEvent(title = "Third Dismissed")
        
        val scenario = fixture.launchDismissedEventsActivity()
        
        // Wait for recycler to be ready
        onView(withId(R.id.list_events))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        
        // All events should be visible
        onView(withText("First Dismissed"))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        onView(withText("Second Dismissed"))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        onView(withText("Third Dismissed"))
            .inRoot(anySuitableRoot())
            .check(matches(isDisplayed()))
        
        scenario.close()
    }
    
    companion object {
        private const val LOG_TAG = "DismissedEventsActivityTest"
    }
}

