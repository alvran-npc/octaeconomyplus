/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.shop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Arranges an already-sorted category into display sections (config.yml: shop.layouts.&lt;category&gt;).
 * Each section starts on a fresh page. A "rows" section puts one item type per row and one material per
 * column (7 columns = the width of the item grid), so armor and tools line up neatly. The returned
 * list may contain null entries, which are empty slots.
 */
final class ShopLayout {
    static final int PAGE_SIZE = 28;
    static final int ROW_WIDTH = 7;

    private ShopLayout() { }

    private record Matcher(String family, Pattern glob) {
        boolean matches(String id, String itemFamily) {
            return family != null ? family.equals(itemFamily) : glob.matcher(id).matches();
        }
    }

    static List<ShopItem> arrange(List<ShopItem> sorted, List<Map<?, ?>> sections, Logger log, String categoryId) {
        List<ShopItem> remaining = new ArrayList<>(sorted);
        if (sections == null || sections.isEmpty()) return remaining;
        List<ShopItem> result = new ArrayList<>();

        for (Map<?, ?> section : sections) {
            try {
                List<Matcher> match = matchers(strings(section.get("match")));
                if (match.isEmpty()) continue;
                boolean rowsLayout = "rows".equalsIgnoreCase(String.valueOf(section.get("layout")));
                boolean newPage = !Boolean.FALSE.equals(section.get("new-page"));
                List<ShopItem> cells = rowsLayout
                        ? rowCells(remaining, match, matchers(strings(section.get("rows"))), strings(section.get("columns")))
                        : flowCells(remaining, match);
                if (cells.stream().noneMatch(item -> item != null)) continue;
                if (newPage) padToPage(result);
                result.addAll(cells);
                Set<ShopItem> used = new HashSet<>();
                for (ShopItem item : cells) if (item != null) used.add(item);
                remaining.removeAll(used);
            } catch (RuntimeException ex) {
                log.warning("shop.layouts." + categoryId + ": skipped an invalid section (" + ex.getMessage() + ")");
            }
        }
        if (!remaining.isEmpty()) {
            padToPage(result);
            result.addAll(remaining);
        }
        while (!result.isEmpty() && result.get(result.size() - 1) == null) result.remove(result.size() - 1);
        return result;
    }

    private static List<ShopItem> flowCells(List<ShopItem> remaining, List<Matcher> match) {
        List<ShopItem> cells = new ArrayList<>();
        for (ShopItem item : remaining) if (matchesAny(match, item)) cells.add(item);
        return cells;
    }

    private static List<ShopItem> rowCells(List<ShopItem> remaining, List<Matcher> match, List<Matcher> rows, List<String> columns) {
        List<String> columnNames = columns.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
        ShopItem[][] grid = new ShopItem[rows.size()][columnNames.size()];
        for (ShopItem item : remaining) {
            if (!matchesAny(match, item)) continue;
            String id = item.id().toLowerCase(Locale.ROOT);
            String family = ItemGrouping.key(id).family().toLowerCase(Locale.ROOT);
            int column = columnNames.indexOf(family);
            if (column < 0) continue;
            for (int row = 0; row < rows.size(); row++) {
                if (rows.get(row).matches(id, family)) {
                    if (grid[row][column] == null) grid[row][column] = item;
                    break;
                }
            }
        }
        // Drop columns that no row uses so a missing material never leaves a blank stripe.
        List<Integer> active = new ArrayList<>();
        for (int column = 0; column < columnNames.size(); column++) {
            for (ShopItem[] row : grid) if (row[column] != null) { active.add(column); break; }
        }
        List<ShopItem> cells = new ArrayList<>();
        for (ShopItem[] row : grid) {
            List<ShopItem> line = new ArrayList<>();
            for (int column : active) line.add(row[column]);
            if (line.stream().allMatch(item -> item == null)) continue;
            while (line.size() % ROW_WIDTH != 0) line.add(null);
            cells.addAll(line);
        }
        return cells;
    }

    private static boolean matchesAny(List<Matcher> matchers, ShopItem item) {
        String id = item.id().toLowerCase(Locale.ROOT);
        String family = ItemGrouping.key(id).family().toLowerCase(Locale.ROOT);
        for (Matcher matcher : matchers) if (matcher.matches(id, family)) return true;
        return false;
    }

    private static void padToPage(List<ShopItem> list) {
        while (!list.isEmpty() && list.size() % PAGE_SIZE != 0) list.add(null);
    }

    private static List<Matcher> matchers(List<String> patterns) {
        List<Matcher> out = new ArrayList<>();
        for (String raw : patterns) {
            String pattern = raw.trim().toLowerCase(Locale.ROOT);
            if (pattern.isEmpty()) continue;
            if (pattern.startsWith("family:")) { out.add(new Matcher(pattern.substring(7).trim(), null)); continue; }
            StringBuilder regex = new StringBuilder("^");
            for (char c : pattern.toCharArray()) {
                if (c == '*') regex.append(".*");
                else regex.append(Pattern.quote(String.valueOf(c)));
            }
            out.add(new Matcher(null, Pattern.compile(regex.append('$').toString())));
        }
        return out;
    }

    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) for (Object entry : list) out.add(String.valueOf(entry));
        else if (value != null) out.add(String.valueOf(value));
        return out;
    }
}
