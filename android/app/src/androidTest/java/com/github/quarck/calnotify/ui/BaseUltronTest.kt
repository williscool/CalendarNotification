package com.github.quarck.calnotify.ui

import com.atiurin.ultron.allure.config.UltronAllureConfig
import com.atiurin.ultron.core.config.UltronConfig
import org.junit.BeforeClass

/**
 * Base class for Ultron UI tests with centralized configuration.
 * 
 * All UI test classes should extend this to get:
 * - 15-second operation timeout (faster than default)
 * - Automatic screenshot capture on failures (Allure integration)
 * 
 * Individual test classes can override setConfig() if they need custom settings.
 */
abstract class BaseUltronTest {
    
    companion object {
        @BeforeClass @JvmStatic
        fun setConfig() {
            UltronConfig.apply {
                operationTimeoutMs = 15_000  // 15 seconds instead of 60s default
            }
            UltronAllureConfig.applyRecommended()  // Enable automatic screenshots on failure
        }
    }
}

