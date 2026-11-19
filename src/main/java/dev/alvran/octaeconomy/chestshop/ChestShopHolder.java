/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.chestshop;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ChestShopHolder implements InventoryHolder {
    private final UUID viewer;
    private final String shopKey;
    private Inventory inventory;

    public ChestShopHolder(UUID viewer, String shopKey) {
        this.viewer = viewer;
        this.shopKey = shopKey;
    }

    public UUID viewer() { return viewer; }
    public String shopKey() { return shopKey; }
    public void attach(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
