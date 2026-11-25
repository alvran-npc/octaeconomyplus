# Changelog

## [0.0.3-2026-DEV-8] - 2026-11-25
### Ditambahkan
- Menu roda Roulette tersendiri (`CASINO_WHEEL`): SPIN menarik taruhan dan membuka roda 54 slot dengan cincin 26 kantong berurutan seperti roda Eropa yang berputar melewati penunjuk tetap di tengah atas, melambat, lalu berhenti pada hasil yang sudah ditentukan, dengan suara tik, pengumuman menang/kalah, dan kembali otomatis ke meja.
- Kunci baru `casino/animation/roulette.yml`: `laps`, `slowdown-steps`, `result-hold-ticks` (otomatis ditambahkan ke file lama).
- Kartu dompet dan kartu bermain bertanggung jawab di menu utama Casino; tooltip peringatan 21+ dibungkus per baris.
- Halaman kategori Shop memakai bingkai DEV-7; kartu Player Information di baris bawah.

### Diubah
- Tata letak meja Roulette: taruhan luar di sekitar nol hijau, blok beton berwarna dengan jumlah stack = nomor, taruhan/chip terpilih menyala, sudut dekoratif; Back pindah ke slot 52 dan chip ke slot 46-48.
- Halaman kategori Shop: Back pindah ke slot 46, Close ke slot 52, tombol Search baru di slot 47; panah tab/halaman hanya muncul bila dapat dipakai.
- Lore item Shop didesain ulang; petunjuk klik mengikuti `shop.click-actions` dan pratinjau jumlah tidak lagi menampilkan petunjuk klik.
- Menu utama Casino, gerbang 21+, dan halaman kategori Shop memakai tata letak berbingkai.
- Menutup menu roda saat berputar kini langsung menyelesaikan hasil yang sudah terkunci, bukan refund. Disconnect dan shutdown tetap refund.

### Diperbaiki
- Tooltip peringatan 21+ tidak lagi tampil sebagai satu baris yang sangat panjang.
- Menghapus kode latar kategori yang tidak terpakai dan pengaturan animasi usang (`frames`, `interval-increase-*`, `display-slot` tidak lagi dibaca).

## [0.0.3-2026-DEV-7] - 2026-11-25
### Ditambahkan
- Kartu Player Information (nama, rank, saldo Vault) di tengah bawah menu utama Shop dan menu jumlah. Rank diambil dari primary group Vault dan memakai `Member` sebagai cadangan.
- Kunci opsional `gui/shop.yml`: `shop.frame-corner`, `shop.player-info.default-rank`, `shop.player-info.currency-label` (digabung otomatis ke file lama).
- `VaultHook#primaryGroup(Player)` memakai layanan Permission Vault, aman bila tidak ada provider permission.

### Diubah
- Bingkai menu utama/jumlah Shop kini satu lapis yang rapi dengan aksen hanya di sudut; ornamen permata di sudut dan baris bawah dihapus.
- Lore kartu kategori dan tab kategori didesain ulang dengan garis pemisah, simbol unik, dan baris Buy/Sell terpisah.
- Menu utama: Close pindah ke slot 47, Search ke slot 51. Menu jumlah: Back pindah ke slot 47, Close ke slot 51. Tombol Sebelumnya/Berikutnya hanya muncul bila halaman tersedia.

### Diperbaiki
- Menghapus kode bingkai yang tidak terpakai dan komentar tata letak yang usang di `ShopModule`.
- Menyelaraskan versi di `pom.xml`, workflow CI, dan kedua README.

## [0.0.3-2026-DEV-6] - 2026-11-25
### Ditambahkan
- Bingkai Shop premium dengan ornamen sudut amethyst/prismarine, garis pemisah ganda, header khusus, rail samping utuh, dan area item kategori 4×7.
- Struktur folder data baru untuk Shop, Auction, Casino, GUI, integrasi, metadata pemain, dan database SQLite.
- Migrasi konfigurasi/database DEV-5 ke lokasi baru tanpa menimpa file tujuan yang sudah ada.
- Animasi roda Roulette yang melambat, dengan hasil ditentukan sebelum animasi dimulai.

