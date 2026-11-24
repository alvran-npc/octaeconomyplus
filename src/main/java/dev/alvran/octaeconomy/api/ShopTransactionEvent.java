/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.api;

import java.math.BigDecimal;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;

public final class ShopTransactionEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player; private final ItemStack item; private final int amount; private final BigDecimal total; private final String currency; private final boolean purchase;
    private boolean cancelled;
    public ShopTransactionEvent(Player player, ItemStack item, int amount, BigDecimal total, String currency, boolean purchase) {
        this.player = player; this.item = item.clone(); this.amount = amount; this.total = total; this.currency = currency; this.purchase = purchase;
    }
    public Player getPlayer() { return player; } public ItemStack getItem() { return item.clone(); }
    public int getAmount() { return amount; } public BigDecimal getTotal() { return total; } public String getCurrency() { return currency; } public boolean isPurchase() { return purchase; }
    @Override public boolean isCancelled() { return cancelled; } @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; } public static HandlerList getHandlerList() { return HANDLERS; }
}
