# Changelog

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
