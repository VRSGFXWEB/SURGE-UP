# Surge Up Beta - Part 2

Isi:
- Loading screen baru (animasi, tips, progress) sebelum menu dan sebelum pertandingan.
- Lobby baru mengikuti desain foto: menu kiri (BERANDA, STORE, KARAKTER, SENJATA, VAULT, EVENT, RANK),
  banner SURGEUP, chat GLOBAL / TIM / GUILD, panel TEMAN, kartu SQUAD, tombol MULAI.
- Lobby 100% native (tanpa HTML): lantai persegi penuh + karakter siluet. HTML hanya untuk gameplay.
- Teman: TAMBAH lewat ID, permintaan teman, status Online/Offline.
- Mabar: UNDANG teman online, banner UNDANGAN SQUAD (TERIMA / X), room 2 orang, kode room 5 huruf,
  status MENUNGGU TEMAN SIAP / TEMAN SIAP, tombol SIAP untuk anggota, MULAI untuk host.
- Room native: BUAT ROOM, GABUNG pakai kode, pilih map (TRAINING / ARENA), chat TIM di dalam room.
- Musik lobby original (dibuat sendiri), otomatis jalan di lobby, bisa dimatikan di Pengaturan.
- Chat 7 menit, filter kata terlarang + ban otomatis 2 jam, layar ban, notifikasi admin + banner 5 detik.

Mode yang benar-benar jalan: SOLO vs bot (map training / arena) dan 1v1 online lawan teman
(memakai netcode game yang sudah ada). 2v2 belum ada karena inti game hanya mendukung 1 lawan.

## Build
A) AndroidIDE: Open project > tunggu Gradle sync > Build > Build APK.
B) GitHub (tanpa PC): upload semua isi folder ini (termasuk .github) ke repo baru,
   tab Actions > Build APK > Run workflow > download artifact SurgeUp-debug-apk.

## Pembaruan terbaru
- Lobby tanpa HTML. Karakter 3D low-poly beranimasi (idle): PUNK (dari model di game) dan BALOK (karakter original).
- Tombol CHAT kecil di menu kiri, panel chat global muncul saat ditekan.
- Menu KARAKTER bisa diklik. Karakter yang dipakai ikut tampil ke teman saat undangan diterima.
- Klik kartu profil: ganti foto, bio, salin ID, ganti karakter.
- Level dihapus sementara. Menu STORE, EVENT, RANK, ikon surat/dompet, banner dihapus.

## Pembaruan: halaman terpisah
- Halaman KARAKTER (PUNK, JOSEPH), SENJATA (AK-47, M1887), TEMAN (tambah, permintaan, daftar), PENGATURAN. Tombol kembali menuju BERANDA.
- Latar lobby dan menu: gambar render map training. Karakter 3D beranimasi (gambar berurutan), tanpa HTML.
- Klik foto pemain (teman atau chat) untuk melihat profil, karakter, ID, bio.
- MULAI bisa dibatalkan. Mode 2VS2 di dialog room: cari lawan maksimal 40 detik, bisa dibatalkan.
- Pengaturan grafik (kualitas, bayangan, FPS) dan senjata awal diteruskan ke game lewat parameter URL.

## Pembaruan: 3D native
- Karakter (Joseph, Punk) dan senjata (AK-47, M1887) dirender 3D native (OpenGL ES), bisa diputar dengan geser jari.
- Lobby: latar foto bergerak pelan + karakter 3D. Ketuk karakter atau tombol emote untuk gerakan.
- Halaman KARAKTER, SENJATA, PROFIL (karakter 3D di kiri), TEMAN, PENGATURAN terpisah dari BERANDA.
- Ukuran UI bisa diatur di PENGATURAN. Layar penuh/poni di game diperbaiki.
- Joseph baru tampil di lobby/profil. Di dalam pertandingan masih memakai Punk.

## Perbaikan terbaru
- Bug tulang 3D (karakter jadi rusak) diperbaiki. Senjata ditampilkan sejajar dan miring sedikit.
- Joseph dipakai di dalam game: mesh Joseph dipasang ke kerangka Punk (assets/joseph_rig.glb), memakai animasi Punk.
- Tombol emoji dihapus. Ketuk karakter di lobby memicu gerakan.
- Layar penuh juga di menu (tidak ada strip hitam di kiri).
