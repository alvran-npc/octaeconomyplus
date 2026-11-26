# OctaEconomy

**Plugin ekonomi serba ada dengan Shop GUI berkategori, Sell GUI, dan Auction House multi-currency. Terintegrasi penuh dengan Vault dan EssentialsX.**

Author: **Alvran** · [GitHub](https://github.com/alvran-npc) · Versi: `0.0.3-2026`

OctaEconomy adalah proyek plugin Paper orisinal. Plugin ini menyediakan toko yang dapat dikonfigurasi, penjualan item dari inventory, dan rumah lelang berbasis database tanpa memerlukan plugin shop atau auction lain.

## Fitur

- Menu toko berkategori yang dimuat dari satu file YAML untuk setiap kategori.
- Katalog vanilla lengkap yang dibuat otomatis: seluruh `Material` Paper reguler yang dapat menjadi item ditempatkan ke kategori vanilla yang sesuai; material teknis difilter dan spawner dibuat untuk tipe entitas hidup yang didukung. Entry YAML eksplisit akan mengungguli harga/item otomatis.
- Menu kategori berpaginasi dengan susunan trapesium dari tengah berpola 3-5-7-7, aksen kaca dekoratif, pencarian item, pemilih jumlah, pembelian stack dengan shift-click, dan aksi klik kiri/kanan yang dapat dikonfigurasi.
- Harga beli dan jual per item, pemilihan mata uang per item, serta template item vanilla, MMOItems, dan spawner.
- Sell GUI dengan pengembalian item yang tidak dapat dijual.
- `/sell hand`, `/sell handall`, dan `/sell inv` dengan multiplier jual berbasis permission dan ringkasan transaksi per mata uang.
- Harga dinamis dan batas jual item harian opsional (keduanya nonaktif secara default).
- Listing Auction House disimpan menggunakan SQLite secara default, dengan dukungan konfigurasi MySQL/MariaDB.
- Listing lelang multi-currency dengan tema visual Nether, Overworld, Ocean, dan The End, biaya listing, pajak, batas listing, kedaluwarsa, pembatalan, pencarian, dan menu konfirmasi.
- Diskon shop musiman dari `shops/discount.yml`, dengan rentang tanggal opsional dan filter kategori/item. Harga diskon tampil pada lore ringkas dan digunakan dalam transaksi pembelian.
|---|---|
| Server | Paper 1.21.x (plugin memakai deklarasi kompatibilitas Bukkit `api-version: '1.20'`) |
| Java | Java 21 |
| Plugin wajib | Vault dan EssentialsX (nama plugin `Essentials`) |
| Plugin opsional | MMOItems, ExcellentEconomy, SmartSpawner |
1. Pasang Java 21 dan server Paper 1.21.x yang didukung.
2. Pasang dan jalankan Vault serta EssentialsX. Pastikan EssentialsX mendaftarkan provider ekonomi melalui Vault.
3. Salin `OctaEconomy-0.0.3-2026.jar` ke direktori `plugins/` server.
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

Delapan kategori vanilla dengan `auto-include-vanilla: true` diisi dari registry `Material` Paper saat startup/reload, bukan hanya dari entry contoh YAML. Semua material reguler yang dapat menjadi item dimasukkan ke kategori yang sesuai; material teknis/khusus Creative difilter. Entry kustom dalam YAML kategori akan menggantikan harga otomatis untuk material tersebut. Kategori Spawners juga membuat entry untuk `EntityType` hidup yang didukung. Kategori MMOItems opsional tetap perlu diisi menggunakan pasangan `type` + `id` pada `shop/mmoitems.yml`; registry item pihak ketiga tidak dienumerasi otomatis pada versi ini.

Harga otomatis adalah harga awal dari bagian `auto-pricing` setiap kategori. Tambahkan material secara eksplisit ke YAML kategori untuk menentukan harga/mata uang khusus, atau ubah `auto-pricing.buy-base`, `sell-ratio`, dan `currency` untuk item yang dibuat otomatis.

### Menambahkan item ke kategori toko

Buka `plugins/OctaEconomy/shops/category/blocks.yml`, lalu tambahkan entry di bawah `items`:

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

Buat file YAML baru di `plugins/OctaEconomy/shops/category/`, misalnya `ores.yml`:

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
| `octaeconomy.auction.expire.custom` | op | Mengatur durasi khusus pada `/ah sell` |
| `octaeconomy.casino.use` | true | Membuka Casino dan menu konfirmasi usia |
| `octaeconomy.casino.roulette` | true | Bermain Roulette dengan mata uang Vault virtual |
Pada startup pertama dan setiap `/octaeconomy reload`, OctaEconomy memperluas setiap kategori shop vanilla berdasarkan registry `Material` dari server Paper yang sedang berjalan, lalu menuliskan seluruh daftar ke `plugins/OctaEconomy/shop/*.yml`. Setiap item yang dibuat akan memiliki field eksplisit `material`, `buy-price`, `sell-price`, dan `currency`. Spawner mob yang didukung juga dibuat dengan registry `EntityType`. Entri serta harga manual yang sudah ada tidak ditimpa; plugin hanya menambahkan item atau field yang belum ada. Bagian `catalog-defaults` hanya menentukan harga awal untuk material baru yang ditemukan, bukan harga tersembunyi di memori. Material teknis/admin yang tidak dapat diperoleh secara normal (misalnya command block, barrier, bedrock, dan air) sengaja tidak dimasukkan ke shop publik.

`/sell hand`, `/sell handall`, `/sell inv`, dan SellGUI menggunakan katalog item yang sama, sehingga `sell-price` yang dikonfigurasi berlaku untuk Shop maupun fitur Sell. Spawner secara default hanya dapat dibeli (`sell-price: -1.00`) untuk mencegah penjualan spawner dari item admin.

## Konfigurasi

### `config.yml`

- `language`: `en`, `id`, atau `zh`.
- `database.type`: `sqlite` (default) atau `mysql`.
- `database.sqlite-file`: lokasi SQLite relatif terhadap folder data plugin.
- `database.mysql`: pengaturan koneksi MySQL.
- `economy.default-currency`: mata uang yang digunakan jika item shop atau command lelang tidak menyebutkannya.
- Pengaturan Shop kini berada di bagian `shop:` pada `config.yml` (sebelumnya `shops/settings.yml`).
- `shop.click-actions`: aksi klik kiri dan kanan (`buy` atau `sell`).
- `shop.sorting`: `mode` (`grouped`, `alphabetical`, `file`) dan `alphabetical-categories` (default `spawners`, `mobs_drop`, `redstone`). Mode grouped menjaga item terkait tetap berdekatan, misalnya semua item oak berdampingan.
- `shop.dynamic-price`: batas fluktuasi dan recovery harga opsional; nonaktif secara default.
- `shop.daily-limit`: jumlah default dan batas jumlah harian per item opsional; nonaktif secara default.
- `shop.sell-multiplier.enabled`: mengaktifkan multiplier jual berbasis permission.
- `sound`: suara sukses dan error.
- `transaction-log.enabled`: mengaktifkan log transaksi harian yang dirotasi. Nonaktif secara default.

`config-version` diperiksa saat startup. Key default yang belum ada akan digabungkan ke konfigurasi pengguna tanpa menimpa nilai yang sudah ada.

### Struktur folder data saat runtime

```text
plugins/OctaEconomy/
├── config.yml                      # opsi umum + pengaturan Shop (shop:)
├── data/
│   ├── PlayerData.yml
│   └── database.db                 # dibuat SQLite saat startup pertama
├── lang/
│   └── message/                    # en.yml, id.yml, zh.yml
├── shops/
│   ├── discount.yml
│   └── category/                   # blocks.yml, redstone.yml, spawners.yml, dll.
├── auctions/
│   ├── auctions.yml
│   └── theme.yml
├── casino/
│   ├── settings.yml
│   ├── animation/roulette.yml
│   └── games/roulette.yml
├── gui/
│   ├── shop.yml                    # teks GUI Shop/Sell, template lore kategori + item
│   └── auction.yml
└── integrations/
    ├── discordsrv.yml
    ├── discordwebhook.yml
    └── saweria-tebex.yml
```

Plugin membuat `data/database.db` melalui SQLite saat database diinisialisasi; file database biner memang tidak disertakan di dalam JAR source. Dua file YAML Discord disediakan untuk pengaturan integrasi mendatang dan nonaktif secara default. Callback masuk Saweria/Tebex memakai file terpisah `integrations/saweria-tebex.yml`. Saat upgrade, tata letak lama (`shop/*.yml`, `shops/settings.yml`, `shops/discounts.yml`, `lang/*.yml`, `data/Playerdata.yml`) dipindahkan otomatis ke lokasi di atas. Tidak ada yang ditimpa: jika tujuan sudah ada, file lama dibiarkan, dan nilai `shops/settings.yml` lama digabungkan ke `config.yml`.

### `gui/shop.yml` dan `auctions/theme.yml`

- **Lore yang dapat diedit:** `gui/shop.yml` → `shop.lore.categories.main-menu`, `shop.lore.categories.tab`, `shop.lore.shop-items`, dan `shop.player-info` mengatur setiap baris yang dibaca pemain, dengan placeholder seperti `{items}`, `{buy-range}`, `{buy-price}`, `{sell-price}`, `{currency}`, `{player}`, `{rank}`, `{balance}` serta baris blok `{buy}` / `{sell}` / `{click-hints}`. Ubah lalu jalankan `/octaeconomy reload`; tidak perlu mengubah source.
- `shop.currency-names` memetakan id mata uang ke teks yang dilihat pemain (`vault: money`).
- `shop.background` dan `shop.accent` mengatur panel di sekitar trapesium simetris 3-5-7-7. Baris yang belum penuh diposisikan secara simetris; jumlah item genap diseimbangkan di kiri dan kanan dengan satu slot tengah yang sengaja kosong, bukan menempel ke kiri.
- `shop.category-background` dan `shop.category-accent` mengatur bingkai halaman kategori. Menu utama Shop tetap trapesium 3-5-7-7; halaman kategori memakai tujuh tab di slot 1–7, navigasi kelompok di slot 0 dan 8, serta grid item persegi 4x7.
- `auction.theme` menetapkan tema default: `nether`, `overworld`, `ocean`, atau `the_end`. Pemain dapat menggantinya lewat tombol tema di footer Auction House, atau menggunakan `/ah theme` dan `/ah theme <dimension>`. Tema langsung diterapkan dan berlaku selama sesi pemain; setelah reconnect, plugin menggunakan tema default dari konfigurasi.
- Setiap tema memiliki `title` berwarna sesuai tema tetapi tetap bertuliskan `Auction House`, serta `background`, `accent`, `emblem`, `tagline`, dan `price-style` pada `auction.themes.<theme>`. Warna legacy atau MiniMessage yang sudah ada di nama item/kategori dipertahankan tanpa dibungkus palet warna lain. Lore GUI secara eksplisit tidak italic.

### `shops/discount.yml`

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

### `auctions/auctions.yml`

Mengatur durasi listing, pajak, biaya listing, rentang harga maksimum, jumlah listing, mata uang yang diizinkan, interval pemeriksaan kedaluwarsa, durasi khusus minimum, dan blacklist. Durasi khusus memerlukan `octaeconomy.auction.expire.custom`. Format yang didukung `30m`, `12h`, `3d`, dan `1w`; default 48 jam dengan maksimum terkonfigurasi 168 jam. Batas harga per mata uang dapat dibuat di `currency-limits.<currency>.minimum` dan `.maximum`.

### `casino/settings.yml`, `casino/games/roulette.yml`, and `casino/animation/roulette.yml`

Casino hanya menggunakan Vault. Atur `minimum-bet`, `maximum-bet`, tiga nilai `chip-values`, dan pengali profit Roulette. Menu 21+ memperingatkan pemain di bawah umur untuk keluar serta memerlukan pernyataan mandiri per sesi; menu ini bukan verifikasi identitas. Semua nominal memakai saldo virtual server; tidak ada deposit atau penarikan uang nyata.

### File bahasa

Edit `lang/message/en.yml`, `lang/message/id.yml`, dan `lang/message/zh.yml`. Semua key pesan saat ini tersedia di ketiga file bahasa. Atur `language: id` untuk Bahasa Indonesia atau `language: zh` untuk Chinese Simplified. Key yang hilang akan menggunakan fallback English.

## Integrasi

Chest Shop sudah dihapus pada versi ini. Tidak ada command `/chestshop` maupun modul Chest Shop.

### Pengaturan Webhook

- **Vault + EssentialsX:** Wajib. OctaEconomy membaca dan menulis saldo melalui provider ekonomi Vault yang aktif; plugin tidak membuat penyimpanan saldo tandingan.
- **MMOItems:** Opsional. Definisi item shop dapat memakai `mmoitems.type` dan `mmoitems.id`. Jika plugin atau API tidak tersedia, entry tersebut dilewati dan item vanilla tetap aktif.
- **ExcellentEconomy:** Opsional. ID mata uang dikonfigurasi melalui API service ExcellentEconomy. Hook dinonaktifkan dengan aman jika plugin/API tidak tersedia.
- **SmartSpawner:** Opsional. Entry kategori spawner akan mencoba API pembuatan item SmartSpawner terlebih dahulu dan memakai item spawner vanilla sebagai fallback jika hook tidak tersedia.

Integrasi opsional ditemukan saat runtime. OctaEconomy tidak membundel, melakukan shade, atau menyalin class dari plugin lain.


1. Atur `webhooks.enabled: true`, `webhooks.saweria.enabled: true`, dan masukkan Streaming Key Saweria ke `webhooks.saweria.streaming-key`.
2. Arahkan path HTTPS publik ke `http://127.0.0.1:8765/saweria` (atau sesuaikan dengan `webhooks.paths.saweria`).
3. OctaEconomy memvalidasi `Saweria-Callback-Signature` dan mencegah event ID yang sama diumumkan berulang sebelum menyiarkan nama donatur dan nominal ke pemain online.

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
target/OctaEconomy-0.0.3-2026.jar
```

Proyek Maven menggunakan scope `provided` untuk Paper API dan Vault API serta tidak melakukan shade dependency. Paper menyediakan driver JDBC SQLite. Saat `database.type: mysql` digunakan, Paper memuat konektor MySQL yang dideklarasikan di `plugin.yml`.

## FAQ dan troubleshooting

**Auction House menampilkan pesan storage masih dimulai.** Database diinisialisasi secara asynchronous saat startup. Tunggu pesan `Auction storage ready`. Jika inisialisasi gagal, periksa error startup serta direktori SQLite atau kredensial MySQL.

**Item MMOItems tidak muncul di shop.** Pastikan plugin MMOItems aktif dan type serta ID item valid. Entry tidak valid dilewati dengan warning yang menyebut file dan key.

**Mata uang ExcellentEconomy tidak dapat dipilih.** Tambahkan ID mata uang persis ke `auctions/auctions.yml` dan pastikan ExcellentEconomy telah mendaftarkan API service-nya. Vault tetap menjadi mata uang default.

**MySQL gagal terhubung.** Buat database lebih dahulu, periksa kredensial dan firewall, lalu set `database.type: mysql`. Paper harus dapat memuat library konektor MySQL yang dideklarasikan saat startup.

**Seller offline belum menerima payout ExcellentEconomy.** Operasi cache provider tersebut membutuhkan pemain online. Payout tetap tertunda dan dicoba kembali saat pemain join.

**Server tidak menyala karena Essentials hilang.** EssentialsX adalah dependency wajib pada build ini. Pasang EssentialsX dan Vault sebelum mengaktifkan OctaEconomy.

## Lisensi & Kepemilikan

OctaEconomy **100% gratis**. Penjualan ulang, akses paywall, akses yang mensyaratkan donasi wajib, dan bundling plugin ke produk berbayar dilarang. Fork dan modifikasi harus tetap gratis, tetap mencantumkan kredit **Alvran**, dan tidak boleh mengklaim kepemilikan asli. Satu-satunya author/pemilik asli adalah **Alvran**: https://github.com/alvran-npc. Baca [`LICENSE`](LICENSE) untuk lisensi kustom lengkap.

## Credits

- Author asli: **Alvran** — https://github.com/alvran-npc
- Dibuat untuk ekosistem Paper/Bukkit API dan ekonomi Vault.
- MMOItems, ExcellentEconomy, dan SmartSpawner merupakan integrasi runtime opsional.


## Pembaruan v0.0.3-2026
- Istilah money, urutan item berkelompok, tata letak file baru, dan lore yang dapat diedit (lihat changelog).
- **Format harga:** harga tampil `15,000.00` di seluruh Shop (`shop.price-format`).
- **PlaceholderAPI:** pasang PlaceholderAPI dan pakai `%placeholder%` di baris lore `gui/shop.yml` (kategori, item, dan kartu pemain). Bersifat opsional; nonaktifkan dengan `shop.placeholderapi: false`.
- **Layout Tools:** halaman 1 berisi armor dengan satu jenis per baris dan satu material per kolom (leather sampai netherite), halaman berikutnya berisi pedang, beliung, cangkul, kapak, dan sekop dengan cara yang sama, lalu sisanya.
- **Layout Blocks:** halaman awal berisi jenis kayu (azalea, oak, ...), lalu jenis batu, lalu semua blok lain.
- Layout kustom: ubah `shop.layouts.<kategori>.sections` di `config.yml` (pola seperti `*_helmet` atau `family:oak`, `layout: rows` atau flow).

## Pembaruan v0.0.3-2026-DEV-8
- **Menu roda Roulette:** menekan SPIN kini menarik taruhan dan membuka GUI roda tersendiri. Cincin 26 kantong berwarna (urutan roda Eropa) berputar melewati penunjuk tetap di tengah atas, melambat, lalu berhenti pada hasil yang sudah ditentukan, lengkap dengan suara tik dan pengumuman menang/kalah sebelum kembali ke meja. Atur di `casino/animation/roulette.yml` (`laps`, `start-interval-ticks`, `maximum-interval-ticks`, `slowdown-steps`, `result-hold-ticks`).
- Menutup menu roda saat berputar kini langsung menampilkan hasil yang sudah terkunci, bukan mengembalikan taruhan, sehingga putaran tidak bisa dibatalkan ketika terlihat kalah. Disconnect dan shutdown plugin tetap mengembalikan taruhan.
- Meja Roulette didesain ulang: taruhan luar simetris di sekitar angka nol hijau, angka berupa blok beton berwarna dengan jumlah stack sebagai nomor, taruhan/chip terpilih menyala, dan baris bawah berisi chip, SPIN, taruhan saat ini, hasil terakhir, dan Back dengan sudut dekoratif.
- Menu utama Casino dan gerbang 21+ memakai tata letak berbingkai, kartu dompet, dan teks peringatan yang dibungkus per baris agar tidak lagi keluar dari layar.
- Halaman kategori Shop memakai bingkai bersih DEV-7: tab kategori di rail atas, Back/Search/Previous/Page/Next/Player Information/Close di baris bawah, tab kategori aktif menyala, dan panah tab/halaman hanya muncul bila tersedia.
- Lore item Shop didesain ulang dengan pemisah dan simbol, dan petunjuk klik kini mengikuti `click-actions` yang dikonfigurasi, bukan selalu kiri=beli/kanan=jual.

## Pembaruan v0.0.3-2026-DEV-7
- Menu utama Shop dan menu jumlah memakai bingkai satu lapis yang rapi: satu rail menerus di keempat sisi, dengan aksen hanya pada empat sudut. Ornamen permata lama yang membuat bingkai terlihat berantakan dihapus.
- Kartu **Player Information** baru di tengah bawah menu utama Shop dan menu jumlah, menampilkan nama, rank (primary group Vault, cadangan `Member`), dan saldo Vault pemain.
- Lore kartu kategori dan tab kategori didesain ulang dengan simbol unik (`✦ ❖ ◆ ◇ ▸ ➜`), garis pemisah, dan baris rentang Buy/Sell terpisah.
- Tombol baris bawah dipindahkan dari sudut: Close di slot 47 dan Search di slot 51 pada menu utama; Back di slot 47 dan Close di slot 51 pada menu jumlah. Tombol halaman Sebelumnya/Berikutnya hanya muncul bila halaman tersedia.
- Kunci opsional baru di `gui/shop.yml`: `shop.frame-corner`, `shop.player-info.default-rank`, `shop.player-info.currency-label`. Kunci ini ditambahkan otomatis ke file lama tanpa menimpa pengaturan Anda.

## Pembaruan v0.0.3-2026-DEV-6
- `/ah sell <price> [currency] [expired]` mendukung durasi seperti `12h`, `3d`, dan `1w`. Durasi khusus memerlukan permission `octaeconomy.auction.expire.custom` dan dibatasi konfigurasi `auctions/auctions.yml`.
- Menu utama Shop memakai siluet terpusat empat tingkat 3-5-7-7 di bawah header khusus, ornamen sudut, rail samping yang utuh, garis pemisah ganda, dan footer tersendiri. Halaman kategori memakai tujuh tab di bagian atas, navigasi kategori pada kedua ujung, serta grid item berbingkai 4x7.
- Menu `/casino` memiliki gerbang pernyataan mandiri usia 21+. Game pertama adalah Roulette single-zero original yang hanya menggunakan mata uang virtual Vault. Gerbang ini bukan verifikasi identitas/usia; fitur tidak menerima setoran atau pembayaran uang nyata.
- Pengaturan Casino berada di `casino/settings.yml`, aturan game di `casino/games/roulette.yml`, dan animasi di `casino/animation/roulette.yml`; permission: `octaeconomy.casino.use` dan `octaeconomy.casino.roulette`.
