package com.posificator

import com.posificator.arrival.ArrivalEffects
import com.posificator.arrival.ArrivalEngine
import com.posificator.arrival.GeofenceRegistrations
import com.posificator.data.KeyValueStorage
import com.posificator.data.PlaceRepository
import com.posificator.model.LocalNow
import com.posificator.model.Place
import com.posificator.model.nextActivation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArrivalEngineTest {
    private class MemoryStorage : KeyValueStorage {
        val values = mutableMapOf<String, String>()
        override fun getString(key: String) = values[key]
        override fun putString(key: String, value: String) { values[key] = value }
    }

    private class RecordingEffects : ArrivalEffects {
        val log = mutableListOf<String>()
        override fun notifyArrival(place: Place) { log += "notify ${place.id}" }
        override fun scheduleWindowStart(place: Place, epochMillis: Long) { log += "schedule ${place.id} $epochMillis" }
        override fun cancelWindowStart(placeId: String) { log += "cancel $placeId" }
    }

    private val storage = MemoryStorage()
    private val repo = PlaceRepository(storage)
    private val effects = RecordingEffects()
    private var now = LocalNow(20261011, 21 * 60)
    // Encode the local time directly so the test can read it back.
    private val engine = ArrivalEngine(storage, repo, effects, { now }, { it.date * 10_000L + it.minuteOfDay })

    @Test
    fun notifiesOncePerVisit() {
        repo.upsert(Place("home", "Home", 0.0, 0.0))
        engine.onEnter("home")
        engine.onEnter("home") // e.g. a re-sent initial trigger
        assertEquals(listOf("cancel home", "notify home"), effects.log)

        effects.log.clear()
        engine.onExit("home")
        engine.onEnter("home")
        assertEquals(listOf("cancel home", "cancel home", "notify home"), effects.log)
    }

    @Test
    fun arrivingBeforeTheWindowNotifiesWhenItOpens() {
        repo.upsert(Place("gym", "Gym", 0.0, 0.0, startMinute = 21 * 60 + 30, endMinute = 23 * 60))
        engine.onEnter("gym")
        assertEquals(listOf("schedule gym ${20261011 * 10_000L + 21 * 60 + 30}"), effects.log)

        effects.log.clear()
        now = LocalNow(20261011, 21 * 60 + 30)
        engine.onWindowStart("gym")
        assertEquals(listOf("cancel gym", "notify gym"), effects.log)
    }

    @Test
    fun windowStartAfterLeavingDoesNothing() {
        repo.upsert(Place("gym", "Gym", 0.0, 0.0, startMinute = 22 * 60, endMinute = 23 * 60))
        engine.onEnter("gym")
        engine.onExit("gym")
        effects.log.clear()
        now = LocalNow(20261011, 22 * 60)
        engine.onWindowStart("gym")
        assertTrue(effects.log.isEmpty())
    }

    @Test
    fun nextActivationHandlesDatesAndHours() {
        val hours = Place("p", "P", 0.0, 0.0, startMinute = 8 * 60, endMinute = 9 * 60)
        assertEquals(LocalNow(20261012, 480), hours.nextActivation(LocalNow(20261011, 600)))
        assertEquals(LocalNow(20261011, 480), hours.nextActivation(LocalNow(20261011, 300)))
        val dates = Place("p", "P", 0.0, 0.0, startDate = 20261101, endDate = 20261102)
        assertEquals(LocalNow(20261101, 0), dates.nextActivation(LocalNow(20261011, 600)))
        assertNull(dates.nextActivation(LocalNow(20261103, 0)))
        assertEquals(LocalNow(20270101, 0), dates.copy(startDate = 20270101, endDate = null).nextActivation(LocalNow(20261231, 600)))
    }

    @Test
    fun registrationsOnlyReportChangedPlaces() {
        val regs = GeofenceRegistrations(storage)
        val a = Place("a", "A", 1.0, 1.0)
        val b = Place("b", "B", 2.0, 2.0)
        assertEquals(listOf(a, b), regs.diff(listOf(a, b)).added)
        regs.markRegistered(listOf(a, b))
        val a2 = a.copy(radiusMeters = 500.0)
        val diff = regs.diff(listOf(a2, b.copy(name = "Renamed")))
        assertEquals(setOf("a"), diff.removed)
        assertEquals(listOf(a2), diff.added)
        assertEquals(setOf("b"), regs.diff(listOf(a)).removed)
    }
}
