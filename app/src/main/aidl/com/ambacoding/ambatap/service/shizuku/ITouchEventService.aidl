package com.ambacoding.ambatap.service.shizuku;

import com.ambacoding.ambatap.service.shizuku.ITouchEventListener;

/** Dijalankan Shizuku di proses shell; membaca event layar sentuh lewat getevent. */
interface ITouchEventService {
    /** Kode transaksi khusus yang dipanggil Shizuku saat layanan dihentikan. */
    void destroy() = 16777114;

    /** Keluaran `getevent -lp`. */
    String describeDevices() = 1;

    /** Mulai mengalirkan `getevent -lt <devicePath>`; aliran sebelumnya dihentikan. */
    void start(String devicePath, ITouchEventListener listener) = 2;

    void stop() = 3;
}
