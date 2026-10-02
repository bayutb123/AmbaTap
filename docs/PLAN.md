# AmbaTap — Rencana Pengembangan

Aplikasi **macro recorder / auto clicker** untuk Android: merekam rangkaian sentuhan
(tap, long press, swipe, multi-touch) lalu memutarnya ulang secara otomatis,
dengan UI Kotlin + Jetpack Compose dan **tanpa root**.

---

## 1. Tujuan & Ruang Lingkup

### Fitur inti (MVP)
1. **Rekam macro** — rekam tap, long press, dan swipe di aplikasi mana pun, lengkap dengan jeda antar aksi.
2. **Putar ulang macro** — jalankan sekali, N kali, tanpa batas, atau selama durasi tertentu; atur kecepatan (0.5x–4x).
3. **Panel melayang (floating controller)** — tombol Rekam / Play / Pause / Stop di atas aplikasi lain.
4. **Daftar macro** — simpan, ganti nama, duplikat, hapus.
5. **Auto clicker manual** — taruh titik-titik target di layar (tanpa merekam), atur interval per titik.
6. **Emergency stop** — hentikan macro kapan saja (tombol panel, tombol volume, atau notifikasi).

### Fitur lanjutan (setelah MVP)
- Editor macro (urutkan ulang, ubah koordinat/jeda, sisipkan aksi).
- Aksi global: Back, Home, Recents, buka notifikasi, buka aplikasi tertentu, input teks.
- Randomisasi posisi & waktu (± px / ± ms) agar tidak terlalu "robotik".
- Impor/ekspor macro (JSON), Quick Settings tile, jadwal eksekusi.
- Mode presisi tinggi via **Shizuku** (rekam event input mentah).
- Kondisi/trigger (mis. tunggu sampai teks/elemen tertentu muncul).

### Di luar cakupan
- Fitur yang butuh root sebagai syarat wajib.
- Bypass anti-cheat / proteksi aplikasi pihak ketiga.

---

## 2. Keputusan Teknis Kunci

### 2.1 Pemutaran (playback) — `AccessibilityService.dispatchGesture()`
Satu-satunya cara resmi non-root untuk menyuntikkan sentuhan ke aplikasi lain.

- `GestureDescription` + `StrokeDescription(path, startTime, duration)`.
- Tap = path 1 titik, durasi ~50 ms. Long press = 1 titik, durasi ≥ 600 ms.
  Swipe = `Path` dengan banyak titik (lineTo / quadTo untuk lintasan halus).
- Multi-touch (pinch, dll.) = beberapa stroke dalam satu gesture
  (batas: `GestureDescription.getMaxStrokeCount()`, biasanya 10–20).
- Gesture panjang (> `getMaxGestureDuration()`, ±60 dtk) dipecah dengan
  `StrokeDescription.continueStroke()` (API 26+).
- Aksi global (Back/Home/Recents/Notifikasi) via `performGlobalAction()`.
- Gesture dijalankan berurutan secara **suspend**: bungkus `dispatchGesture` +
  `GestureResultCallback` dengan `suspendCancellableCoroutine`, sehingga
  pause/stop cukup dengan membatalkan `Job` coroutine.

### 2.2 Perekaman (recording) — bagian tersulit
Android tidak mengizinkan aplikasi biasa membaca sentuhan global. Opsi yang dievaluasi:

| Pendekatan | Akurasi | Syarat | Catatan |
|---|---|---|---|
| **A. Overlay penangkap + forward** | Baik (single-touch) | Accessibility | Overlay transparan full-screen menangkap `MotionEvent`, simpan, lalu teruskan ke app di bawah dengan `dispatchGesture`. |
| B. Event aksesibilitas (`TYPE_VIEW_CLICKED`, dll.) | Rendah (semantik, bukan koordinat) | Accessibility | Tidak semua app/game mengirim event; tidak ada data swipe. |
| C. `getevent` dari `/dev/input` via **Shizuku** | Sangat tinggi, multi-touch, real-time | Shizuku (ADB/wireless debugging) | Sentuhan berjalan natural tanpa delay; perlu setup oleh user. |
| D. `onMotionEvent` + `setMotionEventSources` (API 34+) | Tinggi | Accessibility, Android 14+ | Perlu riset: perilaku consume vs observe sumber touchscreen berbeda antar versi. Kandidat eksperimen. |

