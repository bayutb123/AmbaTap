# Kebijakan Privasi AmbaTap

_Berlaku sejak versi 1.0.0._

AmbaTap adalah aplikasi perekam dan pemutar macro sentuhan untuk Android. Kebijakan ini
menjelaskan data apa yang disentuh aplikasi dan bagaimana data itu diperlakukan.

## Ringkasan

- AmbaTap **tidak mengumpulkan, mengirim, atau menjual** data apa pun.
- AmbaTap **tidak memiliki izin internet**; semua data tetap di perangkat Anda.
- Tidak ada iklan, analitik, atau pelacak pihak ketiga.

## Layanan Aksesibilitas

AmbaTap memakai API Layanan Aksesibilitas Android (`AccessibilityService`) hanya untuk:

1. **Memutar ulang gesture** (tap, long press, swipe) yang Anda rekam atau atur sendiri,
   menggunakan `dispatchGesture`.
2. **Menampilkan panel kontrol dan lapisan perekaman** di atas aplikasi lain.
3. **Membaca tombol volume** agar macro bisa dihentikan dengan menekan volume turun dua kali.
   Tombol hanya ditahan selama macro berjalan.
4. **Mengisi teks** ke kolom yang sedang fokus, hanya bila Anda menambahkan aksi "Ketik teks"
   ke macro.
5. **Memeriksa isi layar untuk aksi bersyarat**, hanya bila Anda menambahkan aksi tersebut:
   - "Tunggu teks" mencari teks/deskripsi elemen di layar yang cocok dengan kata kunci Anda.
   - "Tunggu warna" mengambil tangkapan layar (Android 11+) dan membaca warna **satu piksel**
     di titik yang Anda pilih.

   Pemeriksaan berlangsung di memori saat macro berjalan. Isi layar dan tangkapan layar
   tidak disimpan, tidak dicatat, dan tidak dikirim ke mana pun; yang tersimpan hanyalah
   kata kunci dan warna yang Anda atur di macro.

Saat merekam, AmbaTap mencatat **koordinat dan waktu sentuhan Anda** pada layar. Di luar aksi
bersyarat di atas, AmbaTap tidak membaca isi layar, teks, kata sandi, atau notifikasi aplikasi lain.

## Data yang disimpan

- **Macro**: nama, daftar aksi (koordinat relatif, durasi, jeda), ukuran layar saat direkam,
  dan pengaturan pemutaran. Disimpan di database lokal aplikasi.
- **Pengaturan**: hitung mundur, indikator sentuhan, transparansi panel. Disimpan lokal.

Data ini terhapus bila Anda menghapus macro, menghapus data aplikasi, atau mencopot AmbaTap.

## Ekspor dan impor

Fitur ekspor menulis macro ke file JSON di lokasi yang **Anda pilih sendiri** melalui pemilih
file Android. AmbaTap tidak mengirim file tersebut ke mana pun; membagikannya sepenuhnya
keputusan Anda. Impor hanya membaca file yang Anda pilih.

## Notifikasi

Izin notifikasi dipakai untuk menampilkan status macro yang sedang berjalan beserta tombol
Jeda dan Berhenti.

## Kontak

Pertanyaan tentang kebijakan ini dapat diajukan melalui
[GitHub Issues](https://github.com/bayutb123/AmbaTap/issues).
