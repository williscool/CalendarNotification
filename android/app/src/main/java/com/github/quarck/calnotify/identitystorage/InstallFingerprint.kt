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
 * Remembers which OS installation this app's data was created by.
 *
 * ## What problem this solves
 *
 * The events database stores Calendar Provider row ids (`cid`, `id`). Those
 * numbers are assigned by whichever device inserted the row, so a database
 * restored onto a different phone contains ids that belong to a provider that
 * no longer exists. Identity capture reads the provider *by* those ids, so it
 * has to know whether they are still ours before trusting what comes back.
 *
 * ## How it works: detection by absence
 *
 * The fingerprint is written once the ids have been verified, and is
 * deliberately **excluded from backup**. A restored database therefore arrives
 * without it. There is nothing clever here -- the signal is simply that a value
 * we always write is missing, which can only happen if this data came from
 * elsewhere (or if this is a first run).
 *
 * The exclusion is the whole mechanism, and it is easy to break silently:
 *
 * - `backup_rules.xml` is **not read on API 31+**. `data_extraction_rules.xml`
 *   is. Both must carry the exclusion, or it is ignored on modern devices.
 * - Android 12 split cloud backup from device-to-device transfer into
 *   **independent** rule sets. Excluding from `<cloud-backup>` alone leaves the
 *   cable-transfer path wide open.
 *
 * Get either wrong and the fingerprint is restored along with everything else,
 * always matches, and no restore is ever detected. Nothing fails loudly; the
 * feature just never runs. That is why this is verified with an actual
 * backup/restore cycle (`scripts/test_cloud_backup.sh`) rather than by reading
 * the XML.
 *
 * ## Why `Build.FINGERPRINT`
 *
 * `ANDROID_ID` is the more obvious identifier but survives a restore onto the
 * same device, which is precisely the case that needs distinguishing.
 * `Build.FINGERPRINT` identifies the OS build, so it changes when the device
 * does. It also changes on an OS update, which reads as a false "restored" --
 * acceptable, because the only consequence is re-verifying ids that were
 * already correct.
 *
 * ## What this does NOT tell you
 *
 * A match proves the same install, so the stored ids are ours. A **mismatch
 * proves only "not this install"** -- it cannot distinguish a new device from
 * an old backup restored here, nor from an existing user upgrading to the
 * version that introduced this file. Treating a mismatch as "restored" would
 * disable capture for every existing user, so callers must verify separately;
 * see `storedIdsBelongToThisProvider`, which samples real events against the
 * provider and uses this only as a fast path.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
open class InstallFingerprint(ctx: Context) : PersistentStorageBase(ctx, PREFS_NAME) {

    /** Identifies the install this data was created by. Empty until first run. */
    var value by StringProperty("", PREF_VALUE)

    /**
     * Whether the stored fingerprint matches this install.
     *
     * False on a genuinely fresh install too, since nothing is stored yet --
     * so this alone cannot tell a first run from a restore. Callers separate
     * the two by looking at whether any data already exists: only a restore
     * arrives with events in the database.
     */
    open fun matchesCurrentInstall(): Boolean {
        val stored = value
        return stored.isNotEmpty() && stored == currentInstall()
    }

    /** Record this install as the origin of the current data. */
    open fun markCurrentInstall() {
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
