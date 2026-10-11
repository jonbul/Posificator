package com.posificator.arrival

import com.posificator.data.KeyValueStorage
import com.posificator.data.PlaceRepository
import com.posificator.model.LocalNow
import com.posificator.model.Place
import com.posificator.model.currentLocalNow
import com.posificator.model.epochMillisAt
import com.posificator.model.isActiveAt
import com.posificator.model.nextActivation

/** What the engine asks the platform to do. */
interface ArrivalEffects {
    fun notifyArrival(place: Place)

    /**
     * The user is inside [place] but outside its date/time window. At [epochMillis] the window opens:
     * Android re-checks with an alarm; iOS schedules the notification itself for that moment.
     */
    fun scheduleWindowStart(place: Place, epochMillis: Long)

    fun cancelWindowStart(placeId: String)
}

/**
 * Decides when to notify, from geofence enter/exit events. It remembers which places the user is
 * inside and which of those visits already notified, so a visit notifies once: on arrival if the
 * window is open, otherwise when the window opens while the user is still there.
 *
 * State lives in [storage] because Android handles each event in a fresh process.
 */
class ArrivalEngine(
    private val storage: KeyValueStorage,
    private val repository: PlaceRepository,
    private val effects: ArrivalEffects,
    private val now: () -> LocalNow = ::currentLocalNow,
    private val toEpochMillis: (LocalNow) -> Long = ::epochMillisAt,
) {
    fun onEnter(placeId: String) {
        val inside = load(INSIDE)
        if (placeId in inside) return
        save(INSIDE, inside + placeId)
        evaluate(placeId)
    }

    fun onExit(placeId: String) {
        forget(placeId)
    }

    /** The scheduled window start has arrived (Android alarm). */
    fun onWindowStart(placeId: String) {
        if (placeId in load(INSIDE)) evaluate(placeId)
    }

    /** The place's geofence is being replaced, so its next enter event starts a fresh visit. */
    fun forget(placeId: String) {
        save(INSIDE, load(INSIDE) - placeId)
        save(NOTIFIED, load(NOTIFIED) - placeId)
        effects.cancelWindowStart(placeId)
    }

    fun forgetAll() {
        load(INSIDE).forEach { effects.cancelWindowStart(it) }
        save(INSIDE, emptySet())
        save(NOTIFIED, emptySet())
    }

    private fun evaluate(placeId: String) {
        val place = repository.find(placeId)?.takeIf { it.enabled } ?: return
        if (placeId in load(NOTIFIED)) return
        val current = now()
        if (place.isActiveAt(current)) {
            save(NOTIFIED, load(NOTIFIED) + placeId)
            effects.cancelWindowStart(placeId)
            effects.notifyArrival(place)
        } else {
            place.nextActivation(current)?.let { effects.scheduleWindowStart(place, toEpochMillis(it)) }
        }
    }

    private fun load(key: String): Set<String> =
        storage.getString(key)?.split(',')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    private fun save(key: String, ids: Set<String>) = storage.putString(key, ids.joinToString(","))

    private companion object {
        const val INSIDE = "arrival.inside"
        const val NOTIFIED = "arrival.notified"
    }
}