**Keputusan:** MVP pakai **A**, dengan **C** sebagai mode "presisi tinggi" di fase berikutnya.
**D** diriset sebagai spike opsional untuk Android 14+.

Detail alur pendekatan A:
1. Saat rekam, `AccessibilityService` menambahkan overlay `TYPE_ACCESSIBILITY_OVERLAY`
   (tidak butuh izin `SYSTEM_ALERT_WINDOW`) yang **touchable** dan menutup layar.
2. Overlay menerima `ACTION_DOWN → MOVE → UP`, mengumpulkan titik + timestamp,
   lalu mengklasifikasikan: tap / long press / swipe (berdasarkan jarak & durasi;
   gunakan `ViewConfiguration.getScaledTouchSlop()` dan `getLongPressTimeout()`).
3. Aksi disimpan beserta jeda sejak aksi sebelumnya.
4. Overlay di-set `FLAG_NOT_TOUCHABLE` → aksi diputar ke app di bawahnya
   via `dispatchGesture` → setelah callback selesai, overlay touchable lagi.
5. Keterbatasan yang harus dikomunikasikan di UI: swipe/scroll baru "terjadi" setelah
   jari diangkat (tidak live), dan multi-touch belum didukung di mode ini.

### 2.3 Koordinat & orientasi
- Simpan koordinat **ternormalisasi** (0..1) relatif terhadap ukuran layar fisik
  (`WindowManager.currentWindowMetrics` / `maximumWindowMetrics`), plus metadata
  ukuran layar & rotasi saat direkam.
- Saat playback, konversi ke piksel sesuai ukuran & rotasi saat ini; tampilkan
  peringatan bila orientasi berbeda dari saat rekam.
- Perhatikan display cutout & status bar: overlay dan `dispatchGesture` sama-sama
  memakai koordinat layar absolut, pastikan overlay benar-benar full-screen
  (`layoutInDisplayCutoutMode = ALWAYS`, flag `LAYOUT_NO_LIMITS`).

### 2.4 Compose di dalam overlay
Overlay dibuat dari `Service`, bukan `Activity`, sehingga `ComposeView` butuh
owner manual: implementasikan `LifecycleOwner`, `SavedStateRegistryOwner`, dan
`ViewModelStoreOwner` lalu pasang dengan `setViewTreeLifecycleOwner(...)` dkk.
sebelum `windowManager.addView(...)`. Buat helper `OverlayWindow` reusable.

---

## 3. Tech Stack

| Area | Pilihan |
|---|---|
| Bahasa | Kotlin 2.x (K2), Coroutines + Flow |
| UI | Jetpack Compose + Material 3, Navigation Compose (type-safe routes) |
| Arsitektur | MVVM + UDF (state `StateFlow`, event satu arah), repository pattern |
| DI | Hilt (atau Koin bila ingin lebih ringan) |
| Penyimpanan | Room (macro) + DataStore Preferences (pengaturan) |
| Serialisasi | kotlinx.serialization (daftar aksi sebagai JSON & impor/ekspor) |
| Build | Gradle Kotlin DSL + Version Catalog (`libs.versions.toml`) |
| SDK | `minSdk 26`, `targetSdk`/`compileSdk` versi stabil terbaru |
| Test | JUnit, Turbine, MockK, Compose UI Test, Robolectric |
| Opsional | Shizuku API (mode presisi), WorkManager (jadwal) |

---

## 4. Arsitektur & Struktur Proyek

```
app/src/main/java/com/ambacoding/ambatap/
├── AmbaTapApp.kt                  # @HiltAndroidApp
├── MainActivity.kt                # host NavHost Compose
├── domain/
│   ├── model/                     # Macro, MacroAction, PlaybackConfig, ...
│   ├── repository/                # MacroRepository (interface)
│   └── usecase/                   # SaveMacro, PlayMacro, ImportMacro, ...
├── data/
│   ├── local/                     # Room: MacroEntity, MacroDao, AppDatabase, Converters
│   ├── prefs/                     # SettingsDataStore
│   └── repository/                # MacroRepositoryImpl
├── engine/
│   ├── player/                    # MacroPlayer, GestureFactory, Randomizer
│   ├── recorder/                  # TouchRecorder, GestureClassifier
│   └── EngineState.kt             # Idle / Recording / Playing / Paused (StateFlow)
├── service/
│   ├── AmbaTapAccessibilityService.kt
│   ├── ServiceBridge.kt           # akses aman ke instance service dari UI/VM
│   └── overlay/                   # OverlayWindow, FloatingPanel, RecordLayer, PointPicker
├── ui/
│   ├── theme/
│   ├── onboarding/                # panduan izin aksesibilitas
│   ├── home/                      # daftar macro
│   ├── editor/                    # editor aksi
│   ├── settings/
│   └── playground/                # layar uji akurasi tap/swipe
└── util/
```

