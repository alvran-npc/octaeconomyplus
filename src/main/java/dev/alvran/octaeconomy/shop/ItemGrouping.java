/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.shop;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Sorts item ids by "family" so related items stay together: oak_log, stripped_oak_log, oak_planks,
 * oak_stairs, oak_slab, oak_button ... then the next wood. Ids that match no known pattern simply
 * form their own family and fall back to alphabetical order.
 */
final class ItemGrouping {
    /** Item "forms" in display order inside one family (index + 1 is the rank; no form = rank 0). */
    private static final List<String> FORMS = List.of(
            "NUGGET", "INGOT", "ORE", "BLOCK", "LOG", "WOOD", "STEM", "HYPHAE", "PLANKS", "MOSAIC", "BRICKS", "TILES",
            "STAIRS", "SLAB", "WALL", "FENCE", "FENCE_GATE", "DOOR", "TRAPDOOR", "PRESSURE_PLATE", "BUTTON",
            "SIGN", "HANGING_SIGN", "BOAT", "CHEST_BOAT", "RAFT", "CHEST_RAFT", "LEAVES", "SAPLING", "PROPAGULE",
            "SWORD", "PICKAXE", "AXE", "SHOVEL", "HOE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "HORSE_ARMOR",
            "WOOL", "CARPET", "BED", "BANNER", "CONCRETE", "CONCRETE_POWDER", "TERRACOTTA", "GLAZED_TERRACOTTA",
            "STAINED_GLASS", "STAINED_GLASS_PANE", "GLASS", "GLASS_PANE", "SHULKER_BOX", "CANDLE", "DYE",
            "SPAWN_EGG", "BUCKET");
    /** Words that describe a variant of the same material (polished_andesite belongs with andesite). */
    private static final List<String> MODIFIERS = List.of(
            "POLISHED", "SMOOTH", "CUT", "CHISELED", "CRACKED", "MOSSY", "STRIPPED", "COBBLED", "DEEPSLATE",
            "WAXED", "EXPOSED", "WEATHERED", "OXIDIZED", "RAW", "INFESTED", "CHIPPED", "DAMAGED");
    private static final List<String> FAMILY_TAILS = List.of("_BRICK", "_TILE", "_MOSAIC");
    private static final List<String> FORMS_LONGEST_FIRST = FORMS.stream()
            .sorted(Comparator.comparingInt(String::length).reversed()).toList();

    private ItemGrouping() { }

    record Key(String family, int formRank, int modifierKey, String name) implements Comparable<Key> {
        @Override public int compareTo(Key other) {
            int result = family.compareTo(other.family);
            if (result != 0) return result;
            result = Integer.compare(formRank, other.formRank);
            if (result != 0) return result;
            result = Integer.compare(modifierKey, other.modifierKey);
            return result != 0 ? result : name.compareTo(other.name);
        }
    }

    static Key key(String id) {
        String name = id.toUpperCase(Locale.ROOT);
        String family = name;
        int formRank = 0;
        for (String form : FORMS_LONGEST_FIRST) {
            String suffix = "_" + form;
            if (name.length() > suffix.length() && name.endsWith(suffix)) {
                family = name.substring(0, name.length() - suffix.length());
                formRank = FORMS.indexOf(form) + 1;
                for (String tail : FAMILY_TAILS) {
                    if (family.length() > tail.length() && family.endsWith(tail)) {
                        family = family.substring(0, family.length() - tail.length());
                        break;
                    }
                }
                break;
            }
        }
        int modifierKey = 0;
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (int i = 0; i < MODIFIERS.size(); i++) {
                String prefix = MODIFIERS.get(i) + "_";
                if (family.length() > prefix.length() && family.startsWith(prefix)) {
                    family = family.substring(prefix.length());
                    modifierKey = modifierKey * 32 + (i + 1);
                    stripped = true;
                    break;
                }
            }
        }
        return new Key(family, formRank, modifierKey, name);
    }

    static Comparator<ShopItem> grouped() { return Comparator.comparing(item -> key(item.id())); }

    static Comparator<ShopItem> alphabetical() {
        return Comparator.comparing((ShopItem item) -> item.id().toLowerCase(Locale.ROOT));
    }
}
