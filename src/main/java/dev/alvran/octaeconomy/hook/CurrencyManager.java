/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.hook;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.java.JavaPlugin;

public final class CurrencyManager {
    private final VaultHook vault;
    private final ExcellentEconomyHook excellent;
    private final Map<String, CurrencyProvider> cache = new ConcurrentHashMap<>();
    public CurrencyManager(JavaPlugin plugin) {
        vault = new VaultHook(plugin);
        excellent = new ExcellentEconomyHook(plugin);
    }
    public void setup() { cache.clear(); vault.setup(); excellent.setup(); if (vault.available()) cache.put("vault", vault); }
    public CurrencyProvider get(String rawId) {
        if (rawId == null || rawId.isBlank()) return null;
        String id = rawId.toLowerCase(Locale.ROOT);
        if (id.equals("vault")) return vault.available() ? vault : null;
        return cache.computeIfAbsent(id, key -> {
            CurrencyProvider provider = excellent.provider(rawId);
            return provider;
        });
    }
    public boolean has(String id) { return get(id) != null; }
    public VaultHook vault() { return vault; }
}
