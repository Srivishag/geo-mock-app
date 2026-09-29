# GeoMock — GPS Mock Location Provider 📍

[![Android](https://img.shields.io/badge/Platform-Android%207.0%2B%20%28API%2024%2B%29-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![OpenStreetMap](https://img.shields.io/badge/Map-osmdroid%20%28OpenStreetMap%29-7EBC6F?logo=openstreetmap&logoColor=white)](https://osmdroid.github.io/osmdroid/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**GeoMock** is a modern, developer-friendly Android application designed for testing location-dependent apps on physical devices. It leverages Android's official `LocationManager` test-provider APIs to broadcast high-fidelity synthetic GPS and network coordinates system-wide—**without requiring root, system hacks, VPN manipulation, or commercial API keys.**

---

## 🌟 Key Features

### 1. 🗺️ OpenStreetMap Interactive Location Picker
- Built using **osmdroid** with multi-CDN tile caching (no Google Maps API keys required).
- Tap or drag anywhere on the map to pin exact coordinates.
- Live coordinate readout chip and smooth camera transitions.

### 2. 🎯 Real Device GPS & "Locate Me"
- Dedicated **"Locate Me"** crosshair action to immediately center the map on your physical position.
- Dual visual pins: distinctive **Target Mock Pin** and a blue **Real GPS Location Indicator**.
- One-tap **"Use Real GPS"** shortcut to quickly base mock testing on your current location.

### 3. 🔍 Smart Address Search & Raw Coordinate Parsing
- Search cities, landmarks, and addresses worldwide via OpenStreetMap Nominatim geocoding.
- Smart clipboard / coordinate parsing engine supporting multiple formats:
  - Decimal degrees: `13.0827, 80.2707` or `13.0827 80.2707`
  - Signed / Directional coordinates: `37.7749° N, 122.4194° W`

### 4. 🔖 Clean Location Bookmarks & Landmark Presets
- Save frequently tested spots with custom names, category icons (🏠, 🏢, 🧪, 🏖️, etc.), and notes.
- Horizontal carousel and list management with active bookmark indicators and one-tap selection.
- Pre-loaded starter landmarks (Times Square NYC, Eiffel Tower, Shibuya Crossing, Golden Gate Bridge, Marina Bay, Big Ben).

### 5. 🛡️ Multi-Provider Anti-Rubberbanding (Anti-Jitter)
- Simulates across all Android location providers simultaneously:
  - `LocationManager.GPS_PROVIDER`
  - `LocationManager.NETWORK_PROVIDER`
  - `LocationManager.PASSIVE_PROVIDER`
  - `LocationManager.FUSED_PROVIDER` (`"fused"`)
- High-frequency telemetry injection (**2 Hz / 500ms**) with complete realistic attributes (altitude, vertical accuracy, bearing, speed accuracy, `isMock` flags) to prevent the system from alternating between real and mock coordinates.

### 6. 🚀 Background Foreground Service
- Runs as an official Android `location` foreground service with persistent status notifications.
- Remains active even when the app is minimized or the screen is turned off.

---

## 📱 Developer Options Setup (Physical Devices)

To allow GeoMock to mock locations on an Android device:

1. **Enable Developer Options**:
   - Go to **Settings** → **About phone**.
   - Tap **Build number** 7 times until you see *"You are now a developer!"*.
2. **Select Mock Location App**:
   - Go to **Settings** → **System** (or **Additional settings**) → **Developer options**.
   - Scroll down to the **Debugging** section and tap **Select mock location app**.
   - Choose **GeoMock - GPS Spoofer**.

---

## 🛠️ Tech Stack & Architecture

```
com.example.locationspoofer
├── data/
│   ├── LocationRepository.kt       # Persistent JSON SharedPreferences bookmark storage
│   └── SavedLocation.kt            # Bookmark data model
├── location/
│   ├── CoordinateParser.kt         # Regex-based multi-format lat/lon parser
│   ├── DeviceLocationHelper.kt     # Safe device GPS/Network query helper
│   ├── LocationSearchManager.kt    # OSM Nominatim geocoding integration
│   ├── LocationValidator.kt        # Boundary (-90..90, -180..180) & accuracy validation
│   └── MockLocationManager.kt      # Android LocationManager test-provider dispatcher
├── model/
│   ├── LocationSearchResult.kt     # Geocoding search model
│   └── SpoofLocation.kt            # Core mock location entity
├── service/
│   └── LocationForegroundService.kt# 2 Hz continuous background publisher & notification
└── ui/
    ├── theme/                      # Material 3 dark/light color schemes & typography
    ├── MainScreen.kt               # Root Compose container & top-level state
    ├── MapLocationPicker.kt        # osmdroid MapView Compose integration
    ├── SaveLocationDialog.kt       # Modern bookmark creation modal with icon selector
    └── SavedLocationsSection.kt    # Horizontal bookmark carousel & management
```

- **Language**: Kotlin 2.x
- **Framework**: Jetpack Compose (BOM) & Material 3
- **Map SDK**: osmdroid `6.1.20` (OpenStreetMap)
- **Minimum SDK**: Android 7.0 (API 24)
- **Target / Compile SDK**: Android 16 (API 36)
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`)

---

## 🚀 Building & Running

### Prerequisites
- Java Development Kit (JDK 17 or higher)
- Android SDK (API 34+)
- An Android physical device or emulator with Developer Options enabled

### Build via Command Line (Gradle)

```bash
# Clone the repository
git clone https://github.com/Srivishag/geo-mock-app.git
cd geo-mock-app

# Run unit test suite
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Install on connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 💡 Troubleshooting & FAQs

### Why is my location bouncing / jumping between real and mock? (Rubber-Banding)
On physical Android devices, Google Play Services uses **Google Location Accuracy** (Wi-Fi BSSID and Cell tower scanning) in addition to GPS satellites. When active, Wi-Fi scans can briefly report your true location.

**How to eliminate jumping:**
1. In the app, tap **Open Location Settings ⚙️** (or navigate to **Android Settings → Location**).
2. Tap **Location Services**:
   - Turn **OFF** **Wi-Fi scanning**.
   - Turn **OFF** **Bluetooth scanning**.
   - Turn **OFF** **Google Location Accuracy** (set to *Device GPS only*).

---

## 🔒 Security & Legitimate Use

This application is strictly designed for:
- Quality assurance (QA) and mobile app development testing.
- Geofencing and location-based feature validation.
- Testing GPS routing algorithms in custom Android applications.

It uses Android's documented and official `LocationManager.addTestProvider` / `setTestProviderLocation` APIs and complies with standard Android platform security.

---

## 📄 License

```
Copyright 2026 GeoMock Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
