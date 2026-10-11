package com.posificator.platform

import com.posificator.model.Place
import kotlinx.coroutines.flow.StateFlow

enum class LocationAccess { NotRequested, Denied, WhileInUse, Always }

data class PermissionStatus(
    val location: LocationAccess,
    val notificationsAllowed: Boolean,
) {
    /** Geofences only fire in the background with "Always" location access and notifications allowed. */
    val readyForGeofencing: Boolean
        get() = location == LocationAccess.Always && notificationsAllowed
}

data class Coordinates(val latitude: Double, val longitude: Double)

/**
 * Everything the shared code needs from the OS: permissions, a one-shot location fix,
 * and native geofencing (Android Geofencing API / iOS CoreLocation region monitoring).
 * Arrival notifications are posted by the platform's own geofence callbacks.
 */
interface LocationPlatform {
    val permissions: StateFlow<PermissionStatus>

    /** Most regions the OS will monitor at once (Android: 100, iOS: 20). */
    val maxGeofences: Int

    /** Walks the user through the next missing permission, or opens system settings if it was denied. */
    fun requestPermissions()

    /** Re-reads permission state, e.g. after returning from system settings. */
    fun refreshPermissions()

    suspend fun currentLocation(): Coordinates?

    /** Replaces the OS-registered geofences with [places]. */
    suspend fun syncGeofences(places: List<Place>)
}
