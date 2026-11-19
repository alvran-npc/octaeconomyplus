# Changelog

All notable changes to OctaEconomy are documented in this file.
The format follows Keep a Changelog and the project uses Semantic Versioning.

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
