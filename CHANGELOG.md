# Changelog

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
