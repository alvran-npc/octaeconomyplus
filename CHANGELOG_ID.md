# Catatan Perubahan

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
