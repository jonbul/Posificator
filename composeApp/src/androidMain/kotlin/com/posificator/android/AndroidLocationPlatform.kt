package com.posificator.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.posificator.model.Place
import com.posificator.platform.Coordinates
import com.posificator.platform.LocationAccess
import com.posificator.platform.LocationPlatform
import com.posificator.platform.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Must be created in [ComponentActivity.onCreate] so the permission launchers can be registered. */
class AndroidLocationPlatform(private val activity: ComponentActivity) : LocationPlatform {

    private val registrar = GeofenceRegistrar(activity)
    private val fusedClient = LocationServices.getFusedLocationProviderClient(activity)

    private var askedForeground = false
    private var askedBackground = false
    private var askedNotifications = false

    private val _permissions = MutableStateFlow(readPermissions())
    override val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    override val maxGeofences = GeofenceRegistrar.MAX_GEOFENCES

    private val foregroundLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissions()
            // Android only lets apps ask for background location after foreground location is granted.
            if (activity.hasFineLocation() && !activity.hasBackgroundLocation()) requestPermissions()
        }

    private val singleLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPermissions()
        }

    override fun requestPermissions() {
        when {
            !activity.hasFineLocation() -> {
                if (askedForeground && !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    openAppSettings()
                } else {
                    askedForeground = true
                    val wanted = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                            askedNotifications = true
                        }
                    }
                    foregroundLauncher.launch(wanted.toTypedArray())
                }
            }

            !activity.hasBackgroundLocation() -> {
                if (askedBackground) {
                    openAppSettings()
                } else {
                    askedBackground = true
                    // On Android 11+ this shows the system page with "Allow all the time".
                    singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }

            !activity.notificationsAllowed() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedNotifications) {
                    askedNotifications = true
                    singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings()
                }
            }
        }
    }

    override fun refreshPermissions() {
        _permissions.value = readPermissions()
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): Coordinates? {
        if (!activity.hasFineLocation() && !activity.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val location = runCatching {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
                ?: fusedClient.lastLocation.await()
        }.getOrNull() ?: return null
        return Coordinates(location.latitude, location.longitude)
    }

    override suspend fun syncGeofences(places: List<Place>) = registrar.sync(places)

    private fun readPermissions(): PermissionStatus {
        val location = when {
            activity.hasFineLocation() && activity.hasBackgroundLocation() -> LocationAccess.Always
            activity.hasFineLocation() -> LocationAccess.WhileInUse
            askedForeground -> LocationAccess.Denied
            else -> LocationAccess.NotRequested
        }
        return PermissionStatus(location, activity.notificationsAllowed())
    }

    private fun openAppSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
        )
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
            )
        } else {
            openAppSettings()
        }
    }
}
