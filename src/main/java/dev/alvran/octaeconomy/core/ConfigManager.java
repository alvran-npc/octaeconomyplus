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
        for (String directory : List.of("data", "lang", "shops", "shop", "auctions",
                "casino/animation", "casino/games", "gui", "integrations")) {
            new File(root, directory).mkdirs();
        }

        migrateLegacyFiles();

        saveIfMissing("config.yml");
        saveIfMissing("data/Playerdata.yml");
        saveIfMissing("shops/settings.yml");
        saveIfMissing("shops/discounts.yml");
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
        for (String language : List.of("en", "id", "zh")) saveIfMissing("lang/" + language + ".yml");
        for (String category : CATEGORIES) saveIfMissing("shop/" + category + ".yml");

        for (String path : List.of("config.yml", "data/Playerdata.yml", "shops/settings.yml",
                "shops/discounts.yml", "auctions/auctions.yml", "auctions/theme.yml",
                "casino/settings.yml", "casino/animation/roulette.yml", "casino/games/roulette.yml",
                "gui/shop.yml", "gui/auction.yml", "integrations/discordsrv.yml", "integrations/discordwebhook.yml", "integrations/saweria-tebex.yml")) {
            mergeDefaults(path);
        }
        normalizeDatabasePath();
    }

    private void migrateLegacyFiles() {
        // Copy rather than move: old paths remain as a rollback source until the owner removes them.
        copyIfTargetMissing("auction.yml", "auctions/auctions.yml");
        copyIfTargetMissing("gui.yml", "gui/shop.yml");
        copyIfTargetMissing("gui.yml", "auctions/theme.yml");
        copyIfTargetMissing("casino.yml", "casino/settings.yml");
        copyIfTargetMissing("integrations.yml", "integrations/saweria-tebex.yml");
        copyIfTargetMissing("discounts.yml", "shops/discounts.yml");

        File oldShopFolder = new File(root, "shops");
        for (String category : CATEGORIES) {
            copyIfTargetMissing(new File(oldShopFolder, category + ".yml"), new File(root, "shop/" + category + ".yml"));
        }

        File oldDatabase = new File(root, "data/auction.db");
        File newDatabase = new File(root, "data/database.db");
        if (oldDatabase.isFile() && !newDatabase.exists()) {
            try { Files.copy(oldDatabase.toPath(), newDatabase.toPath(), StandardCopyOption.COPY_ATTRIBUTES); }
            catch (IOException ex) { plugin.getLogger().warning("Could not migrate data/auction.db to data/database.db: " + ex.getMessage()); }
        }

        // Move the former root-level shop options into the dedicated settings file.
        File settingsFile = new File(root, "shops/settings.yml");
        File oldConfig = new File(root, "config.yml");
        if (!settingsFile.exists() && oldConfig.isFile()) {
            YamlConfiguration old = YamlConfiguration.loadConfiguration(oldConfig);
            ConfigurationSection shopSection = old.getConfigurationSection("shop");
            if (shopSection != null) {
                YamlConfiguration settings = new YamlConfiguration();
                for (String key : shopSection.getKeys(true)) {
                    if (!shopSection.isConfigurationSection(key)) settings.set("shop." + key, shopSection.get(key));
                }
                try { settings.save(settingsFile); }
                catch (IOException ex) { plugin.getLogger().warning("Could not migrate shop settings: " + ex.getMessage()); }
            }
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
            else if (path.equals("data/Playerdata.yml")) {
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
