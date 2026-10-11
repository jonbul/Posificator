package com.posificator.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

/** Receives geofence transitions from Google Play services and hands them to the arrival engine. */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w("GeofenceReceiver", GeofenceStatusCodes.getStatusCodeString(event.errorCode))
            return
        }
        val engine = arrivalEngine(context)
        val ids = event.triggeringGeofences.orEmpty().map { it.requestId }
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> ids.forEach(engine::onEnter)
            Geofence.GEOFENCE_TRANSITION_EXIT -> ids.forEach(engine::onExit)
        }
    }
}
