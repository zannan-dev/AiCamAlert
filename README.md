# AiCam Alert

Android app that warns drivers in Kerala about nearby **Kerala MVD AI speed cameras**. It shows camera locations on a list and an offline-capable map, and triggers proactive alerts (sound, heads-up notification, full-screen overlay) when you are approaching a camera — both while the app is open and in the background.

> **Dataset:** 704 camera locations bundled in `app/src/main/assets/kerala_ai_cameras.json`, covering 14 districts: Alappuzha, Ernakulam, Idukki, Kannur, Kasaragod, Kollam, Kottayam, Kozhikode, Malappuram, Palakkad, Pathanamthitta, Thiruvananthapuram, Thrissur, Wayanad.

## Features

- **Browse cameras**
  - List view with search (name / district), district filter with counts, distance filter (`< 5/10/25/50 km`), and sort by distance or district.
  - Map view powered by **OSMdroid** (OpenStreetMap) with clustered markers, user location, and tap-to-focus. Tile cache tuned for performance (12 download threads, 500 MB disk cache).
  - Distance to each camera computed on-device via `Location.distanceBetween`, throttled to every 12 s / 100 m in `CameraViewModel:242`.

- **Proactive proximity alerts**
  - Foreground in-app banner (`CameraListScreen:277`) + siren via `AlertSoundManager`.
  - Background via `CameraProximityService:35` — foreground service with lock-screen heads-up notification and full-screen `FullScreenAlertActivity`.
  - **Snooze** — "I HAVE NOTICED" pauses alerts for 1 hour; persisted in `aicam_prefs` and reflected in the foreground notification.
  - Direction-aware: bearing check (`ProximityEngine:72`) suppresses alerts for cameras behind you (90° cone, skipped below ~7 km/h).
  - Speed-aware radius: `500 m @ 0 m/s` → `500 + speed×8s` capped at `1200 m` (`ProximityEngine:62`).

- **Efficient location handling**
  - Spatial grid index (`CameraRepository:17`, `CameraItem:18`) — ~1.1 km cells (0.01°), proximity checks scan only the 9 adjacent cells (~5–15 cameras instead of 704).
  - OS-level **geofencing** (`CameraGeofenceManager:26`, `CameraClusterer:134`) clusters cameras into ≤100 geofences (each ≥3 km radius + 2.5 km buffer). Entering a cluster starts the active radar; exiting stops it — no continuous GPS needed while outside clusters.
  - Shared `AppLocationManager` / `CameraRepository` / `ProximityEngine` via `AiCamApplication` — no duplicate GPS or JSON parsing between UI and service.

- **Background Radar toggle**
  - Handles the full Android permission chain: foreground location → background location (`Allow all the time`) → `POST_NOTIFICATIONS` (Android 13+) → optional overlay & battery-optimization exemption cards.
  - Auto-disables radar if required permissions are revoked (`CameraViewModel:121`).

- **UX**
  - Material 3, Jetpack Compose, dark/light theme toggle.
  - Location-disabled dialog, permission guidance dialogs, empty-state handling.

## Tech Stack

| Layer | Library |
|-------|---------|
| Language | Kotlin 2.4.10 |
| UI | Jetpack Compose (BOM 2026.06.01), Material 3, Material Icons |
| Architecture | MVVM — `CameraViewModel:31` + `StateFlow` + `combine` |
| Location | `com.google.android.gms:play-services-location:21.3.0`, `androidx.lifecycle:lifecycle-process` for foreground tracking |
| Map | `org.osmdroid:osmdroid-android:6.1.20` |
| Async | `kotlinx-coroutines-android:1.10.2` |
| Build | AGP 9.3.1, Gradle 8.x (configuration cache enabled), `compileSdk 37`, `minSdk 24`, `targetSdk 37`, Java 11 |

## Project Structure

