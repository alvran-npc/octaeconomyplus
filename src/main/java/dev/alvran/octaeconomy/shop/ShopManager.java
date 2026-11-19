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
import org.bukkit.entity.LivingEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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
        Set<Material> explicitlyConfigured = new HashSet<>();
        Set<EntityType> explicitlyConfiguredEntities = new HashSet<>();
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
                ConfigurationSection spawnerNode = node.getConfigurationSection("spawner");
                if (node.getString("material") != null && node.getConfigurationSection("mmoitems") == null && spawnerNode == null) {
                    explicitlyConfigured.add(template.getType());
                }
                if (spawnerNode != null) {
                    try { explicitlyConfiguredEntities.add(EntityType.valueOf(spawnerNode.getString("entity", "PIG").toUpperCase(Locale.ROOT))); }
                    catch (IllegalArgumentException ignored) { /* the invalid item was already skipped or warned by createTemplate */ }
                }
            } catch (Exception ex) { warn(file, "invalid item '" + key + "': " + ex.getMessage()); }
        }

        // Populate the default catalogue from the server's actual Material enum. Explicit YAML
        // entries always win, so server owners can override prices and custom item metadata.
        if (category.getBoolean("auto-include-vanilla", false) && !id.equals("mmoitems") && !id.equals("spawners")) {
            double baseBuy = category.getDouble("auto-pricing.buy-base", 10.0);
            double sellRatio = category.getDouble("auto-pricing.sell-ratio", 0.25);
            String currency = category.getString("auto-pricing.currency", plugin.getConfig().getString("economy.default-currency", "vault"));
            for (Material material : Material.values()) {
                if (!material.isItem() || material.isAir() || explicitlyConfigured.contains(material)
                        || classifyVanilla(material) == null || !id.equals(classifyVanilla(material))) continue;
                String key = "auto_" + material.name().toLowerCase(Locale.ROOT);
                if (items.containsKey(id + ":" + key)) continue;
                ItemStack template = new ItemStack(material);
                double buyValue = autoPrice(material, baseBuy);
                BigDecimal buy = BigDecimal.valueOf(buyValue).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal sell = BigDecimal.valueOf(buyValue * Math.max(0.0, Math.min(1.0, sellRatio)))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                ShopItem generated = new ShopItem(key, id, template, buy, sell, currency);
                loaded.add(generated);
                items.put(id + ":" + key, generated);
            }
        }
        // Generate a complete set of vanilla mob spawners as well. Explicit YAML entries win,
        // and custom SmartSpawner items are used when its optional hook is available.
        if (id.equals("spawners") && category.getBoolean("auto-include-entities", true)) {
            double baseBuy = category.getDouble("auto-pricing.buy-base", 1000.0);
            double sellRatio = category.getDouble("auto-pricing.sell-ratio", 0.25);
            String currency = category.getString("auto-pricing.currency", plugin.getConfig().getString("economy.default-currency", "vault"));
            for (EntityType entity : EntityType.values()) {
                if (entity == EntityType.PLAYER || entity.getEntityClass() == null
                        || !LivingEntity.class.isAssignableFrom(entity.getEntityClass())
                        || explicitlyConfiguredEntities.contains(entity)) continue;
                ItemStack template = smartSpawner.create(entity);
                if (template == null) template = vanillaSpawner(entity);
                if (template == null || template.getType().isAir()) continue;
                var meta = template.getItemMeta();
                meta.displayName(Component.text("Spawner: " + prettyEntity(entity)).decoration(TextDecoration.ITALIC, false));
                template.setItemMeta(meta);
                String key = "auto_" + entity.name().toLowerCase(Locale.ROOT);
                double buyValue = Math.min(1_000_000_000.0, baseBuy * (entity == EntityType.ENDER_DRAGON || entity == EntityType.WITHER ? 25.0 : 1.0));
                BigDecimal buy = BigDecimal.valueOf(Math.max(0.1, buyValue)).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal sell = BigDecimal.valueOf(Math.max(0.0, buyValue * Math.max(0.0, Math.min(1.0, sellRatio))))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                ShopItem generated = new ShopItem(key, id, template, buy, sell, currency);
                loaded.add(generated);
                items.put(id + ":" + key, generated);
            }
        }
        categories.put(id, info); categoryItems.put(id, List.copyOf(loaded));
    }

    private String classifyVanilla(Material material) {
        String name = material.name();
        if (isUnavailableCreativeItem(name) || name.equals("SPAWNER")) return null;
        if (isToolOrArmor(name)) return "vanilla_tools";
        if (isRedstone(name)) return "redstone";
        if (isFarmFood(name)) return "farming_food";
        if (isMobDrop(name)) return "mobs_drop";
        if (material.isBlock() && isColoredBlock(name)) return "colored_blocks";
        if (isDecoration(name)) return "decoration";
        if (material.isBlock()) return "blocks";
        return "misc";
    }

    private boolean isUnavailableCreativeItem(String name) {
        return name.startsWith("LEGACY_") || Set.of(
                "AIR", "CAVE_AIR", "VOID_AIR", "BEDROCK", "BARRIER", "COMMAND_BLOCK",
                "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART",
                "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "DEBUG_STICK", "KNOWLEDGE_BOOK",
                "END_PORTAL_FRAME", "END_PORTAL", "NETHER_PORTAL", "LIGHT", "REINFORCED_DEEPSLATE",
                "SPAWNER", "PLAYER_HEAD", "PLAYER_WALL_HEAD"
        ).contains(name);
    }

    private boolean isToolOrArmor(String n) {
        return n.endsWith("_SWORD") || n.endsWith("_PICKAXE") || n.endsWith("_AXE")
                || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || Set.of("BOW", "CROSSBOW", "TRIDENT", "MACE", "SHIELD", "ELYTRA", "FISHING_ROD",
                "SHEARS", "FLINT_AND_STEEL", "BRUSH", "CLOCK", "COMPASS", "RECOVERY_COMPASS",
                "SPYGLASS", "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK", "SADDLE", "LEAD").contains(n)
                || n.endsWith("_HORSE_ARMOR") || n.equals("WOLF_ARMOR");
    }

    private boolean isRedstone(String n) {
        return n.contains("REDSTONE") || Set.of("REPEATER", "COMPARATOR", "PISTON", "STICKY_PISTON",
                "DISPENSER", "DROPPER", "OBSERVER", "HOPPER", "LEVER", "TRIPWIRE_HOOK", "TARGET",
                "DAYLIGHT_DETECTOR", "TNT", "NOTE_BLOCK", "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR",
                "LIGHTNING_ROD", "DAYLIGHT_DETECTOR", "REDSTONE_LAMP", "CRAFTER").contains(n)
                || n.endsWith("_PRESSURE_PLATE") || n.endsWith("_BUTTON");
    }

    private boolean isFarmFood(String n) {
        return n.contains("SEEDS") || n.endsWith("_SEED") || Set.of("WHEAT", "CARROT", "POTATO", "BEETROOT",
                "BEETROOT_SEEDS", "MELON_SLICE", "PUMPKIN", "PUMPKIN_PIE", "APPLE", "GOLDEN_APPLE",
                "ENCHANTED_GOLDEN_APPLE", "GOLDEN_CARROT", "BAKED_POTATO", "POISONOUS_POTATO", "BREAD", "COOKIE",
                "CAKE", "BEETROOT_SOUP", "MUSHROOM_STEW", "RABBIT_STEW", "SUSPICIOUS_STEW", "CHORUS_FRUIT",
                "GLISTERING_MELON_SLICE", "SUGAR", "SUGAR_CANE", "COCOA_BEANS",
                "NETHER_WART", "SWEET_BERRIES", "GLOW_BERRIES", "HONEY_BOTTLE", "HONEYCOMB", "EGG",
                "BEEF", "COOKED_BEEF", "PORKCHOP", "COOKED_PORKCHOP", "CHICKEN", "COOKED_CHICKEN",
                "MUTTON", "COOKED_MUTTON", "RABBIT", "COOKED_RABBIT", "COD", "COOKED_COD", "SALMON",
                "COOKED_SALMON", "TROPICAL_FISH", "PUFFERFISH", "DRIED_KELP").contains(n)
                || n.endsWith("_MUSHROOM") || n.endsWith("_STEW") || n.endsWith("_SOUP");
    }

    private boolean isMobDrop(String n) {
        return Set.of("ROTTEN_FLESH", "BONE", "STRING", "SPIDER_EYE", "FERMENTED_SPIDER_EYE", "GUNPOWDER", "ENDER_PEARL",
                "BLAZE_ROD", "BLAZE_POWDER", "GHAST_TEAR", "SLIME_BALL", "MAGMA_CREAM", "LEATHER",
                "FEATHER", "PHANTOM_MEMBRANE", "SHULKER_SHELL", "NAUTILUS_SHELL", "PRISMARINE_SHARD",
                "PRISMARINE_CRYSTALS", "RABBIT_HIDE", "RABBIT_FOOT", "INK_SAC", "GLOW_INK_SAC", "TURTLE_SCUTE",
                "ARMADILLO_SCUTE", "WIND_CHARGE", "BREEZE_ROD", "NETHER_STAR", "TOTEM_OF_UNDYING",
                "WITHER_SKELETON_SKULL", "DRAGON_BREATH", "ECHO_SHARD", "SCUTE", "HONEYCOMB").contains(n);
    }

    private boolean isColoredBlock(String n) {
        return n.startsWith("WHITE_") || n.startsWith("ORANGE_") || n.startsWith("MAGENTA_")
                || n.startsWith("LIGHT_BLUE_") || n.startsWith("YELLOW_") || n.startsWith("LIME_")
                || n.startsWith("PINK_") || n.startsWith("GRAY_") || n.startsWith("LIGHT_GRAY_")
                || n.startsWith("CYAN_") || n.startsWith("PURPLE_") || n.startsWith("BLUE_")
                || n.startsWith("BROWN_") || n.startsWith("GREEN_") || n.startsWith("RED_")
                || n.startsWith("BLACK_");
    }

    private boolean isDecoration(String n) {
        return n.contains("FLOWER") || n.contains("TULIP") || n.contains("ORCHID") || n.contains("DAISY")
                || n.contains("CORAL") || n.contains("BANNER") || n.contains("CANDLE") || n.contains("POT")
                || n.contains("CARPET") || n.contains("GLASS") || n.contains("FENCE") || n.contains("WALL")
                || n.contains("STAIRS") || n.contains("SLAB") || n.contains("DOOR") || n.contains("TRAPDOOR")
                || n.contains("SIGN") || n.contains("HANGING_SIGN") || n.contains("BED") || n.contains("HEAD")
                || n.contains("HANGING_ROOTS") || n.contains("VASE") || n.contains("PAINTING")
                || n.contains("ITEM_FRAME") || n.contains("ARMOR_STAND") || n.contains("CHAIN")
                || n.contains("BOOKSHELF") || n.contains("CHISELED_BOOKSHELF") || n.contains("FLOWERPOT")
                || Set.of("TORCH", "SOUL_TORCH", "LANTERN", "SOUL_LANTERN", "END_ROD", "SEA_LANTERN",
                "GLOWSTONE", "SEA_PICKLE", "LILY_PAD", "SCAFFOLDING", "BELL", "CONDUIT", "BEACON",
                "DECORATED_POT", "BONE_BLOCK", "HAY_BLOCK", "MOSS_BLOCK", "MOSS_CARPET", "AZALEA",
                "FLOWERING_AZALEA", "SMALL_DRIPLEAF", "BIG_DRIPLEAF").contains(n);
    }

    private String prettyEntity(EntityType entity) {
        String[] words = entity.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private double autoPrice(Material material, double base) {
        String n = material.name();
        double multiplier = 1.0;
        if (n.contains("NETHERITE") || n.equals("NETHER_STAR") || n.equals("ELYTRA")) multiplier = 40.0;
        else if (n.contains("DIAMOND") || n.equals("TOTEM_OF_UNDYING") || n.equals("HEART_OF_THE_SEA")) multiplier = 12.0;
        else if (n.contains("EMERALD") || n.contains("ANCIENT_DEBRIS")) multiplier = 8.0;
        else if (n.contains("GOLD") || n.contains("AMETHYST")) multiplier = 4.0;
        else if (n.contains("IRON") || n.contains("COPPER")) multiplier = 2.5;
        if (material.getMaxStackSize() == 1) multiplier *= 4.0;
        return Math.max(0.1, Math.min(1_000_000_000.0, base * multiplier));
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
        try {
            ItemStack stack = new ItemStack(Material.SPAWNER);
            if (!(stack.getItemMeta() instanceof BlockStateMeta meta)) return null;
            BlockState state = meta.getBlockState();
            if (!(state instanceof CreatureSpawner spawner)) return null;
            spawner.setSpawnedType(entity);
            meta.setBlockState(spawner);
            stack.setItemMeta(meta);
            return stack;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Some LivingEntity types are not valid mob-spawner targets; skip only that entity.
            return null;
        }
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
