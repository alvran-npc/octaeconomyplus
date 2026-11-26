# Changelog

## [0.0.3-2026] - 2026-11-25
Fix update: no new gameplay, tidier files and editable texts.

### Changed
- Shop and Casino menus show **money** instead of `vault` (configurable via `shop.currency-names` and `casino/settings.yml: currency-label`), including purchase/sell chat messages.
- Shop items are grouped by type instead of plain A-Z: related items stay together (oak log, stripped oak log, oak planks, oak stairs, oak slab, oak button...). Spawners, Mob Drops and Redstone stay alphabetical. Controlled by `shop.sorting.mode` and `shop.sorting.alphabetical-categories` in `config.yml`.
- Data folder reorganized: `shop/*.yml` -> `shops/category/`, `shops/discounts.yml` -> `shops/discount.yml`, `lang/*.yml` -> `lang/message/`, `data/Playerdata.yml` -> `data/PlayerData.yml`. Shop settings (`shops/settings.yml`) moved into `config.yml` under `shop:`. Older layouts are migrated automatically without overwriting anything (`config-version` 7).
- `/octaeconomy reload` now reloads `config.yml` after file migration so moved settings apply immediately.

### Added
- Prices are shown as `15,000.00` (period decimal, thousands separators) in item lore, category ranges, the confirm menu and chat. Pattern: `shop.price-format` in `config.yml`.
- Optional PlaceholderAPI support: `%placeholders%` inside the `gui/shop.yml` lore are resolved per player (soft dependency, no hard requirement; toggle with `shop.placeholderapi`).
- Section layouts per category (`shop.layouts` in `config.yml`). Tools: page 1 armor (one piece type per row, one material per column), then swords, pickaxes, hoes, axes and shovels on the next page(s) in the same row layout, then everything else. Blocks: wood types first (azalea, oak...), then stone types, then other blocks.
- Editable Shop lore: `gui/shop.yml` `shop.lore.categories.main-menu`, `shop.lore.categories.tab`, `shop.lore.shop-items` and `shop.player-info` with placeholders and block lines (`{buy}`, `{sell}`, `{click-hints}`). Missing keys fall back to built-in defaults.
- `shop.lore.divider` and `shop.currency-names` in `gui/shop.yml`.

### Fixed
- Vanilla item titles in the Shop no longer render in italic.
- A seasonal-sale name with MiniMessage tags is no longer cut off mid-tag in item lore.

## [0.0.3-2026-DEV-8] - 2026-11-25
### Added
- Dedicated Roulette wheel menu (`CASINO_WHEEL`): SPIN debits the stake and opens a 54-slot wheel with a 26-pocket ring in European wheel order that rotates past a fixed top-center pointer, decelerates, and lands on the pre-determined result, with tick sounds, a win/loss reveal and an automatic return to the table.
- New `casino/animation/roulette.yml` keys: `laps`, `slowdown-steps`, `result-hold-ticks` (existing files receive them automatically).
- Wallet card and responsible-play card in the Casino main menu; word-wrapped 21+ warning tooltip.
- Shop category pages share the DEV-7 frame; Player Information card on the bottom row.

### Changed
- Roulette table layout: outside bets around a green zero, colored concrete number blocks with stack size = number, glowing selected bet/chip, decorative corners; Back moved to slot 52 and chips to slots 46-48.
- Shop category pages: Back moved to slot 46, Close to slot 52, new Search button at slot 47; tab/page arrows only appear when usable.
- Shop item lore redesigned; click hints now reflect `shop.click-actions` and the quantity preview no longer shows click hints.
- Casino main menu, 21+ gate and shop category pages use a framed layout.
- Closing the wheel menu mid-spin now settles the locked-in result immediately instead of refunding. Disconnect and shutdown still refund.

### Fixed
- The 21+ warning tooltip no longer renders as one very long line.
- Removed unused category background code and obsolete animation settings (`frames`, `interval-increase-*`, `display-slot` are no longer read).

## [0.0.3-2026-DEV-7] - 2026-11-25
### Added
- Player Information card (name, rank, Vault balance) at the bottom center of the Shop main menu and quantity menu. Rank comes from the Vault primary group and falls back to `Member`.
- Optional `gui/shop.yml` keys: `shop.frame-corner`, `shop.player-info.default-rank`, `shop.player-info.currency-label` (merged into existing files automatically).
- `VaultHook#primaryGroup(Player)` using Vault's Permission service, safe when no permission provider exists.

### Changed
- Shop main/quantity frame is now a clean single-pane border with accented corners only; jewel ornaments on the corners and bottom row were removed.
- Category card and category tab lore redesigned with divider lines, unique symbols, and separate Buy/Sell lines.
- Main menu: Close moved to slot 47, Search to slot 51. Quantity menu: Back moved to slot 47, Close to slot 51. Previous/Next page buttons appear only when a page exists.

