# Catatan Perubahan

Semua perubahan penting OctaEconomy didokumentasikan di file ini.
Format mengikuti Keep a Changelog dan proyek menggunakan Semantic Versioning.

## [0.0.1-SNAPSHOT] - 2026-11-19

### Ditambahkan
- Struktur plugin modular awal dengan interface lifecycle `Module`.
- ShopGUI berkategori dan berpaginasi dengan deteksi kategori YAML serta pencarian item.
- Pemilih jumlah, aksi klik beli/jual, pembelian stack, dan pemeriksaan kapasitas inventory.
- SellGUI dengan pemrosesan item sellable dan pengembalian item yang tidak dapat dijual.
- Command `/sell hand`, `/sell handall`, dan `/sell inv`.
- Sell multiplier berbasis permission, harga dinamis opsional, dan batas jual harian opsional per pemain.
- Auction House multi-currency dengan alur listing, pencarian, konfirmasi, pembatalan, kedaluwarsa, pengambilan item, dan notifikasi payout kepada penjual.
- Penyimpanan SQLite dengan serialisasi metadata item, query listing terindeks, delivery tertunda, dan payout tertunda.
- Mode koneksi MySQL/MariaDB yang dapat dikonfigurasi.
- Integrasi ekonomi Vault dan hook runtime untuk MMOItems, ExcellentEconomy, serta SmartSpawner.
- File bahasa English, Indonesian, dan Chinese Simplified dengan fallback English.
- Build Maven melalui `pom.xml` dan wrapper bootstrap Maven (`mvnw` / `mvnw.cmd`).
- `README.md`, `README_ID.md`, `LICENSE` kustom, dan class event API awal.
