/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.chestshop;

import dev.alvran.octaeconomy.core.Module;
import dev.alvran.octaeconomy.core.LangManager;
import dev.alvran.octaeconomy.core.TaskScheduler;
import dev.alvran.octaeconomy.hook.CurrencyManager;
import dev.alvran.octaeconomy.hook.CurrencyProvider;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Native, lightweight chest market. It is original and independent of QuickShop-Hikari internals. */
public final class ChestShopModule implements Module, Listener, CommandExecutor, TabCompleter {
    private static final class ShopData {
        final String key;
        final String world;
        final int x, y, z;
        final UUID owner;
        final String ownerName;
        final ItemStack item;
        final BigDecimal price;
        final String currency;

        ShopData(String key, String world, int x, int y, int z, UUID owner, String ownerName,
                 ItemStack item, BigDecimal price, String currency) {
            this.key = key; this.world = world; this.x = x; this.y = y; this.z = z;
            this.owner = owner; this.ownerName = ownerName; this.item = item.clone(); this.item.setAmount(1);
            this.price = price; this.currency = currency;
        }
    }

    private final JavaPlugin plugin;
    private final CurrencyManager currencies;
    private final TaskScheduler scheduler;
    private final LangManager lang;
    private final Map<String, ShopData> shops = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickGuard = new ConcurrentHashMap<>();
    private final MiniMessage mini = MiniMessage.miniMessage();
    private File dataFile;
    private YamlConfiguration settings;

    public ChestShopModule(JavaPlugin plugin, CurrencyManager currencies, TaskScheduler scheduler, LangManager lang) {
        this.plugin = plugin; this.currencies = currencies; this.scheduler = scheduler; this.lang = lang;
    }

    @Override public String name() { return "ChestShop"; }

    @Override public void enable() {
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Native Chest Shop module enabled.");
    }

    @Override public void disable() {
        saveAsync();
        shops.clear();
        clickGuard.clear();
    }

