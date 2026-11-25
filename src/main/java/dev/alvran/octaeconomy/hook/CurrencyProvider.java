/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.hook;

import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public interface CurrencyProvider {
    String id();
    String displayName();
    double balance(Player player);
    boolean withdraw(Player player, double amount);
    boolean deposit(Player player, double amount);
    boolean deposit(OfflinePlayer player, double amount);
    default boolean available() { return true; }
}
