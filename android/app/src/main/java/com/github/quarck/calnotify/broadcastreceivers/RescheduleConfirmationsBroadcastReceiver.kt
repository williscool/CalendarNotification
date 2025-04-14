package com.github.quarck.calnotify.broadcastreceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.logs.DevLog

class RescheduleConfirmationsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            DevLog.error(LOG_TAG, "either context or intent is null!!")
            return
        }

        val value = intent.getStringExtra("reschedule_confirmations")
        if (value != null) {
            ApplicationController.onReceivedRescheduleConfirmations(context, value)
        } else {
            DevLog.error(LOG_TAG, "No reschedule confirmations data received")
        }
    }

    companion object {
        private const val LOG_TAG = "BroadcastReceiverRescheduleConfirmations"
    }
} 