### Fixed
- Removed unused frame code and a stale layout comment in `ShopModule`.
- Synchronized version strings in `pom.xml`, the CI workflow, and both READMEs.

## [0.0.3-2026-DEV-6] - 2026-11-25
### Added
- Premium Shop frame with ornamental amethyst/prismarine corners, double-rule separators, a dedicated title rail, uninterrupted side gutters, and a reserved inner 4x7 category item area.
- Organized data folder layout for Shop, Auction, Casino, GUI, integrations, player metadata, and SQLite storage.
- Non-destructive configuration/database migration from DEV-5 paths.
- Decelerating visual Roulette wheel animation, with the winning result fixed before the animation begins.

### Changed
- Shop main menu now uses a four-tier centered 3-5-7-7 silhouette beneath a dedicated title rail; the side gutters remain unobstructed and decorative corner ornaments no longer overlap the category cards or footer controls.
- Shop category pages display 28 items per page to preserve the side gutters and full frame.
- Moved category files to `shop/`, Shop settings and discounts to `shops/`, Auction configuration to `auctions/`, Casino configuration to `casino/`, and menu configuration to `gui/`.
- SQLite default path is now `data/database.db`.

### Fixed
- Roulette animation tasks, active spin state, and daily sell-usage cache are cleaned up to avoid accumulating per-player state.
- Interrupted Roulette spins are cancelled and refunded on inventory close, player quit, or plugin disable.

### Notes
- `data/Playerdata.yml` is reserved for future persistent player metadata; 21+ confirmation remains session-only.
- DiscordSRV and outgoing Discord webhook files are configuration placeholders; inbound Saweria/Tebex webhooks use `integrations/saweria-tebex.yml`.


## [0.0.3-2026-DEV-5] - 2026-11-24
### Added
- Casino module with a 21+ self-attestation gate and Vault-only virtual-currency Roulette.
- Custom Auction House listing expiry syntax and permission.
### Changed
- Removed the Chest Shop command, module integration, and configuration references from the active plugin.
- Shop category pages now use a square 4x9 item grid and a seven-category navigation rail.
- Shop category cards show automatic item counts and buy/sell price ranges without internal identifiers.
- The Shop main menu retains its trapezoid category layout.

## [0.0.3-2026-DEV-4] - 2026-11-19
### Fixed
- Materialized the complete vanilla Material catalogue into category YAML files instead of keeping most entries only in the runtime cache.
- Added explicit buy-price, sell-price, and currency fields for every generated item.
- Added missing item price/currency fields to existing user YAML entries without overwriting configured values.
- Expanded supported mob spawners into explicit YAML entries.
- Improved Redstone classification for rails, copper bulbs, slime/honey blocks, and additional redstone components.
- Kept Shop and Sell commands on the same fully configured item catalogue.

# Changelog

### Build Fix (2026-11-24)
- Fixed the Java 21 compilation error in `AuctionModule`: the truncated listing display name is now stored in a final local variable before it is captured by the asynchronous completion lambda.
- Kept the listing creation, item return/refund, listing fee, and success notification flow unchanged.

## [0.0.3-2026-DEV-2] - 2026-11-19

### Added
- Added a native Chest Shop module with chest targeting, owner restocking, buyer quantity GUI, per-shop currency pricing, persistent serialized item templates, double-chest alias handling, and player/admin permissions.
- Added hopper transfer/pickup protection for registered Chest Shops to prevent bypassing the purchase GUI.
- Added `/donate` with a clickable Saweria support link to the author.
- Added optional Saweria HTTP Webhook notifications with `Saweria-Callback-Signature` verification, donor/amount broadcasts, request-size limits, and event de-duplication.
- Added optional Tebex `payment.completed` notifications with raw-body `X-Signature` verification, endpoint validation response, buyer/package/amount broadcasts, and event de-duplication.
- Added automatic vanilla shop catalogue generation across the default material categories and automatic generation of spawners for supported living entity types.
- Added `chestshop.yml` and `integrations.yml` defaults with non-destructive configuration merging.

### Changed
- Updated the Maven artifact version to `0.0.3-2026-DEV-2` and aligned the GitHub Actions artifact name/path.
- Shortened Shop lore labels, prices, currency IDs, and discount text to reduce tooltip width.
- Shortened Auction House listing lore to seller, compact price/currency, remaining time, and inspect action.
- Kept the Auction House inventory title as `Auction House` across all four selectable dimension themes.
- Enabled the Auction House currency wildcard (`*`) so any currency registered by ExcellentEconomy can be used; exact allowlists remain supported.
- Updated English and Indonesian documentation with Chest Shop, complete vanilla catalogue behavior, Saweria/Tebex webhook setup, and security requirements.

