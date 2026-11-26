/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.api;

import dev.alvran.octaeconomy.auction.AuctionListing;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class AuctionPurchaseEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player buyer; private final AuctionListing listing; private boolean cancelled;
    public AuctionPurchaseEvent(Player buyer, AuctionListing listing) { this.buyer=buyer; this.listing=listing; }
    public Player getBuyer() { return buyer; } public AuctionListing getListing() { return listing; }
    @Override public boolean isCancelled() { return cancelled; } @Override public void setCancelled(boolean value) { cancelled=value; }
    @Override public HandlerList getHandlers() { return HANDLERS; } public static HandlerList getHandlerList() { return HANDLERS; }
}
