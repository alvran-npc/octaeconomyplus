/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.hook;

import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultHook implements CurrencyProvider {
    private final JavaPlugin plugin;
    private Economy economy;
    public VaultHook(JavaPlugin plugin) { this.plugin = plugin; }
    public boolean setup() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            plugin.getLogger().warning("No Vault economy provider is registered. OctaEconomy will keep Vault transactions unavailable until a provider is installed.");
            return false;
        }
        economy = registration.getProvider();
        plugin.getLogger().info("Using Vault economy provider: " + economy.getName());
        return true;
    }
    @Override public String id() { return "vault"; }
    @Override public String displayName() { return "Vault"; }
    @Override public double balance(Player player) { return economy == null ? 0 : economy.getBalance(player); }
    @Override public boolean withdraw(Player player, double amount) { return economy != null && economy.withdrawPlayer(player, amount).transactionSuccess(); }
    @Override public boolean deposit(Player player, double amount) { return economy != null && economy.depositPlayer(player, amount).transactionSuccess(); }
    @Override public boolean deposit(OfflinePlayer player, double amount) { return economy != null && economy.depositPlayer(player, amount).transactionSuccess(); }
    public Economy provider() { return economy; }

    /** Primary permission group through Vault's Permission service, or null when no provider is available. */
    public String primaryGroup(Player player) {
        try {
            RegisteredServiceProvider<Permission> registration = Bukkit.getServicesManager().getRegistration(Permission.class);
            if (registration == null) return null;
            String group = registration.getProvider().getPrimaryGroup(player);
            return group == null || group.isBlank() ? null : group;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