    @Override public void reload() {
        settings = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "chestshop.yml"));
        dataFile = new File(plugin.getDataFolder(), "chestshops.yml");
        loadShops();
    }

    private void loadShops() {
        shops.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = yaml.getConfigurationSection("shops");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection node = root.getConfigurationSection(key);
                if (node == null) continue;
                byte[] encoded = Base64.getDecoder().decode(node.getString("item", ""));
                ItemStack item = ItemStack.deserializeBytes(encoded);
                if (item.getType().isAir()) continue;
                ShopData data = new ShopData(key, node.getString("world", "world"), node.getInt("x"), node.getInt("y"), node.getInt("z"),
                        UUID.fromString(node.getString("owner")), node.getString("owner-name", "Unknown"), item,
                        new BigDecimal(node.getString("price", "1.00")), node.getString("currency", "vault"));
                shops.put(key, data);
                registerDoubleChestAlias(data);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipped invalid chest shop entry '" + key + "': " + ex.getMessage());
            }
        }
    }

    private void saveAsync() {
        Map<String, ShopData> snapshot = new HashMap<>(shops);
        scheduler.async(() -> writeSnapshot(snapshot));
    }

    private void saveNow() {
        writeSnapshot(new HashMap<>(shops));
    }

    private void writeSnapshot(Map<String, ShopData> snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.values().stream().distinct().sorted(Comparator.comparing(value -> value.key)).forEach(shop -> {
            String path = "shops." + shop.key;
            yaml.set(path + ".world", shop.world);
            yaml.set(path + ".x", shop.x); yaml.set(path + ".y", shop.y); yaml.set(path + ".z", shop.z);
            yaml.set(path + ".owner", shop.owner.toString()); yaml.set(path + ".owner-name", shop.ownerName);
            yaml.set(path + ".item", Base64.getEncoder().encodeToString(shop.item.serializeAsBytes()));
            yaml.set(path + ".price", shop.price.toPlainString()); yaml.set(path + ".currency", shop.currency);
        });
        try {
            File parent = dataFile.getParentFile(); if (parent != null) parent.mkdirs();
            File temp = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
            yaml.save(temp);
            try {
                java.nio.file.Files.move(temp.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException noAtomicMove) {
                java.nio.file.Files.move(temp.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save chest shops", ex);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { lang.send(sender, "players-only"); return true; }
        if (!settings.getBoolean("enabled", true)) { msg(player, "chestshop-disabled"); return true; }
        if (!player.hasPermission("octaeconomy.chestshop.use")) { lang.send(player, "no-permission"); return true; }
        if (args.length == 0) {
            msg(player, "chestshop-help");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(player, args);
            case "remove" -> remove(player);
            case "info" -> info(player);
            default -> lang.send(player, "usage", Map.of("usage", "/chestshop <create <price> [currency]|remove|info>"));
        }
        return true;
    }

    private void create(Player player, String[] args) {
        if (!player.hasPermission("octaeconomy.chestshop.create")) { lang.send(player, "no-permission"); return; }
        if (args.length < 2 || args.length > 3) { lang.send(player, "usage", Map.of("usage", "/chestshop create <price> [currency]")); return; }
        BigDecimal price;
        try { price = new BigDecimal(args[1]).setScale(2, RoundingMode.HALF_UP); }
        catch (NumberFormatException ex) { msg(player, "chestshop-price-invalid"); return; }
        if (price.signum() <= 0 || !Double.isFinite(price.doubleValue())) { msg(player, "chestshop-price-invalid"); return; }
        String currency = args.length == 3 ? args[2] : plugin.getConfig().getString("economy.default-currency", "vault");
        if (currencies.get(currency) == null) { lang.send(player, "currency-unavailable", Map.of("currency", safe(currency))); return; }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) { msg(player, "chestshop-hold-item"); return; }
        Block block = player.getTargetBlockExact(Math.max(2, settings.getInt("target-distance", 6)));
        if (block == null || !(block.getState() instanceof Chest)) { msg(player, "chestshop-look-chest"); return; }
        String key = key(block);
        if (findShop(block) != null) { msg(player, "chestshop-already-exists"); return; }
        long owned = shops.values().stream().distinct().filter(shop -> shop.owner.equals(player.getUniqueId())).count();
        int limit = Math.max(1, settings.getInt("shops-per-player", 10));
        if (!player.hasPermission("octaeconomy.chestshop.limit.bypass") && owned >= limit) {
            msg(player, "chestshop-limit", "limit", limit); return;
        }
        ShopData shop = new ShopData(key, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                player.getUniqueId(), player.getName(), held.clone(), price, currency);
        shops.put(key, shop);
        registerDoubleChestAlias(shop);
        saveAsync();
        msg(player, "chestshop-created");
        play(player, Sound.BLOCK_NOTE_BLOCK_PLING);
    }

    private void remove(Player player) {
        Block block = targetChest(player);
        if (block == null) { msg(player, "chestshop-look-shop"); return; }
        ShopData shop = findShop(block);
        if (shop == null) { msg(player, "chestshop-not-found"); return; }
        if (!shop.owner.equals(player.getUniqueId()) && !player.hasPermission("octaeconomy.chestshop.admin")) {
            msg(player, "chestshop-not-owner"); return;
        }
        removeShopAliases(shop); saveAsync();
        msg(player, "chestshop-removed");
    }

    private void info(Player player) {
        Block block = targetChest(player);
        if (block == null) { msg(player, "chestshop-look-shop"); return; }
        ShopData shop = findShop(block);
        if (shop == null) { msg(player, "chestshop-not-found"); return; }
        int stock = countStock(chestInventory(block), shop.item);
        lang.send(player, "chestshop-info", Map.of("item", safe(pretty(shop.item.getType())), "price", shop.price.toPlainString(), "currency", safe(shop.currency), "stock", stock, "owner", safe(shop.ownerName)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getHand() != EquipmentSlot.HAND) return;
        ShopData shop = findShop(event.getClickedBlock());
        if (shop == null) return;
        Player player = event.getPlayer();
        if (player.getUniqueId().equals(shop.owner) || player.hasPermission("octaeconomy.chestshop.admin")) return;
        event.setCancelled(true);
        openShop(player, shop);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShopChestBreak(BlockBreakEvent event) {
        ShopData shop = findShop(event.getBlock());
        if (shop == null) return;
        Player player = event.getPlayer();
        if (!shop.owner.equals(player.getUniqueId()) && !player.hasPermission("octaeconomy.chestshop.admin")) {
            event.setCancelled(true);
            msg(player, "chestshop-break-denied");
            return;
        }
        removeShopAliases(shop);
        saveAsync();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        // Prevent hopper chains from extracting stock or inserting untracked items behind the shop GUI.
        if (isShopInventory(event.getSource()) || isShopInventory(event.getDestination())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (isShopInventory(event.getInventory())) event.setCancelled(true);
    }

    private boolean isShopInventory(Inventory inventory) {
        if (inventory == null) return false;
        if (inventory.getHolder() instanceof DoubleChest doubleChest) {
            for (InventoryHolder side : new InventoryHolder[]{doubleChest.getLeftSide(), doubleChest.getRightSide()}) {
                if (side instanceof Chest half && findShop(half.getBlock()) != null) return true;
            }
        }
        return inventory.getLocation() != null && findShop(inventory.getLocation().getBlock()) != null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ChestShopHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer().equals(player.getUniqueId())) return;
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) return;
        if (raw == 10) purchase(player, holder.shopKey(), 1);
        else if (raw == 13) purchase(player, holder.shopKey(), 16);
        else if (raw == 16) purchase(player, holder.shopKey(), 64);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { clickGuard.remove(event.getPlayer().getUniqueId()); }

    @EventHandler(ignoreCancelled = true)
    public void onGuiDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ChestShopHolder) event.setCancelled(true);
    }

    private void openShop(Player player, ShopData shop) {
        ChestShopHolder holder = new ChestShopHolder(player.getUniqueId(), shop.key);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Chest Shop").decoration(TextDecoration.ITALIC, false));
        holder.attach(inventory);
        ItemStack preview = shop.item.clone(); preview.setAmount(1);
        ItemMeta previewMeta = preview.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(ui("<gray>Owner <white>" + safe(compact(shop.ownerName, 12))));
        lore.add(ui("<green>Price <white>" + compact(shop.price.stripTrailingZeros().toPlainString(), 12) + " " + safe(compact(shop.currency, 7))));
        lore.add(ui("<gray>Stock <white>" + countStock(chestInventoryAt(shop), shop.item)));
        previewMeta.lore(lore); previewMeta.displayName(previewMeta.hasDisplayName()
                ? previewMeta.displayName().decoration(TextDecoration.ITALIC, false)
                : Component.text(pretty(shop.item.getType())).decoration(TextDecoration.ITALIC, false));
        preview.setItemMeta(previewMeta);
        inventory.setItem(4, preview);
        inventory.setItem(10, button(Material.IRON_NUGGET, "<green>Buy 1", "<gray>" + compact(shop.price.toPlainString(), 12) + " " + safe(compact(shop.currency, 7))));
        inventory.setItem(13, button(Material.IRON_INGOT, "<green>Buy 16", "<gray>" + compact(shop.price.multiply(BigDecimal.valueOf(16)).toPlainString(), 12) + " " + safe(compact(shop.currency, 7))));
        inventory.setItem(16, button(Material.IRON_BLOCK, "<green>Buy 64", "<gray>" + compact(shop.price.multiply(BigDecimal.valueOf(64)).toPlainString(), 12) + " " + safe(compact(shop.currency, 7))));
        inventory.setItem(22, button(Material.CHEST, "<gold>Stock information", "<gray>Restocked by the owner"));
        player.openInventory(inventory);
    }

    private void purchase(Player buyer, String key, int amount) {
        long now = System.currentTimeMillis();
        Long previous = clickGuard.put(buyer.getUniqueId(), now);
        if (previous != null && now - previous < 250) return;
        ShopData shop = shops.get(key);
        if (shop == null) { msg(buyer, "chestshop-shop-missing"); buyer.closeInventory(); return; }
        if (shop.owner.equals(buyer.getUniqueId())) { msg(buyer, "chestshop-own-purchase"); return; }
        Block block = shopBlock(shop);
        if (block == null || !(block.getState() instanceof Chest)) {
            removeShopAliases(shop); saveAsync(); msg(buyer, "chestshop-shop-missing"); buyer.closeInventory(); return;
        }
        Inventory stock = chestInventory(block);
        if (stock == null || countStock(stock, shop.item) < amount) {
            msg(buyer, "chestshop-out-of-stock"); return;
        }
        ItemStack toGive = shop.item.clone();
        toGive.setAmount(amount);
        if (!canFit(buyer.getInventory(), toGive)) { lang.send(buyer, "inventory-full"); return; }
        CurrencyProvider provider = currencies.get(shop.currency);
        if (provider == null) { lang.send(buyer, "currency-unavailable", Map.of("currency", safe(shop.currency))); return; }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(shop.owner);
        if (!owner.isOnline() && !shop.currency.equalsIgnoreCase("vault")) {
            msg(buyer, "chestshop-owner-online-required"); return;
        }
        BigDecimal total = shop.price.multiply(BigDecimal.valueOf(amount)).setScale(2, RoundingMode.HALF_UP);
        if (!Double.isFinite(total.doubleValue())) { msg(buyer, "chestshop-price-invalid"); return; }
        if (!provider.withdraw(buyer, total.doubleValue())) { lang.send(buyer, "insufficient-funds"); return; }
        if (!removeStock(stock, shop.item, amount)) {
            provider.deposit(buyer, total.doubleValue());
            msg(buyer, "chestshop-stock-changed"); return;
        }
        Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(toGive);
        if (!leftovers.isEmpty()) {
            restoreStock(stock, shop.item, amount);
            provider.deposit(buyer, total.doubleValue());
            lang.send(buyer, "inventory-full"); return;
        }
        if (!provider.deposit(owner, total.doubleValue())) {
            removeStock(buyer.getInventory(), shop.item, amount);
            restoreStock(stock, shop.item, amount);
            provider.deposit(buyer, total.doubleValue());
            msg(buyer, "chestshop-payment-refunded");
            plugin.getLogger().warning("Chest shop payout failed for owner " + shop.owner + " at " + shop.key);
            return;
        }
        lang.send(buyer, "chestshop-purchased", Map.of("amount", amount, "item", safe(pretty(shop.item.getType())), "price", total.toPlainString(), "currency", safe(shop.currency)));
        Player onlineOwner = owner.getPlayer();
        if (onlineOwner != null) lang.send(onlineOwner, "chestshop-owner-sale", Map.of("amount", amount, "item", safe(pretty(shop.item.getType())), "price", total.toPlainString(), "currency", safe(shop.currency)));
        play(buyer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
    }

    private boolean canFit(Inventory inventory, ItemStack item) {
        int remaining = item.getAmount();
        for (ItemStack current : inventory.getStorageContents()) {
            if (current == null || current.getType().isAir()) remaining -= item.getMaxStackSize();
            else if (current.isSimilar(item)) remaining -= Math.max(0, current.getMaxStackSize() - current.getAmount());
            if (remaining <= 0) return true;
        }
        return remaining <= 0;
    }

    private int countStock(Inventory inventory, ItemStack template) {
        if (inventory == null) return 0;
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) if (item != null && item.isSimilar(template)) count += item.getAmount();
        return count;
    }

    private boolean removeStock(Inventory inventory, ItemStack template, int amount) {
        if (countStock(inventory, template) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < inventory.getStorageContents().length && remaining > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || !current.isSimilar(template)) continue;
            int taken = Math.min(remaining, current.getAmount());
            int after = current.getAmount() - taken;
            if (after <= 0) inventory.setItem(slot, null); else { current.setAmount(after); inventory.setItem(slot, current); }
            remaining -= taken;
        }
        return remaining == 0;
    }

    private void restoreStock(Inventory inventory, ItemStack template, int amount) {
        ItemStack restore = template.clone(); restore.setAmount(amount);
        Map<Integer, ItemStack> leftovers = inventory.addItem(restore);
        if (!leftovers.isEmpty()) {
            plugin.getLogger().severe("Chest shop rollback could not restore all stock for material " + template.getType());
            if (inventory.getLocation() != null) {
                for (ItemStack item : leftovers.values()) inventory.getLocation().getWorld().dropItemNaturally(inventory.getLocation(), item);
            }
        }
    }

    private Inventory chestInventoryAt(ShopData shop) {
        Block block = shopBlock(shop);
        return block == null ? null : chestInventory(block);
    }

    private Inventory chestInventory(Block block) {
        if (block == null || !(block.getState() instanceof Chest chest)) return null;
        return chest.getInventory();
    }

    private Block shopBlock(ShopData shop) {
        var world = Bukkit.getWorld(shop.world);
        return world == null ? null : world.getBlockAt(shop.x, shop.y, shop.z);
    }

    private Block targetChest(Player player) {
        Block block = player.getTargetBlockExact(Math.max(2, settings.getInt("target-distance", 6)));
        return block != null && block.getState() instanceof Chest ? block : null;
    }

    private void registerDoubleChestAlias(ShopData shop) {
        Block block = shopBlock(shop);
        if (block == null || !(block.getState() instanceof Chest chest)) return;
        if (!(chest.getInventory().getHolder() instanceof DoubleChest doubleChest)) return;
        for (InventoryHolder side : List.of(doubleChest.getLeftSide(), doubleChest.getRightSide())) {
            if (side instanceof Chest half) shops.put(key(half.getBlock()), shop);
        }
    }

    private ShopData findShop(Block block) {
        if (block == null) return null;
        ShopData direct = shops.get(key(block));
        if (direct != null) return direct;
        if (block.getState() instanceof Chest chest && chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
            for (InventoryHolder side : List.of(doubleChest.getLeftSide(), doubleChest.getRightSide())) {
                if (side instanceof Chest half) {
                    ShopData found = shops.get(key(half.getBlock()));
                    if (found != null) {
                        shops.putIfAbsent(key(block), found);
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private void removeShopAliases(ShopData shop) {
        shops.entrySet().removeIf(entry -> entry.getValue() == shop);
    }

    private String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private ItemStack button(Material material, String title, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta(); meta.displayName(ui(title));
        if (lore.length > 0) meta.lore(java.util.Arrays.stream(lore).map(this::ui).toList());
        stack.setItemMeta(meta); return stack;
    }

    private Component ui(String text) { return mini.deserialize(text).decoration(TextDecoration.ITALIC, false); }
    private void msg(CommandSender sender, String key) { lang.send(sender, key); }
    private void msg(CommandSender sender, String key, String placeholder, Object value) {
        lang.send(sender, key, Map.of(placeholder, value));
    }
    private String safe(String text) { return Objects.toString(text, "").replace("<", "\\<").replace(">", "\\>"); }
    private String compact(String text, int max) {
        String clean = Objects.toString(text, "").replaceAll("[\\r\\n]", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(1, max - 1)) + "…";
    }
    private String pretty(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) { if (name.length() > 0) name.append(' '); name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)); }
        return name.toString();
    }
    private void play(Player player, Sound sound) { player.playSound(player.getLocation(), sound, 0.6f, 1.2f); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("create", "remove", "info");
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) return List.of("vault");
        return List.of();
    }
}
