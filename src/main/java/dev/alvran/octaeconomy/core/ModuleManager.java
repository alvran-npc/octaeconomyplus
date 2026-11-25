/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.core;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModuleManager {
    private final JavaPlugin plugin;
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager(JavaPlugin plugin) { this.plugin = plugin; }
    public void register(Module module) { modules.add(module); }
    public void enableAll() {
        for (Module module : modules) {
            try { module.enable(); }
            catch (Exception ex) { plugin.getLogger().log(Level.SEVERE, "Could not enable module " + module.name(), ex); }
        }
    }
    public void reloadAll() {
        for (Module module : modules) {
            try { module.reload(); }
            catch (Exception ex) { plugin.getLogger().log(Level.WARNING, "Could not reload module " + module.name(), ex); }
        }
    }
    public void disableAll() {
        for (int i = modules.size() - 1; i >= 0; i--) {
            try { modules.get(i).disable(); }
            catch (Exception ex) { plugin.getLogger().log(Level.WARNING, "Could not disable module " + modules.get(i).name(), ex); }
        }
    }
    public List<Module> modules() { return List.copyOf(modules); }
}
