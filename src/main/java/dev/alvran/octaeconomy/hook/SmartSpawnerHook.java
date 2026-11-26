/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.hook;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional SmartSpawner item factory. Vanilla spawners remain the fallback. */
public final class SmartSpawnerHook {
    private final JavaPlugin plugin;
    private Object api;
    private Method createSpawnerItem;
    private boolean logged;
    public SmartSpawnerHook(JavaPlugin plugin) { this.plugin = plugin; }
    public void setup() {
        api = null; createSpawnerItem = null;
        Plugin target = Bukkit.getPluginManager().getPlugin("SmartSpawner");
        if (target == null || !target.isEnabled()) { log("SmartSpawner not found; vanilla spawner items will be used."); return; }
        try {
            ClassLoader loader = target.getClass().getClassLoader();
            Class<?> provider = Class.forName("github.nighter.smartspawner.api.SmartSpawnerProvider", true, loader);
            api = provider.getMethod("getAPI").invoke(null);
            createSpawnerItem = api.getClass().getMethod("createSpawnerItem", EntityType.class);
            plugin.getLogger().info("SmartSpawner hook enabled.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            log("SmartSpawner API is unavailable; vanilla spawner items will be used.");
        }
    }
    private void log(String message) { if (!logged) { plugin.getLogger().info(message); logged = true; } }
    public ItemStack create(EntityType type) {
        if (api == null || createSpawnerItem == null) return null;
        try { Object item = createSpawnerItem.invoke(api, type); return item instanceof ItemStack stack ? stack.clone() : null; }
        catch (ReflectiveOperationException ex) { return null; }
    }
}
