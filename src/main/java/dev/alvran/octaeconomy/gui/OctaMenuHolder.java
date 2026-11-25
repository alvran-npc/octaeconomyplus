/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class OctaMenuHolder implements InventoryHolder {
    private final UUID owner;
    private final MenuType type;
    private final String context;
    private final int page;
    private final int quantity;
    private final long listingId;
    private final Map<Integer, String> slotKeys = new HashMap<>();
    private Inventory inventory;

    public OctaMenuHolder(UUID owner, MenuType type, String context, int page, int quantity, long listingId) {
        this.owner = owner; this.type = type; this.context = context; this.page = page; this.quantity = quantity; this.listingId = listingId;
    }
    public UUID owner() { return owner; }
    public MenuType type() { return type; }
    public String context() { return context; }
    public int page() { return page; }
    public int quantity() { return quantity; }
    public long listingId() { return listingId; }
    public void map(int slot, String key) { slotKeys.put(slot, key); }
    public String key(int slot) { return slotKeys.get(slot); }
    public void attach(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
