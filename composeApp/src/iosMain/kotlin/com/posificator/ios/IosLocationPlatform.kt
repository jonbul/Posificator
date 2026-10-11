package com.posificator.ios

import com.posificator.data.PlaceRepository
import com.posificator.model.Place
import com.posificator.model.arrivalMessage
import com.posificator.model.arrivalTitle
import com.posificator.model.currentLocalNow
import com.posificator.model.isActiveAt
import com.posificator.platform.Coordinates
import com.posificator.platform.LocationAccess
import com.posificator.platform.LocationPlatform
import com.posificator.platform.PermissionStatus
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLCircularRegion
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLRegion
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject

/**
 * CoreLocation region monitoring. Created once at app launch (see [AppGraph.start]) so that
 * when iOS relaunches the app in the background for a region entry, the delegate is ready.
 */
@OptIn(ExperimentalForeignApi::class)
class IosLocationPlatform(private val repository: PlaceRepository) : LocationPlatform {

    private val manager = CLLocationManager()
    private val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()

    // CLLocationManager holds its delegate weakly, so keep a strong reference here.
    private val delegate = LocationDelegate(this)

    private var notificationStatus = UNAuthorizationStatusNotDetermined
    private var askedForAlways = false
    private var pendingLocation: CompletableDeferred<Coordinates?>? = null

    private val _permissions = MutableStateFlow(readPermissions())
    override val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    override val maxGeofences = MAX_REGIONS

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        refreshPermissions()
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidBecomeActiveNotification,
            null,
            NSOperationQueue.mainQueue,
        ) { _ -> refreshPermissions() }
    }

    override fun requestPermissions() {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
            kCLAuthorizationStatusAuthorizedWhenInUse ->
                // iOS shows the "Change to Always Allow" prompt only once; after that it's a Settings change.
                if (askedForAlways) openSettings() else {
                    askedForAlways = true
                    manager.requestAlwaysAuthorization()
                }
            kCLAuthorizationStatusAuthorizedAlways -> when (notificationStatus) {
                UNAuthorizationStatusNotDetermined ->
                    notificationCenter.requestAuthorizationWithOptions(
                        UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
                    ) { _, _ -> refreshPermissions() }
                UNAuthorizationStatusDenied -> openSettings()
                else -> Unit
            }
            else -> openSettings()
        }
    }

    override fun refreshPermissions() {
        _permissions.value = readPermissions()
        notificationCenter.getNotificationSettingsWithCompletionHandler { settings ->
            notificationStatus = settings?.authorizationStatus ?: UNAuthorizationStatusNotDetermined
            _permissions.value = readPermissions()
        }
    }

    override suspend fun currentLocation(): Coordinates? = withContext(Dispatchers.Main) {
        val status = manager.authorizationStatus
        if (status != kCLAuthorizationStatusAuthorizedWhenInUse && status != kCLAuthorizationStatusAuthorizedAlways) {
            return@withContext null
        }
        val deferred = CompletableDeferred<Coordinates?>()
        pendingLocation?.complete(null)
        pendingLocation = deferred
        manager.requestLocation()
        withTimeoutOrNull(LOCATION_TIMEOUT_MS) { deferred.await() }
    }

    override suspend fun syncGeofences(places: List<Place>) = withContext(Dispatchers.Main) {
        val maxRadius = manager.maximumRegionMonitoringDistance
        val wanted = places.take(MAX_REGIONS).associateBy { it.id }
        val alreadyMonitored = mutableSetOf<String>()

        manager.monitoredRegions.forEach { item ->
            val region = item as? CLCircularRegion ?: return@forEach
            val place = wanted[region.identifier]
            if (place != null && region.matches(place, maxRadius)) {
                alreadyMonitored += place.id
            } else {
                manager.stopMonitoringForRegion(region)
            }
        }

        wanted.values.filterNot { it.id in alreadyMonitored }.forEach { place ->
            val region = CLCircularRegion(
                center = CLLocationCoordinate2DMake(place.latitude, place.longitude),
                radius = place.radiusMeters.coerceAtMost(maxRadius),
                identifier = place.id,
            )
            region.notifyOnEntry = true
            region.notifyOnExit = false
            manager.startMonitoringForRegion(region)
        }
    }

    internal fun onAuthorizationChanged() = refreshPermissions()

    internal fun onLocation(location: CLLocation?) {
        val coordinates = location?.coordinate?.useContents { Coordinates(latitude, longitude) }
        pendingLocation?.complete(coordinates)
        pendingLocation = null
    }

    internal fun onEnteredRegion(identifier: String) {
        val place = repository.find(identifier)?.takeIf { it.enabled && it.isActiveAt(currentLocalNow()) } ?: return
        val content = UNMutableNotificationContent().apply {
            setTitle(place.arrivalTitle())
            setBody(place.arrivalMessage())
            setSound(UNNotificationSound.defaultSound)
        }
        val request = UNNotificationRequest.requestWithIdentifier("arrival-${place.id}", content, null)
        notificationCenter.addNotificationRequest(request, null)
    }

    private fun readPermissions(): PermissionStatus {
        val location = when (manager.authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> LocationAccess.NotRequested
            kCLAuthorizationStatusAuthorizedWhenInUse -> LocationAccess.WhileInUse
            kCLAuthorizationStatusAuthorizedAlways -> LocationAccess.Always
            else -> LocationAccess.Denied
        }
        val notifications = notificationStatus == UNAuthorizationStatusAuthorized ||
            notificationStatus == UNAuthorizationStatusProvisional ||
            notificationStatus == UNAuthorizationStatusEphemeral
        return PermissionStatus(location, notifications)
    }

    private fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    private fun CLCircularRegion.matches(place: Place, maxRadius: Double): Boolean {
        val sameCenter = center.useContents { latitude == place.latitude && longitude == place.longitude }
        return sameCenter && radius == place.radiusMeters.coerceAtMost(maxRadius)
    }

    companion object {
        /** iOS lets an app monitor at most 20 regions. */
        const val MAX_REGIONS = 20
        private const val LOCATION_TIMEOUT_MS = 15_000L
    }
}

@OptIn(ExperimentalForeignApi::class)
private class LocationDelegate(
    private val platform: IosLocationPlatform,
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        platform.onAuthorizationChanged()
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        platform.onLocation(didUpdateLocations.lastOrNull() as? CLLocation)
    }

    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        platform.onLocation(null)
    }

    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didEnterRegion: CLRegion) {
        platform.onEnteredRegion(didEnterRegion.identifier)
    }

    override fun locationManager(manager: CLLocationManager, monitoringDidFailForRegion: CLRegion?, withError: NSError) {
        println("Posificator: monitoring failed for ${monitoringDidFailForRegion?.identifier}: ${withError.localizedDescription}")
    }
}