Bisa dimulai sebagai **single module**; pecah ke modul (`:core:model`, `:core:data`,
`:engine`, `:feature:*`) bila kode sudah besar.

### Alur komponen
```
UI (Compose) ──► ViewModel ──► UseCase ──► Repository ──► Room
                     │
                     └──► ServiceBridge ──► AccessibilityService
                                               ├── OverlayManager (panel, record layer)
                                               ├── TouchRecorder ──► List<MacroAction>
                                               └── MacroPlayer ──► dispatchGesture()
```
`EngineState` diekspos sebagai `StateFlow` sehingga panel melayang dan UI utama
selalu sinkron.

---

## 5. Model Data

```kotlin
@Serializable
data class Macro(
    val id: Long = 0,
    val name: String,
    val actions: List<MacroAction>,
    val screen: ScreenInfo,          // width, height, rotation saat rekam
    val config: PlaybackConfig = PlaybackConfig(),
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class PlaybackConfig(
    val repeat: RepeatMode = RepeatMode.Count(1), // Count(n) | Infinite | Duration(ms)
    val speed: Float = 1f,
    val delayBetweenLoopsMs: Long = 0,
    val randomOffsetPx: Int = 0,
    val randomDelayMs: Long = 0,
)

@Serializable
sealed interface MacroAction {
    val delayBeforeMs: Long

    data class Tap(val x: Float, val y: Float, val durationMs: Long = 50, ...) : MacroAction
    data class LongPress(val x: Float, val y: Float, val durationMs: Long, ...) : MacroAction
    data class Swipe(val points: List<TimedPoint>, val durationMs: Long, ...) : MacroAction
    data class MultiTouch(val strokes: List<Stroke>, ...) : MacroAction
    data class Wait(val durationMs: Long, ...) : MacroAction
    data class GlobalAction(val type: GlobalType, ...) : MacroAction  // BACK, HOME, RECENTS, NOTIFICATIONS
    data class LaunchApp(val packageName: String, ...) : MacroAction
    data class InputText(val text: String, ...) : MacroAction           // ACTION_SET_TEXT pada node fokus
}
```
Room: `MacroEntity(id, name, actionsJson, screenJson, configJson, createdAt, updatedAt)`
— daftar aksi disimpan sebagai JSON agar skema fleksibel; tambahkan field
`schemaVersion` untuk migrasi format.

---

## 6. Izin & Konfigurasi

`AndroidManifest.xml`
```xml
<service
    android:name=".service.AmbaTapAccessibilityService"
    android:exported="false"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

`res/xml/accessibility_service_config.xml`
```xml
<accessibility-service
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:accessibilityFlags="flagRequestFilterKeyEvents|flagReportViewIds"
    android:canRequestFilterKeyEvents="true"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:notificationTimeout="100"
    android:description="@string/accessibility_description" />
