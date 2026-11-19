/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.shop;

import dev.alvran.octaeconomy.hook.MMOItemsHook;
import dev.alvran.octaeconomy.hook.SmartSpawnerHook;
import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Parses shop YAML once and stores normalized item templates. */
public final class ShopManager {
    private final JavaPlugin plugin;
    private final MMOItemsHook mmoItems;
    private final SmartSpawnerHook smartSpawner;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private final Map<String, List<ShopItem>> categoryItems = new LinkedHashMap<>();
    private final Map<String, ShopItem> items = new HashMap<>();
    public ShopManager(JavaPlugin plugin, MMOItemsHook mmoItems, SmartSpawnerHook smartSpawner) {
        this.plugin = plugin; this.mmoItems = mmoItems; this.smartSpawner = smartSpawner;
    }

    public void reload() {
        categories.clear(); categoryItems.clear(); items.clear();
        File folder = new File(plugin.getDataFolder(), "shops");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) loadCategory(file);
    }

    private void loadCategory(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection category = yaml.getConfigurationSection("category");
        if (category == null) { warn(file, "missing category section"); return; }
        String id = category.getString("id", file.getName().replaceFirst("(?i)\\.yml$", "")).toLowerCase(Locale.ROOT);
        if (id.equals("mmoitems") && !mmoItems.isAvailable()) return;
        Material icon = Material.matchMaterial(category.getString("icon", "CHEST"));
        if (icon == null) icon = Material.CHEST;
        ShopCategory info = new ShopCategory(id, category.getString("display-name", id), icon,
                category.getInt("slot", 10), category.getString("permission", ""));
        String categoryPermission = "octaeconomy.shop.category." + id;
        if (plugin.getServer().getPluginManager().getPermission(categoryPermission) == null) {
            plugin.getServer().getPluginManager().addPermission(new org.bukkit.permissions.Permission(categoryPermission, org.bukkit.permissions.PermissionDefault.TRUE));
        }
        List<ShopItem> loaded = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("items");
        if (section != null) for (String key : section.getKeys(false)) {
            try {
                ConfigurationSection node = section.getConfigurationSection(key);
                if (node == null) continue;
                ItemStack template = createTemplate(node, file, key);
                if (template == null || template.getType().isAir()) { warn(file, "item '" + key + "' could not be created and was skipped"); continue; }
                BigDecimal buy = price(node, "buy-price");
                BigDecimal sell = price(node, "sell-price");
                String currency = node.getString("currency", plugin.getConfig().getString("economy.default-currency", "vault"));
                ShopItem item = new ShopItem(key, id, template, buy, sell, currency);
                loaded.add(item); items.put(id + ":" + key, item);
            } catch (Exception ex) { warn(file, "invalid item '" + key + "': " + ex.getMessage()); }
        }
        categories.put(id, info); categoryItems.put(id, List.copyOf(loaded));
    }

    private BigDecimal price(ConfigurationSection node, String key) {
        if (!node.contains(key)) return BigDecimal.valueOf(-1);
        double value = node.getDouble(key, -1);
        if (!Double.isFinite(value)) return BigDecimal.valueOf(-1);
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private ItemStack createTemplate(ConfigurationSection node, File file, String key) {
        ItemStack result = null;
        ConfigurationSection mmo = node.getConfigurationSection("mmoitems");
        ConfigurationSection spawner = node.getConfigurationSection("spawner");
        if (mmo != null) {
            result = mmoItems.create(mmo.getString("type", ""), mmo.getString("id", ""));
            if (result == null) warn(file, "MMOItems item '" + key + "' unavailable; skipped");
        } else if (spawner != null) {
            EntityType entity;
            try { entity = EntityType.valueOf(spawner.getString("entity", "PIG").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { warn(file, "invalid spawner entity for '" + key + "'"); return null; }
            if (spawner.getBoolean("smart", false)) result = smartSpawner.create(entity);
            if (result == null) result = vanillaSpawner(entity);
        } else {
            Material material = Material.matchMaterial(node.getString("material", ""));
            if (material != null && material.isItem()) result = new ItemStack(material);
        }
        if (result != null) result.setAmount(1);
        return result;
    }

    private ItemStack vanillaSpawner(EntityType entity) {
        ItemStack stack = new ItemStack(Material.SPAWNER);
        if (stack.getItemMeta() instanceof BlockStateMeta meta) {
            BlockState state = meta.getBlockState();
            if (state instanceof CreatureSpawner spawner) { spawner.setSpawnedType(entity); meta.setBlockState(spawner); stack.setItemMeta(meta); }
        }
        return stack;
    }

    private void warn(File file, String message) { plugin.getLogger().warning("shops/" + file.getName() + ": " + message); }
    public Collection<ShopCategory> categories() { return Collections.unmodifiableCollection(categories.values()); }
    public ShopCategory category(String id) { return categories.get(id.toLowerCase(Locale.ROOT)); }
    public List<ShopItem> items(String category) { return categoryItems.getOrDefault(category, List.of()); }
    public Collection<ShopItem> allItems() { return Collections.unmodifiableCollection(items.values()); }
    public ShopItem item(String category, String key) { return items.get(category + ":" + key); }
    public ShopItem match(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        for (ShopItem item : items.values()) if (item.template().isSimilar(stack)) return item;
        return null;
    }
}
