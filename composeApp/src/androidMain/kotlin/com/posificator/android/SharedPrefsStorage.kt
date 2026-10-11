package com.posificator.android

import android.content.Context
import com.posificator.data.KeyValueStorage
import com.posificator.data.PlaceRepository

class SharedPrefsStorage(context: Context) : KeyValueStorage {
    private val prefs = context.applicationContext.getSharedPreferences("posificator", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    // commit() rather than apply(): receivers read this right after the UI writes it.
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }
}

/** A fresh repository reads the latest saved places, which is what the receivers need. */
fun placeRepository(context: Context) = PlaceRepository(SharedPrefsStorage(context))
