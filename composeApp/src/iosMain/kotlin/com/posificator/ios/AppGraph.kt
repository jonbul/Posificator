package com.posificator.ios

import com.posificator.data.PlaceRepository

/** Process-wide singletons, shared by the UI and background region callbacks. */
object AppGraph {
    val repository: PlaceRepository by lazy { PlaceRepository(UserDefaultsStorage()) }
    val platform: IosLocationPlatform by lazy { IosLocationPlatform(repository) }

    /** Call from application(_:didFinishLaunchingWithOptions:) so region events are delivered after a background relaunch. */
    fun start() {
        platform.refreshPermissions()
    }
}