```
- `flagRequestFilterKeyEvents` → tombol volume sebagai emergency stop (`onKeyEvent`).
- Overlay memakai `TYPE_ACCESSIBILITY_OVERLAY`, jadi `SYSTEM_ALERT_WINDOW` **tidak** wajib.
- `POST_NOTIFICATIONS` (Android 13+) untuk notifikasi status + tombol Stop.

**Onboarding izin:** cek status via `AccessibilityManager.getEnabledAccessibilityServiceList`,
arahkan ke `Settings.ACTION_ACCESSIBILITY_SETTINGS`, tampilkan **prominent disclosure**
(wajib bila rilis di Play Store — Google ketat soal penggunaan AccessibilityService;
siapkan juga opsi distribusi via GitHub Releases/F-Droid). Tambahkan juga panduan
"izinkan pengaturan terbatas" untuk APK sideload di Android 13+.

---

## 7. Desain UI

**Aplikasi utama (Compose):**
1. **Onboarding** — penjelasan, status izin, tombol buka pengaturan aksesibilitas.
2. **Home** — daftar macro (kartu: nama, jumlah aksi, durasi, tombol Play),
   FAB "Rekam baru" & "Auto clicker manual", banner jika service mati.
3. **Detail/Editor macro** — timeline aksi (drag-to-reorder), edit jeda/koordinat,
   tombol "pilih ulang posisi di layar", pengaturan playback (ulang, kecepatan, random).
4. **Settings** — tema, ukuran/transparansi panel, emergency stop (volume up/down),
   hitung mundur sebelum mulai, tampilkan indikator sentuhan saat playback.
5. **Playground** — kanvas uji yang menggambar setiap sentuhan masuk (verifikasi akurasi).

**Overlay:**
- **Floating panel** — bisa di-drag & di-collapse; state: Idle → Recording → Playing/Paused.
- **Record layer** — full-screen transparan + indikator merah "REC" + jejak sentuhan.
- **Point picker** — penanda bernomor yang bisa digeser untuk mode auto clicker manual.
- **Touch indicator** — lingkaran kecil di posisi tap saat playback (opsional, debug).

---

## 8. Tahapan Pengerjaan (Milestone)

### Fase 0 — Setup proyek (±1–2 hari)
- [x] Buat proyek Android (Compose, Kotlin DSL, version catalog), package `com.ambacoding.ambatap`.
- [x] Tambah Hilt, Room, DataStore, kotlinx.serialization, Navigation Compose.
- [x] Tema Material 3 + struktur package di atas.
- [x] CI GitHub Actions: `./gradlew lint testDebugUnitTest assembleDebug`.
- [x] Perbarui `.gitignore` untuk Android (`build/`, `.gradle/`, `local.properties`, `*.iml`, `.idea/`).

### Fase 1 — Accessibility service & playback dasar (±3–4 hari)
- [x] `AmbaTapAccessibilityService` + config XML + `ServiceBridge`.
- [x] Layar onboarding & deteksi status service.
- [x] `GestureFactory` (Tap, LongPress, Swipe → `GestureDescription`).
      Belum: memecah gesture > `getMaxGestureDuration()` dengan `continueStroke()` (sekarang di-clamp).
- [x] `MacroPlayer` berbasis coroutine: play / pause / resume / stop, repeat, speed.
- [x] Layar Playground + macro hardcoded untuk uji.
- [x] Emergency stop dasar: volume turun 2× saat macro aktif (dimajukan dari Fase 2).
- **Selesai bila:** macro hardcoded bisa men-tap & swipe di app lain secara akurat.

### Fase 2 — Overlay & floating panel (±3 hari)
- [x] Helper `OverlayWindow` (Compose di Service dengan lifecycle/saved-state owner).
- [x] Floating panel draggable dengan state dari `EngineState` (sementara `PlaybackState`).
      Panel ditembus otomatis saat gesture macro jatuh di atasnya.
- [x] Notifikasi status + tombol Stop; emergency stop via tombol volume.
- [x] Hitung mundur 3-2-1 sebelum mulai (dari pengaturan, saat diputar lewat panel).

### Fase 3 — Perekaman (±4–5 hari)
- [x] Record layer full-screen (`TYPE_ACCESSIBILITY_OVERLAY`).
- [x] `GestureClassifier` (tap / long press / swipe) + simplifikasi path
      (Ramer–Douglas–Peucker) agar jumlah titik swipe wajar.
- [x] Forward sentuhan ke app di bawah (toggle `FLAG_NOT_TOUCHABLE` → dispatch → restore).
      Jeda antar aksi diukur dari gesture yang diteruskan, bukan sentuhan asli.
- [x] Abaikan sentuhan di area floating panel; catat jeda antar aksi.
- [x] Simpan hasil rekaman → dialog nama macro (sheet overlay: nama, ulangi, uji putar, buang).
- **Selesai bila:** rekam di app nyata (mis. kalkulator) → putar ulang hasil identik.

### Fase 4 — Persistensi & manajemen macro (±2–3 hari)
- [x] Room entity/DAO/repository + mapper JSON.
- [x] Home: daftar, rename, duplikat, hapus (dengan undo), putar/stop langsung dari daftar.
- [x] Pengaturan playback per macro.

### Fase 5 — Auto clicker manual & editor (±4–5 hari)
- [ ] Point picker overlay (tambah/geser/hapus titik, interval per titik).
- [x] Editor timeline: reorder, edit nilai, sisipkan Wait/Global/LaunchApp.
      Urutan diubah dengan tombol Naik/Turun (belum drag).
- [x] Randomisasi posisi & jeda.

### Fase 6 — Polish & rilis v1.0 (±3–4 hari)
- [ ] Impor/ekspor JSON (Storage Access Framework), share macro.
- [ ] Quick Settings tile (buka panel / jalankan macro terakhir).
- [ ] Penanganan rotasi & perbedaan resolusi; peringatan bila tidak cocok.
- [ ] Optimasi baterai: hentikan overlay saat idle, tidak ada wakelock permanen.
- [ ] Ikon, nama, screenshot, kebijakan privasi, signed release + GitHub Release.

### Fase 7 — Lanjutan (pasca v1.0)
- [ ] Mode presisi via Shizuku: parsing `getevent -lt` (multi-touch protocol B,
      skala raw → piksel via `getevent -p`), rekam real-time tanpa overlay penangkap.
- [ ] Spike `onMotionEvent` (API 34+) sebagai alternatif tanpa Shizuku.
- [ ] Aksi bersyarat: tunggu teks/elemen (`AccessibilityNodeInfo`) atau warna piksel
      (`takeScreenshot()` API 30+).
- [ ] Jadwal eksekusi (AlarmManager exact / WorkManager) + trigger saat app tertentu dibuka.
- [ ] Multi-touch recording (via Shizuku) & playback pinch/zoom.

---

## 9. Pengujian

- **Unit test:** `GestureClassifier`, simplifikasi path, konversi koordinat & rotasi,
  serialisasi/migrasi JSON, logika repeat/speed `MacroPlayer` (dengan `TestDispatcher`).
- **Instrumented test:** Room DAO, Compose UI (Home, Editor).
- **Uji manual terstruktur** di Playground: akurasi posisi (< 2 px), akurasi timing
  (deviasi < 20 ms per aksi), swipe panjang, loop 1000×, stop saat tengah gesture.
- **Matriks perangkat:** Android 8, 11, 13, 14, 15+; minimal satu Samsung (One UI),
  Xiaomi (HyperOS/MIUI — sering membunuh service, perlu panduan autostart/battery),
  dan Pixel/AOSP; resolusi & DPI berbeda; mode gesture vs 3-tombol navigasi.

---

## 10. Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|---|---|---|
| Kebijakan Play Store untuk AccessibilityService | App ditolak | Prominent disclosure, deklarasi penggunaan, alternatif distribusi (GitHub/F-Droid). |
| OEM membunuh service di background | Macro berhenti | Panduan whitelist baterai/autostart per OEM, deteksi & notifikasi saat service mati. |
| Rekam via overlay tidak live untuk swipe | UX kurang natural | Indikator jejak sentuhan, info di UI, mode Shizuku sebagai opsi presisi. |
| App target memblokir gesture terinjeksi / layar `FLAG_SECURE` | Macro gagal di app tertentu | Dokumentasikan keterbatasan; deteksi `onCancelled` dan tampilkan error. |
| Perbedaan resolusi/rotasi | Tap meleset | Koordinat ternormalisasi + metadata layar + peringatan. |
| Macro tak terkendali (loop infinite) | Pengalaman buruk | Emergency stop berlapis (panel, volume, notifikasi), batas default. |

---

## 11. Definisi Selesai v1.0
- User dapat mengaktifkan service melalui onboarding yang jelas.
- Merekam tap/long press/swipe di app lain dan memutarnya ulang dengan akurat.
- Membuat auto clicker manual dengan beberapa titik dan interval.
- Menyimpan, mengedit, mengulang, dan mengekspor/impor macro.
- Macro selalu bisa dihentikan dalam < 1 detik.
- Lolos CI (lint + unit test + build) dan diuji di minimal 3 perangkat/OEM berbeda.
