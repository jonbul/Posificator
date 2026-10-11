package com.posificator.android

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.posificator.arrival.GeofenceRegistrations
import com.posificator.model.Place
import com.posificator.model.currentLocalNow
import com.posificator.model.isExpiredAt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** Registers saved places with the Android Geofencing API, touching only the ones that changed. */
class GeofenceRegistrar(context: Context) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getGeofencingClient(appContext)
    private val registrations = GeofenceRegistrations(SharedPrefsStorage(appContext))
    private val engine = arrivalEngine(appContext)

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
    suspend fun sync(places: List<Place>) = mutex.withLock {
        if (!appContext.hasFineLocation() || !appContext.hasBackgroundLocation()) {
            runCatching { client.removeGeofences(pendingIntent).await() }
            registrations.clear()
            engine.forgetAll()
            return@withLock
        }

        val wanted = places.take(MAX_GEOFENCES)
        val diff = registrations.diff(wanted)
        if (diff.removed.isNotEmpty()) {
            runCatching { client.removeGeofences(diff.removed.toList()).await() }
            diff.removed.forEach(engine::forget)
        }
        if (diff.added.isNotEmpty()) {
            val geofences = diff.added.map { place ->
                Geofence.Builder()
                    .setRequestId(place.id)
                    .setCircularRegion(place.latitude, place.longitude, place.radiusMeters.toFloat())
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            }
            val request = GeofencingRequest.Builder()
                // Fire "enter" right away if the user is already inside, e.g. when saving the place they're at.
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build()
            val added = runCatching { client.addGeofences(request, pendingIntent).await() }
                .onFailure { Log.w(TAG, "Couldn't register geofences", it) }
                .isSuccess
            // On failure, leave the failed places unregistered so the next sync retries them.
            registrations.markRegistered(if (added) wanted else wanted - diff.added.toSet())
        } else {
            registrations.markRegistered(wanted)
        }
    }

    /** After a reboot every geofence is gone, so register everything again from scratch. */
    suspend fun reregisterAll() {
        registrations.clear()
        engine.forgetAll()
        val now = currentLocalNow()
        sync(placeRepository(appContext).places.value.filter { it.enabled && !it.isExpiredAt(now) })
    }

    companion object {
        const val MAX_GEOFENCES = 100
        private const val TAG = "GeofenceRegistrar"
        private val mutex = Mutex()
    }
}