```
AiCamAlert/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/kerala_ai_cameras.json   # 704 cameras
│       ├── java/com/example/aicamalert/
│       │   ├── AiCamApplication.kt          # Singletons: repo, location, geofence, foreground tracker
│       │   ├── MainActivity.kt              # OSMdroid config + theme + CameraListScreen entry
│       │   ├── CameraListScreen.kt          # Main orchestration composable (list ↔ map + alert overlay)
│       │   ├── CameraProximityService.kt    # Foreground service — background alerts
│       │   ├── FullScreenAlertActivity.kt   # Lock-screen full-screen warning
│       │   ├── CameraGeofenceReceiver.kt    # Geofence enter/exit → start/stop radar
│       │   ├── SnoozeAlertReceiver.kt       # Snooze / Resume actions
│       │   ├── data/
│       │   │   ├── CameraRepository.kt      # JSON parsing + spatial grid + findNearestCamera
│       │   │   └── model/CameraItem.kt      # gridKey spatial hash
│       │   ├── location/
│       │   │   ├── AppLocationManager.kt
│       │   │   ├── AppForegroundTracker.kt
│       │   │   ├── CameraGeofenceManager.kt / CameraClusterer
│       │   │   └── ProximityEngine.kt       # dynamic radius + bearing check
│       │   ├── alert/AlertSoundManager.kt
│       │   ├── viewmodel/CameraViewModel.kt
│       │   ├── ui/components/               # CameraCard, CameraMapView, SearchAndFilters, HeaderSection, LocationStatusBar
│       │   ├── ui/theme/
│       │   └── util/PermissionUtils.kt
│       └── res/                             # strings, themes, adaptive icons
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Permissions

Declared in `app/src/main/AndroidManifest.xml:5`:

```xml
ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION
FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION
POST_NOTIFICATIONS, USE_FULL_SCREEN_INTENT
WAKE_LOCK, SYSTEM_ALERT_WINDOW, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
INTERNET, ACCESS_NETWORK_STATE   <!-- OSMdroid tile loading -->
```

Foreground service type is `location` (`AndroidManifest.xml:33`).

## Getting Started

### Prerequisites

- Android Studio Ladybug or newer (AGP 9.3.1)
- JDK 11+
- Android SDK with `compileSdk 37` installed
- Device/emulator running Android 7.0+ (API 24+)

### Setup & Run

```bash
# Clone
git clone <repo-url>
cd AiCamAlert

# Open in Android Studio or build from CLI
./gradlew assembleDebug

# Install on connected device / emulator
./gradlew installDebug
# or
adb install app/build/outputs/apk/debug/app-debug.apk
```

No API keys required — OSMdroid uses OpenStreetMap tiles with user-agent `AiCamAlertProject/<packageName>` set in `MainActivity.kt:27`.

### Filtering / Dataset

To update camera data, replace `app/src/main/assets/kerala_ai_cameras.json` (array of `{name, district, latitude, longitude}`). The spatial grid and geofence clusters rebuild automatically on next launch.

## How It Works

1. **Load:** `CameraRepository:34` parses the bundled JSON on `Dispatchers.IO` and builds a `HashMap<Long, List<CameraItem>>` grid.
2. **Locate:** `AppLocationManager` emits `StateFlow<Location?>` — foreground updates when the app is visible, continuous updates only when radar geofences are active.
3. **Geofence:** `CameraGeofenceManager:49` groups cameras into ~0.2° cells (`CameraClusterer:134`), expanding the cell size until ≤100 geofences. OS wakes the app on enter/exit.
4. **Alert:** `ProximityEngine:33` queries the 3×3 grid neighbourhood, picks the nearest camera within the dynamic radius, checks bearing, and triggers sound + notification + full-screen intent (background) or in-app banner (foreground).

## Build Variants

- `debug` — includes Compose tooling and test manifest.
- `release` — optimization disabled (`app/build.gradle.kts:22`); configure signing as needed.

```bash
./gradlew assembleRelease
./gradlew test
./gradlew connectedAndroidTest
```

## License

No license file currently declared. Add a `LICENSE` if you intend to open-source this project.
