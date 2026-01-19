//
//   Calendar Notifications Plus
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
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

package com.github.quarck.calnotify.utils

fun String.toLongOrNull(): Long? {

    var ret: Long?

    try {
        ret = this.toLong()
    }
    catch (ex: Exception) {
        ret = null
    }

    return ret;
}

fun String.toIntOrNull(): Int? {

    var ret: Int?

    try {
        ret = this.toInt()
    }
    catch (ex: Exception) {
        ret = null
    }

    return ret;
}

/**
 * Truncates a string to a maximum length, appending ellipsis if truncated.
 * Useful for chip/label display where space is limited.
 * 
 * @param maxLength Maximum length including ellipsis (default 15)
 * @return Original string if <= maxLength, otherwise truncated with "…"
 */
fun String.truncateForChip(maxLength: Int = 15): String {
    return if (length <= maxLength) this else "${take(maxLength - 1)}…"
}
