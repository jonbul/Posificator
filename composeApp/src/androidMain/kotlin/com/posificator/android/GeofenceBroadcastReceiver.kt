package com.posificator.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.posificator.model.currentLocalNow
import com.posificator.model.isActiveAt

/** Receives geofence transitions from Google Play services and posts the arrival notification. */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w("GeofenceReceiver", GeofenceStatusCodes.getStatusCodeString(event.errorCode))
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val repository = placeRepository(context)
        val now = currentLocalNow()
        event.triggeringGeofences.orEmpty()
            .mapNotNull { repository.find(it.requestId) }
            .filter { it.enabled && it.isActiveAt(now) }
            .forEach { ArrivalNotifications.show(context, it) }
    }
}
