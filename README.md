# AiCam Alert

Android app that warns drivers in Kerala about nearby **Kerala MVD AI speed cameras**. It shows camera locations on a list and an online map with a local tile cache, and triggers proactive alerts (sound, heads-up notification, full-screen overlay) when you are approaching a camera — both while the app is open and in the background.

> **Dataset:** 704 camera locations bundled in `app/src/main/assets/kerala_ai_cameras.json`, covering 14 districts: Alappuzha, Ernakulam, Idukki, Kannur, Kasaragod, Kollam, Kottayam, Kozhikode, Malappuram, Palakkad, Pathanamthitta, Thiruvananthapuram, Thrissur, Wayanad.

## Features

- **Browse cameras**
  - List view ordered nearest first, with a floating bottom search button that expands into search (name / district), district filters with counts, and distance filters (`< 5/10/25/50 km`).
  - Map view powered by **OSMdroid** (OpenStreetMap) with individual camera markers, user location, and tap-to-focus. Both themes use OpenStreetMap tiles with an app-identifying User-Agent; dark mode uses a night filter with charcoal land, blue water, green vegetation, and softer light labels. The tile source has a separate cache from previously blocked tiles, with 2 download threads and a 500 MB disk limit. Geofence regions are clustered; map markers are not.
  - Distance to each camera computed on-device via `Location.distanceBetween`, throttled to every 12 s / 100 m in `CameraViewModel:242`.

- **Proactive proximity alerts**
  - A bundled two-tone siren and vibration repeat until acknowledged. Audio runs on the alarm stream through `MediaPlayer` owned by the radar foreground service. The warning stays visible after passing the camera.
  - Tap **Stop alarm** in the in-app/full-screen warning or its ongoing notification. Acknowledgement stops sound and vibration without disabling other camera alerts; turning off radar also stops playback.
  - No global sound cooldown: the next camera can alert immediately after acknowledgement. Pending alarms are restored if the radar service is recreated; transient system audio interruptions can pause playback.
  - Background via `CameraProximityService:35` — foreground service with lock-screen heads-up notification and full-screen `FullScreenAlertActivity`.
  - Each camera alerts once per approach, after movement is confirmed from fresh GPS fixes (including slow approaches). It re-arms after the user travels more than 1.4 km away (outside the largest alert radius); acknowledging the banner does not silence other cameras.
  - Path-aware: requires the camera ahead within 30° of travel, within 35 m of the projected path (plus up to 25 m GPS tolerance), and at least 3 m closer after confirmed movement. Slow or unreliable GPS headings use movement-derived direction. This reduces nearby-road alerts; exact road matching is not available because the camera dataset has no road geometry.
  - Speed-aware radius: `500 m @ 0 m/s` → `500 + speed×8s` capped at `1200 m` (`ProximityEngine:62`).

- **Efficient location handling**
  - Spatial grid index (`CameraRepository:17`, `CameraItem:18`) — ~1.1 km cells (0.01°), proximity checks scan only cells covering the requested radius (including grid boundaries).
  - OS-level **geofencing** (`CameraGeofenceManager:26`, `CameraClusterer:134`) clusters cameras into ≤100 geofences (each ≥3 km radius + 2.5 km buffer). Entering a cluster starts GPS tracking; exiting pauses GPS. The user-started foreground service stays ready for background alarm audio, including while outside a zone.
  - Shared `AppLocationManager` / `CameraRepository` / `ProximityEngine` via `AiCamApplication` — no duplicate GPS or JSON parsing between UI and service.

- **Required startup setup**
  - Entry is blocked until precise and background location, notifications (including the alarm channel), device location, full-screen alerts, display-over-apps access, and battery optimisation exemption are enabled.
  - One guided action at a time opens Android's permission dialog or the relevant app settings. Permanently denied permissions route to Settings, unsupported settings routes fall back to app details, and grants are checked again on resume.
  - "Finish setup & enable alerts" enables radar. Actual grants are checked on every launch, so a saved setup flag cannot bypass revoked access. Android versions without a special grant treat it as already available.

- **Background Radar toggle**
  - Restores geofences after reboot or app update when radar was enabled and its required permissions remain granted.
  - Handles the full Android permission chain: foreground location → background location (`Allow all the time`) → `POST_NOTIFICATIONS` (Android 13+) → optional overlay & battery-optimization exemption cards.
  - Auto-disables radar if required permissions are revoked (`CameraViewModel:121`).

- **UX**
  - Material 3 with black/white backgrounds, neutral theme-aware text and surfaces, blue primary controls, automatic system light/dark appearance, and a minimal blue video-camera launcher icon on black with a white lens and themed-icon support.
  - Camera-alert switch in the app bar and floating search with expandable filters in the list view. Tapping a map camera opens a theme-aware details card with its name, district, live distance, recenter control, and directions.
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
│       │   ├── CameraGeofenceReceiver.kt    # Geofence enter/exit → start/pause GPS
│       │   ├── data/
│       │   │   ├── CameraRepository.kt      # JSON parsing + spatial grid + findNearestCamera
│       │   │   └── model/CameraItem.kt      # gridKey spatial hash
│       │   ├── location/
│       │   │   ├── AppLocationManager.kt
│       │   │   ├── AppForegroundTracker.kt
│       │   │   ├── CameraGeofenceManager.kt / CameraClusterer
│       │   │   ├── ProximityEngine.kt       # dynamic radius + bearing check
│       │   │   └── CameraAlertGate.kt       # movement validation + once-per-approach alerts
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
FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION, FOREGROUND_SERVICE_MEDIA_PLAYBACK
POST_NOTIFICATIONS, USE_FULL_SCREEN_INTENT, RECEIVE_BOOT_COMPLETED, VIBRATE
WAKE_LOCK, SYSTEM_ALERT_WINDOW, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
INTERNET, ACCESS_NETWORK_STATE   <!-- OSMdroid tile loading -->
```

Foreground service types are `location|mediaPlayback` (`AndroidManifest.xml:33`).

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

No API keys required. The map tile source sends an identifiable `AiCamAlert/<version>` User-Agent and shows OpenStreetMap attribution.

### Filtering / Dataset

To update camera data, replace `app/src/main/assets/kerala_ai_cameras.json` (array of `{name, district, latitude, longitude}`). The spatial grid and geofence clusters rebuild automatically on next launch.

## How It Works

1. **Load:** `CameraRepository:34` parses the bundled JSON on `Dispatchers.IO` and builds a `HashMap<Long, List<CameraItem>>` grid.
2. **Locate:** `AppLocationManager` emits `StateFlow<Location?>` — high-accuracy updates when the app is visible or radar is active.
3. **Geofence:** `CameraGeofenceManager:49` groups cameras into ~0.2° cells (`CameraClusterer:134`), expanding the cell size until ≤100 geofences. OS wakes the app on enter/exit.
4. **Alert:** `CameraAlertGate` confirms movement, then `ProximityEngine` queries nearby cameras, filters by projected path and decreasing distance, and picks the nearest qualifying camera within the dynamic radius. Each camera is alerted once until the user exits its zone. Background alerts use sound, notification, and a full-screen intent; foreground alerts use the existing in-app banner.

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
