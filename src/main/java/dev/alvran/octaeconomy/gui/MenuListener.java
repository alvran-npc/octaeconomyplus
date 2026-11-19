/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.gui;

import dev.alvran.octaeconomy.auction.AuctionModule;
import dev.alvran.octaeconomy.shop.ShopModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MenuListener implements Listener {
    private final ShopModule shop;
    private final AuctionModule auction;
    public MenuListener(ShopModule shop, AuctionModule auction) { this.shop = shop; this.auction = auction; }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof OctaMenuHolder holder)) return;
        if (holder.owner() != null && !holder.owner().equals(event.getWhoClicked().getUniqueId())) { event.setCancelled(true); return; }
        if (holder.type() == MenuType.SELL) {
            if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP || event.getClick() == ClickType.SWAP_OFFHAND) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        switch (holder.type()) {
            case SHOP_MAIN, SHOP_CATEGORY, SHOP_QUANTITY -> shop.handleClick(player, event, holder);
            case AUCTION_LIST, AUCTION_CONFIRM, AUCTION_MINE, AUCTION_EXPIRED -> auction.handleClick(player, event, holder);
            default -> { }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof OctaMenuHolder holder)) return;
        if (holder.type() != MenuType.SELL) event.setCancelled(true);
        else if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof OctaMenuHolder holder)) return;
        if (holder.type() == MenuType.SELL && event.getPlayer() instanceof Player player) shop.finishSellGui(player, event.getInventory());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Close first so the SellGUI close handler can settle/return its contents
        // before per-player state is discarded.
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof OctaMenuHolder holder && holder.type() == MenuType.SELL) {
            player.closeInventory();
        }
        shop.clearPlayer(player.getUniqueId());
        auction.clearPlayer(player.getUniqueId());
    }
}
