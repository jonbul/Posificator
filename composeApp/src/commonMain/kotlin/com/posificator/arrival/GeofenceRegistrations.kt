package com.posificator.arrival

import com.posificator.data.KeyValueStorage
import com.posificator.model.Place
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Remembers what was last registered with the OS, so a sync only touches places that changed.
 * Re-registering an unchanged geofence would fire a fresh "enter" for places the user is already in.
 */
class GeofenceRegistrations(private val storage: KeyValueStorage) {

    data class Diff(val removed: Set<String>, val added: List<Place>)

    fun diff(places: List<Place>): Diff {
        val registered = load()
        val wanted = places.associate { it.id to signature(it) }
        val removed = registered.filter { (id, sig) -> wanted[id] != sig }.keys
        val added = places.filter { registered[it.id] != wanted[it.id] }
        return Diff(removed, added)
    }

    fun registered(): Set<String> = load().keys

    fun markRegistered(places: List<Place>) = save(places.associate { it.id to signature(it) })

    fun clear() = save(emptyMap())

    /** Any edit to a place (location, radius or schedule) re-registers it, so saving a place you're in notifies. */
    private fun signature(place: Place) =
        listOf(place.latitude, place.longitude, place.radiusMeters, place.startDate, place.endDate, place.startMinute, place.endMinute)
            .joinToString("|")

    private fun load(): Map<String, String> =
        storage.getString(KEY)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyMap()

    private fun save(map: Map<String, String>) = storage.putString(KEY, json.encodeToString(serializer, map))

    private companion object {
        const val KEY = "geofences.registered"
        val json = Json
        val serializer = MapSerializer(String.serializer(), String.serializer())
    }
}
