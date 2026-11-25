/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.shop;

import org.bukkit.Material;

public record ShopCategory(String id, String displayName, Material icon, int slot, String permission) { }
