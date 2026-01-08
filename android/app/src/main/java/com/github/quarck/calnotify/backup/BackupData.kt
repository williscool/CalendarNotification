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

package com.github.quarck.calnotify.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Root backup data structure.
 * Version field allows future migrations if the schema changes.
 */
@Serializable
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val appVersionCode: Long,
    val appVersionName: String,
    /** Main app settings from default SharedPreferences */
    val settings: Map<String, JsonElement>,
    /** Car mode Bluetooth device settings */
    val carModeSettings: Map<String, JsonElement>
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

