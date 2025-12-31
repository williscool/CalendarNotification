package com.github.quarck.calnotify.ui

import com.atiurin.ultron.allure.config.UltronAllureConfig
import com.atiurin.ultron.core.config.UltronConfig
import org.junit.BeforeClass

/**
 * Base class for Ultron UI tests with centralized configuration.
 * 
 * All UI test classes should extend this to get:
 * - 5-second operation timeout (fast, assumes IdlingResource handles async waits)
 * - Automatic screenshot capture on failures (Allure integration)
 * 
 * For tests using waitForAsyncTasks=true in UITestFixture, the IdlingResource
 * handles synchronization with background operations, so Ultron doesn't need
 * to poll for long periods.
 * 
 * Individual test classes can override setConfig() if they need custom settings.
 */
abstract class BaseUltronTest {
    
    companion object {
        /**
         * Default timeout for Ultron operations.
         * With IdlingResource synchronization, 5 seconds is plenty.
         * Tests without IdlingResource may need longer timeouts.
         */
        const val DEFAULT_TIMEOUT_MS = 5_000L
        
        /**
         * Longer timeout for tests without IdlingResource.
         * Use sparingly - prefer IdlingResource for proper synchronization.
         */
        const val EXTENDED_TIMEOUT_MS = 15_000L
        
        @BeforeClass @JvmStatic
        fun setConfig() {
            UltronConfig.apply {
                operationTimeoutMs = DEFAULT_TIMEOUT_MS
            }
            UltronAllureConfig.applyRecommended()  // Enable automatic screenshots on failure
        }
    }
}

