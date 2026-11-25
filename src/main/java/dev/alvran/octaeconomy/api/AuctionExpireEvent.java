/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.api;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class AuctionExpireEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final long listingId; private final UUID seller;
    public AuctionExpireEvent(long listingId, UUID seller) { this.listingId=listingId; this.seller=seller; }
    public long getListingId() { return listingId; } public UUID getSeller() { return seller; }
    @Override public HandlerList getHandlers() { return HANDLERS; } public static HandlerList getHandlerList() { return HANDLERS; }
}
