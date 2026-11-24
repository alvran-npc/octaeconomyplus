# OctaEconomy

**An all-in-one economy plugin featuring a categorized Shop GUI, Sell GUI, and a multi-currency Auction House. Fully integrated with Vault and EssentialsX.**

Author: **Alvran** · [GitHub](https://github.com/alvran-npc) · Version: `0.0.3-2026-DEV-5`

OctaEconomy is an original Paper plugin project. It provides a configurable shop, inventory selling, and a database-backed auction house without requiring another shop or auction plugin.

## Features

- Categorized shop menus loaded from one YAML file per category.
- Complete generated vanilla catalogue: regular item-bearing Paper `Material` entries are assigned automatically to the eight vanilla categories; technical-only materials are filtered, and supported living entity types receive generated spawner entries. Explicit YAML entries override generated prices/items.
- Paginated category menus with a symmetric, centered 3-5-7-9-9 trapezoid layout, balanced glass accents, item search, quantity selector, shift-click stack purchases, and configurable left/right click actions.
- Buy and sell prices per item, per-item currency selection, and vanilla, MMOItems, and spawner templates.
- Sell GUI with return handling for items that cannot be sold.
- `/sell hand`, `/sell handall`, and `/sell inv` with permission-based sell multipliers and grouped summaries.
- Optional in-memory dynamic prices and daily item sell limits (both disabled by default).
- Auction listings backed by SQLite by default, with MySQL/MariaDB configuration support.
- Multi-currency auction listings with four live-switchable dimension themes (Nether, Overworld, Ocean, and The End), a dedicated 7×4 listing grid, themed header and side rails, listing fee, tax, listing limits, expiration, cancellation, search, and confirmation menu.
- Seasonal shop discounts configured in `discounts.yml`, with optional date windows and category/item targeting. Discounted prices are displayed in compact lore and applied to purchases.
|---|---|
| Server | Paper 1.21.x (the plugin uses the Bukkit `api-version: '1.20'` compatibility declaration) |
| Java | Java 21 |
| Required plugins | Vault and EssentialsX (plugin name `Essentials`) |
| Optional plugins | MMOItems, ExcellentEconomy, SmartSpawner |
1. Install Java 21 and a supported Paper 1.21.x server.
2. Install and start Vault and EssentialsX. Confirm that EssentialsX registers an economy provider through Vault.
3. Copy `OctaEconomy-0.0.3-2026-DEV-5.jar` into the server's `plugins/` directory.
- `/shop blocks` opens the `blocks` category directly.
- `/shop search diamond` opens matching shop entries.
- Click an item to open its quantity selector. The selector offers 1, 16, 32, 64, and a plus/minus custom quantity control.
- Shift-left-click buys a stack of 64. Right-click sells one matching item from the player's storage when the configured right-click action is `sell`.
- A purchase is rejected when the player does not have enough inventory space. The plugin withdraws the money before giving the item and refunds if insertion unexpectedly fails.

### Sell GUI and sell commands

- `/sellgui` or `/sellg` opens the sell inventory.
- Place items into the GUI and close it. Sellable items are deposited into the configured currency. Unsellable items are returned to the player's inventory or dropped safely at the player's location if the inventory is full.
- `/sell hand` sells the stack in the main hand.
- `/sell handall` sells all matching stacks in the player's storage.
- `/sell inv` sells all configured sellable items in the player's storage.
- Permission multipliers use nodes such as `octaeconomy.sell.multiplier.1.5`. The highest positive multiplier granted to the player is selected.

### Default catalogue coverage

The eight vanilla categories with `auto-include-vanilla: true` are populated from the Paper `Material` registry during startup/reload, not just from the sample YAML entries. All regular item-bearing materials are assigned to a category; technical-only materials are filtered. Custom entries in each YAML file override generated values for that material. The Spawners category also generates entries for supported living `EntityType` values. The optional MMOItems category is intentionally configured by exact `type` + `id` entries in `shops/mmoitems.yml`; its third-party item registry is not auto-enumerated by this version.

Generated prices are starting defaults from each category's `auto-pricing` section. Set a material explicitly in the category YAML to use a custom price/currency, or adjust `auto-pricing.buy-base`, `sell-ratio`, and `currency` for generated entries.

### Add an item to a shop category

Open `plugins/OctaEconomy/shops/blocks.yml` and add an entry under `items`:

```yaml
items:
  andesite:
    material: ANDESITE
    buy-price: 7.0
    sell-price: 1.5
    currency: vault
```

Use `-1` or omit a price to disable that transaction type. Reload with `/oeco reload`.

### Add a new category

Create a new YAML file in `plugins/OctaEconomy/shops/`, for example `ores.yml`:

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

The category is discovered from the YAML file. The `id` should be unique and lowercase. A category-specific permission may be set in `category.permission`. The `category.slot` value controls the category order only; OctaEconomy places categories into its centered trapezoid and does not use that value as a raw inventory slot.

### Add an ExcellentEconomy currency

1. Install and start ExcellentEconomy.
2. Find the exact currency ID configured in ExcellentEconomy.
3. The default `settings.allowed-currencies` includes `"*"`, so any currency registered by ExcellentEconomy is already permitted. If you remove the wildcard to create a strict allowlist, add the exact currency ID here.
4. Set the same ID as an item's `currency`, or pass it to `/ah sell <price> <currency>`.
5. Reload the plugin. The hook resolves the ExcellentEconomy API through Bukkit's service registry at runtime.

The API exposes cached player operations for balances, deposits, and withdrawals. Offline ExcellentEconomy payouts remain pending until the player joins; Vault providers may support direct offline deposits.

### Auction House

- `/ah` opens the market.
- `/ah sell 2500` lists the item stack in the main hand using the default currency.
- `/ah sell 2500 gems` lists it using the configured `gems` currency.
- `/ah sell 2500 gems 3d` lists it for three days (requires `octaeconomy.auction.expire.custom`); `/ah sell 2500 12h` uses the default currency for twelve hours.
- Click a listing, review its details, then confirm the purchase.
- `/ah search sword` searches active listings.
- `/ah mylistings` opens the seller's active listings. Click one to cancel it.
- `/ah expired` displays expired listings. `/ah collect` retrieves pending purchased, expired, or cancelled items.

Listings are stored with `ItemStack#serializeAsBytes`, preserving Bukkit item metadata and plugin-provided item metadata. Purchase state, buyer delivery, and seller payout records are committed together in a database transaction. The plugin does not allow a seller to purchase their own listing.

## Commands

| Command | Description |
|---|---|
| `/shop [category]` | Open the shop or a category |
| `/shop search <keyword>` | Search shop entries |
| `/sellgui`, `/sellg` | Open Sell GUI |
| `/sell hand` | Sell the stack in the main hand |
| `/sell handall` | Sell all matching stacks in storage |
| `/sell inv` | Sell all configured sellable items |
| `/ah`, `/auction` | Open Auction House |
| `/ah sell <price> [currency] [expired]` | List the item in the main hand; custom durations include `12h`, `3d`, `1w` |
| `/casino [roulette]` | Open the 21+ gate, casino menu, or Roulette |
| `/ah expired` | View expired listings |
| `/ah collect` | Claim pending auction items |
| `/ah search <keyword>` | Search active listings |
| `/ah mylistings` | Manage your active listings |
| `/ah theme [dimension]` | Open the theme picker or apply a dimension theme |
| `/donate` | Show the author's Saweria support link |
| `/octaeconomy reload`, `/oeco reload` | Reload config, language, and shop files |
| `/octaeconomy version` | Show plugin version and author |
| `/octaeconomy help` | Show command help |
| `/octaeconomy debug` | Show basic cache and database state |

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `octaeconomy.shop.use` | true | Use the shop |
| `octaeconomy.shop.category.<id>` | true for discovered categories | Category access node; optional `category.permission` may further restrict it |
| `octaeconomy.sell.use` | true | Use sell commands |
| `octaeconomy.sell.gui` | true | Open Sell GUI |
| `octaeconomy.sell.hand` | true | Use `/sell hand` |
| `octaeconomy.sell.handall` | true | Use `/sell handall` |
| `octaeconomy.sell.inv` | true | Use `/sell inv` |
| `octaeconomy.sell.multiplier.<value>` | false | Grant a sell multiplier, e.g. `.1.5` |
| `octaeconomy.auction.use` | true | Use Auction House |
| `octaeconomy.auction.sell` | true | Create auction listings |
| `octaeconomy.auction.limit.<n>` | false | Override the base active listing limit with the highest granted number |
| `octaeconomy.auction.expire.custom` | op | Set a custom duration in `/ah sell` |
| `octaeconomy.casino.use` | true | Open the casino and age confirmation menu |
| `octaeconomy.casino.roulette` | true | Play Roulette with virtual Vault currency |
On first startup and every `/octaeconomy reload`, OctaEconomy expands each vanilla shop category from the running Paper server's `Material` registry and writes the full list into `plugins/OctaEconomy/shops/*.yml`. Every generated item receives explicit `material`, `buy-price`, `sell-price`, and `currency` fields. Supported mob spawners are expanded the same way using the server's `EntityType` registry. Existing item entries and manually configured prices are preserved; only missing entries/fields are added. The `catalog-defaults` section controls prices for newly discovered materials, not hidden runtime pricing. Admin-only/unobtainable technical materials (for example command blocks, barrier, bedrock and air) are intentionally excluded from the public shop.

`/sell hand`, `/sell handall`, `/sell inv`, and SellGUI use the same expanded item catalogue, so the configured `sell-price` is shared between Shop and Sell features. Spawner entries are buy-only by default (`sell-price: -1.00`) to avoid selling spawners obtained through admin tools.

## Configuration

### `config.yml`

- `language`: `en`, `id`, or `zh`.
- `database.type`: `sqlite` (default) or `mysql`.
- `database.sqlite-file`: SQLite path relative to the plugin data folder.
- `database.mysql`: MySQL connection settings.
- `economy.default-currency`: currency used when a shop item or auction command omits one.
- `shop.click-actions`: actions for left and right clicks (`buy` or `sell`).
- `shop.dynamic-price`: optional fluctuation bounds and recovery settings; disabled by default.
- `shop.daily-limit`: optional default and per-item daily amount limits; disabled by default.
- `shop.sell-multiplier.enabled`: enables permission-based sell multipliers.
- `sound`: success and error sounds.
- `transaction-log.enabled`: enables a rotating daily transaction log. Disabled by default.

`config-version` is checked on startup. Missing default keys are merged into the user's existing configuration without replacing existing values.

### `gui.yml`

- `shop.background` and `shop.accent` control the panes around the symmetric 3-5-7-9-9 trapezoid. Incomplete rows are centered symmetrically; even item counts are mirrored around a deliberate center gap instead of being left-aligned.
- `shop.category-background` and `shop.category-accent` style the category-page frame. The Shop main menu remains a 3-5-7-9-9 trapezoid; category pages use seven category tabs in slots 1–7, side paging in slots 0 and 8, and a square 4x9 product grid.
- `auction.theme` sets the default dimension theme: `nether`, `overworld`, `ocean`, or `the_end`. Players can change it from the theme button in the Auction House footer or by using `/ah theme` and `/ah theme <dimension>`. The selected theme applies immediately and remains for that player session; after reconnect, the configured default is used again.
- Each theme has a configurable color-styled `title` that still reads `Auction House`, plus `background`, `accent`, `emblem`, `tagline`, and `price-style` values under `auction.themes.<theme>`. Existing legacy or MiniMessage colors in configured item/category names are preserved rather than wrapped in another palette. GUI lore is explicitly non-italic.

### `discounts.yml`

Enable a seasonal offer by setting the top-level `enabled: true`, choosing its key in `active`, and setting that offer's `enabled: true`. For example:

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

Dates use ISO local date-time and the server's local timezone. Leave `starts-at` and `ends-at` empty for a manually controlled offer. Empty `categories` and `items` lists apply the offer to every shop item. When both lists are populated, an item must match both filters. Item filters accept either an item ID or `category-id:item-id`. Discounts affect shop buy prices only; sell prices are unchanged. A discount line is omitted from lore when no valid active offer applies. Run `/oeco reload` after editing. The implementation caps configured discounts at 90%.

### `auction.yml`

Controls listing duration, tax, fee, maximum price range, listing count, allowed currencies, expiration scan interval, minimum custom duration, and blacklists. Custom durations require `octaeconomy.auction.expire.custom`. Formats are `30m`, `12h`, `3d`, and `1w`; defaults are 48 hours, with a configured maximum of 168 hours. Per-currency price bounds can be defined under `currency-limits.<currency>.minimum` and `.maximum`. The default `allowed-currencies` contains `vault` and `"*"`; the wildcard permits any currency currently registered by ExcellentEconomy. Remove `"*"` and enumerate exact currency IDs to use an allowlist.

### `casino.yml`

Casino uses Vault only. Configure `minimum-bet`, `maximum-bet`, three `chip-values`, and Roulette payout profit multipliers. The 21+ menu warns underage players to leave and requires a session self-attestation; it is not identity verification. All amounts are virtual server balance; there are no real-money deposits or withdrawals.

### Language files

Edit `lang/en.yml`, `lang/id.yml`, and `lang/zh.yml`. Every current message key exists in all three language files. Set `language: id` to select Indonesian, or `language: zh` for Simplified Chinese. Missing keys fall back to English.

## Integrations

Chest Shop was removed from this version. There is no `/chestshop` command or Chest Shop module.

### Webhook setup

- **Vault + EssentialsX:** Required. OctaEconomy reads and writes through the active Vault economy provider; it does not maintain a competing balance store.
- **MMOItems:** Optional. Shop item definitions can use `mmoitems.type` and `mmoitems.id`. If the plugin or API is unavailable, those entries are skipped and vanilla shop entries remain active.
- **ExcellentEconomy:** Optional. Configured currency IDs are resolved through the ExcellentEconomy service API. The hook is disabled safely when the plugin/API is absent.
- **SmartSpawner:** Optional. Spawner category entries try the SmartSpawner item API first and fall back to a vanilla spawner item if that hook is unavailable.

Optional integrations are discovered at runtime. OctaEconomy does not bundle, shade, or copy another plugin's classes.


1. Set `webhooks.enabled: true`, `webhooks.saweria.enabled: true`, and paste your Saweria Streaming Key into `webhooks.saweria.streaming-key`.
2. Route the public HTTPS path to `http://127.0.0.1:8765/saweria` (or set the matching custom `webhooks.paths.saweria`).
3. OctaEconomy validates `Saweria-Callback-Signature` and de-duplicates event IDs before broadcasting donor name and amount to online players.

Saweria's HTTP webhook sends a JSON POST and documents the signature as HMAC-SHA256 over the concatenated ordered fields `version`, `id`, `amount_raw`, `donator_name`, and `donator_email`. See [Saweria HTTP Webhook docs](https://saweria.co/docs/webhook).

**Tebex:**

1. In Tebex, open **Developers → Webhooks → Endpoints**, add your public HTTPS callback URL, and subscribe to `payment.completed`.
2. Copy the endpoint secret into `webhooks.tebex.secret` and set `webhooks.tebex.enabled: true`.
3. Route the public path to `http://127.0.0.1:8765/tebex` (or set the matching custom `webhooks.paths.tebex`).
4. Tebex validation callbacks receive the required `200` JSON response containing the webhook `id`. Completed payments broadcast the buyer, up to three package names, and paid amount.

OctaEconomy verifies Tebex `X-Signature` against the raw request body and the endpoint secret. Notifications are informational only: Tebex package delivery, commands, ranks, and entitlement fulfillment must still be configured in Tebex or a separate fulfillment integration. See [Tebex Webhook docs](https://docs.tebex.io/developers/webhooks/overview).

The `notifications.saweria` and `notifications.tebex` MiniMessage templates can be customized. Supported placeholders include `{donator}`, `{amount}`, `{message}`, `{player}`, `{packages}`, and `{transaction}`. Event IDs are retained in `data/webhook-events.log` to avoid repeat announcements when a provider retries a callback.

## Performance and safety notes

- Auction JDBC work is serialized on a dedicated daemon worker thread; Bukkit inventory operations and events are marshalled to the server thread.
- SQLite uses WAL mode, normal synchronous mode, a busy timeout, and indexed listing searches/status checks.
- The expiration scan is small and bounded (up to 100 listings per pass); it is not a full-table scan on the main thread.
- Shop YAML and item templates are cached during load. GUI listeners return immediately for inventories not owned by OctaEconomy.
- Player UUIDs are used for auction ownership. Listing state and pending-delivery/payout creation are committed atomically.
- Dynamic prices and daily sell counters are in-memory in this initial version; they reset after a restart. Keep them disabled if persistent economic history is required.
- Daily sell counters and dynamic price multipliers are in-memory and reset on restart. A daily counter is retained across player disconnects during the same server process.
- The Auction House UI provides configurable dimension themes, search, listing management, and confirmation, but advanced category filters and configurable price/time sorting are not implemented yet. Shop lore placeholders such as `{buy}`, `{sell}`, `{stock}`, `{category}`, and `{currency}` are also not configurable in this first release.
- A purchase withdraws buyer funds through the external economy provider before the SQL transaction reserves the listing. A hard process crash in that short gap can require manual reconciliation; plugin/database transactions cannot atomically commit an external Vault transaction.
- Delivery and payout records are deleted only after the external inventory/economy operation succeeds. A hard process crash between an external Vault deposit or item insertion and the database acknowledgement can cause a duplicate on recovery; exactly-once delivery across an external plugin and a SQL database cannot be guaranteed without idempotency support from both systems.
- Sell GUI items are returned on normal close, disconnect, or plugin disable. A sudden operating-system/process crash while the GUI is open can occur before a close event runs; do not treat this first release as crash-proof escrow.
- The plugin reads and writes through the active Vault economy provider; it does not register a competing fallback economy provider. Install EssentialsX and verify its Vault provider is active.
- Folia region scheduling has not been implemented or verified. Use standard Paper rather than Folia for this release.

## Build from source

Requirements: JDK 21, Apache Maven 3.8.6 or newer, and network access to the configured Maven repositories. Run the following from the project root (the directory containing `pom.xml`):

```bash
mvn --batch-mode --no-transfer-progress clean package
```

The GitHub Actions workflow installs Maven directly and does not depend on a Maven Wrapper file being present at the repository root.

The output is:

```text
target/OctaEconomy-0.0.3-2026-DEV-5.jar
```

The Maven project uses `provided` scope for Paper API and Vault API and does not shade dependencies. Paper supplies the SQLite JDBC driver. When `database.type: mysql` is selected, Paper resolves the MySQL connector declared in `plugin.yml`.

## FAQ and troubleshooting

**The Auction House says storage is starting.** The database initializes asynchronously during startup. Wait for the `Auction storage ready` message. If initialization fails, inspect the startup error and check the SQLite directory or MySQL credentials.

**Shop items from MMOItems do not appear.** Confirm the MMOItems plugin is enabled and that the type and item ID are valid. Invalid entries are skipped with a warning naming the file and key.

**ExcellentEconomy currency cannot be selected.** Add the exact currency ID to `auction.yml` and verify ExcellentEconomy has registered its API service. Vault is still the default currency.

**MySQL does not connect.** Create the database first, verify credentials and firewall rules, then set `database.type: mysql`. Paper must be able to resolve its declared MySQL connector library at startup.

**A player did not receive an offline ExcellentEconomy payout.** That provider's cached operations require the player to be online. The payout remains pending and is retried on join.

**The server does not start because Essentials is missing.** EssentialsX is a required dependency in this build. Install EssentialsX and Vault before enabling OctaEconomy.

## License & Ownership

OctaEconomy is **100% free**. Resale, paywall access, mandatory-donation access, and bundling this plugin into paid products are prohibited. Forks and modifications must remain free, retain credit to **Alvran**, and must not claim original ownership. The sole original author/owner is **Alvran**: https://github.com/alvran-npc. See [`LICENSE`](LICENSE) for the complete custom license.

## Credits

- Original author: **Alvran** — https://github.com/alvran-npc
- Built for the Paper/Bukkit API and Vault economy ecosystem.
- MMOItems, ExcellentEconomy, and SmartSpawner are optional runtime integrations.


## v0.0.3-2026-DEV-5 updates
- `/ah sell <price> [currency] [expired]` supports duration tokens such as `12h`, `3d`, and `1w`. Custom duration requires `octaeconomy.auction.expire.custom` and is bounded by `auction.yml`.
- Shop main menu retains the trapezoid category cards. Category pages now show seven category tabs across the top, with category pagination at both ends, and products in a square 4x9 grid. Category cards show item count and automatic buy/sell price ranges.
- New `/casino` menu has a 21+ self-attestation gate. The first game is an original single-zero Roulette implementation using Vault virtual currency only. The gate is self-attestation, not identity/age verification, and the feature does not accept real-money deposits or payments.
- Casino settings live in `casino.yml`; permissions are `octaeconomy.casino.use` and `octaeconomy.casino.roulette`.
