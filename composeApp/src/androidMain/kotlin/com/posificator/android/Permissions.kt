package com.posificator.android

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal fun Context.isGranted(permission: String) =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** Geofencing requires precise location; coarse alone is rejected by the API. */
internal fun Context.hasFineLocation() = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

internal fun Context.hasBackgroundLocation() =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

internal fun Context.notificationsAllowed(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
        return false
    }
    return getSystemService(NotificationManager::class.java).areNotificationsEnabled()
}
