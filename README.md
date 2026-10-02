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

## Struktur

```
app/src/main/java/com/bayutb123/ambatap/
├── domain/   # model (Macro, MacroAction, ...) & interface repository
├── data/     # Room (macro), DataStore (pengaturan), implementasi repository
├── di/       # modul Hilt
└── ui/       # Compose: theme, navigation, home, onboarding, settings, editor
```
