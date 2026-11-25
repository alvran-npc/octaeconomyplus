/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.core;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class LangManager {
    private final JavaPlugin plugin;
    private final Map<String, String> messages = new ConcurrentHashMap<>();
    private final MiniMessage mini = MiniMessage.miniMessage();
    private String language = "en";

    public LangManager(JavaPlugin plugin) { this.plugin = plugin; }
    public void reload() {
        language = plugin.getConfig().getString("language", "en").toLowerCase();
        if (!language.equals("en") && !language.equals("id") && !language.equals("zh")) language = "en";
        messages.clear();
        YamlConfiguration fallback = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "Lang/Message/en.yml"));
        YamlConfiguration selected = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "Lang/Message/" + language + ".yml"));
        for (String key : fallback.getKeys(false)) messages.put(key, selected.getString(key, fallback.getString(key, "")));
    }
    public Component component(String key, Map<String, ?> placeholders) {
        String raw = messages.getOrDefault(key, messages.getOrDefault("error", "<red>Error"));
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) raw = raw.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        return mini.deserialize(raw);
    }
    public Component component(String key) { return component(key, Map.of()); }
    public void send(CommandSender sender, String key) { sender.sendMessage(component(key)); }
    public void send(CommandSender sender, String key, Map<String, ?> values) { sender.sendMessage(component(key, values)); }
    public String raw(String key) { return messages.getOrDefault(key, ""); }
    public String legacy(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }
}
