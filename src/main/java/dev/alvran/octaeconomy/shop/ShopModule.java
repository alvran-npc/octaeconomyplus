/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.shop;

import dev.alvran.octaeconomy.OctaEconomy;
import dev.alvran.octaeconomy.api.ShopTransactionEvent;
import dev.alvran.octaeconomy.core.LangManager;
import dev.alvran.octaeconomy.core.Module;
import dev.alvran.octaeconomy.core.TaskScheduler;
import dev.alvran.octaeconomy.gui.MenuType;
import dev.alvran.octaeconomy.gui.OctaMenuHolder;
import dev.alvran.octaeconomy.hook.CurrencyManager;
import dev.alvran.octaeconomy.hook.CurrencyProvider;
import dev.alvran.octaeconomy.hook.MMOItemsHook;
import dev.alvran.octaeconomy.hook.SmartSpawnerHook;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ShopModule implements Module {
    private final OctaEconomy plugin;
    private final LangManager lang;
    private final CurrencyManager currencies;
    private final TaskScheduler scheduler;
    private final MMOItemsHook mmoItems;
    private final SmartSpawnerHook smartSpawner;
    private final ShopManager manager;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private YamlConfiguration guiConfig;
    private YamlConfiguration discountsConfig;
    private boolean discountConfigWarningLogged;
    private static final int[][] SHOP_ROWS = {
            {3, 4, 5}, {11, 12, 13, 14, 15}, {19, 20, 21, 22, 23, 24, 25},
            {27, 28, 29, 30, 31, 32, 33, 34, 35}, {36, 37, 38, 39, 40, 41, 42, 43, 44}
    };
    private static final int SHOP_PAGE_CAPACITY = 33;
    private record DiscountOffer(String id, String displayName, BigDecimal percent) { }
    private final Map<UUID, Long> clickDebounce = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, DailyUsage>> dailyUsage = new ConcurrentHashMap<>();
    private record DailyUsage(LocalDate date, int amount) { }
    private record PriceState(BigDecimal multiplier, long updatedAt) { }
    private final Map<String, PriceState> dynamicPrices = new ConcurrentHashMap<>();

    public ShopModule(OctaEconomy plugin, LangManager lang, CurrencyManager currencies, TaskScheduler scheduler,
                      MMOItemsHook mmoItems, SmartSpawnerHook smartSpawner) {
        this.plugin = plugin; this.lang = lang; this.currencies = currencies; this.scheduler = scheduler;
        this.mmoItems = mmoItems; this.smartSpawner = smartSpawner;
        this.manager = new ShopManager(plugin, mmoItems, smartSpawner);
    }

    @Override public String name() { return "ShopGUI"; }
    @Override public void enable() { reload(); }
    @Override public void disable() { clickDebounce.clear(); dailyUsage.clear(); dynamicPrices.clear(); }
    @Override public void reload() {
        guiConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "gui.yml"));
        discountsConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "discounts.yml"));
        discountConfigWarningLogged = false;
        manager.reload();
    }
    public ShopManager manager() { return manager; }
    public void clearPlayer(UUID player) {
        // Keep current-day counters across disconnects; otherwise a player could
        // bypass an enabled sell limit by reconnecting. Old-day entries are pruned.
        clickDebounce.remove(player);
        Map<String, DailyUsage> usage = dailyUsage.get(player);
        if (usage != null) {
            LocalDate today = LocalDate.now();
            usage.entrySet().removeIf(entry -> !entry.getValue().date().equals(today));
            if (usage.isEmpty()) dailyUsage.remove(player, usage);
        }
    }

    public boolean onShopCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { lang.send(sender, "players-only"); return true; }
        if (!player.hasPermission("octaeconomy.shop.use")) { lang.send(player, "no-permission"); return true; }
        if (args.length == 0) { openMain(player, 0); return true; }
        if (args[0].equalsIgnoreCase("search")) {
            if (args.length < 2) { lang.send(player, "usage", Map.of("usage", "/shop search <keyword>")); return true; }
            String search = String.join(" ", Arrays.copyOfRange(args, 1, args.length)); openCategory(player, "@search:" + search, 0); return true;
        }
        openMainCategory(player, args[0]);
        return true;
    }

    public boolean onSellGuiCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { lang.send(sender, "players-only"); return true; }
        if (!player.hasPermission("octaeconomy.sell.gui")) { lang.send(player, "no-permission"); return true; }
        openSellGui(player); return true;
    }

    public boolean onSellCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { lang.send(sender, "players-only"); return true; }
        if (!player.hasPermission("octaeconomy.sell.use")) { lang.send(player, "no-permission"); return true; }
        if (args.length != 1) { lang.send(player, "usage", Map.of("usage", "/sell <hand|handall|inv>")); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "hand" -> {
                if (!player.hasPermission("octaeconomy.sell.hand")) { lang.send(player, "no-permission"); return true; }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { lang.send(player, "cannot-sell"); return true; }
                sellFromInventory(player, hand, hand.getAmount(), true);
            }
            case "handall" -> {
                if (!player.hasPermission("octaeconomy.sell.handall")) { lang.send(player, "no-permission"); return true; }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { lang.send(player, "cannot-sell"); return true; }
                sellFromInventory(player, hand, Integer.MAX_VALUE, false);
            }
            case "inv" -> {
                if (!player.hasPermission("octaeconomy.sell.inv")) { lang.send(player, "no-permission"); return true; }
                sellInventory(player);
            }
            default -> lang.send(player, "usage", Map.of("usage", "/sell <hand|handall|inv>"));
        }
        return true;
    }

    public List<String> tabCompleteShop(String[] args) {
        if (args.length == 1) { List<String> result = new ArrayList<>(manager.categories().stream().map(ShopCategory::id).toList()); result.add("search"); return result; }
        if (args.length == 2 && args[0].equalsIgnoreCase("search")) return List.of("stone", "diamond", "iron");
        return List.of();
    }
    public List<String> tabCompleteSell(String[] args) { return args.length == 1 ? List.of("hand", "handall", "inv") : List.of(); }

    private void openMainCategory(Player player, String category) {
        ShopCategory found = manager.category(category);
        if (found == null || !player.hasPermission("octaeconomy.shop.category." + found.id())
                || (!found.permission().isBlank() && !player.hasPermission(found.permission()))) {
            lang.send(player, "category-missing"); return;
        }
        openCategory(player, category, 0);
    }

    public void openMain(Player player, int page) {
        List<ShopCategory> accessible = manager.categories().stream()
                .filter(category -> player.hasPermission("octaeconomy.shop.category." + category.id()))
                .filter(category -> category.permission().isBlank() || player.hasPermission(category.permission()))
                // category.slot controls ordering only; the actual GUI position always follows the trapezoid.
                .sorted(Comparator.comparingInt(ShopCategory::slot).thenComparing(ShopCategory::id))
                .toList();
        int maxPage = Math.max(0, (accessible.size() - 1) / SHOP_PAGE_CAPACITY);
        page = Math.min(Math.max(0, page), maxPage);
        OctaMenuHolder holder = new OctaMenuHolder(player.getUniqueId(), MenuType.SHOP_MAIN, "", page, 1, -1);
        Inventory inventory = Bukkit.createInventory(holder, 54, ui(guiConfig.getString("shop.title", "<dark_green>OCTA MARKET <gray>/ <white>Categories")));
        holder.attach(inventory);
        paintShopBackground(inventory);
        int start = page * SHOP_PAGE_CAPACITY;
        int visible = Math.min(SHOP_PAGE_CAPACITY, Math.max(0, accessible.size() - start));
        List<Integer> slots = centeredTrapezoidSlots(visible);
        for (int i = 0; i < visible; i++) {
            ShopCategory category = accessible.get(start + i);
            ItemStack icon = new ItemStack(category.icon());
            ItemMeta meta = icon.getItemMeta();
            // Preserve user-defined category colors rather than adding another palette.
            meta.displayName(ui(category.displayName()));
            meta.lore(List.of(
                    ui("<dark_gray>┌─ CATEGORY ───────────────────"),
                    ui("<gray>▸ <white>Identifier <dark_gray>· <gray>" + miniEscape(category.id())),
                    ui("<gray>▸ <white>Available items <dark_gray>· <white>" + manager.items(category.id()).size()),
                    ui("<dark_gray>└──────────────────────────────"),
                    ui("<yellow>➜ Click to open this collection")));
            setNormalLore(meta);
            icon.setItemMeta(meta);
            int slot = slots.get(i);
            inventory.setItem(slot, icon);
            holder.map(slot, category.id());
        }
        setNav(inventory, page, true);
        player.openInventory(inventory);
    }

    public void openCategory(Player player, String category, int page) {
        boolean searchMode = category.startsWith("@search:");
        List<ShopItem> entries;
        String title;
        if (searchMode) {
            String needle = category.substring(8).toLowerCase(Locale.ROOT);
            entries = manager.allItems().stream().filter(item -> {
                ShopCategory categoryInfo = manager.category(item.categoryId());
                if (categoryInfo == null || !player.hasPermission("octaeconomy.shop.category." + categoryInfo.id())) return false;
                if (!categoryInfo.permission().isBlank() && !player.hasPermission(categoryInfo.permission())) return false;
                return item.displayName().toLowerCase(Locale.ROOT).contains(needle)
                        || item.id().toLowerCase(Locale.ROOT).contains(needle)
                        || item.template().getType().name().toLowerCase(Locale.ROOT).contains(needle);
            }).toList();
            title = "<green>Shop Search";
        } else {
            ShopCategory info = manager.category(category);
            if (info == null || !player.hasPermission("octaeconomy.shop.category." + info.id())
                    || (!info.permission().isBlank() && !player.hasPermission(info.permission()))) {
                lang.send(player, "category-missing"); return;
            }
            entries = manager.items(category);
            title = "<green>Shop <dark_gray>· " + miniEscape(info.id());
        }

        int maxPage = Math.max(0, (entries.size() - 1) / SHOP_PAGE_CAPACITY);
        page = Math.min(Math.max(0, page), maxPage);
        OctaMenuHolder holder = new OctaMenuHolder(player.getUniqueId(), MenuType.SHOP_CATEGORY, category, page, 1, -1);
        Inventory inventory = Bukkit.createInventory(holder, 54, ui(title)); holder.attach(inventory);
        paintShopBackground(inventory);
        int start = page * SHOP_PAGE_CAPACITY;
        int visible = Math.min(SHOP_PAGE_CAPACITY, Math.max(0, entries.size() - start));
        List<Integer> slots = centeredTrapezoidSlots(visible);
        for (int i = 0; i < visible; i++) {
            ShopItem item = entries.get(start + i);
            int slot = slots.get(i);
            inventory.setItem(slot, createShopDisplay(item));
            holder.map(slot, item.categoryId() + ":" + item.id());
        }
        inventory.setItem(45, button(Material.ARROW, "<gold>Back"));
        inventory.setItem(48, button(Material.SPECTRAL_ARROW, "<aqua>Previous"));
        inventory.setItem(49, button(Material.COMPASS, "<green><bold>Shop</bold>", "<gray>Left: buy", "<gray>Right: sell", "<gray>Shift: stack"));
        inventory.setItem(50, button(Material.SPECTRAL_ARROW, "<aqua>Next"));
        inventory.setItem(53, button(Material.BARRIER, "<red>Close"));
        player.openInventory(inventory);
    }

    /** Centered 3-5-7-9-9 rows; an incomplete final row is centered too. */
    private List<Integer> centeredTrapezoidSlots(int count) {
        List<Integer> result = new ArrayList<>(Math.min(SHOP_PAGE_CAPACITY, Math.max(0, count)));
        int remaining = Math.min(SHOP_PAGE_CAPACITY, Math.max(0, count));
        for (int[] row : SHOP_ROWS) {
            if (remaining <= 0) break;
            int amount = Math.min(row.length, remaining);
            int center = row.length / 2;
            if ((amount & 1) == 1) {
                // Odd counts occupy a contiguous run centered on the middle slot.
                int offset = center - amount / 2;
                for (int i = 0; i < amount; i++) result.add(row[offset + i]);
            } else {
                // Even counts are mirrored around the center, leaving one intentional center gap.
                int half = amount / 2;
                for (int i = center - half; i < center; i++) result.add(row[i]);
                for (int i = center + 1; i <= center + half; i++) result.add(row[i]);
            }
            remaining -= amount;
        }
        return result;
    }

    private void paintShopBackground(Inventory inventory) {
        Material filler = materialOr(guiConfig.getString("shop.background", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE);
        Material accent = materialOr(guiConfig.getString("shop.accent", "GREEN_STAINED_GLASS_PANE"), Material.GREEN_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane(filler));

        // Symmetric corners around the 3-5-7-9-9 item silhouette.
        for (int slot : new int[]{0, 1, 2, 6, 7, 8, 9, 10, 16, 17, 18, 26}) {
            inventory.setItem(slot, pane(accent));
        }
        // The footer is reserved for navigation; its two ends frame the controls.
        inventory.setItem(45, pane(accent));
        inventory.setItem(53, pane(accent));
    }

    private Material materialOr(String name, Material fallback) {
        Material material = Material.matchMaterial(name == null ? "" : name);
        return material == null || !material.isItem() ? fallback : material;
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

    /** Parses configured MiniMessage and legacy colors without adding a second palette. */
    private Component ui(String value) {
        if (value == null || value.isEmpty()) return Component.empty().decoration(TextDecoration.ITALIC, false);
        return mini.deserialize(legacyToMiniMessage(value)).decoration(TextDecoration.ITALIC, false);
    }

    private void setNormalLore(ItemMeta meta) {
        if (meta.lore() != null) meta.lore(meta.lore().stream()
                .map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
    }

    private String miniEscape(String value) { return value.replace("<", "\\<").replace(">", "\\>"); }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }


    private ItemStack createShopDisplay(ShopItem item) {
        ItemStack display = item.template();
        ItemMeta meta = display.getItemMeta();
        if (meta.displayName() == null || meta.displayName().equals(Component.empty())) {
            meta.displayName(Component.text(prettyMaterial(item.template().getType().name())));
        } else {
            meta.displayName(meta.displayName().decoration(TextDecoration.ITALIC, false));
        }

        // Shop previews intentionally use compact commerce lore. The purchased template is never
        // modified, so original MMOItems/vanilla metadata remains intact in the delivered item.
        List<Component> lore = new ArrayList<>();
        if (item.canBuy()) {
            BigDecimal regular = dynamicPrice(item, true);
            DiscountOffer offer = activeDiscount(item);
            if (offer != null) {
                BigDecimal discounted = applyDiscount(regular, offer);
                lore.add(ui("<gray>Was <strikethrough>" + compactText(format(regular), 12) + " " + miniEscape(compactCurrency(item.currency(), 7))));
                lore.add(ui("<green>Now <bold>" + compactText(format(discounted), 12) + " " + miniEscape(compactCurrency(item.currency(), 7))));
                lore.add(ui("<gold>✦ " + compactText(offer.displayName(), 12) + " "
                        + offer.percent().stripTrailingZeros().toPlainString() + "%"));
            } else {
                lore.add(ui("<green>Buy <white>" + compactText(format(regular), 12) + " " + miniEscape(compactCurrency(item.currency(), 7))));
            }
        } else lore.add(ui("<dark_gray>Buy: <red>Unavailable"));

        if (item.canSell()) lore.add(ui("<red>Sell <white>" + compactText(format(dynamicPrice(item, false)), 12) + " " + miniEscape(compactCurrency(item.currency(), 7))));
        else lore.add(ui("<dark_gray>Sell: <red>Unavailable"));
        lore.add(ui("<dark_gray>Left-click: buy"));
        lore.add(ui("<dark_gray>Right-click: sell"));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private String compactCurrency(String currency, int max) {
        return compactText(currency == null ? "vault" : currency, max);
    }

    private String prettyMaterial(String material) {
        String[] parts = material.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append(' ');
            if (!part.isEmpty()) result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private String compactText(String text, int max) {
        if (text == null) return "";
        String clean = text.replaceAll("[\r\n]", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(1, max - 1)) + "…";
    }

    private DiscountOffer activeDiscount(ShopItem item) {
        if (discountsConfig == null || !discountsConfig.getBoolean("enabled", false)) return null;
        String active = Objects.toString(discountsConfig.getString("active", ""), "").trim();
        if (active.isEmpty()) return null;
        var offer = discountsConfig.getConfigurationSection("offers." + active);
        if (offer == null || !offer.getBoolean("enabled", false)) return null;

        double rawPercent = offer.getDouble("percent", 0.0);
        if (!Double.isFinite(rawPercent) || rawPercent <= 0.0 || rawPercent > 90.0) return null;
        BigDecimal percent = BigDecimal.valueOf(rawPercent);

        List<String> categories = offer.getStringList("categories");
        List<String> items = offer.getStringList("items");
        if (!categories.isEmpty() && categories.stream().noneMatch(value -> value.equalsIgnoreCase(item.categoryId()))) return null;
        if (!items.isEmpty() && items.stream().noneMatch(value -> value.equalsIgnoreCase(item.id())
                || value.equalsIgnoreCase(item.categoryId() + ":" + item.id()))) return null;

        LocalDateTime now = LocalDateTime.now();
        String startsAt = Objects.toString(offer.getString("starts-at", ""), "").trim();
        String endsAt = Objects.toString(offer.getString("ends-at", ""), "").trim();
        try {
            if (!startsAt.isEmpty() && now.isBefore(LocalDateTime.parse(startsAt))) return null;
            if (!endsAt.isEmpty() && now.isAfter(LocalDateTime.parse(endsAt))) return null;
        } catch (DateTimeParseException ex) {
            if (!discountConfigWarningLogged) {
                plugin.getLogger().warning("Invalid starts-at or ends-at value in discounts.yml; discounts are disabled until fixed.");
                discountConfigWarningLogged = true;
            }
            return null;
        }
        String name = offer.getString("display-name", "SPECIAL OFFER");
        return new DiscountOffer(active, name, percent);
    }

    private BigDecimal applyDiscount(BigDecimal original, DiscountOffer offer) {
        BigDecimal fraction = offer.percent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return original.multiply(BigDecimal.ONE.subtract(fraction)).setScale(2, RoundingMode.HALF_UP);
    }

    private void setNav(Inventory inventory, int page, boolean main) {
        inventory.setItem(45, button(main ? Material.BARRIER : Material.ARROW,
                main ? "<red>Close" : "<gold>Back"));
        inventory.setItem(48, button(Material.SPECTRAL_ARROW, "<aqua>Previous"));
        inventory.setItem(49, button(Material.BOOK, "<green>Shop", "<gray>Page " + (page + 1)));
        inventory.setItem(50, button(Material.SPECTRAL_ARROW, "<aqua>Next"));
        inventory.setItem(53, main
                ? button(Material.SPYGLASS, "<aqua>Search", "<gray>/shop search <word>")
                : button(Material.BARRIER, "<red><bold>✕ CLOSE</bold>"));
    }

    private ItemStack button(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta();
        meta.displayName(ui(name));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(this::ui).toList());
        stack.setItemMeta(meta); return stack;
    }

    public void handleClick(Player player, InventoryClickEvent event, OctaMenuHolder holder) {
        if (!allowClick(player)) return;
        int slot = event.getRawSlot(); if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        switch (holder.type()) {
            case SHOP_MAIN -> {
                if (slot == 45) { player.closeInventory(); return; }
                if (slot == 53) return;
                if (slot == 48) { openMain(player, Math.max(0, holder.page() - 1)); return; }
                if (slot == 50) { openMain(player, holder.page() + 1); return; }
                String category = holder.key(slot); if (category != null) openMainCategory(player, category);
            }
            case SHOP_CATEGORY -> {
                if (slot == 45) { openMain(player, 0); return; }
                if (slot == 48) { openCategory(player, holder.context(), Math.max(0, holder.page() - 1)); return; }
                if (slot == 50) { openCategory(player, holder.context(), holder.page() + 1); return; }
                if (slot == 53) { player.closeInventory(); return; }
                String key = holder.key(slot); if (key == null) return;
                String[] parts = key.split(":", 2); ShopItem item = manager.item(parts[0], parts[1]); if (item == null) return;
                if (event.getClick().isRightClick()) {
                    String action = plugin.getConfig().getString("shop.click-actions.right", "sell");
                    if (action.equalsIgnoreCase("sell")) sellFromInventory(player, item.template(), 1, false);
                    else if (action.equalsIgnoreCase("buy")) buy(player, item, 1);
                } else if (event.getClick().isLeftClick()) {
                    String action = plugin.getConfig().getString("shop.click-actions.left", "buy");
                    if (action.equalsIgnoreCase("buy")) {
                        if (event.isShiftClick()) buy(player, item, 64); else openQuantity(player, item, 1);
                    } else if (action.equalsIgnoreCase("sell")) {
                        sellFromInventory(player, item.template(), event.isShiftClick() ? 64 : 1, false);
                    }
                }
            }
            case SHOP_QUANTITY -> {
                String[] parts = holder.context().split(":", 2); if (parts.length != 2) return;
                ShopItem item = manager.item(parts[0], parts[1]); if (item == null) { player.closeInventory(); return; }
                if (slot == 45) { openCategory(player, item.categoryId(), 0); return; }
                int amount = holder.quantity();
                if (slot == 20) { openQuantity(player, item, 1); return; }
                if (slot == 21) { buy(player, item, 1); return; }
                if (slot == 22) { buy(player, item, 16); return; }
                if (slot == 23) { buy(player, item, 32); return; }
                if (slot == 24) { buy(player, item, 64); return; }
                if (slot == 29) { openQuantity(player, item, Math.max(1, amount - 1)); return; }
                if (slot == 33) { openQuantity(player, item, Math.min(2304, amount + 1)); return; }
                if (slot == 31) { buy(player, item, amount); return; }
                if (slot == 53) player.closeInventory();
            }
            default -> { }
        }
    }

    private void openQuantity(Player player, ShopItem item, int quantity) {
        OctaMenuHolder holder = new OctaMenuHolder(player.getUniqueId(), MenuType.SHOP_QUANTITY, item.categoryId() + ":" + item.id(), 0, quantity, -1);
        Inventory inv = Bukkit.createInventory(holder, 54, ui(guiConfig.getString("shop.title", "<dark_green>OCTA MARKET") + " <gray>/ <white>Quantity")); holder.attach(inv);
        paintShopBackground(inv);
        inv.setItem(20, createShopDisplay(item));
        inv.setItem(21, button(Material.PAPER, "<green>Buy 1"));
        inv.setItem(22, button(Material.PAPER, "<green>Buy 16"));
        inv.setItem(23, button(Material.PAPER, "<green>Buy 32"));
        inv.setItem(24, button(Material.PAPER, "<green>Buy 64"));
        inv.setItem(29, button(Material.RED_DYE, "<red>- 1"));
        inv.setItem(30, button(Material.NAME_TAG, "<yellow>Selected: " + quantity));
        inv.setItem(33, button(Material.LIME_DYE, "<green>+ 1"));
        BigDecimal total = purchaseUnitPrice(item).multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        inv.setItem(31, button(Material.EMERALD, "<green>Confirm purchase", "<gray>Total: " + format(total) + " " + item.currency()));
        inv.setItem(45, button(Material.ARROW, "<yellow>Back")); inv.setItem(53, button(Material.BARRIER, "<red>Close"));
        player.openInventory(inv);
    }

    private void buy(Player player, ShopItem item, int amount) {
        if (!item.canBuy()) { lang.send(player, "cannot-buy"); return; }
        ItemStack purchase = item.template(); purchase.setAmount(amount);
        if (!canFit(player.getInventory(), purchase)) { lang.send(player, "inventory-full"); play(player, "error"); return; }
        BigDecimal total = purchaseUnitPrice(item).multiply(BigDecimal.valueOf(amount)).setScale(2, RoundingMode.HALF_UP);
        if (!Double.isFinite(total.doubleValue()) || total.signum() < 0) { lang.send(player, "error"); return; }
        CurrencyProvider provider = currencies.get(item.currency());
        if (total.signum() > 0 && provider == null) { lang.send(player, "currency-unavailable", Map.of("currency", item.currency())); return; }
        ShopTransactionEvent transaction = new ShopTransactionEvent(player, purchase, amount, total, item.currency(), true);
        Bukkit.getPluginManager().callEvent(transaction);
        if (transaction.isCancelled()) return;
        if (total.signum() > 0 && !provider.withdraw(player, total.doubleValue())) {
            lang.send(player, "insufficient-funds"); play(player, "error"); return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(purchase);
        if (!leftovers.isEmpty()) {
            if (total.signum() > 0) provider.deposit(player, total.doubleValue());
            leftovers.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            lang.send(player, "inventory-full"); return;
        }
        updateDynamicPrice(item, true);
        lang.send(player, "purchase-success", Map.of("amount", amount, "item", item.displayName(), "price", format(total), "currency", item.currency()));
        play(player, "success"); logTransaction(player.getName() + " BUY " + amount + "x " + item.id() + " " + total + " " + item.currency());
        player.closeInventory();
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

    private void sellFromInventory(Player player, ItemStack match, int requested, boolean exactHand) {
        if (match == null || match.getType().isAir()) { lang.send(player, "cannot-sell"); return; }
        ShopItem item = manager.match(match);
        if (item == null || !item.canSell()) { lang.send(player, "cannot-sell"); return; }
        CurrencyProvider provider = currencies.get(item.currency());
        int remaining = requested, sold = 0; BigDecimal total = BigDecimal.ZERO;
        PlayerInventory inventory = player.getInventory();
        int firstSlot = exactHand ? inventory.getHeldItemSlot() : 0;
        int lastSlot = exactHand ? firstSlot + 1 : inventory.getStorageContents().length;
        for (int i = firstSlot; i < lastSlot && remaining > 0; i++) {
            ItemStack current = inventory.getItem(i);
            if (current == null || current.getType().isAir() || !current.isSimilar(match)) continue;
            int take = Math.min(current.getAmount(), remaining);
            if (isDailyLimitReached(player, item, take)) break;
            BigDecimal value = dynamicPrice(item, false).multiply(BigDecimal.valueOf(take)).multiply(sellMultiplier(player)).setScale(2, RoundingMode.HALF_UP);
            ShopTransactionEvent transaction = new ShopTransactionEvent(player, current.clone(), take, value, item.currency(), false);
            Bukkit.getPluginManager().callEvent(transaction);
            if (transaction.isCancelled() || value.signum() < 0 || !Double.isFinite(value.doubleValue())
                    || (value.signum() > 0 && (provider == null || !provider.deposit(player, value.doubleValue())))) continue;
            current.setAmount(current.getAmount() - take); if (current.getAmount() <= 0) inventory.setItem(i, null);
            addDailyUsage(player, item, take); updateDynamicPrice(item, false); sold += take; remaining -= take; total = total.add(value);
        }
        if (sold <= 0) { lang.send(player, "cannot-sell"); return; }
        lang.send(player, "sell-success", Map.of("amount", sold, "item", item.displayName(), "price", format(total), "currency", item.currency()));
        play(player, "success"); logTransaction(player.getName() + " SELL " + sold + "x " + item.id() + " " + total + " " + item.currency());
    }

    private void sellInventory(Player player) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>(); Map<String, Integer> counts = new LinkedHashMap<>();
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getStorageContents().length; i++) {
            ItemStack stack = inventory.getItem(i); if (stack == null || stack.getType().isAir()) continue;
            ShopItem item = manager.match(stack); if (item == null || !item.canSell()) continue;
            CurrencyProvider provider = currencies.get(item.currency());
            int amount = stack.getAmount(); if (isDailyLimitReached(player, item, amount)) continue;
            BigDecimal value = dynamicPrice(item, false).multiply(BigDecimal.valueOf(amount)).multiply(sellMultiplier(player)).setScale(2, RoundingMode.HALF_UP);
            ShopTransactionEvent transaction = new ShopTransactionEvent(player, stack.clone(), amount, value, item.currency(), false);
            Bukkit.getPluginManager().callEvent(transaction);
            if (transaction.isCancelled() || value.signum() < 0 || !Double.isFinite(value.doubleValue())
                    || (value.signum() > 0 && (provider == null || !provider.deposit(player, value.doubleValue())))) continue;
            inventory.setItem(i, null); addDailyUsage(player, item, amount); updateDynamicPrice(item, false);
            totals.merge(item.currency(), value, BigDecimal::add); counts.merge(item.currency(), amount, Integer::sum);
            logTransaction(player.getName() + " SELL " + amount + "x " + item.id() + " " + value + " " + item.currency());
        }
        sendSummaries(player, totals, counts);
    }

    private void openSellGui(Player player) {
        OctaMenuHolder holder = new OctaMenuHolder(player.getUniqueId(), MenuType.SELL, "", 0, 0, -1);
        String title = guiConfig.getString("sell.title", "<dark_green>Sell Items");
        Inventory inv = Bukkit.createInventory(holder, 54, ui(title)); holder.attach(inv);
        lang.send(player, "sellgui-open"); player.openInventory(inv);
    }

    public void finishSellGui(Player player, Inventory inventory) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>(); Map<String, Integer> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot); if (stack == null || stack.getType().isAir()) continue;
            ShopItem item = manager.match(stack); CurrencyProvider provider = item == null || !item.canSell() ? null : currencies.get(item.currency());
            boolean sold = false;
            if (item != null && item.canSell() && !isDailyLimitReached(player, item, stack.getAmount())) {
                BigDecimal value = dynamicPrice(item, false).multiply(BigDecimal.valueOf(stack.getAmount())).multiply(sellMultiplier(player)).setScale(2, RoundingMode.HALF_UP);
                ShopTransactionEvent transaction = new ShopTransactionEvent(player, stack.clone(), stack.getAmount(), value, item.currency(), false);
                Bukkit.getPluginManager().callEvent(transaction);
                if (!transaction.isCancelled() && value.signum() >= 0 && Double.isFinite(value.doubleValue())
                        && (value.signum() == 0 || (provider != null && provider.deposit(player, value.doubleValue())))) {
                    totals.merge(item.currency(), value, BigDecimal::add); counts.merge(item.currency(), stack.getAmount(), Integer::sum);
                    addDailyUsage(player, item, stack.getAmount()); updateDynamicPrice(item, false); sold = true;
                    logTransaction(player.getName() + " SELLGUI " + stack.getAmount() + "x " + item.id() + " " + value + " " + item.currency());
                }
            }
            if (!sold) giveOrDrop(player, stack.clone());
            inventory.setItem(slot, null);
        }
        if (!totals.isEmpty()) sendSummaries(player, totals, counts);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private void sendSummaries(Player player, Map<String, BigDecimal> totals, Map<String, Integer> counts) {
        if (totals.isEmpty()) { lang.send(player, "cannot-sell"); return; }
        for (String currency : totals.keySet()) lang.send(player, "sell-summary", Map.of("items", counts.get(currency), "total", format(totals.get(currency)), "currency", currency));
        play(player, "success");
    }

    private BigDecimal purchaseUnitPrice(ShopItem item) {
        BigDecimal price = dynamicPrice(item, true);
        DiscountOffer offer = activeDiscount(item);
        return offer == null ? price : applyDiscount(price, offer);
    }

    private BigDecimal dynamicPrice(ShopItem item, boolean buying) {
        BigDecimal base = buying ? item.buyPrice() : item.sellPrice();
        if (!plugin.getConfig().getBoolean("shop.dynamic-price.enabled", false)) return base;
        String key = item.categoryId() + ":" + item.id();
        PriceState state = dynamicPrices.getOrDefault(key, new PriceState(BigDecimal.ONE, System.currentTimeMillis()));
        BigDecimal factor = recover(state);
        return base.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal recover(PriceState state) {
        long now = System.currentTimeMillis();
        double recovery = Math.max(0, plugin.getConfig().getDouble("shop.dynamic-price.recovery-per-hour", 0.02));
        double hours = Math.max(0, now - state.updatedAt()) / 3_600_000.0;
        BigDecimal factor = state.multiplier();
        if (factor.compareTo(BigDecimal.ONE) > 0) factor = factor.subtract(BigDecimal.valueOf(recovery * hours)).max(BigDecimal.ONE);
        else if (factor.compareTo(BigDecimal.ONE) < 0) factor = factor.add(BigDecimal.valueOf(recovery * hours)).min(BigDecimal.ONE);
        return factor;
    }

    private void updateDynamicPrice(ShopItem item, boolean purchasedFromShop) {
        if (!plugin.getConfig().getBoolean("shop.dynamic-price.enabled", false)) return;
        String key = item.categoryId() + ":" + item.id();
        PriceState old = dynamicPrices.getOrDefault(key, new PriceState(BigDecimal.ONE, System.currentTimeMillis()));
        BigDecimal factor = recover(old);
        BigDecimal delta = BigDecimal.valueOf(Math.max(0, plugin.getConfig().getDouble("shop.dynamic-price.fluctuation-percent", 2.0)) / 100.0);
        factor = purchasedFromShop ? factor.add(delta) : factor.subtract(delta);
        BigDecimal min = BigDecimal.valueOf(plugin.getConfig().getDouble("shop.dynamic-price.min-multiplier", 0.75));
        BigDecimal max = BigDecimal.valueOf(plugin.getConfig().getDouble("shop.dynamic-price.max-multiplier", 1.50));
        factor = factor.max(min).min(max);
        dynamicPrices.put(key, new PriceState(factor, System.currentTimeMillis()));
    }

    private BigDecimal sellMultiplier(Player player) {
        if (!plugin.getConfig().getBoolean("shop.sell-multiplier.enabled", true)) return BigDecimal.ONE;
        BigDecimal highest = BigDecimal.ONE;
        for (var permission : player.getEffectivePermissions()) {
            String node = permission.getPermission().toLowerCase(Locale.ROOT);
            String prefix = "octaeconomy.sell.multiplier.";
            if (!permission.getValue() || !node.startsWith(prefix)) continue;
            try { BigDecimal value = new BigDecimal(node.substring(prefix.length())); if (value.compareTo(highest) > 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0) highest = value; }
            catch (NumberFormatException ignored) { }
        }
        return highest;
    }

    private boolean isDailyLimitReached(Player player, ShopItem item, int amount) {
        if (!plugin.getConfig().getBoolean("shop.daily-limit.enabled", false)) return false;
        int limit = plugin.getConfig().getInt("shop.daily-limit.items." + item.id(), plugin.getConfig().getInt("shop.daily-limit.default-amount", 0));
        if (limit <= 0) return false;
        Map<String, DailyUsage> user = dailyUsage.getOrDefault(player.getUniqueId(), Map.of());
        DailyUsage usage = user.get(item.categoryId() + ":" + item.id());
        int used = usage == null || !usage.date().equals(LocalDate.now()) ? 0 : usage.amount();
        return used + amount > limit;
    }

    private void addDailyUsage(Player player, ShopItem item, int amount) {
        if (!plugin.getConfig().getBoolean("shop.daily-limit.enabled", false)) return;
        dailyUsage.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>()).compute(item.categoryId() + ":" + item.id(), (key, old) -> {
            int used = old == null || !old.date().equals(LocalDate.now()) ? 0 : old.amount(); return new DailyUsage(LocalDate.now(), used + amount);
        });
    }

    private boolean allowClick(Player player) {
        long now = System.currentTimeMillis(); Long previous = clickDebounce.put(player.getUniqueId(), now);
        return previous == null || now - previous > 120;
    }
    private String format(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private void play(Player player, String key) {
        if (!plugin.getConfig().getBoolean("sound.enabled", true)) return;
        String name = plugin.getConfig().getString("sound." + key, key.equals("success") ? "ENTITY_EXPERIENCE_ORB_PICKUP" : "UI_BUTTON_CLICK");
        try { player.playSound(player.getLocation(), Sound.valueOf(name.toUpperCase(Locale.ROOT)), 0.7f, 1.2f); } catch (IllegalArgumentException ignored) { }
    }
    private void logTransaction(String line) {
        if (!plugin.getConfig().getBoolean("transaction-log.enabled", false)) return;
        scheduler.async(() -> {
            try {
                File dir = new File(plugin.getDataFolder(), "logs"); dir.mkdirs();
                File file = new File(dir, "transactions-" + LocalDate.now() + ".log");
                java.nio.file.Files.writeString(file.toPath(), System.currentTimeMillis() + " " + line + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                File[] old = dir.listFiles((d, n) -> n.startsWith("transactions-") && n.endsWith(".log"));
                if (old != null && old.length > 7) { Arrays.sort(old, Comparator.comparingLong(File::lastModified).reversed()); for (int i = 7; i < old.length; i++) old[i].delete(); }
            } catch (Exception ex) { plugin.getLogger().log(Level.WARNING, "Could not write transaction log", ex); }
        });
    }
}
