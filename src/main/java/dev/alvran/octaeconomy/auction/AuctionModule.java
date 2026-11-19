/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.auction;

import dev.alvran.octaeconomy.OctaEconomy;
import dev.alvran.octaeconomy.api.AuctionListEvent;
import dev.alvran.octaeconomy.api.AuctionPurchaseEvent;
import dev.alvran.octaeconomy.api.AuctionExpireEvent;
import dev.alvran.octaeconomy.core.LangManager;
import dev.alvran.octaeconomy.core.Module;
import dev.alvran.octaeconomy.core.TaskScheduler;
import dev.alvran.octaeconomy.gui.MenuType;
import dev.alvran.octaeconomy.gui.OctaMenuHolder;
import dev.alvran.octaeconomy.hook.CurrencyManager;
import dev.alvran.octaeconomy.hook.CurrencyProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionModule implements Module, CommandExecutor, TabCompleter {
    private final OctaEconomy plugin;
    private final LangManager lang;
    private final CurrencyManager currencies;
    private final TaskScheduler scheduler;
    private final AuctionDatabase database;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<UUID, Long> debounce = new ConcurrentHashMap<>();
    private final Set<UUID> delivering = ConcurrentHashMap.newKeySet();
    private final Set<UUID> paying = ConcurrentHashMap.newKeySet();
    // Theme preference is per player session; gui.yml defines the default.
    private final Map<UUID, String> playerThemes = new ConcurrentHashMap<>();
    private int expiryTask = -1;
    private YamlConfiguration auctionConfig;
    private YamlConfiguration guiConfig;
    private record AuctionTheme(String id, Material background, Material accent, Material emblem,
                                String title, String tagline, String priceStyle) { }
    private static final int[] AUCTION_GRID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public AuctionModule(OctaEconomy plugin, LangManager lang, CurrencyManager currencies, TaskScheduler scheduler) {
        this.plugin = plugin; this.lang = lang; this.currencies = currencies; this.scheduler = scheduler;
        this.database = new AuctionDatabase(plugin);
    }

    @Override public String name() { return "AuctionHouse"; }
    public boolean isDatabaseReady() { return database.isReady(); }
    public void clearPlayer(UUID player) { debounce.remove(player); playerThemes.remove(player); }
    @Override public void enable() {
        reload();
        database.initialize().whenComplete((ignored, error) -> {
            if (error == null) plugin.getLogger().info("Auction storage ready.");
            else plugin.getLogger().log(Level.SEVERE, "Auction storage could not be initialized; Auction House commands are disabled.", error);
        });
        long seconds = Math.max(15, auctionConfig.getLong("settings.expire-check-seconds", 30));
        expiryTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> database.expireBatch().thenAccept(expired -> {
            for (AuctionListing listing : expired) runMain(() -> Bukkit.getPluginManager().callEvent(new AuctionExpireEvent(listing.id(), listing.sellerId())));
        }).exceptionally(error -> { plugin.getLogger().warning("Auction expiry check failed: " + error.getMessage()); return null; }), seconds * 20L, seconds * 20L).getTaskId();
    }
    @Override public void disable() {
        if (expiryTask != -1) Bukkit.getScheduler().cancelTask(expiryTask);
        debounce.clear(); delivering.clear(); paying.clear(); database.close();
    }
    @Override public void reload() {
        auctionConfig = YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(), "auction.yml"));
        guiConfig = YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(), "gui.yml"));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { lang.send(sender, "players-only"); return true; }
        if (!player.hasPermission("octaeconomy.auction.use")) { lang.send(player, "no-permission"); return true; }
        if (!database.isReady()) { lang.send(player, "database-not-ready"); return true; }
        if (args.length == 0) { openMarket(player, "", 0); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> sellCommand(player, args);
            case "expired" -> openExpired(player, 0);
            case "collect" -> collect(player);
            case "search" -> {
                if (args.length < 2) { usage(player, "/ah search <keyword>"); return true; }
                openMarket(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)), 0);
            }
            case "mylistings" -> openMine(player, 0);
            case "theme" -> {
                if (args.length == 1) openThemeSelector(player, "market", "", 0);
                else {
                    String selected = normalizeTheme(args[1]);
                    if (selected == null) { usage(player, "/ah theme <nether|overworld|ocean|the_end>"); return true; }
                    playerThemes.put(player.getUniqueId(), selected);
                    lang.send(player, "auction-theme-changed", Map.of("theme", selected));
                    openMarket(player, "", 0);
                }
            }
            default -> openMarket(player, "", 0);
        }
        return true;
    }

    private void sellCommand(Player player, String[] args) {
        if (!player.hasPermission("octaeconomy.auction.sell")) { lang.send(player, "no-permission"); return; }
        if (args.length < 2 || args.length > 3) { usage(player, "/ah sell <price> [currency]"); return; }
        BigDecimal price;
        try { price = new BigDecimal(args[1]); }
        catch (NumberFormatException ex) { usage(player, "/ah sell <price> [currency]"); return; }
        if (price.signum() <= 0 || !Double.isFinite(price.doubleValue())) { usage(player, "price must be a positive finite number"); return; }
        final BigDecimal listingPrice = price.setScale(2, RoundingMode.HALF_UP);
        String currency = args.length == 3 ? args[2] : plugin.getConfig().getString("economy.default-currency", "vault");
        List<String> allowed = auctionConfig.getStringList("settings.allowed-currencies");
        if (!allowed.stream().anyMatch(value -> value.equals("*") || value.equalsIgnoreCase(currency))) { lang.send(player, "currency-unavailable", Map.of("currency", currency)); return; }
        CurrencyProvider provider = currencies.get(currency);
        if (provider == null) { lang.send(player, "currency-unavailable", Map.of("currency", currency)); return; }
        BigDecimal min = BigDecimal.valueOf(auctionConfig.getDouble("currency-limits." + currency + ".minimum", auctionConfig.getDouble("settings.minimum-price", 1.0)));
        BigDecimal max = BigDecimal.valueOf(auctionConfig.getDouble("currency-limits." + currency + ".maximum", auctionConfig.getDouble("settings.maximum-price", 1_000_000_000.0)));
        if (listingPrice.compareTo(min) < 0 || listingPrice.compareTo(max) > 0) { usage(player, "price range: " + min + " - " + max); return; }
        ItemStack held = player.getInventory().getItemInMainHand().clone();
        if (held.getType().isAir()) { lang.send(player, "blacklisted"); return; }
        if (isBlacklisted(player, held)) { lang.send(player, "blacklisted"); return; }
        int maxListings = maxListings(player);
        database.countActive(player.getUniqueId()).thenAccept(count -> runMain(() -> {
            if (!player.isOnline()) return;
            if (count >= maxListings) { lang.send(player, "listing-limit"); return; }
            ItemStack current = player.getInventory().getItemInMainHand();
            if (!current.isSimilar(held) || current.getAmount() != held.getAmount()) { lang.send(player, "error"); return; }
            AuctionListEvent listEvent = new AuctionListEvent(player, held.clone(), listingPrice, currency);
            Bukkit.getPluginManager().callEvent(listEvent);
            if (listEvent.isCancelled()) return;
            BigDecimal fee = BigDecimal.valueOf(auctionConfig.getDouble("settings.listing-fee", 0.0)).setScale(2, RoundingMode.HALF_UP);
            if (fee.signum() > 0 && !provider.withdraw(player, fee.doubleValue())) { lang.send(player, "insufficient-funds"); return; }
            ItemStack listed = held.clone(); player.getInventory().setItemInMainHand(null);
            long now = System.currentTimeMillis();
            long duration = Math.max(1, Math.min(auctionConfig.getLong("settings.default-duration-hours", 48), auctionConfig.getLong("settings.maximum-duration-hours", 168)));
            long expires = now + duration * 60L * 60L * 1000L;
            String display = plainName(listed);
            if (display.length() > 120) display = display.substring(0, 120);
            database.createListing(player.getUniqueId(), player.getName(), display, currency, listingPrice.doubleValue(), now, expires, listed.serializeAsBytes())
                    .whenComplete((id, error) -> runMain(() -> {
                        if (error != null || id == null || id < 0) {
                            giveOrDrop(player, listed);
                            if (fee.signum() > 0) provider.deposit(player, fee.doubleValue());
                            plugin.getLogger().log(Level.WARNING, "Could not create auction listing", error);
                            lang.send(player, "error"); return;
                        }
                        lang.send(player, "auction-created", Map.of("price", listingPrice.toPlainString(), "currency", currency));
                        play(player, "success");
                    }));
        })).exceptionally(error -> { plugin.getLogger().log(Level.WARNING, "Could not count auction listings", error); runMain(() -> lang.send(player, "error")); return null; });
    }

    private int maxListings(Player player) {
        int max = auctionConfig.getInt("settings.listings-per-player", 5);
        String prefix = "octaeconomy.auction.limit.";
        for (var perm : player.getEffectivePermissions()) {
            String node = perm.getPermission().toLowerCase(Locale.ROOT);
            if (!perm.getValue() || !node.startsWith(prefix)) continue;
            try { max = Math.max(max, Math.min(1000, Integer.parseInt(node.substring(prefix.length())))); }
            catch (NumberFormatException ignored) { }
        }
        return Math.max(1, max);
    }

    private boolean isBlacklisted(Player player, ItemStack item) {
        if (auctionConfig.getStringList("settings.blacklist.worlds").contains(player.getWorld().getName())) return true;
        if (auctionConfig.getStringList("settings.blacklist.materials").stream().anyMatch(s -> s.equalsIgnoreCase(item.getType().name()))) return true;
        String name = plainName(item).toLowerCase(Locale.ROOT);
        if (auctionConfig.getStringList("settings.blacklist.name-keywords").stream().anyMatch(name::contains)) return true;
        if (item.getItemMeta() != null && item.getItemMeta().hasLore()) {
            String lore = String.join(" ", item.getItemMeta().getLore()).toLowerCase(Locale.ROOT);
            return auctionConfig.getStringList("settings.blacklist.lore-keywords").stream().anyMatch(lore::contains);
        }
        return false;
    }

    private void openMarket(Player player, String search, int page) {
        database.activeListings(search, AUCTION_GRID_SLOTS.length, page * AUCTION_GRID_SLOTS.length).whenComplete((listings, error) -> runMain(() -> {
            if (error != null) { plugin.getLogger().log(Level.WARNING, "Could not load auction listings", error); lang.send(player, "error"); return; }
            AuctionTheme theme = auctionTheme(player);
            OctaMenuHolder holder = new OctaMenuHolder(player.getUniqueId(), MenuType.AUCTION_LIST, search, page, 0, -1);
            Inventory inv = Bukkit.createInventory(holder, 54, auctionTitle(theme, "Market")); holder.attach(inv);
            paintAuctionFrame(inv, theme);
            paintAuctionHeader(inv, theme, "LIVE LOTS", "Browse current offers");
            for (int i = 0; i < listings.size(); i++) {
                AuctionListing listing = listings.get(i);
                ItemStack display = decode(listing.itemData()); if (display == null) continue;
                ItemMeta meta = display.getItemMeta();
                normalizeItemText(meta);
                // Keep each line short. The raw serialized item retains its original NBT/lore;
                // only the market preview uses this compact listing summary.
                List<Component> lore = new ArrayList<>();
                lore.add(ui("<gray>Seller <white>" + escape(compactText(listing.sellerName(), 12))));
                lore.add(themedPrice(theme, "♦", listing.price(), compactText(listing.currency(), 7)));
                lore.add(ui("<gray>Time <white>" + compactText(timeLeft(listing.expiresAt()), 14)));
                lore.add(ui("<yellow>Inspect lot"));
                meta.lore(lore); display.setItemMeta(meta);
                int slot = AUCTION_GRID_SLOTS[i];
                inv.setItem(slot, display); holder.map(slot, Long.toString(listing.id()));
            }
            inv.setItem(45, button(Material.BARRIER, "<red><bold>✕ CLOSE</bold>"));
            inv.setItem(47, themeSwitchButton(theme));
            inv.setItem(48, button(theme.accent(), "<yellow>← Previous"));
            inv.setItem(49, button(theme.emblem(), theme.title(), "<gray>Theme: <white>" + themeLabel(theme.id())));
            inv.setItem(50, button(theme.accent(), "<yellow>Next →"));
            inv.setItem(52, button(Material.BOOK, "<green><bold>MY LISTINGS</bold>", "<gray>Manage your active offers"));
            inv.setItem(53, button(Material.CHEST, "<aqua><bold>COLLECT</bold>", "<gray>Retrieve your auction items"));
            player.openInventory(inv);
        }));
    }

    private void openConfirm(Player player, long listingId) {
        database.find(listingId).whenComplete((listing, error) -> runMain(() -> {
            if (error != null || listing == null) { lang.send(player, "error"); return; }
            if (listing.sellerId().equals(player.getUniqueId())) { lang.send(player, "no-permission"); return; }
            AuctionTheme theme = auctionTheme(player);
            OctaMenuHolder holder = new OctaMenuHolder(player.getUniqueId(), MenuType.AUCTION_CONFIRM, "", 0, 0, listingId);
            Inventory inv = Bukkit.createInventory(holder, 27, auctionTitle(theme, "Confirm Purchase")); holder.attach(inv);
            paintAuctionFrame(inv, theme);
            ItemStack item = decode(listing.itemData());
            if (item != null) {
                ItemMeta itemMeta = item.getItemMeta();
                normalizeItemText(itemMeta);
                List<Component> itemLore = new ArrayList<>();
                itemLore.add(themedPrice(theme, "Total", listing.price(), listing.currency()));
                itemLore.add(ui("<gray>Seller: <white>" + escape(compactText(listing.sellerName(), 16))));
                itemLore.add(ui("<gray>Expires: <white>" + timeLeft(listing.expiresAt())));
                itemMeta.lore(itemLore); item.setItemMeta(itemMeta);
                inv.setItem(13, item);
            }
            inv.setItem(11, button(Material.RED_STAINED_GLASS_PANE, "<red><bold>← CANCEL</bold>", "<gray>Return to the market"));
            inv.setItem(15, button(theme.accent(), "<green><bold>CONFIRM PURCHASE</bold>", "<white>" + format(listing.price()) + " " + listing.currency(), "<yellow>Click to complete this purchase"));
            inv.setItem(22, themeSwitchButton(theme));
            player.openInventory(inv);
            lang.send(player, "auction-confirm", Map.of("item", listing.itemName(), "price", format(listing.price()), "currency", listing.currency()));
        }));
    }

    public void handleClick(Player player, InventoryClickEvent event, OctaMenuHolder holder) {
        if (!allowClick(player)) return;
        int slot = event.getRawSlot(); if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        switch (holder.type()) {
            case AUCTION_LIST -> {
                if (slot == 45) { player.closeInventory(); return; }
                if (slot == 47) { openThemeSelector(player, "market", holder.context(), holder.page()); return; }
                if (slot == 48) { openMarket(player, holder.context(), Math.max(0, holder.page() - 1)); return; }
                if (slot == 50) { openMarket(player, holder.context(), holder.page() + 1); return; }
                if (slot == 52) { openMine(player, 0); return; }
                if (slot == 53) { collect(player); return; }
                String key = holder.key(slot); if (key != null) try { openConfirm(player, Long.parseLong(key)); } catch (NumberFormatException ignored) { }
            }
            case AUCTION_CONFIRM -> {
                if (slot == 11) { openMarket(player, "", 0); return; }
                if (slot == 15) { purchase(player, holder.listingId()); return; }
                if (slot == 22) { openThemeSelector(player, "confirm", Long.toString(holder.listingId()), 0); return; }
            }
            case AUCTION_THEME -> {
                String selected = holder.key(slot);
                if (selected != null) {
                    playerThemes.put(player.getUniqueId(), selected);
                    play(player, "success");
                    lang.send(player, "auction-theme-changed", Map.of("theme", selected));
                    String[] destination = holder.context().split("\n", 2);
                    String view = destination.length > 0 ? destination[0] : "market";
                    String query = destination.length > 1 ? destination[1] : "";
                    switch (view) {
                        case "mine" -> openMine(player, holder.page());
                        case "expired" -> openExpired(player, holder.page());
                        case "confirm" -> { try { openConfirm(player, Long.parseLong(query)); } catch (NumberFormatException ignored) { openMarket(player, "", 0); } }
                        default -> openMarket(player, query, holder.page());
                    }
                    return;
                }
                if (slot == 22) {
                    String[] destination = holder.context().split("\n", 2);
                    String view = destination.length > 0 ? destination[0] : "market";
                    String query = destination.length > 1 ? destination[1] : "";
                    switch (view) {
                        case "mine" -> openMine(player, holder.page());
                        case "expired" -> openExpired(player, holder.page());
                        case "confirm" -> { try { openConfirm(player, Long.parseLong(query)); } catch (NumberFormatException ignored) { openMarket(player, "", 0); } }
                        default -> openMarket(player, query, holder.page());
                    }
                }
            }
            case AUCTION_MINE -> {
                if (slot == 45) { openMarket(player, "", 0); return; }
                if (slot == 47) { openThemeSelector(player, "mine", "", holder.page()); return; }
                if (slot == 48) { openMine(player, Math.max(0, holder.page() - 1)); return; }
                if (slot == 50) { openMine(player, holder.page() + 1); return; }
                if (slot == 53) { collect(player); return; }
                String key = holder.key(slot); if (key != null) try { cancelListing(player, Long.parseLong(key)); } catch (NumberFormatException ignored) { }
            }
            case AUCTION_EXPIRED -> {
                if (slot == 45) { openMarket(player, "", 0); return; }
                if (slot == 47) { openThemeSelector(player, "expired", "", holder.page()); return; }
                if (slot == 48) { openExpired(player, Math.max(0, holder.page() - 1)); return; }
                if (slot == 50) { openExpired(player, holder.page() + 1); return; }
                if (slot == 53) collect(player);
            }
            default -> { }
        }
    }

    private void purchase(Player player, long id) {
        database.find(id).whenComplete((listing, error) -> runMain(() -> {
            if (error != null || listing == null) { lang.send(player, "error"); return; }
            if (listing.sellerId().equals(player.getUniqueId())) { lang.send(player, "no-permission"); return; }
            AuctionPurchaseEvent purchaseEvent = new AuctionPurchaseEvent(player, listing);
            Bukkit.getPluginManager().callEvent(purchaseEvent);
            if (purchaseEvent.isCancelled()) return;
            CurrencyProvider provider = currencies.get(listing.currency());
            if (provider == null) { lang.send(player, "currency-unavailable", Map.of("currency", listing.currency())); return; }
            ItemStack item = decode(listing.itemData()); if (item == null || !canFit(player.getInventory(), item)) { lang.send(player, "inventory-full"); return; }
            if (!provider.withdraw(player, listing.price())) { lang.send(player, "insufficient-funds"); return; }
            database.purchase(id, player.getUniqueId(), auctionConfig.getDouble("settings.tax-percent", 5.0)).whenComplete((success, purchaseError) -> runMain(() -> {
                if (purchaseError != null || !Boolean.TRUE.equals(success)) {
                    provider.deposit(player, listing.price());
                    if (purchaseError != null) plugin.getLogger().log(Level.WARNING, "Auction purchase transaction failed", purchaseError);
                    lang.send(player, "error"); return;
                }
                lang.send(player, "auction-bought"); player.closeInventory();
                deliverPending(player);
                paySeller(listing.sellerId());
                logTransaction("PURCHASE listing=" + id + " buyer=" + player.getUniqueId() + " price=" + listing.price() + " currency=" + listing.currency());
            }));
        }));
    }

    private void cancelListing(Player player, long id) {
        database.cancel(id, player.getUniqueId()).whenComplete((success, error) -> runMain(() -> {
            if (error != null || !Boolean.TRUE.equals(success)) { lang.send(player, "error"); return; }
            lang.send(player, "auction-cancelled"); openMine(player, 0);
        }));
    }

    private void openMine(Player player, int page) {
        database.ownListings(player.getUniqueId(), AUCTION_GRID_SLOTS.length, page * AUCTION_GRID_SLOTS.length).whenComplete((listings, error) -> runMain(() -> {
            if (error != null) { lang.send(player, "error"); return; }
            AuctionTheme theme = auctionTheme(player);
            OctaMenuHolder holder = new OctaMenuHolder(player.getUniqueId(), MenuType.AUCTION_MINE, "", page, 0, -1);
            Inventory inv = Bukkit.createInventory(holder, 54, auctionTitle(theme, "My Listings")); holder.attach(inv);
            paintAuctionFrame(inv, theme);
            paintAuctionHeader(inv, theme, "YOUR LOTS", "Manage your listings");
            for (int i = 0; i < listings.size(); i++) {
                AuctionListing listing = listings.get(i); ItemStack display = decode(listing.itemData()); if (display == null) continue;
                ItemMeta meta = display.getItemMeta();
                normalizeItemText(meta);
                List<Component> lore = normalizedLore(meta); if (!lore.isEmpty()) lore.add(Component.empty());
                lore.add(ui("<dark_gray>╭──────── <white><bold>YOUR LISTING</bold> <dark_gray>────────╮"));
                lore.add(themedPrice(theme, "Asking price", listing.price(), listing.currency()));
                lore.add(ui("<gray>⌛ Expires <dark_gray>» <white>" + timeLeft(listing.expiresAt())));
                lore.add(ui("<dark_gray>╰──────────────────────────────────╯"));
                lore.add(ui("<red>➤ Click to cancel this listing"));
                meta.lore(lore); display.setItemMeta(meta);
                int slot = AUCTION_GRID_SLOTS[i];
                inv.setItem(slot, display); holder.map(slot, Long.toString(listing.id()));
            }
            inv.setItem(45, button(Material.ARROW, "<gold>← BACK TO MARKET"));
            inv.setItem(47, themeSwitchButton(theme));
            inv.setItem(48, button(theme.accent(), "<yellow>← Previous"));
            inv.setItem(49, button(theme.emblem(), theme.title(), "<gray>Theme: <white>" + themeLabel(theme.id())));
            inv.setItem(50, button(theme.accent(), "<yellow>Next →"));
            inv.setItem(53, button(Material.CHEST, "<aqua><bold>COLLECT ITEMS</bold>"));
            player.openInventory(inv);
        }));
    }

    private void openExpired(Player player, int page) {
        database.expiredListings(player.getUniqueId(), AUCTION_GRID_SLOTS.length, page * AUCTION_GRID_SLOTS.length).whenComplete((listings, error) -> runMain(() -> {
            if (error != null) { lang.send(player, "error"); return; }
            AuctionTheme theme = auctionTheme(player);
            OctaMenuHolder holder = new OctaMenuHolder(player.getUniqueId(), MenuType.AUCTION_EXPIRED, "", page, 0, -1);
            Inventory inv = Bukkit.createInventory(holder, 54, auctionTitle(theme, "Expired Items")); holder.attach(inv);
            paintAuctionFrame(inv, theme);
            paintAuctionHeader(inv, theme, "ARCHIVE", "Expired items are kept safe");
            for (int i = 0; i < listings.size(); i++) {
                ItemStack display = decode(listings.get(i).itemData());
                if (display != null) {
                    ItemMeta meta = display.getItemMeta();
                    normalizeItemText(meta);
                    List<Component> lore = normalizedLore(meta); if (!lore.isEmpty()) lore.add(Component.empty());
                    lore.add(ui("<dark_gray>╭──────── <white><bold>ARCHIVED LOT</bold> <dark_gray>────────╮"));
                    lore.add(ui("<gray>◇ This item is safely stored"));
                    lore.add(ui("<yellow>➤ Use /ah collect to retrieve it"));
                    lore.add(ui("<dark_gray>╰──────────────────────────────────╯"));
                    meta.lore(lore); display.setItemMeta(meta);
                    int slot = AUCTION_GRID_SLOTS[i];
                    inv.setItem(slot, display); holder.map(slot, Long.toString(listings.get(i).id()));
                }
            }
            inv.setItem(45, button(Material.ARROW, "<gold>← BACK TO MARKET"));
            inv.setItem(47, themeSwitchButton(theme));
            inv.setItem(48, button(theme.accent(), "<yellow>← Previous"));
            inv.setItem(49, button(theme.emblem(), theme.title(), "<gray>Theme: <white>" + themeLabel(theme.id())));
            inv.setItem(50, button(theme.accent(), "<yellow>Next →"));
            inv.setItem(53, button(Material.CHEST, "<aqua><bold>COLLECT ITEMS</bold>"));
            player.openInventory(inv); lang.send(player, "auction-expired");
        }));
    }

    private void collect(Player player) {
        if (!database.isReady()) { lang.send(player, "database-not-ready"); return; }
        UUID owner = player.getUniqueId();
        if (!delivering.add(owner)) return;
        database.deliveries(owner).whenComplete((items, error) -> runMain(() -> {
            try {
                if (error != null) { lang.send(player, "error"); return; }
                if (items.isEmpty()) { lang.send(player, "auction-none"); return; }
                int count = 0;
                for (AuctionDatabase.Delivery delivery : items) {
                    ItemStack item = decode(delivery.itemData()); if (item == null || !canFit(player.getInventory(), item)) continue;
                    if (player.getInventory().addItem(item).isEmpty()) { count++; database.deleteDelivery(delivery.id()); }
                }
                if (count > 0) lang.send(player, "auction-collected", Map.of("amount", count)); else lang.send(player, "inventory-full");
            } finally { delivering.remove(owner); }
        }));
    }

    private void deliverPending(Player player) {
        UUID owner = player.getUniqueId();
        if (!delivering.add(owner)) return;
        database.deliveries(owner).whenComplete((items, error) -> {
            if (error != null) { delivering.remove(owner); plugin.getLogger().log(Level.WARNING, "Could not load pending auction deliveries", error); return; }
            runMain(() -> {
                try {
                    if (!player.isOnline()) return;
                    for (AuctionDatabase.Delivery delivery : items) {
                        ItemStack item = decode(delivery.itemData()); if (item == null || !canFit(player.getInventory(), item)) continue;
                        if (player.getInventory().addItem(item).isEmpty()) database.deleteDelivery(delivery.id());
                    }
                } finally { delivering.remove(owner); }
            });
        });
    }

    public void onJoin(Player player) {
        if (!database.isReady()) return;
        deliverPending(player); paySeller(player.getUniqueId());
    }

    private void paySeller(UUID sellerId) {
        if (!paying.add(sellerId)) return;
        database.payouts(sellerId).whenComplete((payouts, error) -> {
            if (error != null) { paying.remove(sellerId); plugin.getLogger().log(Level.WARNING, "Could not load pending auction payouts", error); return; }
            runMain(() -> {
                try {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(sellerId);
                    for (AuctionDatabase.Payout payout : payouts) {
                        CurrencyProvider provider = currencies.get(payout.currency());
                        if (provider != null && provider.deposit(offline, payout.amount())) {
                            database.deletePayout(payout.id());
                            Player onlineSeller = offline.getPlayer();
                            if (onlineSeller != null) {
                                lang.send(onlineSeller, "auction-seller-paid", Map.of("amount", format(payout.amount()), "currency", payout.currency()));
                            }
                        }
                    }
                } finally { paying.remove(sellerId); }
            });
        });
    }

    private boolean canFit(PlayerInventory inventory, ItemStack item) {
        int remaining = item.getAmount();
        for (ItemStack current : inventory.getStorageContents()) {
            if (current == null || current.getType().isAir()) remaining -= item.getMaxStackSize();
            else if (current.isSimilar(item)) remaining -= Math.max(0, current.getMaxStackSize() - current.getAmount());
            if (remaining <= 0) return true;
        }
        return remaining <= 0;
    }

    private ItemStack decode(byte[] bytes) {
        try { return ItemStack.deserializeBytes(bytes); }
        catch (Exception ex) { plugin.getLogger().warning("An auction item could not be deserialized and will remain in storage."); return null; }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private String normalizeTheme(String configured) {
        if (configured == null) return null;
        String id = configured.toLowerCase(Locale.ROOT).replace('-', '_');
        return List.of("nether", "overworld", "ocean", "the_end").contains(id) ? id : null;
    }

    private AuctionTheme auctionTheme(Player player) {
        String configured = playerThemes.getOrDefault(player.getUniqueId(), guiConfig.getString("auction.theme", "overworld"));
        String id = normalizeTheme(configured);
        return themeById(id == null ? "overworld" : id);
    }

    private AuctionTheme themeById(String id) {
        String path = "auction.themes." + id + ".";
        Material background; Material accent; Material emblem; String title; String tagline; String priceStyle;
        switch (id) {
            case "nether" -> { background = Material.BLACK_STAINED_GLASS_PANE; accent = Material.RED_STAINED_GLASS_PANE; emblem = Material.BLAZE_POWDER; title = "<gradient:#ff8247:#b51f28><bold>Auction House</bold></gradient>"; tagline = "Ashen markets / Nether treasures"; priceStyle = "<#ff8247>"; }
            case "ocean" -> { background = Material.LIGHT_BLUE_STAINED_GLASS_PANE; accent = Material.CYAN_STAINED_GLASS_PANE; emblem = Material.HEART_OF_THE_SEA; title = "<gradient:#7be9ff:#2678d8><bold>Auction House</bold></gradient>"; tagline = "Deep-sea goods / Ocean relics"; priceStyle = "<#55b8ff>"; }
            case "the_end" -> { background = Material.PURPLE_STAINED_GLASS_PANE; accent = Material.MAGENTA_STAINED_GLASS_PANE; emblem = Material.END_CRYSTAL; title = "<gradient:#e8a4ff:#8e5bda><bold>Auction House</bold></gradient>"; tagline = "Void artifacts / Ender treasures"; priceStyle = "<#c98cff>"; }
            default -> { background = Material.GRAY_STAINED_GLASS_PANE; accent = Material.LIME_STAINED_GLASS_PANE; emblem = Material.COMPASS; title = "<gradient:#b6f58b:#3cae71><bold>Auction House</bold></gradient>"; tagline = "Adventurer market / World essentials"; priceStyle = "<#83d65a>"; }
        }
        background = materialOr(guiConfig.getString(path + "background"), background);
        accent = materialOr(guiConfig.getString(path + "accent"), accent);
        emblem = materialOr(guiConfig.getString(path + "emblem"), emblem);
        title = guiConfig.getString(path + "title", title);
        tagline = guiConfig.getString(path + "tagline", tagline);
        priceStyle = guiConfig.getString(path + "price-style", priceStyle);
        return new AuctionTheme(id, background, accent, emblem, title, tagline, priceStyle);
    }

    private void openThemeSelector(Player player, String view, String query, int page) {
        AuctionTheme active = auctionTheme(player);
        OctaMenuHolder holder = new OctaMenuHolder(player.getUniqueId(), MenuType.AUCTION_THEME, view + "\n" + query, page, 0, -1);
        Inventory inv = Bukkit.createInventory(holder, 27, auctionTitle(active, "Choose Dimension"));
        holder.attach(inv);
        paintAuctionFrame(inv, active);
        inv.setItem(4, button(active.emblem(), active.title(), "<gray>Current dimension", "<white>" + active.id()));
        String[] ids = {"overworld", "nether", "ocean", "the_end"};
        Material[] icons = {Material.GRASS_BLOCK, Material.NETHERRACK, Material.HEART_OF_THE_SEA, Material.END_STONE};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            AuctionTheme theme = themeById(id);
            int slot = 10 + i * 2;
            ItemStack choice = button(icons[i], themeLabel(id),
                    "<gray>" + compactText(theme.tagline(), 24),
                    id.equals(active.id()) ? "<green>Active" : "<yellow>Click to apply");
            inv.setItem(slot, choice);
            holder.map(slot, id);
        }
        inv.setItem(22, button(active.accent(), "<gray>Back"));
        player.openInventory(inv);
    }

    private ItemStack themeSwitchButton(AuctionTheme theme) {
        return button(theme.emblem(), "<gold>Change Theme",
                "<gray>Current: <white>" + themeLabel(theme.id()),
                "<yellow>Click to switch theme");
    }

    private String themeLabel(String id) {
        return switch (id) {
            case "nether" -> "Nether";
            case "ocean" -> "Ocean";
            case "the_end" -> "The End";
            default -> "Overworld";
        };
    }

    private String compactText(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("[\r\n]", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(1, max - 1)) + "…";
    }

    private Material materialOr(String configured, Material fallback) {
        if (configured == null || configured.isBlank()) return fallback;
        Material material = Material.matchMaterial(configured);
        return material != null && material.isItem() ? material : fallback;
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta(); meta.displayName(Component.text(" ")); item.setItemMeta(meta);
        return item;
    }

    private void paintAuctionFrame(Inventory inventory, AuctionTheme theme) {
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane(theme.background()));
        int size = inventory.getSize();
        if (size == 54) {
            // Theme masthead: the central emblem sits above the 7 x 4 listing grid.
            for (int slot : new int[]{0, 1, 2, 3, 5, 6, 7, 8}) inventory.setItem(slot, pane(theme.accent()));
            // Vertical rails keep the listing cards aligned and visually framed.
            for (int slot : new int[]{9, 17, 18, 26, 27, 35, 36, 44}) inventory.setItem(slot, pane(theme.accent()));
            for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, pane(theme.accent()));
        } else if (size == 27) {
            for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, pane(theme.accent()));
            for (int slot = 18; slot < 27; slot++) inventory.setItem(slot, pane(theme.accent()));
        }
    }

    private void paintAuctionHeader(Inventory inventory, AuctionTheme theme, String heading, String subtitle) {
        ItemStack emblem = button(theme.emblem(), theme.title(),
                "<gray>" + themeLabel(theme.id()) + " Theme",
                "<dark_gray>" + compactText(subtitle, 24));
        inventory.setItem(4, emblem);
    }

    private Component themedPrice(AuctionTheme theme, String label, double amount, String currency) {
        Component prefix = ui("<gray>" + label + " ");
        String amountText = compactText(format(amount), 12);
        Component value = ui(theme.priceStyle() + "<bold>" + amountText + " " + escape(compactText(currency, 7)) + "</bold>");
        return prefix.append(value);
    }

    private Component auctionTitle(AuctionTheme theme, String view) {
        // The inventory title stays identical across dimensions; theme colors are expressed
        // by the frame, emblem, controls, and price accent instead of a long caption.
        return ui(theme.title());
    }

    private String legacyToMiniMessage(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char marker = value.charAt(i);
            if ((marker == '&' || marker == '§') && i + 1 < value.length()) {
                char code = Character.toLowerCase(value.charAt(i + 1));
                String tag = switch (code) {
                    case '0' -> "black"; case '1' -> "dark_blue"; case '2' -> "dark_green";
                    case '3' -> "dark_aqua"; case '4' -> "dark_red"; case '5' -> "dark_purple";
                    case '6' -> "gold"; case '7' -> "gray"; case '8' -> "dark_gray";
                    case '9' -> "blue"; case 'a' -> "green"; case 'b' -> "aqua";
                    case 'c' -> "red"; case 'd' -> "light_purple"; case 'e' -> "yellow"; case 'f' -> "white";
                    case 'k' -> "obfuscated"; case 'l' -> "bold"; case 'm' -> "strikethrough";
                    case 'n' -> "underlined"; case 'o' -> "italic"; case 'r' -> "reset";
                    default -> null;
                };
                if (code == 'x' && i + 13 < value.length()) {
                    StringBuilder hex = new StringBuilder();
                    boolean valid = true;
                    for (int j = 0; j < 6; j++) {
                        int pos = i + 2 + j * 2;
                        if (value.charAt(pos) != marker || Character.digit(value.charAt(pos + 1), 16) < 0) { valid = false; break; }
                        hex.append(value.charAt(pos + 1));
                    }
                    if (valid) { out.append("<#").append(hex).append('>'); i += 13; continue; }
                }
                if (code == '#' && i + 7 < value.length()) {
                    String hex = value.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) { out.append("<#").append(hex).append('>'); i += 7; continue; }
                }
                if (tag != null) { out.append('<').append(tag).append('>'); i++; continue; }
            }
            out.append(marker);
        }
        return out.toString();
    }

    /** Parses configured MiniMessage and legacy colors without wrapping them in another palette. */
    private Component ui(String value) {
        if (value == null || value.isEmpty()) return Component.empty().decoration(TextDecoration.ITALIC, false);
        return mini.deserialize(legacyToMiniMessage(value)).decoration(TextDecoration.ITALIC, false);
    }

    private void normalizeItemText(ItemMeta meta) {
        if (meta.hasDisplayName() && meta.displayName() != null) {
            meta.displayName(meta.displayName().decoration(TextDecoration.ITALIC, false));
        }
    }

    private List<Component> normalizedLore(ItemMeta meta) {
        List<Component> result = new ArrayList<>();
        if (meta.lore() != null) meta.lore().stream()
                .map(line -> line.decoration(TextDecoration.ITALIC, false)).forEach(result::add);
        return result;
    }

    private ItemStack button(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.displayName(ui(name));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(this::ui).toList()); stack.setItemMeta(meta); return stack;
    }

    private String plainName(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) return org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
    private String timeLeft(long expires) {
        long left = Math.max(0, expires - System.currentTimeMillis()); long hours = left / 3_600_000L;
        long minutes = (left % 3_600_000L) / 60_000L; return hours + "h " + minutes + "m";
    }
    private String format(double amount) { return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private String escape(String value) { return value.replace("<", "\\<").replace(">", "\\>"); }
    private void usage(Player player, String text) { lang.send(player, "usage", Map.of("usage", text)); }
    private boolean allowClick(Player player) { long now = System.currentTimeMillis(); Long previous = debounce.put(player.getUniqueId(), now); return previous == null || now - previous > 120; }
    private void runMain(Runnable task) { scheduler.main(task); }
    private void play(Player player, String key) {
        if (!plugin.getConfig().getBoolean("sound.enabled", true)) return;
        String name = plugin.getConfig().getString("sound." + key, "UI_BUTTON_CLICK");
        try { player.playSound(player.getLocation(), Sound.valueOf(name.toUpperCase(Locale.ROOT)), 0.7f, 1.1f); } catch (IllegalArgumentException ignored) { }
    }
    private void logTransaction(String line) {
        if (!plugin.getConfig().getBoolean("transaction-log.enabled", false)) return;
        scheduler.async(() -> {
            try { java.io.File dir = new java.io.File(plugin.getDataFolder(), "logs"); dir.mkdirs();
                java.io.File file = new java.io.File(dir, "transactions-" + java.time.LocalDate.now() + ".log");
                java.nio.file.Files.writeString(file.toPath(), System.currentTimeMillis() + " " + line + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                java.io.File[] old = dir.listFiles((d, name) -> name.startsWith("transactions-") && name.endsWith(".log"));
                int keep = Math.max(1, plugin.getConfig().getInt("transaction-log.max-files", 7));
                if (old != null && old.length > keep) {
                    java.util.Arrays.sort(old, java.util.Comparator.comparingLong(java.io.File::lastModified).reversed());
                    for (int i = keep; i < old.length; i++) old[i].delete();
                }
            } catch (Exception ex) { plugin.getLogger().warning("Auction transaction log failed: " + ex.getMessage()); }
        });
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("sell", "expired", "collect", "search", "mylistings", "theme");
        if (args.length == 2 && args[0].equalsIgnoreCase("theme")) return List.of("overworld", "nether", "ocean", "the_end");
        if (args.length == 3 && args[0].equalsIgnoreCase("sell"))
            return auctionConfig.getStringList("settings.allowed-currencies").stream().filter(value -> !value.equals("*")).toList();
        return List.of();
    }
}
