//
//   Calendar Notifications Plus
//   Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.ui

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import com.github.quarck.calnotify.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date

/**
 * The About screen shows the build time and git commit, which build.gradle
 * generates as string resources (resValue) rather than BuildConfig fields so
 * they don't defeat the Gradle build cache for the app module.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class AboutActivityRobolectricTest {

    @Test
    fun about_shows_commit_sha_from_generated_resource() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val commitSha = activity.getString(R.string.git_commit_sha)
                val expected = String.format(activity.getString(R.string.commit_sha_string_format), commitSha)

                val shown = activity.findViewById<TextView>(R.id.text_view_app_commit_sha).text.toString()

                assertEquals(expected, shown)
            }
        }
    }

    @Test
    fun about_shows_build_time_from_generated_resource() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val buildTimeMillis = activity.getString(R.string.build_timestamp_millis).toLong()
                assertTrue("build timestamp should be a real time", buildTimeMillis > 0)
                val formattedDate = SimpleDateFormat.getInstance().format(Date(buildTimeMillis))
                val expected = String.format(activity.getString(R.string.build_time_string_format), formattedDate)

                val shown = activity.findViewById<TextView>(R.id.text_view_app_build_time).text.toString()

                assertEquals(expected, shown)
            }
        }
    }
}