### Diubah
- Halaman kategori Shop menampilkan 28 item per halaman agar rongga samping dan bingkai tetap utuh.
- File kategori dipindahkan ke `shop/`, pengaturan/diskon ke `shops/`, konfigurasi Auction ke `auctions/`, Casino ke `casino/`, dan GUI ke `gui/`.
- Lokasi default SQLite menjadi `data/database.db`.

### Diperbaiki
- Task animasi Roulette, state putaran aktif, dan cache batas jual harian dibersihkan agar state pemain tidak menumpuk.
- Putaran Roulette yang terputus dibatalkan dan taruhan dikembalikan saat inventory ditutup, pemain disconnect, atau plugin dinonaktifkan.

### Catatan
- `data/Playerdata.yml` disiapkan untuk metadata pemain persisten di masa depan; konfirmasi 21+ tetap hanya berlaku selama sesi.
- File DiscordSRV dan outgoing Discord webhook masih berupa placeholder konfigurasi; webhook masuk Saweria/Tebex menggunakan `integrations/saweria-tebex.yml`.


## [0.0.3-2026-DEV-5] - 2026-11-24
### Ditambahkan
- Modul Casino dengan konfirmasi mandiri usia 21+ dan Roulette memakai mata uang virtual Vault saja.
- Durasi kedaluwarsa listing Auction House yang dapat dipilih dengan permission khusus.
### Diubah
- Menghapus command Chest Shop, integrasi modul, dan referensi konfigurasinya dari plugin aktif.
- Halaman kategori Shop menggunakan grid kotak 4x9 dengan rail navigasi tujuh kategori.
- Kartu kategori Shop menampilkan jumlah item serta rentang harga beli/jual otomatis tanpa identifier internal.
- Menu utama Shop mempertahankan susunan kategori trapesium.

## [0.0.3-2026-DEV-4] - 2026-11-19
### Diperbaiki
- Katalog Material vanilla lengkap sekarang dituliskan ke file YAML kategori, bukan hanya disimpan di cache runtime.
- Setiap item hasil generate memiliki field buy-price, sell-price, dan currency yang eksplisit.
- Field harga dan currency yang belum ada pada YAML lama ditambahkan tanpa menimpa nilai yang sudah dikonfigurasi.
- Spawner mob yang didukung diperluas menjadi entri YAML eksplisit.
- Klasifikasi Redstone diperluas untuk rail, copper bulb, slime/honey block, dan komponen redstone lainnya.
- Shop dan seluruh perintah Sell menggunakan katalog item yang sama dan telah dikonfigurasi.

# Catatan Perubahan

### Perbaikan Build (2026-11-24)
- Memperbaiki kesalahan kompilasi Java 21 di `AuctionModule`: nama tampilan listing yang dipotong kini disimpan dalam variabel lokal final sebelum digunakan oleh lambda callback asinkron.
- Alur pembuatan listing, pengembalian item/refund, biaya listing, dan notifikasi sukses tetap dipertahankan.

## [0.0.3-2026-DEV-2] - 2026-11-19

### Ditambahkan
- Menambahkan modul Chest Shop bawaan dengan target chest, restock oleh pemilik, GUI jumlah pembelian, harga multi-currency per shop, template item terserialisasi yang persisten, dukungan alias double chest, serta permission pemain/admin.
- Menambahkan perlindungan transfer/pickup hopper pada Chest Shop terdaftar agar stok tidak dapat diambil tanpa GUI pembelian.
- Menambahkan `/donate` dengan tautan Saweria author yang dapat diklik.
- Menambahkan notifikasi Saweria HTTP Webhook opsional dengan validasi `Saweria-Callback-Signature`, broadcast nama donatur/nominal, batas ukuran request, dan de-duplikasi event.
- Menambahkan notifikasi Tebex `payment.completed` opsional dengan validasi `X-Signature` raw body, respons validasi endpoint, broadcast pembeli/nama paket/nominal, dan de-duplikasi event.
- Menambahkan pembuatan katalog vanilla otomatis pada kategori material default dan pembuatan spawner otomatis untuk tipe entitas hidup yang didukung.
- Menambahkan default `chestshop.yml` dan `integrations.yml` dengan penggabungan konfigurasi yang tidak menimpa nilai pengguna.

