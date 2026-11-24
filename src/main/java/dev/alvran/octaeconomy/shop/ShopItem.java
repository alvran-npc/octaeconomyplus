/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.shop;

import java.math.BigDecimal;
import org.bukkit.inventory.ItemStack;

public record ShopItem(String id, String categoryId, ItemStack template, BigDecimal buyPrice,
                       BigDecimal sellPrice, String currency) {
    public ShopItem { template = template.clone(); }
    @Override public ItemStack template() { return template.clone(); }
    public boolean canBuy() { return buyPrice.signum() >= 0; }
    public boolean canSell() { return sellPrice.signum() >= 0; }
    public String displayName() {
        if (template.getItemMeta() != null && template.getItemMeta().hasDisplayName()) return template.getItemMeta().getDisplayName();
        return template.getType().name().toLowerCase().replace('_', ' ');
    }
}
