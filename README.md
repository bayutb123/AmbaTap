# AmbaTap
Aplikasi auto clicker seperti macro recorder untuk Android dengan Kotlin dan Jetpack Compose

Lihat rencana pengembangan di [docs/PLAN.md](docs/PLAN.md).

## Build

Kebutuhan: JDK 17+ (CI memakai 21) dan Android SDK dengan platform API 37.

```bash
./gradlew assembleDebug        # APK debug di app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit test
./gradlew lint                 # Android lint
```

Bila SDK tidak terdeteksi otomatis, buat `local.properties` berisi `sdk.dir=/path/ke/android-sdk`.

## Uji di perangkat

1. Pasang APK debug: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. Aktifkan layanan lewat layar onboarding, atau langsung dengan adb:
   ```bash
   adb shell settings put secure enabled_accessibility_services \
     com.ambacoding.ambatap/com.ambacoding.ambatap.service.AmbaTapAccessibilityService
   adb shell settings put secure accessibility_enabled 1
   ```
3. Buka **Playground** di beranda, jalankan macro contoh, dan bandingkan titik sentuhan
   (biru) dengan target (abu-abu); nilai `Δ` adalah selisihnya dalam piksel.
   Tekan volume turun 2× untuk menghentikan macro kapan saja.

## Struktur

```
app/src/main/java/com/ambacoding/ambatap/
├── domain/   # model (Macro, MacroAction, ...) & interface repository
├── data/     # Room (macro), DataStore (pengaturan), implementasi repository
├── di/       # modul Hilt
├── engine/   # MacroPlayer, GestureFactory (logika pemutaran, bebas framework)
├── service/  # AccessibilityService, ServiceBridge
└── ui/       # Compose: theme, navigation, home, onboarding, settings, editor, playground
```
