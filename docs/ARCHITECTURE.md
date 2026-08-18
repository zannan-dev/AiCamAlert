# Architecture Overview

> Design notes for the `dev` branch — background radar and proximity system.

## Spatial Grid Index
- `CameraRepository` builds a `HashMap<Long, List<CameraItem>>` with ~1.1 km cells (0.01°).
- Proximity checks scan only 3×3 neighbourhood → ~5–15 candidates vs 704 linear scan.
- `CameraItem.gridKey` packs lat/lon buckets into a single Long.

## Geofence Clustering
- `CameraClusterer` groups 704 cameras into ≤100 OS geofences (0.2° cells, grows until limit).
- Each cluster radius = furthest camera + 2.5 km buffer, min 3 km.
- `CameraGeofenceManager` registers with `GeofencingClient`; enter/exit wakes radar service.

## Proximity Engine
- Dynamic radius: `500 + speed*8s` capped at 1200 m.
- Bearing check: 90° cone, skipped below 2 m/s.

## Relevant sources
- `data/CameraRepository.kt`
- `location/CameraGeofenceManager.kt`
- `location/ProximityEngine.kt`
- `CameraProximityService.kt`
- `viewmodel/CameraViewModel.kt`