### Security
- Webhook callbacks are accepted only after signature verification. The listener binds to localhost by default and must be exposed through an HTTPS reverse proxy.
- Added webhook payload size limits and persistent event-ID de-duplication to avoid duplicate announcements when providers retry callbacks.
- Chest Shop inventory automation is blocked for registered shop chests.

### Notes
- Tebex notifications do not fulfill products, execute commands, or grant ranks. Configure package delivery separately in Tebex or a dedicated fulfillment integration.
- Saweria and Tebex integrations remain disabled until explicitly enabled and configured with their respective secrets.
- The built-in Chest Shop is an original implementation inspired by QuickShop-Hikari's concept; it does not depend on or import QuickShop-Hikari code/API, and it does not import existing QuickShop data.
- MMOItems items remain explicitly configured by type and ID; the runtime hook does not enumerate third-party custom item registries.
- Full Maven compilation and live Paper testing still need to be executed in GitHub Actions or a local environment with dependencies available.

## [0.0.3-2026-DEV-1] - 2026-11-19

### Fixed
- Removed the duplicate Shop helper method that prevented clean Java compilation.
- Corrected all three language files so the Auction theme notification is a root-level key.
- Ensured configured category/item colors are parsed as supplied instead of adding a second color palette.
- Explicitly removed inherited italic decoration from GUI item display names and lore while preserving existing colors and other formatting.
- Centered incomplete Shop rows and tightened the symmetric frame around the fixed 3-5-7-9-9 layout.

### Changed
- Rebuilt the Auction House inventory into a dedicated 7×4 listing grid below a themed dimension masthead, with matching side rails and a separate control footer.
- Added a visible theme emblem/header to Market, My Listings, and Expired screens.
- Improved listing, purchase review, own listing, archive, and theme-picker lore with structured headings and distinct symbols.
- Made the theme picker return to the same Auction House view and page; selecting a theme immediately refreshes that view.
- Added the theme switch control to Market, My Listings, Expired, and Purchase Confirmation screens; `/ah theme` opens the picker and `/ah theme <dimension>` applies a theme directly.
- Styled the Shop quantity selector with the same background frame as other Shop menus.
- Updated the Maven artifact version and GitHub Actions artifact path to `0.0.3-2026-DEV-1`.

### Notes
- The selected Auction House theme is a per-player session preference. `gui.yml` controls the default theme used when a player joins or reconnects.
- Full Maven compilation and live Paper testing must still be run in GitHub Actions or a local development environment with dependencies available.


All notable changes to OctaEconomy are documented in this file.
The format follows Keep a Changelog and the project uses Semantic Versioning.

## [0.0.2-SNAPSHOT] - 2026-11-19

### Added
- Added four selectable Auction House visual themes: Nether, Overworld, Ocean, and The End.
- Added themed auction inventory frames, accent panes, themed emblems, and richer listing/confirmation lore.
- Redesigned Shop category and item pages with a centered 3-5-7-9-9 trapezoid layout.
- Added configurable shop background and accent panes.
- Reworked shop item and category lore with varied symbols, separators, colored price sections, and purchase guidance.
- Added `discounts.yml` with named seasonal offers, optional date windows, category/item filters, MiniMessage display names, and a 90% maximum discount.
- Discounted buy prices are shown in Shop lore and charged by the purchase transaction; sell prices remain unchanged.
- Added config default merging for `gui.yml`, `auction.yml`, and `discounts.yml` during upgrades.
- Updated GitHub Actions to install Maven directly instead of failing when `./mvnw` is absent from the checkout root.

### Changed
- Updated project version to `0.0.2-SNAPSHOT`.
- Updated README documentation and build instructions for the new GUI and discount settings.

### Notes
- The build workflow uses Java 21 and Maven.
- Full server runtime testing remains required before production deployment.

## [0.0.1-SNAPSHOT] - 2026-11-19

### Added
- Initial modular plugin structure with `Module` lifecycle interface.
- Categorized, paginated ShopGUI with YAML category discovery and item search.
- Quantity selection, buy/sell click actions, stack purchases, and inventory-capacity checks.
- SellGUI with sellable-item processing and return handling for unsellable items.
- `/sell hand`, `/sell handall`, and `/sell inv` commands.
- Permission-based sell multipliers, optional dynamic prices, and optional per-player daily sell limits.
- Multi-currency Auction House with listing, search, confirmation, cancellation, expiration, collection, and seller payout notifications.
- SQLite storage with serialized item metadata, indexed listing queries, pending deliveries, and pending payouts.
- Configurable MySQL/MariaDB connection mode.
- Vault economy integration and runtime hooks for MMOItems, ExcellentEconomy, and SmartSpawner.
- English, Indonesian, and Simplified Chinese language files with English fallback.
- Maven build descriptor (`pom.xml`) and Maven bootstrap wrapper (`mvnw` / `mvnw.cmd`).
- `README.md`, `README_ID.md`, custom `LICENSE`, and initial API event classes.
