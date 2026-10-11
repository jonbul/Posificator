package com.posificator

import com.posificator.data.KeyValueStorage
import com.posificator.data.PlaceRepository
import com.posificator.model.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceRepositoryTest {
    private class MemoryStorage : KeyValueStorage {
        val values = mutableMapOf<String, String>()
        override fun getString(key: String) = values[key]
        override fun putString(key: String, value: String) { values[key] = value }
    }

    @Test
    fun placesSurviveReload() {
        val storage = MemoryStorage()
        val repo = PlaceRepository(storage)
        repo.upsert(Place("a", "Home", 43.26, -2.93, radiusMeters = 150.0))
        repo.upsert(Place("a", "Home", 43.26, -2.93, radiusMeters = 300.0))
        repo.upsert(Place("b", "Work", 43.30, -2.98))
        repo.delete("b")

        val reloaded = PlaceRepository(storage)
        assertEquals(listOf(Place("a", "Home", 43.26, -2.93, radiusMeters = 300.0)), reloaded.places.value)
        assertNull(reloaded.find("b"))
    }

    @Test
    fun corruptStorageStartsEmpty() {
        val storage = MemoryStorage().apply { values["places"] = "not json" }
        assertEquals(emptyList(), PlaceRepository(storage).places.value)
    }
}
