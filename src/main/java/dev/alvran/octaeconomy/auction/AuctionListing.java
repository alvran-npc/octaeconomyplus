/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.auction;

import java.util.UUID;

public record AuctionListing(long id, UUID sellerId, String sellerName, String itemName,
                             String currency, double price, long createdAt, long expiresAt,
                             String status, byte[] itemData) { }
