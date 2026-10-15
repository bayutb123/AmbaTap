package com.ambacoding.ambatap.service.trigger

/**
 * Mendeteksi saat aplikasi berpindah ke depan layar, dari rangkaian nama paket aplikasi
 * yang sedang aktif. Paket di [ignored] (mis. AmbaTap sendiri) tidak dihitung sebagai
 * perpindahan, sehingga kembali dari AmbaTap ke aplikasi yang sama tetap memicu.
 */
class ForegroundAppTracker(private val ignored: Set<String> = emptySet()) {
    private var current: String? = null

    /** @return [packageName] bila aplikasi itu baru saja masuk ke depan, selain itu `null`. */
    fun onForeground(packageName: String): String? {
        if (packageName.isBlank()) return null
        if (packageName in ignored) {
            current = null
            return null
        }
        if (packageName == current) return null
        current = packageName
        return packageName
    }
}
