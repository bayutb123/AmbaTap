# Aturan R8 khusus proyek. kotlinx.serialization, Hilt, dan Room sudah
# menyertakan consumer rules masing-masing.

# Dibuat Shizuku lewat refleksi di proses shell terpisah (mode presisi).
-keep class com.ambacoding.ambatap.service.shizuku.TouchEventUserService {
    public <init>(...);
}
