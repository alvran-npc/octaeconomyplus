# OctaEconomy

**Plugin ekonomi serba ada dengan Shop GUI berkategori, Sell GUI, dan Auction House multi-currency. Terintegrasi penuh dengan Vault dan EssentialsX.**

Author: **Alvran** · [GitHub](https://github.com/alvran-npc) · Versi: `0.0.3-2026-DEV-2`

OctaEconomy adalah proyek plugin Paper orisinal. Plugin ini menyediakan toko yang dapat dikonfigurasi, penjualan item dari inventory, dan rumah lelang berbasis database tanpa memerlukan plugin shop atau auction lain.

## Fitur

- Menu toko berkategori yang dimuat dari satu file YAML untuk setiap kategori.
- Katalog vanilla lengkap yang dibuat otomatis: seluruh `Material` Paper reguler yang dapat menjadi item ditempatkan ke kategori vanilla yang sesuai; material teknis difilter dan spawner dibuat untuk tipe entitas hidup yang didukung. Entry YAML eksplisit akan mengungguli harga/item otomatis.
- Menu kategori berpaginasi dengan susunan trapesium dari tengah berpola 3-5-7-9-9, aksen kaca dekoratif, pencarian item, pemilih jumlah, pembelian stack dengan shift-click, dan aksi klik kiri/kanan yang dapat dikonfigurasi.
- Harga beli dan jual per item, pemilihan mata uang per item, serta template item vanilla, MMOItems, dan spawner.
- Sell GUI dengan pengembalian item yang tidak dapat dijual.
- `/sell hand`, `/sell handall`, dan `/sell inv` dengan multiplier jual berbasis permission dan ringkasan transaksi per mata uang.
- Harga dinamis dan batas jual item harian opsional (keduanya nonaktif secara default).
- Listing Auction House disimpan menggunakan SQLite secara default, dengan dukungan konfigurasi MySQL/MariaDB.
- Listing lelang multi-currency dengan tema visual Nether, Overworld, Ocean, dan The End, biaya listing, pajak, batas listing, kedaluwarsa, pembatalan, pencarian, dan menu konfirmasi.
- Diskon shop musiman dari `discounts.yml`, dengan rentang tanggal opsional dan filter kategori/item. Harga diskon tampil pada lore ringkas dan digunakan dalam transaksi pembelian.
- Chest Shop bawaan dengan stok item per peti, harga/mata uang yang dapat dipilih, GUI pembeli, restock oleh pemilik, data shop persisten, perlindungan hopper, dan payout melalui Vault atau ExcellentEconomy.
- Link dukungan Saweria yang dapat diklik melalui `/donate`, serta notifikasi donasi Saweria dan pembelian `payment.completed` Tebex melalui webhook bertanda tangan (opsional).
- Pengiriman item tertunda dan payout seller tertunda disimpan di database.
- File pesan English, Indonesian, dan Chinese Simplified dengan fallback ke English.
- Hook runtime untuk MMOItems, ExcellentEconomy, dan SmartSpawner. Spawner vanilla tetap tersedia sebagai fallback.

## Persyaratan

| Komponen | Persyaratan |
|---|---|
| Server | Paper 1.21.x (plugin memakai deklarasi kompatibilitas Bukkit `api-version: '1.20'`) |
| Java | Java 21 |
| Plugin wajib | Vault dan EssentialsX (nama plugin `Essentials`) |
| Plugin opsional | MMOItems, ExcellentEconomy, SmartSpawner |
| Chest Shop | Bawaan; tidak membutuhkan QuickShop-Hikari |
| Callback donasi/store | Saweria HTTP Webhook dan Tebex Webhooks (opsional, dikonfigurasi dengan secret) |
| Ekonomi | Provider ekonomi Vault yang berfungsi, biasanya disediakan oleh EssentialsX |
| Database | Driver JDBC SQLite bawaan Paper untuk mode SQLite; konektor MySQL dimuat oleh Paper ketika mode MySQL digunakan |

Proyek dikompilasi terhadap Paper API `1.21.4-R0.1-SNAPSHOT`. Source sengaja menghindari kode NMS yang bergantung pada versi tertentu.

## Instalasi

1. Pasang Java 21 dan server Paper 1.21.x yang didukung.
2. Pasang dan jalankan Vault serta EssentialsX. Pastikan EssentialsX mendaftarkan provider ekonomi melalui Vault.
3. Salin `OctaEconomy-0.0.3-2026-DEV-2.jar` ke direktori `plugins/` server.
4. Jalankan server sekali. OctaEconomy membuat konfigurasi default, file bahasa, file kategori toko, pengaturan Chest Shop, pengaturan webhook, dan direktori database lelang ketika database diinisialisasi.
5. Sesuaikan `plugins/OctaEconomy/config.yml`, `auction.yml`, `gui.yml`, `discounts.yml`, dan file di dalam folder `shops/`.
6. Restart server setelah mengubah detail koneksi database atau pengaturan library Paper. Gunakan `/oeco reload` untuk perubahan konfigurasi biasa, bahasa, dan file shop.

## Cara penggunaan

### Shop

- `/shop` membuka menu kategori.
- `/shop blocks` membuka kategori `blocks` secara langsung.
- `/shop search diamond` menampilkan item toko yang cocok.
- Klik item untuk membuka pemilih jumlah. Pemilih menyediakan 1, 16, 32, 64, serta kontrol plus/minus untuk jumlah khusus.
- Shift-left-click membeli satu stack berjumlah 64. Right-click menjual satu item yang cocok dari storage pemain saat aksi klik kanan disetel ke `sell`.
- Pembelian ditolak jika ruang inventory tidak cukup. Plugin menarik uang sebelum memberikan item dan melakukan refund jika penambahan item gagal secara tidak terduga.

### Sell GUI dan perintah jual

- `/sellgui` atau `/sellg` membuka inventory penjualan.
- Masukkan item ke GUI lalu tutup. Item yang dapat dijual akan dibayar ke mata uang terkait. Item yang tidak dapat dijual dikembalikan ke inventory pemain atau di-drop dengan aman di lokasi pemain jika inventory penuh.
- `/sell hand` menjual stack di main hand.
- `/sell handall` menjual semua stack yang cocok di storage pemain.
- `/sell inv` menjual semua item sellable yang dikonfigurasi di storage pemain.
- Multiplier permission menggunakan node seperti `octaeconomy.sell.multiplier.1.5`. Multiplier positif tertinggi yang dimiliki pemain akan dipilih.

### Cakupan katalog default

Delapan kategori vanilla dengan `auto-include-vanilla: true` diisi dari registry `Material` Paper saat startup/reload, bukan hanya dari entry contoh YAML. Semua material reguler yang dapat menjadi item dimasukkan ke kategori yang sesuai; material teknis/khusus Creative difilter. Entry kustom dalam YAML kategori akan menggantikan harga otomatis untuk material tersebut. Kategori Spawners juga membuat entry untuk `EntityType` hidup yang didukung. Kategori MMOItems opsional tetap perlu diisi menggunakan pasangan `type` + `id` pada `shops/mmoitems.yml`; registry item pihak ketiga tidak dienumerasi otomatis pada versi ini.

Harga otomatis adalah harga awal dari bagian `auto-pricing` setiap kategori. Tambahkan material secara eksplisit ke YAML kategori untuk menentukan harga/mata uang khusus, atau ubah `auto-pricing.buy-base`, `sell-ratio`, dan `currency` untuk item yang dibuat otomatis.

### Menambahkan item ke kategori toko

Buka `plugins/OctaEconomy/shops/blocks.yml`, lalu tambahkan entry di bawah `items`:

```yaml
items:
  andesite:
    material: ANDESITE
    buy-price: 7.0
    sell-price: 1.5
    currency: vault
```

Gunakan `-1` atau hilangkan harga untuk menonaktifkan jenis transaksi tersebut. Muat ulang dengan `/oeco reload`.

### Menambahkan kategori baru

Buat file YAML baru di `plugins/OctaEconomy/shops/`, misalnya `ores.yml`:

```yaml
category:
  id: ores
  display-name: "&bOres"
  icon: DIAMOND_ORE
  slot: 22
  permission: ""
items:
  diamond_ore:
    material: DIAMOND_ORE
    buy-price: 500.0
    sell-price: 100.0
    currency: vault
```

Kategori akan ditemukan dari file YAML. `id` harus unik dan menggunakan huruf kecil. Permission khusus kategori dapat diatur melalui `category.permission`. Nilai `category.slot` hanya mengatur urutan kategori; OctaEconomy tetap menempatkan kategori pada layout trapesium simetris dan tidak memakai nilai tersebut sebagai slot inventory mentah.

### Menambahkan mata uang ExcellentEconomy

1. Pasang dan jalankan ExcellentEconomy.
2. Cari ID mata uang persis seperti yang dikonfigurasi di ExcellentEconomy.
3. Secara default, `settings.allowed-currencies` sudah berisi `"*"`, sehingga semua mata uang yang terdaftar di ExcellentEconomy langsung diizinkan. Jika wildcard dihapus untuk membuat allowlist ketat, tambahkan ID mata uang yang tepat pada bagian tersebut.
4. Gunakan ID yang sama sebagai nilai `currency` pada item shop, atau berikan pada `/ah sell <price> <currency>`.
5. Muat ulang plugin. Hook akan mencari API ExcellentEconomy melalui service registry Bukkit saat runtime.

API tersebut menyediakan operasi pemain yang menggunakan cache untuk saldo, deposit, dan withdraw. Payout ExcellentEconomy untuk seller offline tetap tertunda hingga pemain masuk; provider Vault dapat mendukung deposit offline secara langsung.

### Chest Shop

- Pegang item yang ingin dijual, arahkan pandangan ke chest/trapped chest, lalu jalankan `/chestshop create <price> [currency]`.
- Pemilik chest dapat klik kanan shop chest untuk membuka inventory normal dan melakukan restock dengan item yang cocok. Pemain lain melakukan klik kanan untuk membuka GUI pembelian.
- Pembeli dapat membeli 1, 16, atau 64 item. Transaksi memeriksa stok, kapasitas inventory, ketersediaan mata uang, dan payout pemilik sebelum selesai.
- `/chestshop info` menampilkan pemilik, item, harga, mata uang, dan stok saat ini. `/chestshop remove` menghapus pendaftaran shop; isi chest tetap berada di dalam chest.
- Contoh: `/chestshop create 25 gems` membuat shop dengan harga 25 unit mata uang `gems`. Fitur ini dapat memakai ID mata uang dari provider terdaftar, termasuk ExcellentEconomy. Pemilik shop dengan mata uang ExcellentEconomy harus online saat hasil pembelian dikreditkan; provider Vault dapat mendukung deposit offline.
- Ini adalah Chest Shop bawaan yang ditulis secara orisinal dengan referensi konseptual QuickShop-Hikari dari pengguna. Kode/API QuickShop-Hikari tidak disalin atau dijadikan dependency; shop yang sudah dibuat oleh QuickShop tidak diimpor.

### Auction House

- `/ah` membuka market.
- `/ah sell 2500` melelang stack di main hand dengan mata uang default.
- `/ah sell 2500 gems` melelangnya dengan mata uang `gems` yang sudah dikonfigurasi.
- Klik listing, periksa detailnya, lalu konfirmasi pembelian.
- `/ah search sword` mencari listing aktif.
- `/ah mylistings` membuka listing aktif milik seller. Klik salah satu untuk membatalkannya.
- `/ah expired` menampilkan listing kedaluwarsa. `/ah collect` mengambil item pembelian, item kedaluwarsa, atau item yang listing-nya dibatalkan.

Listing disimpan menggunakan `ItemStack#serializeAsBytes`, sehingga metadata item Bukkit dan metadata dari plugin lain tetap dipertahankan. Status pembelian, pengiriman item ke pembeli, dan catatan payout seller di-commit bersama dalam satu transaksi database. Seller tidak dapat membeli listing miliknya sendiri.

## Commands

| Command | Keterangan |
|---|---|
| `/shop [category]` | Membuka shop atau kategori tertentu |
| `/shop search <keyword>` | Mencari item shop |
| `/sellgui`, `/sellg` | Membuka Sell GUI |
| `/sell hand` | Menjual stack di main hand |
| `/sell handall` | Menjual semua stack sejenis di storage |
| `/sell inv` | Menjual semua item sellable yang dikonfigurasi |
| `/ah`, `/auction` | Membuka Auction House |
| `/ah sell <price> [currency]` | Melelang item di main hand |
| `/ah expired` | Melihat listing kedaluwarsa |
| `/ah collect` | Mengambil item lelang tertunda |
| `/ah search <keyword>` | Mencari listing aktif |
| `/ah mylistings` | Mengelola listing aktif sendiri |
| `/ah theme [dimension]` | Membuka pemilih tema atau menerapkan `nether`, `overworld`, `ocean`, atau `the_end` |
| `/chestshop create <price> [currency]` | Membuat shop pada chest yang ditarget saat memegang item |
| `/chestshop remove` | Menghapus pendaftaran shop yang ditarget |
| `/chestshop info` | Melihat stok dan harga shop yang ditarget |
| `/donate` | Menampilkan tautan dukungan Saweria author |
| `/octaeconomy reload`, `/oeco reload` | Memuat ulang config, bahasa, dan file shop |
| `/octaeconomy version` | Menampilkan versi plugin dan author |
| `/octaeconomy help` | Menampilkan bantuan command |
| `/octaeconomy debug` | Menampilkan status dasar cache dan database |

## Permissions

| Permission | Default | Fungsi |
|---|---|---|
| `octaeconomy.shop.use` | true | Menggunakan shop |
| `octaeconomy.shop.category.<id>` | true untuk kategori yang ditemukan | Node akses kategori; `category.permission` dapat memberi pembatasan tambahan |
| `octaeconomy.sell.use` | true | Menggunakan perintah jual |
| `octaeconomy.sell.gui` | true | Membuka Sell GUI |
| `octaeconomy.sell.hand` | true | Menggunakan `/sell hand` |
| `octaeconomy.sell.handall` | true | Menggunakan `/sell handall` |
| `octaeconomy.sell.inv` | true | Menggunakan `/sell inv` |
| `octaeconomy.sell.multiplier.<value>` | false | Memberikan multiplier jual, misalnya `.1.5` |
| `octaeconomy.auction.use` | true | Menggunakan Auction House |
| `octaeconomy.auction.sell` | true | Membuat listing lelang |
| `octaeconomy.auction.limit.<n>` | false | Mengganti batas listing aktif dasar dengan angka permission tertinggi |
| `octaeconomy.chestshop.use` | true | Menggunakan command Chest Shop |
| `octaeconomy.chestshop.create` | true | Membuat Chest Shop |
| `octaeconomy.chestshop.admin` | op | Menghapus/mengelola Chest Shop siapa pun |
| `octaeconomy.chestshop.limit.bypass` | op | Melewati batas jumlah Chest Shop per pemain |
| `octaeconomy.admin` | op | Reload dan debug |

## Konfigurasi

### `config.yml`

- `language`: `en`, `id`, atau `zh`.
- `database.type`: `sqlite` (default) atau `mysql`.
- `database.sqlite-file`: lokasi SQLite relatif terhadap folder data plugin.
- `database.mysql`: pengaturan koneksi MySQL.
- `economy.default-currency`: mata uang yang digunakan jika item shop atau command lelang tidak menyebutkannya.
- `shop.click-actions`: aksi klik kiri dan kanan (`buy` atau `sell`).
- `shop.dynamic-price`: batas fluktuasi dan recovery harga opsional; nonaktif secara default.
- `shop.daily-limit`: jumlah default dan batas jumlah harian per item opsional; nonaktif secara default.
- `shop.sell-multiplier.enabled`: mengaktifkan multiplier jual berbasis permission.
- `sound`: suara sukses dan error.
- `transaction-log.enabled`: mengaktifkan log transaksi harian yang dirotasi. Nonaktif secara default.

`config-version` diperiksa saat startup. Key default yang belum ada akan digabungkan ke konfigurasi pengguna tanpa menimpa nilai yang sudah ada.

### `gui.yml`

- `shop.background` dan `shop.accent` mengatur panel di sekitar trapesium simetris 3-5-7-9-9. Baris yang belum penuh diposisikan secara simetris; jumlah item genap diseimbangkan di kiri dan kanan dengan satu slot tengah yang sengaja kosong, bukan menempel ke kiri.
- `auction.theme` menetapkan tema default: `nether`, `overworld`, `ocean`, atau `the_end`. Pemain dapat menggantinya lewat tombol tema di footer Auction House, atau menggunakan `/ah theme` dan `/ah theme <dimension>`. Tema langsung diterapkan dan berlaku selama sesi pemain; setelah reconnect, plugin menggunakan tema default dari konfigurasi.
- Setiap tema memiliki `title`, `background`, `accent`, `emblem`, `tagline`, dan `price-style` pada `auction.themes.<theme>`. Warna legacy atau MiniMessage yang sudah ada di nama item/kategori dipertahankan tanpa dibungkus palet warna lain. Lore GUI secara eksplisit tidak italic.

### `discounts.yml`

Aktifkan diskon musiman dengan mengatur `enabled: true` di bagian utama, memilih key pada `active`, lalu mengatur `enabled: true` pada offer tersebut. Contoh:

```yaml
enabled: true
active: christmas
offers:
  christmas:
    enabled: true
    display-name: "<gradient:#ff5454:#65d98b><bold>CHRISTMAS FESTIVAL</bold></gradient>"
    percent: 25.0
    starts-at: "2026-12-20T00:00:00"
    ends-at: "2027-01-05T23:59:59"
    categories: []
    items: []
```

Tanggal menggunakan format ISO local date-time dan zona waktu lokal server. Kosongkan `starts-at` dan `ends-at` untuk offer yang dikontrol manual. List `categories` dan `items` yang kosong berarti diskon berlaku untuk semua item shop. Jika kedua list diisi, item harus cocok dengan kedua filter. Filter item menerima ID item atau format `category-id:item-id`. Diskon hanya memengaruhi harga beli; harga jual tidak berubah. Baris diskon tidak ditampilkan di lore jika tidak ada offer aktif yang cocok. Jalankan `/oeco reload` setelah mengubah file. Batas diskon maksimal 90%.

### `auction.yml`

Mengatur durasi listing, pajak, biaya listing, rentang harga maksimum, jumlah listing, mata uang yang diizinkan, interval pemeriksaan kedaluwarsa, dan blacklist. Batas harga per mata uang dapat dibuat di `currency-limits.<currency>.minimum` dan `.maximum`.

### File bahasa

Edit `lang/en.yml`, `lang/id.yml`, dan `lang/zh.yml`. Semua key pesan saat ini tersedia di ketiga file bahasa. Atur `language: id` untuk Bahasa Indonesia atau `language: zh` untuk Chinese Simplified. Key yang hilang akan menggunakan fallback English.

## Integrasi

- **Vault + EssentialsX:** Wajib. OctaEconomy membaca dan menulis saldo melalui provider ekonomi Vault yang aktif; plugin tidak membuat penyimpanan saldo tandingan.
- **MMOItems:** Opsional. Definisi item shop dapat memakai `mmoitems.type` dan `mmoitems.id`. Jika plugin atau API tidak tersedia, entry tersebut dilewati dan item vanilla tetap aktif.
- **ExcellentEconomy:** Opsional. ID mata uang dikonfigurasi melalui API service ExcellentEconomy. Hook dinonaktifkan dengan aman jika plugin/API tidak tersedia.
- **SmartSpawner:** Opsional. Entry kategori spawner akan mencoba API pembuatan item SmartSpawner terlebih dahulu dan memakai item spawner vanilla sebagai fallback jika hook tidak tersedia.

Integrasi opsional ditemukan saat runtime. OctaEconomy tidak membundel, melakukan shade, atau menyalin class dari plugin lain.

### `chestshop.yml`

Mengatur modul Chest Shop bawaan: `enabled`, batas shop per pemain, dan jarak maksimum target chest saat membuat/menghapus shop. Lokasi shop dan template item terserialisasi disimpan pada `chestshops.yml` dan ditulis secara async.

### `integrations.yml` — Notifikasi Saweria dan Tebex

Listener webhook tidak berjalan sampai setidaknya satu integrasi diaktifkan dan secret-nya diisi. Secara default listener bind ke `127.0.0.1:8765`. Pertahankan listener di localhost dan publikasikan melalui reverse proxy HTTPS; jangan mengekspos listener HTTP biasa plugin secara langsung ke internet. Reverse proxy harus mempertahankan raw request body dan header signature.

**Saweria:**

1. Di Saweria, atur URL HTTP Webhook seperti `https://domain-kamu.example/octaeconomy/saweria`.
2. Atur `webhooks.enabled: true`, `webhooks.saweria.enabled: true`, dan masukkan Streaming Key Saweria ke `webhooks.saweria.streaming-key`.
3. Arahkan path HTTPS publik ke `http://127.0.0.1:8765/saweria` (atau sesuaikan dengan `webhooks.paths.saweria`).
4. OctaEconomy memvalidasi `Saweria-Callback-Signature` dan mencegah event ID yang sama diumumkan berulang sebelum menyiarkan nama donatur dan nominal ke pemain online.

HTTP Webhook Saweria mengirim JSON POST dan mendokumentasikan signature sebagai HMAC-SHA256 dari gabungan berurutan field `version`, `id`, `amount_raw`, `donator_name`, dan `donator_email`. Lihat [dokumentasi HTTP Webhook Saweria](https://saweria.co/docs/webhook).

**Tebex:**

1. Di Tebex, buka **Developers → Webhooks → Endpoints**, tambahkan URL callback HTTPS publik, dan pilih event `payment.completed`.
2. Salin endpoint secret ke `webhooks.tebex.secret` lalu atur `webhooks.tebex.enabled: true`.
3. Arahkan path publik ke `http://127.0.0.1:8765/tebex` (atau sesuaikan dengan `webhooks.paths.tebex`).
4. Callback validasi Tebex akan menerima respons JSON `200` berisi `id` sesuai kebutuhan validasi endpoint. Pembayaran selesai akan menyiarkan nama pembeli, maksimal tiga nama paket, dan jumlah pembayaran.

OctaEconomy memverifikasi `X-Signature` Tebex menggunakan raw request body dan endpoint secret. Notifikasi hanya bersifat informasi: pengiriman paket Tebex, command, rank, dan pemenuhan hak akses tetap harus diatur di Tebex atau integrasi fulfillment terpisah. Lihat [dokumentasi Webhook Tebex](https://docs.tebex.io/developers/webhooks/overview).

Template MiniMessage `notifications.saweria` dan `notifications.tebex` dapat diubah. Placeholder yang tersedia mencakup `{donator}`, `{amount}`, `{message}`, `{player}`, `{packages}`, dan `{transaction}`. ID event disimpan di `data/webhook-events.log` agar retry dari penyedia webhook tidak mengulang notifikasi.

## Catatan performa dan keamanan

- Operasi JDBC Auction dijalankan secara serial pada worker daemon khusus; operasi inventory Bukkit dan pemanggilan event dikembalikan ke server thread.
- SQLite menggunakan WAL mode, normal synchronous mode, busy timeout, dan index untuk pencarian/status listing.
- Pemeriksaan expiry berukuran kecil dan dibatasi (maksimal 100 listing per iterasi); tidak melakukan scan seluruh tabel pada main thread.
- YAML shop dan template item di-cache saat load. Listener GUI langsung berhenti jika inventory bukan milik OctaEconomy.
- UUID pemain digunakan sebagai identitas pemilik lelang. Perubahan status listing dan pembuatan catatan delivery/payout tertunda di-commit secara atomik.
- Penghitung jual harian dan pengali harga dinamis berada di memori dan reset setelah restart. Penghitung harian tetap disimpan ketika pemain disconnect selama proses server yang sama.
- GUI Auction House menyediakan tema dimensi yang dapat dikonfigurasi, pencarian, pengelolaan listing, dan konfirmasi, tetapi filter kategori tingkat lanjut serta pengurutan harga/waktu yang dapat dikonfigurasi belum tersedia. Placeholder lore shop seperti `{buy}`, `{sell}`, `{stock}`, `{category}`, dan `{currency}` juga belum dapat dikonfigurasi.
- Pembelian menarik saldo pembeli melalui provider ekonomi eksternal sebelum transaksi SQL mengunci listing. Crash proses mendadak pada jeda singkat itu dapat membutuhkan rekonsiliasi manual; transaksi plugin/database tidak dapat meng-commit transaksi Vault eksternal secara atomik.
- Catatan delivery dan payout baru dihapus setelah operasi inventory/ekonomi eksternal berhasil. Crash proses mendadak di antara deposit Vault atau penambahan item dengan konfirmasi database dapat menyebabkan duplikasi saat recovery; exactly-once tidak dapat dijamin tanpa dukungan idempotensi dari kedua sistem eksternal.
- Item Sell GUI dikembalikan saat GUI ditutup normal, pemain disconnect, atau plugin dinonaktifkan. Crash OS/proses mendadak ketika GUI masih terbuka dapat terjadi sebelum close event berjalan; versi pertama ini belum dapat dianggap sebagai escrow yang tahan crash.
- Plugin membaca dan menulis melalui provider ekonomi Vault yang sedang aktif; plugin tidak mendaftarkan provider ekonomi fallback yang dapat berbenturan. Pasang EssentialsX dan pastikan provider Vault-nya aktif.
- Region scheduler Folia belum dibuat atau diverifikasi. Gunakan Paper standar, bukan Folia, untuk rilis ini.

## Build dari source

Persyaratan: JDK 21, Apache Maven 3.8.6 atau lebih baru, dan akses jaringan ke Maven repository yang dikonfigurasi. Jalankan perintah berikut dari root proyek (folder yang berisi `pom.xml`):

```bash
mvn --batch-mode --no-transfer-progress clean package
```

Workflow GitHub Actions memasang Maven secara langsung dan tidak bergantung pada keberadaan file Maven Wrapper di root repository. Kamu juga dapat memakai skrip bootstrap `mvnw` secara lokal jika file tersebut tersedia.

File output:

```text
target/OctaEconomy-0.0.3-2026-DEV-2.jar
```

Proyek Maven menggunakan scope `provided` untuk Paper API dan Vault API serta tidak melakukan shade dependency. Paper menyediakan driver JDBC SQLite. Saat `database.type: mysql` digunakan, Paper memuat konektor MySQL yang dideklarasikan di `plugin.yml`.

## FAQ dan troubleshooting

**Auction House menampilkan pesan storage masih dimulai.** Database diinisialisasi secara asynchronous saat startup. Tunggu pesan `Auction storage ready`. Jika inisialisasi gagal, periksa error startup serta direktori SQLite atau kredensial MySQL.

**Item MMOItems tidak muncul di shop.** Pastikan plugin MMOItems aktif dan type serta ID item valid. Entry tidak valid dilewati dengan warning yang menyebut file dan key.

**Mata uang ExcellentEconomy tidak dapat dipilih.** Tambahkan ID mata uang persis ke `auction.yml` dan pastikan ExcellentEconomy telah mendaftarkan API service-nya. Vault tetap menjadi mata uang default.

**MySQL gagal terhubung.** Buat database lebih dahulu, periksa kredensial dan firewall, lalu set `database.type: mysql`. Paper harus dapat memuat library konektor MySQL yang dideklarasikan saat startup.

**Seller offline belum menerima payout ExcellentEconomy.** Operasi cache provider tersebut membutuhkan pemain online. Payout tetap tertunda dan dicoba kembali saat pemain join.

**Server tidak menyala karena Essentials hilang.** EssentialsX adalah dependency wajib pada build ini. Pasang EssentialsX dan Vault sebelum mengaktifkan OctaEconomy.

## Lisensi & Kepemilikan

OctaEconomy **100% gratis**. Penjualan ulang, akses paywall, akses yang mensyaratkan donasi wajib, dan bundling plugin ke produk berbayar dilarang. Fork dan modifikasi harus tetap gratis, tetap mencantumkan kredit **Alvran**, dan tidak boleh mengklaim kepemilikan asli. Satu-satunya author/pemilik asli adalah **Alvran**: https://github.com/alvran-npc. Baca [`LICENSE`](LICENSE) untuk lisensi kustom lengkap.

## Credits

- Author asli: **Alvran** — https://github.com/alvran-npc
- Dibuat untuk ekosistem Paper/Bukkit API dan ekonomi Vault.
- MMOItems, ExcellentEconomy, dan SmartSpawner merupakan integrasi runtime opsional.
- QuickShop-Hikari hanya menjadi referensi konseptual chest shop; Chest Shop bawaan ditulis secara orisinal dan independen.
- Dokumentasi EconomyShopGUI dan AxAuctions hanya dipertimbangkan sebagai inspirasi UX tingkat tinggi. Tidak ada plugin referensi yang menjadi dependency runtime dan tidak ada source code plugin referensi yang disertakan.
