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

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

/**
 * Prevents SwipeRefreshLayout from intercepting horizontal swipe gestures.
 * This allows ItemTouchHelper swipe-to-dismiss to work properly alongside pull-to-refresh.
 * 
 * Usage: Call [setup] in onViewCreated to wire up the RecyclerView and SwipeRefreshLayout.
 */
object SwipeRefreshTouchHelper {
    
    /**
     * Sets up touch handling to prevent SwipeRefreshLayout from stealing horizontal swipes.
     * 
     * @param recyclerView The RecyclerView with swipe-to-dismiss
     * @param refreshLayout The SwipeRefreshLayout wrapping the RecyclerView
     */
    fun setup(recyclerView: RecyclerView, refreshLayout: SwipeRefreshLayout) {
        val touchSlop = ViewConfiguration.get(recyclerView.context).scaledTouchSlop
        var startX = 0f
        var startY = 0f
        var directionDecided = false
        
        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        startY = e.y
                        directionDecided = false
                        // Keep refresh enabled initially
                        refreshLayout.isEnabled = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!directionDecided) {
                            val dx = abs(e.x - startX)
                            val dy = abs(e.y - startY)
                            
                            // Once we've moved past touch slop, decide direction
                            val minSlop = touchSlop / 2  // More sensitive than default
                            if (dx > minSlop || dy > minSlop) {
                                directionDecided = true
                                
                                // If horizontal movement is significant, disable pull-to-refresh
                                // Use a 1.5:1 ratio favoring horizontal (more permissive for swipes)
                                if (dx > dy * 0.67f) {
                                    refreshLayout.isEnabled = false
                                    // Also tell parent views not to intercept
                                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Re-enable pull-to-refresh when gesture ends
                        refreshLayout.isEnabled = true
                        directionDecided = false
                    }
                }
                // Don't consume the event - let ItemTouchHelper handle it
                return false
            }
            
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                // Not consuming touch events
            }
            
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                // Not used
            }
        })
    }
}
