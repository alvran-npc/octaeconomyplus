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
    private final Map<String, ShopItem> items = new LinkedHashMap<>();
    public ShopManager(JavaPlugin plugin, MMOItemsHook mmoItems, SmartSpawnerHook smartSpawner) {
        this.plugin = plugin; this.mmoItems = mmoItems; this.smartSpawner = smartSpawner;
    }

    public void reload() {
        categories.clear(); categoryItems.clear(); items.clear();
        File folder = new File(plugin.getDataFolder(), "Shops/Category");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            try {
                loadCategory(file);
            } catch (RuntimeException ex) {
                // A malformed category must never prevent every other shop category from loading.
                plugin.getLogger().log(Level.WARNING, "Shops/Category/" + file.getName() + ": category load failed; skipped this category", ex);
            }
        }
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
        // Materialize the complete default catalogue into the user's YAML files before
        // parsing them. This makes every buy/sell price visible and editable on disk;
        // the shop cache is loaded from those explicit entries, not hidden runtime defaults.
        materializeCatalogue(file, yaml, category, id);

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
                loaded.add(item);
            } catch (Exception ex) { warn(file, "invalid item '" + key + "': " + ex.getMessage()); }
        }
        loaded.sort(itemOrder(id));
        for (ShopItem item : loaded) items.put(id + ":" + item.id(), item);
        categories.put(id, info); categoryItems.put(id, List.copyOf(loaded));
    }

    private static final Set<String> ALPHABETICAL_CATEGORIES = Set.of("redstone", "mobs_drop", "spawners");
    private static final List<String> WOOD_FAMILIES = List.of("OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED");
    private static final List<String> BUILDING_FAMILIES = List.of("MOSSY_COBBLESTONE", "COBBLED_DEEPSLATE", "RED_SANDSTONE", "SANDSTONE", "STONE", "GRANITE", "DIORITE", "ANDESITE", "DEEPSLATE", "TUFF", "CALCITE", "PRISMARINE", "BLACKSTONE", "BASALT", "NETHERRACK", "END_STONE", "QUARTZ", "PURPUR", "BRICK", "MUD", "CLAY");
    private static final List<String> COLOR_FAMILIES = List.of("WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK");
    private static final List<String> RESOURCE_FAMILIES = List.of("NETHERITE", "DIAMOND", "EMERALD", "GOLD", "IRON", "COPPER", "AMETHYST", "LAPIS", "REDSTONE", "COAL", "QUARTZ", "NETHER_BRICK", "PRISMARINE", "HONEY", "SLIME");
    private static final List<String> WOOD_PARTS = List.of("LOG", "WOOD", "STRIPPED_LOG", "STRIPPED_WOOD", "PLANKS", "MOSAIC", "STAIRS", "MOSAIC_STAIRS", "SLAB", "MOSAIC_SLAB", "FENCE", "FENCE_GATE", "DOOR", "TRAPDOOR", "PRESSURE_PLATE", "BUTTON", "SIGN", "HANGING_SIGN", "BOAT", "CHEST_BOAT", "LEAVES", "SAPLING", "PROPAGULE");
    private static final List<String> COLOR_PARTS = List.of("WOOL", "CARPET", "BED", "BANNER", "CONCRETE_POWDER", "CONCRETE", "GLAZED_TERRACOTTA", "TERRACOTTA", "STAINED_GLASS_PANE", "STAINED_GLASS");
    private static final List<String> TOOL_PARTS = List.of("SWORD", "PICKAXE", "AXE", "SHOVEL", "HOE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "HORSE_ARMOR");

    private Comparator<ShopItem> itemOrder(String categoryId) {
        if (ALPHABETICAL_CATEGORIES.contains(categoryId.toLowerCase(Locale.ROOT)))
            return Comparator.comparing(item -> item.template().getType().name(), String.CASE_INSENSITIVE_ORDER);
        return Comparator.comparingInt(this::sortGroup).thenComparingInt(this::sortFamily)
                .thenComparingInt(this::sortVariant)
                .thenComparing(item -> item.template().getType().name(), String.CASE_INSENSITIVE_ORDER);
    }

    private int sortGroup(ShopItem item) {
        String n = item.template().getType().name();
        if (woodIndex(n) >= 0) return 0;
        if (buildingIndex(n) >= 0) return 1;
        if (colorIndex(n) >= 0) return 2;
        if (resourceIndex(n) >= 0) return 3;
        if (toolIndex(n) >= 0) return 4;
        return 5;
    }
    private int sortFamily(ShopItem item) {
        String n = item.template().getType().name();
        int i=woodIndex(n); if(i>=0)return i;
        i=buildingIndex(n); if(i>=0)return i;
        i=colorIndex(n); if(i>=0)return i;
        i=resourceIndex(n); if(i>=0)return i;
        i=toolIndex(n); return Math.max(0,i);
    }
    private int sortVariant(ShopItem item) {
        String n=item.template().getType().name();
        int i=woodIndex(n);
        if(i>=0) {
            String normalized=n.startsWith("STRIPPED_")?n.substring(9):n;
            String suffix=normalized.substring(WOOD_FAMILIES.get(i).length()+1);
            return WOOD_PARTS.indexOf(suffix)<0?99:WOOD_PARTS.indexOf(suffix);
        }
        i=colorIndex(n);
        if(i>=0) return suffixIndex(COLOR_PARTS,n.substring(COLOR_FAMILIES.get(i).length()+1));
        i=toolIndex(n);
        if(i>=0) return suffixIndex(TOOL_PARTS,n.substring(n.indexOf('_')+1));
        return 99;
    }
    private int suffixIndex(List<String> parts,String suffix) {
        for(int i=0;i<parts.size();i++) if(suffix.endsWith(parts.get(i))) return i;
        return 99;
    }
    private int woodIndex(String n) {
        String normalized=n.startsWith("STRIPPED_")?n.substring(9):n;
        for(int i=0;i<WOOD_FAMILIES.size();i++) if(normalized.startsWith(WOOD_FAMILIES.get(i)+"_")) return i;
        return -1;
    }
    private int buildingIndex(String n) {
        for(int i=0;i<BUILDING_FAMILIES.size();i++) if(n.equals(BUILDING_FAMILIES.get(i))||n.startsWith(BUILDING_FAMILIES.get(i)+"_")) return i;
        return -1;
    }
    private int colorIndex(String n) {
        for(int i=0;i<COLOR_FAMILIES.size();i++) if(n.startsWith(COLOR_FAMILIES.get(i)+"_")) return i;
        return -1;
    }
    private int resourceIndex(String n) {
        for(int i=0;i<RESOURCE_FAMILIES.size();i++) if(n.equals(RESOURCE_FAMILIES.get(i))||n.startsWith(RESOURCE_FAMILIES.get(i)+"_")) return i;
        return -1;
    }
    private int toolIndex(String n) {
        List<String> tiers=List.of("WOODEN","STONE","IRON","GOLDEN","DIAMOND","NETHERITE");
        for(int i=0;i<tiers.size();i++) if(n.startsWith(tiers.get(i)+"_")) return i;
        return -1;
    }

    /**
     * Expands the category YAML with every supported vanilla item and spawner.
     * Existing entries and custom prices are never overwritten. Newly generated
     * entries get explicit material/spawner, buy-price, sell-price and currency keys.
     */
    private void materializeCatalogue(File file, YamlConfiguration yaml, ConfigurationSection category, String id) {
        ConfigurationSection itemSection = yaml.getConfigurationSection("items");
        if (itemSection == null) itemSection = yaml.createSection("items");

        double baseBuy = category.getDouble("catalog-defaults.buy-base",
                yaml.getDouble("catalog-defaults.buy-base",
                        category.getDouble("auto-pricing.buy-base", yaml.getDouble("auto-pricing.buy-base", id.equals("spawners") ? 15000.0 : 10.0))));
        double sellRatio = category.getDouble("catalog-defaults.sell-ratio",
                yaml.getDouble("catalog-defaults.sell-ratio",
                        category.getDouble("auto-pricing.sell-ratio", yaml.getDouble("auto-pricing.sell-ratio", 0.22))));
        sellRatio = Math.max(0.0, Math.min(1.0, sellRatio));
        String defaultCurrency = category.getString("catalog-defaults.currency",
                yaml.getString("catalog-defaults.currency",
                        category.getString("auto-pricing.currency", yaml.getString("auto-pricing.currency",
                                plugin.getConfig().getString("economy.default-currency", "vault")))));
        int added = 0;

        if (id.equals("spawners") && yaml.getBoolean("auto-include-entities", true)) {
            for (EntityType entity : EntityType.values()) {
                if (entity == EntityType.PLAYER || entity.getEntityClass() == null
                        || !LivingEntity.class.isAssignableFrom(entity.getEntityClass())) continue;
                String key = entity.name().toLowerCase(Locale.ROOT) + "_spawner";
                if (itemSection.contains(key)) continue;
                // Only create entries that the target server can represent as a spawner item.
                ItemStack vanilla = vanillaSpawner(entity);
                boolean useSmartSpawner = vanilla == null && smartSpawner.create(entity) != null;
                if (vanilla == null && !useSmartSpawner) continue;
                ConfigurationSection node = itemSection.createSection(key);
                node.set("spawner.entity", entity.name());
                node.set("spawner.smart", useSmartSpawner);
                double multiplier = entity == EntityType.ENDER_DRAGON || entity == EntityType.WITHER ? 25.0
                        : entity == EntityType.WARDEN ? 12.0 : entity == EntityType.ELDER_GUARDIAN ? 8.0 : 1.0;
                BigDecimal buy = money(Math.max(1.0, Math.min(1_000_000_000.0, baseBuy * multiplier)));
                node.set("buy-price", buy.doubleValue());
                node.set("sell-price", -1.0);
                node.set("currency", defaultCurrency);
                added++;
            }
        } else if (!id.equals("spawners") && !id.equals("mmoitems") && category.getBoolean("auto-include-vanilla", true)) {
            for (Material material : Material.values()) {
                if (!material.isItem() || material.isAir() || isUnavailableCreativeItem(material.name())
                        || !id.equals(classifyVanilla(material))) continue;
                String key = material.name().toLowerCase(Locale.ROOT);
                if (itemSection.contains(key)) continue;
                ConfigurationSection node = itemSection.createSection(key);
                double buyValue = autoPrice(material, baseBuy);
                BigDecimal buy = money(buyValue);
                BigDecimal sell = money(buyValue * sellRatio);
                node.set("material", material.name());
                node.set("buy-price", buy.doubleValue());
                node.set("sell-price", sell.doubleValue());
                node.set("currency", defaultCurrency);
                added++;
            }
        }

        // Complete older/manual entries too. A missing currency or sell-price must not
        // silently make an item unsellable merely because an early YAML was incomplete.
        for (String key : itemSection.getKeys(false)) {
            ConfigurationSection node = itemSection.getConfigurationSection(key);
            if (node == null) continue;
            boolean changed = false;
            if (!node.contains("currency") || node.getString("currency", "").isBlank()) {
                node.set("currency", defaultCurrency);
                changed = true;
            }
            boolean isSpawner = node.getConfigurationSection("spawner") != null;
            boolean isMmo = node.getConfigurationSection("mmoitems") != null;
            Material material = Material.matchMaterial(node.getString("material", ""));
            double itemBuy = node.getDouble("buy-price", Double.NaN);
            if (!Double.isFinite(itemBuy)) {
                itemBuy = isSpawner ? baseBuy : material != null && material.isItem() ? autoPrice(material, baseBuy) : -1.0;
                node.set("buy-price", money(itemBuy).doubleValue());
                changed = true;
            }
            double itemSell = node.getDouble("sell-price", Double.NaN);
            if (!Double.isFinite(itemSell)) {
                itemSell = (isSpawner || isMmo || itemBuy < 0) ? -1.0 : itemBuy * sellRatio;
                node.set("sell-price", money(itemSell).doubleValue());
                changed = true;
            }
            if (changed) added++;
        }

        if (added > 0) {
            try {
                yaml.save(file);
                plugin.getLogger().info("Expanded Shops/Category/" + file.getName() + " with " + added
                        + " explicit catalogue entries/default fields. Existing prices were preserved.");
            } catch (java.io.IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not persist expanded shop catalogue to Shops/Category/" + file.getName(), ex);
            }
        }
    }

    private BigDecimal money(double value) {
        if (!Double.isFinite(value)) value = -1.0;
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
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
                "DISPENSER", "DROPPER", "OBSERVER", "HOPPER", "LEVER", "TRIPWIRE", "TRIPWIRE_HOOK", "TARGET",
                "DAYLIGHT_DETECTOR", "TNT", "NOTE_BLOCK", "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR",
                "SCULK_SHRIEKER", "LIGHTNING_ROD", "REDSTONE_LAMP", "CRAFTER", "COPPER_BULB",
                "POWERED_RAIL", "DETECTOR_RAIL", "ACTIVATOR_RAIL", "RAIL", "SLIME_BLOCK", "HONEY_BLOCK",
                "TRAPPED_CHEST", "JUKEBOX").contains(n)
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

    private void warn(File file, String message) { plugin.getLogger().warning("Shops/Category/" + file.getName() + ": " + message); }
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
