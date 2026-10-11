package com.posificator.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when a place's date/time window opens while the user is already inside it. */
class WindowStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val placeId = intent.getStringExtra(EXTRA_PLACE_ID) ?: return
        arrivalEngine(context).onWindowStart(placeId)
    }

    companion object {
        const val EXTRA_PLACE_ID = "placeId"
    }
}
