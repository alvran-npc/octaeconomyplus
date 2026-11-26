/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.api;

import java.math.BigDecimal;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

public final class AuctionListEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player seller; private final ItemStack item; private final BigDecimal price; private final String currency; private boolean cancelled;
    public AuctionListEvent(Player seller, ItemStack item, BigDecimal price, String currency) { this.seller=seller; this.item=item.clone(); this.price=price; this.currency=currency; }
    public Player getSeller() { return seller; } public ItemStack getItem() { return item.clone(); } public BigDecimal getPrice() { return price; } public String getCurrency() { return currency; }
    @Override public boolean isCancelled() { return cancelled; } @Override public void setCancelled(boolean value) { cancelled=value; }
    @Override public HandlerList getHandlers() { return HANDLERS; } public static HandlerList getHandlerList() { return HANDLERS; }
}
