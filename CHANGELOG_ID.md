# Catatan Perubahan

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
