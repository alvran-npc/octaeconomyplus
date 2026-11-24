/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Resolves the two stable MMOItems accessors once, then caches them. */
public final class MMOItemsHook {
    private final JavaPlugin plugin;
    private Object mmoItems;
    private Method getItem;
    private boolean logged;
    public MMOItemsHook(JavaPlugin plugin) { this.plugin = plugin; }
    public void setup() {
        mmoItems = null; getItem = null;
        Plugin target = Bukkit.getPluginManager().getPlugin("MMOItems");
        if (target == null || !target.isEnabled()) { log("MMOItems not found; RPG shop category is disabled."); return; }
        try {
            Class<?> type = Class.forName("net.Indyuce.mmoitems.MMOItems", true, target.getClass().getClassLoader());
            Field field = type.getField("plugin");
            mmoItems = field.get(null);
            getItem = type.getMethod("getItem", String.class, String.class);
            plugin.getLogger().info("MMOItems hook enabled.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            log("MMOItems API could not be initialized; RPG shop category is disabled.");
        }
    }
    private void log(String message) { if (!logged) { plugin.getLogger().info(message); logged = true; } }
    public boolean isAvailable() { return mmoItems != null && getItem != null; }
    public ItemStack create(String type, String id) {
        if (!isAvailable()) return null;
        try { Object result = getItem.invoke(mmoItems, type, id); return result instanceof ItemStack stack ? stack.clone() : null; }
        catch (ReflectiveOperationException ex) { return null; }
    }
}
