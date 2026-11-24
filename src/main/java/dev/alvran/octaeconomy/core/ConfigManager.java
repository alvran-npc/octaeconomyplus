/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.core;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Keeps user values while adding missing defaults during upgrades. */
public final class ConfigManager {
    private final JavaPlugin plugin;
    public ConfigManager(JavaPlugin plugin) { this.plugin = plugin; }
    public void ensureFiles() {
        plugin.getDataFolder().mkdirs();
        saveIfMissing("config.yml"); saveIfMissing("auction.yml"); saveIfMissing("gui.yml"); saveIfMissing("discounts.yml");
        saveIfMissing("integrations.yml"); saveIfMissing("casino.yml");
        for (String lang : List.of("en", "id", "zh")) saveIfMissing("lang/" + lang + ".yml");
        for (String shop : List.of("blocks", "colored_blocks", "decoration", "redstone", "farming_food", "misc", "mobs_drop", "mmoitems", "vanilla_tools", "spawners")) saveIfMissing("shops/" + shop + ".yml");
        mergeDefaults("config.yml");
        mergeDefaults("auction.yml");
        mergeDefaults("gui.yml");
        mergeDefaults("discounts.yml");
        mergeDefaults("integrations.yml");
        mergeDefaults("casino.yml");
    }
    private void saveIfMissing(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            File parent = file.getParentFile(); if (parent != null) parent.mkdirs();
            plugin.saveResource(path, false);
        }
    }
    private void mergeDefaults(String path) {
        File target = new File(plugin.getDataFolder(), path);
        YamlConfiguration user = YamlConfiguration.loadConfiguration(target);
        YamlConfiguration defaults;
        try (java.io.Reader reader = new java.io.InputStreamReader(plugin.getResource(path), java.nio.charset.StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (Exception ex) { plugin.getLogger().warning("Could not read default " + path + ": " + ex.getMessage()); return; }
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            if (!user.contains(key)) { user.set(key, defaults.get(key)); changed = true; }
        }
        if (changed) try { user.save(target); } catch (IOException ex) { plugin.getLogger().warning("Could not update " + path + ": " + ex.getMessage()); }
    }
}
