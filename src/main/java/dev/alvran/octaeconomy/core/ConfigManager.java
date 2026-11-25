/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Creates the organized data tree and performs non-destructive migrations from older layouts. */
public final class ConfigManager {
    private static final List<String> CATEGORIES = List.of(
            "blocks", "colored_blocks", "decoration", "redstone", "farming_food",
            "misc", "mobs_drop", "mmoitems", "vanilla_tools", "spawners");
    private final JavaPlugin plugin;
    private final File root;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.root = plugin.getDataFolder();
    }

    public void ensureFiles() {
        root.mkdirs();
        for (String directory : List.of("data", "lang/message", "shops/category", "auctions",
                "casino/animation", "casino/games", "gui", "integrations")) {
            new File(root, directory).mkdirs();
        }

        saveIfMissing("config.yml");
        migrateLegacyFiles();

        saveIfMissing("config.yml");
        saveIfMissing("data/PlayerData.yml");
        saveIfMissing("shops/discount.yml");
        saveIfMissing("auctions/auctions.yml");
        saveIfMissing("auctions/theme.yml");
        saveIfMissing("casino/settings.yml");
        saveIfMissing("casino/animation/roulette.yml");
        saveIfMissing("casino/games/roulette.yml");
        saveIfMissing("gui/shop.yml");
        saveIfMissing("gui/auction.yml");
        saveIfMissing("integrations/discordsrv.yml");
        saveIfMissing("integrations/discordwebhook.yml");
        saveIfMissing("integrations/saweria-tebex.yml");
        for (String language : List.of("en", "id", "zh")) saveIfMissing("lang/message/" + language + ".yml");
        for (String category : CATEGORIES) saveIfMissing("shops/category/" + category + ".yml");

        for (String path : List.of("config.yml", "data/PlayerData.yml",
                "shops/discount.yml", "auctions/auctions.yml", "auctions/theme.yml",
                "casino/settings.yml", "casino/animation/roulette.yml", "casino/games/roulette.yml",
                "gui/shop.yml", "gui/auction.yml", "integrations/discordsrv.yml", "integrations/discordwebhook.yml", "integrations/saweria-tebex.yml")) {
            mergeDefaults(path);
        }
        normalizeDatabasePath();
    }

    /**
     * Brings every older layout (flat files, DEV builds) to the current tree. Files are moved, never
     * overwritten: when a target already exists the old file is left untouched.
     */
    private void migrateLegacyFiles() {
        // Pre-folder layout (copied: these single files fed several new files).
        copyIfTargetMissing("auction.yml", "auctions/auctions.yml");
        copyIfTargetMissing("gui.yml", "gui/shop.yml");
        copyIfTargetMissing("gui.yml", "auctions/theme.yml");
        copyIfTargetMissing("casino.yml", "casino/settings.yml");
        copyIfTargetMissing("integrations.yml", "integrations/saweria-tebex.yml");
        copyIfTargetMissing("discounts.yml", "shops/discount.yml");

        // Seasonal sale file: shops/discounts.yml -> shops/discount.yml
        moveIfTargetMissing(new File(root, "shops/discounts.yml"), new File(root, "shops/discount.yml"));

        // Category files: shops/<id>.yml (oldest) and shop/*.yml (previous) -> shops/category/*.yml
        for (String category : CATEGORIES) {
            moveIfTargetMissing(new File(root, "shops/" + category + ".yml"), new File(root, "shops/category/" + category + ".yml"));
        }
        File oldShopFolder = new File(root, "shop");
        File[] oldCategories = oldShopFolder.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (oldCategories != null) {
            for (File old : oldCategories) moveIfTargetMissing(old, new File(root, "shops/category/" + old.getName()));
            String[] left = oldShopFolder.list();
            if (left != null && left.length == 0) oldShopFolder.delete();
        }

        // Language files: lang/<code>.yml -> lang/message/<code>.yml
        for (String language : List.of("en", "id", "zh")) {
            moveIfTargetMissing(new File(root, "lang/" + language + ".yml"), new File(root, "lang/message/" + language + ".yml"));
        }

        // Player metadata: data/Playerdata.yml -> data/PlayerData.yml (case-safe on every file system)
        renameCaseOnly(new File(root, "data/Playerdata.yml"), new File(root, "data/PlayerData.yml"));

        File oldDatabase = new File(root, "data/auction.db");
        File newDatabase = new File(root, "data/database.db");
        if (oldDatabase.isFile() && !newDatabase.exists()) {
            try { Files.copy(oldDatabase.toPath(), newDatabase.toPath(), StandardCopyOption.COPY_ATTRIBUTES); }
            catch (IOException ex) { plugin.getLogger().warning("Could not migrate data/auction.db to data/database.db: " + ex.getMessage()); }
        }

        migrateShopSettings();
    }

    /** shops/settings.yml now lives in config.yml under "shop:"; old values win over defaults. */
    private void migrateShopSettings() {
        File settingsFile = new File(root, "shops/settings.yml");
        if (!settingsFile.isFile()) return;
        File configFile = new File(root, "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        YamlConfiguration old = YamlConfiguration.loadConfiguration(settingsFile);
        ConfigurationSection shop = old.getConfigurationSection("shop");
        if (shop != null) {
            for (String key : shop.getKeys(true)) {
                if (!shop.isConfigurationSection(key)) config.set("shop." + key, shop.get(key));
            }
        }
        try {
            config.save(configFile);
            Files.delete(settingsFile.toPath());
            plugin.getLogger().info("Moved shops/settings.yml into config.yml (shop section).");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not move shops/settings.yml into config.yml; the old file was kept: " + ex.getMessage());
        }
    }

    private void moveIfTargetMissing(File source, File target) {
        if (!source.isFile() || target.exists()) return;
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        try { Files.move(source.toPath(), target.toPath()); }
        catch (IOException ex) { plugin.getLogger().warning("Could not move " + source.getName() + " to " + target + ": " + ex.getMessage()); }
    }

    /** Renames a file whose new name differs only by letter case (needs a temp hop on Windows/macOS). */
    private void renameCaseOnly(File source, File target) {
        if (!source.isFile()) return;
        try {
            if (target.exists() && !source.getCanonicalFile().equals(target.getCanonicalFile())) return; // two real files: keep both
            File temp = new File(source.getParentFile(), source.getName() + ".tmp-rename");
            Files.move(source.toPath(), temp.toPath());
            Files.move(temp.toPath(), target.toPath());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not rename " + source.getName() + " to " + target.getName() + ": " + ex.getMessage());
        }
    }

    private void copyIfTargetMissing(String oldRelative, String newRelative) {
        copyIfTargetMissing(new File(root, oldRelative), new File(root, newRelative));
    }

    private void copyIfTargetMissing(File source, File target) {
        if (!source.isFile() || target.exists()) return;
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        try { Files.copy(source.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES); }
        catch (IOException ex) { plugin.getLogger().warning("Could not migrate " + source.getName() + " to " + target + ": " + ex.getMessage()); }
    }

    private void normalizeDatabasePath() {
        File configFile = new File(root, "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String path = config.getString("database.sqlite-file", "data/database.db");
        if ("data/auction.db".equalsIgnoreCase(path)) {
            config.set("database.sqlite-file", "data/database.db");
            try { config.save(configFile); }
            catch (IOException ex) { plugin.getLogger().warning("Could not update SQLite path: " + ex.getMessage()); }
        }
    }

    private void saveIfMissing(String path) {
        File file = new File(root, path);
        if (file.exists()) return;
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try {
            if (plugin.getResource(path) != null) plugin.saveResource(path, false);
            else if (path.equals("data/PlayerData.yml")) {
                Files.writeString(file.toPath(), "schema-version: 1\nplayers: {}\n", java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not create " + path + ": " + ex.getMessage());
        }
    }

    private void mergeDefaults(String path) {
        File target = new File(root, path);
        if (!target.isFile() || plugin.getResource(path) == null) return;
        YamlConfiguration user = YamlConfiguration.loadConfiguration(target);
        YamlConfiguration defaults;
        try (java.io.Reader reader = new java.io.InputStreamReader(plugin.getResource(path), java.nio.charset.StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not read default " + path + ": " + ex.getMessage());
            return;
        }
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            if (!user.contains(key)) { user.set(key, defaults.get(key)); changed = true; }
        }
        if (changed) {
            try { user.save(target); }
            catch (IOException ex) { plugin.getLogger().warning("Could not update " + path + ": " + ex.getMessage()); }
        }
    }
}
