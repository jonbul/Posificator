package com.posificator.data

import com.posificator.model.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

class PlaceRepository(private val storage: KeyValueStorage) {

    private val _places = MutableStateFlow(load())
    val places: StateFlow<List<Place>> = _places.asStateFlow()

    fun find(id: String): Place? = _places.value.firstOrNull { it.id == id }

    fun upsert(place: Place) {
        _places.update { current ->
            if (current.any { it.id == place.id }) {
                current.map { if (it.id == place.id) place else it }
            } else {
                current + place
            }
        }
        save()
    }

    fun delete(id: String) {
        _places.update { current -> current.filterNot { it.id == id } }
        save()
    }

    private fun load(): List<Place> {
        val raw = storage.getString(KEY) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    private fun save() {
        storage.putString(KEY, json.encodeToString(serializer, _places.value))
    }

    companion object {
        private const val KEY = "places"
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(Place.serializer())

        fun newId(): String = Random.nextLong().toULong().toString(16)
    }
}
