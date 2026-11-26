/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.hook;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Runtime-only bridge: the optional ExcellentEconomy API is never linked at class load time. */
public final class ExcellentEconomyHook {
    private final JavaPlugin plugin;
    private Object api;
    private Class<?> apiClass;
    private Method hasCurrency, getBalance, withdraw, deposit;
    private boolean loggedMissing;

    public ExcellentEconomyHook(JavaPlugin plugin) { this.plugin = plugin; }

    public void setup() {
        api = null; apiClass = null; hasCurrency = null; getBalance = null; withdraw = null; deposit = null;
        Plugin target = Bukkit.getPluginManager().getPlugin("ExcellentEconomy");
        if (target == null || !target.isEnabled()) {
            logMissingOnce("ExcellentEconomy not found; extra currencies are disabled.");
            return;
        }
        try {
            ClassLoader loader = target.getClass().getClassLoader();
            apiClass = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI", true, loader);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(apiClass);
            if (registration == null) {
                logMissingOnce("ExcellentEconomy API service is not registered; extra currencies are disabled.");
                return;
            }
            api = registration.getProvider();
            hasCurrency = apiClass.getMethod("hasCurrency", String.class);
            getBalance = apiClass.getMethod("getBalance", Player.class, String.class);
            withdraw = apiClass.getMethod("withdraw", Player.class, String.class, double.class);
            deposit = apiClass.getMethod("deposit", Player.class, String.class, double.class);
            plugin.getLogger().info("ExcellentEconomy hook enabled.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            logMissingOnce("ExcellentEconomy API could not be initialized; extra currencies are disabled (" + ex.getClass().getSimpleName() + ").");
        }
    }
    private void logMissingOnce(String message) { if (!loggedMissing) { plugin.getLogger().info(message); loggedMissing = true; } }
    public boolean isAvailable() { return api != null; }
    public boolean supports(String currency) {
        if (api == null || currency == null) return false;
        try { return Boolean.TRUE.equals(hasCurrency.invoke(api, currency)); }
        catch (ReflectiveOperationException ex) { return false; }
    }
    public CurrencyProvider provider(String currency) { return supports(currency) ? new ExcellentCurrencyProvider(currency) : null; }

    private final class ExcellentCurrencyProvider implements CurrencyProvider {
        private final String currency;
        private ExcellentCurrencyProvider(String currency) { this.currency = currency; }
        @Override public String id() { return currency; }
        @Override public String displayName() { return currency; }
        @Override public double balance(Player player) {
            try { Object value = getBalance.invoke(api, player, currency); return value instanceof Number n ? n.doubleValue() : 0; }
            catch (ReflectiveOperationException ex) { return 0; }
        }
        @Override public boolean withdraw(Player player, double amount) { return invokeOperation(withdraw, player, amount); }
        @Override public boolean deposit(Player player, double amount) { return invokeOperation(deposit, player, amount); }
        @Override public boolean deposit(OfflinePlayer player, double amount) {
            return player.isOnline() && player.getPlayer() != null && deposit(player.getPlayer(), amount);
        }
        private boolean invokeOperation(Method method, Player player, double amount) {
            try { return Boolean.TRUE.equals(method.invoke(api, player, currency, amount)); }
            catch (ReflectiveOperationException ex) { return false; }
        }
    }
}
