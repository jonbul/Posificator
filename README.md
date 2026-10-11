# Posificator

Get a notification when you arrive at a saved place. Each place has its own radius (100 m to 5 km), adjustable with a slider.

Kotlin Multiplatform + Compose Multiplatform: one shared UI and data layer, with native geofencing on each platform behind the `LocationPlatform` interface.

| | Android | iOS |
|---|---|---|
| Geofencing | Google Play services Geofencing API | CoreLocation region monitoring |
| Notification | `NotificationManager` from a `BroadcastReceiver` | `UNUserNotificationCenter` from the location delegate |
| Storage | SharedPreferences | NSUserDefaults |
| Max watched places | 100 | 20 |

## Layout

```
composeApp/src/
  commonMain/   Place model, PlaceRepository, LocationPlatform interface, Compose UI
  androidMain/  MainActivity, AndroidLocationPlatform, GeofenceRegistrar, receivers, manifest
  iosMain/      AppGraph, IosLocationPlatform, MainViewController
  commonTest/   repository tests
iosApp/         SwiftUI host app (XcodeGen spec)
```

## Run on Android

Open the folder in Android Studio and run the `composeApp` configuration, or:

```
./gradlew :composeApp:installDebug
```

The app asks for location first, then "Allow all the time" (Android 10+ sends you to a system page for that), then notifications. Geofences are re-registered after a reboot or app update.

## Run on iOS (needs a Mac with Xcode)

```
brew install xcodegen
cd iosApp && xcodegen
open iosApp.xcodeproj
```

Pick your signing team in the target's Signing settings, then run. The Xcode build calls Gradle to build the shared framework. Grant "While Using", then "Change to Always Allow", then notifications.

## Testing arrivals

- Android emulator: Extended controls → Location, set a point outside a saved place, then move it inside. Geofence alerts can take a minute or two.
- iOS simulator: Features → Location → Custom Location, then switch to a point inside the radius.
- Shared logic: `./gradlew :composeApp:allTests`

## Notes

- Saving a place while you're already inside it doesn't notify you; you get one on your next arrival.
- Both OSes batch location updates to save battery, so alerts can be delayed, and small radii are less reliable. That's why the minimum is 100 m.
- Places are entered by coordinates or "Use my current location". There's no map picker yet.
