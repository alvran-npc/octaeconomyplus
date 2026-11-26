/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.hook;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Optional PlaceholderAPI bridge. It uses reflection, so OctaEconomy compiles and runs without
 * PlaceholderAPI; when the plugin is installed, %placeholders% in shop lore are resolved per player.
 */
public final class PlaceholderHook {
    private final Plugin plugin;
    private Method setPlaceholders;
    private boolean unavailable;

    public PlaceholderHook(Plugin plugin) { this.plugin = plugin; }

    public boolean isAvailable() {
        return !unavailable && plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    /** Returns the text with PlaceholderAPI placeholders resolved, or the original text if unavailable. */
    public String apply(Player player, String text) {
        if (player == null || text == null || text.indexOf('%') < 0 || !isAvailable()) return text;
        try {
            if (setPlaceholders == null) {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true, plugin.getClass().getClassLoader());
                setPlaceholders = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            }
            Object result = setPlaceholders.invoke(null, player, text);
            return result instanceof String resolved ? resolved : text;
        } catch (InvocationTargetException ex) {
            return text; // one misbehaving expansion must not disable the hook
        } catch (ReflectiveOperationException | LinkageError ex) {
            unavailable = true;
            plugin.getLogger().warning("PlaceholderAPI is installed but could not be used: " + ex.getMessage());
            return text;
        }
    }
}
