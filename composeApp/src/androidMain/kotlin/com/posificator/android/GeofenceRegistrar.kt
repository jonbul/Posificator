package com.posificator.android

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.posificator.model.Place
import kotlinx.coroutines.tasks.await

/** Registers saved places with the Android Geofencing API. */
class GeofenceRegistrar(context: Context) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getGeofencingClient(appContext)

    // Geofencing fills in the triggering geofences, so this PendingIntent must be mutable.
    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(appContext, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun sync(places: List<Place>) {
        runCatching { client.removeGeofences(pendingIntent).await() }
        if (places.isEmpty() || !appContext.hasFineLocation() || !appContext.hasBackgroundLocation()) return

        val geofences = places.take(MAX_GEOFENCES).map { place ->
            Geofence.Builder()
                .setRequestId(place.id)
                .setCircularRegion(place.latitude, place.longitude, place.radiusMeters.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        val request = GeofencingRequest.Builder()
            // No initial trigger: saving a place while standing in it shouldn't count as arriving.
            .setInitialTrigger(0)
            .addGeofences(geofences)
            .build()

        runCatching { client.addGeofences(request, pendingIntent).await() }
            .onFailure { Log.w(TAG, "Couldn't register geofences", it) }
    }

    /** Re-registers whatever is saved; used after reboot or an app update. */
    suspend fun syncSaved() {
        sync(placeRepository(appContext).places.value.filter { it.enabled })
    }

    companion object {
        const val MAX_GEOFENCES = 100
        private const val TAG = "GeofenceRegistrar"
    }
}