### Diubah
- Memperbarui versi artifact Maven menjadi `0.0.3-2026-DEV-2` dan menyamakan nama/path artifact GitHub Actions.
- Memperpendek label lore Shop, harga, ID mata uang, dan teks diskon agar tooltip tidak terlalu lebar.
- Memperpendek lore listing Auction House menjadi seller, harga/mata uang ringkas, waktu tersisa, dan aksi inspect.
- Mempertahankan judul inventory Auction House sebagai `Auction House` pada keempat tema dimensi.
- Mengaktifkan wildcard mata uang Auction House (`*`) agar semua mata uang yang terdaftar di ExcellentEconomy dapat digunakan; allowlist ID eksplisit tetap didukung.
- Memperbarui dokumentasi English dan Indonesia terkait Chest Shop, cakupan katalog vanilla, setup webhook Saweria/Tebex, dan persyaratan keamanan.

### Keamanan
- Callback webhook hanya diterima setelah validasi signature. Listener secara default bind ke localhost dan harus dipublikasikan melalui reverse proxy HTTPS.
- Menambahkan batas ukuran payload webhook dan penyimpanan ID event untuk mencegah notifikasi berulang saat provider melakukan retry.
- Menonaktifkan otomatisasi inventory hopper untuk chest shop terdaftar.

### Catatan
- Notifikasi Tebex tidak mengirim produk, menjalankan command, atau memberikan rank. Pengiriman paket tetap harus diatur di Tebex atau integrasi fulfillment khusus.
- Integrasi Saweria dan Tebex tetap nonaktif sampai diaktifkan dan dikonfigurasi menggunakan secret masing-masing.
- Chest Shop bawaan merupakan implementasi orisinal yang terinspirasi konsep QuickShop-Hikari; tidak bergantung pada atau mengimpor kode/API QuickShop-Hikari, dan tidak mengimpor data QuickShop yang sudah ada.
- Item MMOItems tetap dikonfigurasi secara eksplisit menggunakan type dan ID; hook runtime tidak melakukan enumerasi registry item kustom plugin pihak ketiga.
- Kompilasi Maven penuh dan pengujian Paper langsung tetap harus dijalankan melalui GitHub Actions atau lingkungan lokal yang memiliki dependency.

## [0.0.3-2026-DEV-1] - 2026-11-19

### Diperbaiki
- Menghapus method helper Shop duplikat yang mencegah kompilasi Java bersih.
- Memperbaiki ketiga file bahasa agar notifikasi perubahan tema Auction menjadi key di root YAML.
- Memastikan warna kategori/item yang sudah dikonfigurasi diparse apa adanya tanpa menambahkan palet warna kedua.
- Menghapus dekorasi italic bawaan dari nama item dan lore GUI secara eksplisit, sambil mempertahankan warna dan format lain yang sudah ada.
- Memusatkan baris Shop yang belum penuh dan merapikan frame simetris untuk layout tetap 3-5-7-9-9.

### Diubah
- Menyusun ulang inventory Auction House menjadi grid listing 7×4 di bawah masthead dimensi bertema, dilengkapi rail samping dan footer kontrol terpisah.
- Menambahkan emblem/header tema yang terlihat pada halaman Market, My Listings, dan Expired.
- Memperbarui lore listing, review pembelian, listing sendiri, arsip, dan pemilih tema dengan heading terstruktur serta simbol berbeda.
- Pemilih tema kini kembali ke halaman dan nomor halaman Auction House yang sama; pemilihan tema langsung memperbarui tampilan tersebut.
- Menambahkan kontrol penggantian tema pada Market, My Listings, Expired, dan layar Konfirmasi Pembelian; `/ah theme` membuka pemilih tema dan `/ah theme <dimension>` menerapkan tema langsung.
- Menyamakan latar GUI pemilih jumlah Shop dengan frame menu Shop lainnya.
- Memperbarui versi artifact Maven dan jalur artifact GitHub Actions menjadi `0.0.3-2026-DEV-1`.

