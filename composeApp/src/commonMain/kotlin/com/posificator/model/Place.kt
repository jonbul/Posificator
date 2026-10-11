package com.posificator.model

import kotlinx.serialization.Serializable

/** A saved location the user wants to be notified about when they arrive. */
@Serializable
data class Place(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = DEFAULT_RADIUS_METERS,
    val enabled: Boolean = true,
) {
    companion object {
        /** Both platforms are unreliable below ~100 m, so the slider starts there. */
        const val MIN_RADIUS_METERS = 100.0
        const val MAX_RADIUS_METERS = 5_000.0
        const val DEFAULT_RADIUS_METERS = 200.0
    }
}

fun Place.arrivalTitle(): String = "You've arrived"

fun Place.arrivalMessage(): String = "You're now at $name."
