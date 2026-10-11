package com.posificator.ios

import com.posificator.data.KeyValueStorage
import platform.Foundation.NSUserDefaults

class UserDefaultsStorage : KeyValueStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}
