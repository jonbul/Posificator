package com.posificator.data

/** Minimal persistent string store, backed by SharedPreferences on Android and NSUserDefaults on iOS. */
interface KeyValueStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}
