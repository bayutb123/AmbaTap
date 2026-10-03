# AmbaTap

Aplikasi auto clicker dan macro recorder untuk Android, dibuat dengan Kotlin dan Jetpack Compose.
Rekam tap, long press, dan swipe di aplikasi mana pun lalu putar ulang otomatis, **tanpa root**.

## Fitur

- **Rekam macro** di atas aplikasi lain; setiap sentuhan diteruskan ke aplikasi saat direkam.
- **Auto clicker manual**: taruh dan geser titik tap/tahan/swipe langsung di layar.
- **Putar ulang** 1×, N×, tak terbatas, atau selama durasi tertentu; kecepatan 0,5×–4×;
  jeda antar loop; acak posisi dan jeda.
- **Panel melayang** yang bisa digeser, **notifikasi** dengan tombol Jeda/Berhenti, dan
  **berhenti darurat** dengan menekan volume turun 2×.
- **Editor**: ubah koordinat, durasi, dan jeda; urutkan; sisipkan Tunggu, Kembali, Beranda,
  Buka aplikasi, atau Ketik teks.
- **Impor/ekspor** macro sebagai file JSON, **Quick Settings tile**, indikator sentuhan,
  peringatan bila orientasi layar berbeda dari saat direkam.
- Semua data tersimpan di perangkat; aplikasi tidak memiliki izin internet.
  Lihat [kebijakan privasi](docs/PRIVACY.md).

Rencana dan status pengembangan: [docs/PLAN.md](docs/PLAN.md).

## Build

Kebutuhan: JDK 17+ (CI memakai 21) dan Android SDK dengan platform API 37.

```bash
./gradlew assembleDebug        # APK debug di app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit test
./gradlew lint                 # Android lint
./gradlew assembleRelease      # APK release (R8); ditandatangani bila kunci tersedia
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
   Untuk APK sideload di Android 13+, bila tombol aktifkan tidak bisa ditekan: buka
   Info aplikasi AmbaTap › ⋮ › **Izinkan setelan terbatas**.
3. **Playground** (bawah beranda): jalankan macro contoh dan bandingkan titik sentuhan
   (biru) dengan target (abu-abu); nilai `Δ` adalah selisihnya dalam piksel.
4. **Rekam macro**: AmbaTap diminimalkan dan panel muncul. Buka aplikasi tujuan, tekan
   tombol merah di panel, lakukan tap/swipe seperti biasa (sentuhan diteruskan ke aplikasi
   setelah jari diangkat), lalu tekan tombol kotak untuk selesai dan simpan.
5. **Auto clicker**: overlay pemilih titik terbuka di atas aplikasi sebelumnya. Geser titik,
   tambah titik/swipe, atur jeda dan jumlah ulang, uji, lalu simpan. Selama mengatur titik,
   aplikasi di bawahnya tetap bisa disentuh (scroll, pindah layar) kecuali di area penanda
   dan toolbar. Toolbar bisa dipindah dengan menggeser gagang di atasnya.
6. Putar macro dari daftar di beranda, panel, atau Quick Settings tile **Panel AmbaTap**.
   Tekan volume turun 2× untuk berhenti kapan saja.

### Batasan yang diketahui

- Saat merekam, swipe/scroll baru terjadi di aplikasi setelah jari diangkat, dan sentuhan
  yang terjadi selagi gesture sebelumnya masih diteruskan tidak ikut terekam.
- Perekaman hanya mendukung satu jari; gesture sistem (swipe tepi untuk Kembali/Beranda)
  tidak bisa direkam.
- Aplikasi yang memblokir gesture terinjeksi atau memakai `FLAG_SECURE` bisa tidak merespons.
- Gesture yang lebih panjang dari batas sistem (±60 detik) dipotong.

## Rilis

Workflow [`release.yml`](.github/workflows/release.yml) membuat GitHub Release berisi APK
bertanda tangan setiap kali tag `v*` di-push.

1. Buat keystore sekali saja dan **simpan cadangannya** (kehilangan kunci = tidak bisa
   memperbarui aplikasi yang sudah terpasang):
   ```bash
   keytool -genkeypair -v -keystore ambatap-release.jks -alias ambatap \
     -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 ambatap-release.jks   # isi untuk secret KEYSTORE_BASE64
   ```
2. Isi secrets repo: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Naikkan `versionCode`/`versionName` di `app/build.gradle.kts` bila perlu, merge ke `main`,
   lalu:
   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```

Untuk build bertanda tangan secara lokal, set environment `AMBATAP_KEYSTORE_PATH`,
`AMBATAP_KEYSTORE_PASSWORD`, `AMBATAP_KEY_ALIAS`, `AMBATAP_KEY_PASSWORD` (atau properti
`ambatap.keystore.path` dst. di `~/.gradle/gradle.properties`).

## Struktur

```
app/src/main/java/com/ambacoding/ambatap/
├── domain/   # model (Macro, MacroAction, ...), helper edit & statistik, interface repository
├── data/     # Room (macro), DataStore (pengaturan), impor/ekspor JSON
├── di/       # modul Hilt
├── engine/   # player (MacroPlayer, GestureFactory) & recorder (klasifikasi, RDP), bebas framework
├── service/  # AccessibilityService, overlay (panel, rekam, pemilih titik, indikator), notifikasi, tile
└── ui/       # Compose: theme, navigation, home, onboarding, editor, settings, playground
```

## Lisensi pihak ketiga

Font Space Grotesk, IBM Plex Sans, dan JetBrains Mono dibundel di bawah SIL Open Font
License 1.1; teks lisensinya ada di `app/src/main/assets/licenses/`.