### Catatan
- Tema Auction House yang dipilih berlaku sebagai preferensi pemain selama sesi. `gui.yml` mengatur tema default saat pemain join atau reconnect.
- Kompilasi Maven penuh dan pengujian Paper langsung tetap harus dijalankan melalui GitHub Actions atau lingkungan lokal yang memiliki dependency.


Semua perubahan penting OctaEconomy dicatat di file ini. Format mengikuti Keep a Changelog dan proyek menggunakan Semantic Versioning.

## [0.0.2-SNAPSHOT] - 2026-11-19

### Ditambahkan
- Menambahkan empat tema visual Auction House yang dapat dipilih: Nether, Overworld, Ocean, dan The End.
- Menambahkan frame inventory bertema, panel aksen, emblem, serta lore listing/konfirmasi yang lebih kaya.
- Mendesain ulang halaman kategori dan item Shop dengan susunan trapesium dari tengah berpola 3-5-7-9-9.
- Menambahkan panel latar dan aksen Shop yang dapat dikonfigurasi.
- Memperbarui lore item dan kategori Shop dengan simbol bervariasi, pemisah, bagian harga berwarna, dan panduan pembelian.
- Menambahkan `discounts.yml` untuk offer musiman bernama, jadwal tanggal opsional, filter kategori/item, display name MiniMessage, dan diskon maksimum 90%.
- Harga beli setelah diskon ditampilkan di lore Shop dan diterapkan pada transaksi pembelian; harga jual tidak berubah.
- Menambahkan penggabungan default konfigurasi untuk `gui.yml`, `auction.yml`, dan `discounts.yml` saat upgrade.
- Memperbarui GitHub Actions agar memasang Maven secara langsung, tanpa gagal ketika `./mvnw` tidak tersedia di root checkout.

### Diubah
- Memperbarui versi proyek menjadi `0.0.2-SNAPSHOT`.
- Memperbarui dokumentasi README dan instruksi build untuk GUI dan pengaturan diskon baru.

### Catatan
- Workflow build menggunakan Java 21 dan Maven.
- Pengujian runtime pada server penuh tetap diperlukan sebelum penggunaan di server produksi.

## [0.0.1-SNAPSHOT] - 2026-11-19

### Ditambahkan
- Struktur plugin modular awal dengan lifecycle interface `Module`.
- ShopGUI berkategori dan berpaginasi, kategori ditemukan dari YAML, serta pencarian item.
- Pemilih jumlah, aksi klik beli/jual, pembelian stack, dan pemeriksaan kapasitas inventory.
- SellGUI dengan pemrosesan item yang dapat dijual dan pengembalian item yang tidak dapat dijual.
- Command `/sell hand`, `/sell handall`, dan `/sell inv`.
- Sell multiplier berbasis permission, harga dinamis opsional, dan batas jual harian per pemain opsional.
- Auction House multi-currency dengan listing, pencarian, konfirmasi, pembatalan, kedaluwarsa, pengambilan item, dan notifikasi payout seller.
- Penyimpanan SQLite dengan serialisasi metadata item, query listing ber-index, pending delivery, dan pending payout.
- Mode koneksi MySQL/MariaDB yang dapat dikonfigurasi.
- Integrasi ekonomi Vault dan hook runtime untuk MMOItems, ExcellentEconomy, dan SmartSpawner.
- File bahasa English, Indonesian, dan Chinese Simplified dengan fallback English.
- Build descriptor Maven (`pom.xml`) dan skrip bootstrap Maven (`mvnw` / `mvnw.cmd`).
- `README.md`, `README_ID.md`, `LICENSE` kustom, dan class API event awal.
