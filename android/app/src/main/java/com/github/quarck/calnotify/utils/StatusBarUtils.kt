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

package com.github.quarck.calnotify.utils

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.quarck.calnotify.R

/**
 * Sets up the status bar spacer for edge-to-edge display support.
 * Call this after setContentView() in activities that have a status_bar_spacer view.
 * 
 * The spacer view should be positioned at the top of the AppBarLayout or header area,
 * with initial height of 0dp. This function sets its height to match the actual
 * status bar inset on the device.
 */
fun AppCompatActivity.setupStatusBarSpacer() {
    findViewById<View>(R.id.status_bar_spacer)?.let { spacer ->
        ViewCompat.setOnApplyWindowInsetsListener(spacer) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.layoutParams.height = statusBarInset
            view.requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(spacer)
    }
}
