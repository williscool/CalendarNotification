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

package com.github.quarck.calnotify.identitystorage

import android.content.Context
import android.os.Build
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.PersistentStorageBase

/**
 * Detects that this app's data arrived from somewhere else.
 *
 * Works by *absence*: the fingerprint is written on first run and deliberately
 * excluded from backup, so a restored database arrives without it. A missing or
 * mismatched value therefore means "this data did not originate here".
 *
 * **The exclusion is what makes this work, and it is easy to get wrong.**
 * `backup_rules.xml` is not read on API 31+; `data_extraction_rules.xml` is,
 * and Android 12 split cloud backup from device-to-device transfer into
 * separate rule sets. The fingerprint must be excluded from *both*, or a
 * restore over the missing path goes undetected and nothing downstream ever
 * runs. See docs/dev_todo/portable_event_identity.md.
 *
 * Note this is a strictly weaker signal than it looks: a matching fingerprint
 * proves the same install, but a mismatch only proves "not the same install" --
 * it does not distinguish a new device from an old backup restored onto this
 * one. Callers that care about that difference must verify separately; see the
 * validation sample in the plan.
 */
class InstallFingerprint(ctx: Context) : PersistentStorageBase(ctx, PREFS_NAME) {

    /** Identifies the install this data was created by. Empty until first run. */
    var value by StringProperty("", PREF_VALUE)

    /**
     * Whether the stored fingerprint matches this install.
     *
     * False on a genuinely fresh install too -- there is nothing stored yet.
     * [markCurrentInstall] resolves both cases identically, so callers should
     * check this before recording, not after.
     */
    fun matchesCurrentInstall(): Boolean {
        val stored = value
        return stored.isNotEmpty() && stored == currentInstall()
    }

    /** Record this install as the origin of the current data. */
    fun markCurrentInstall() {
        val current = currentInstall()
        if (value != current) {
            DevLog.info(LOG_TAG, "Recording install fingerprint")
            value = current
        }
    }

    companion object {
        private const val LOG_TAG = "InstallFingerprint"

        /**
         * Must match the `install_fingerprint.xml` exclusions in BOTH
         * res/xml/data_extraction_rules.xml and res/xml/backup_rules.xml.
         */
        const val PREFS_NAME = "install_fingerprint"

        const val PREF_VALUE = "value"

        /**
         * Identifies this OS installation.
         *
         * `ANDROID_ID` would be the obvious choice but is scoped per app-signing
         * key and survives a restore onto the same device, which is precisely
         * the case we need to tell apart. The build fingerprint changes with
         * the device and with OS updates; an OS update producing a false
         * "restored" reading is acceptable, since the downstream work is
         * idempotent and only re-resolves identity that is already correct.
         */
        private fun currentInstall(): String = Build.FINGERPRINT ?: ""
    }
}
