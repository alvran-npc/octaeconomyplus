/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.casino;

import dev.alvran.octaeconomy.OctaEconomy;
import dev.alvran.octaeconomy.core.LangManager;
import dev.alvran.octaeconomy.core.Module;
import dev.alvran.octaeconomy.gui.MenuType;
import dev.alvran.octaeconomy.gui.OctaMenuHolder;
import dev.alvran.octaeconomy.hook.CurrencyManager;
import dev.alvran.octaeconomy.hook.CurrencyProvider;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

/** 21+ self-attestation menu and Vault-only virtual Roulette game. */
public final class CasinoModule implements Module, CommandExecutor, TabCompleter {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<Integer> RED_NUMBERS = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    private record Bet(BigDecimal stake, String selection) { }
    private record SpinSession(Bet bet, int result, String color, BigDecimal stake) { }

    private final OctaEconomy plugin;
    private final LangManager lang;
    private final CurrencyManager currencies;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Set<UUID> ageAccepted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingRoulette = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SpinSession> activeSpins = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> animationTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Bet> bets = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastResults = new ConcurrentHashMap<>();
    private YamlConfiguration config;
    private YamlConfiguration gameConfig;
    private YamlConfiguration animationConfig;

    public CasinoModule(OctaEconomy plugin, LangManager lang, CurrencyManager currencies) {
        this.plugin = plugin; this.lang = lang; this.currencies = currencies;
    }

    @Override public String name() { return "Casino"; }
    @Override public void enable() { reload(); }
    @Override public void disable() {
        for (UUID id : new ArrayList<>(activeSpins.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) cancelSpin(player, true);
            else cancelSpin(id, null, false);
        }
        ageAccepted.clear(); pendingRoulette.clear(); activeSpins.clear(); animationTasks.clear(); bets.clear(); lastResults.clear();
    }
    @Override public void reload() {
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "casino/settings.yml"));
        gameConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "casino/games/roulette.yml"));
        animationConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "casino/animation/roulette.yml"));
    }

    public void clearPlayer(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) cancelSpin(player, true); else cancelSpin(id, null, false);
        ageAccepted.remove(id); pendingRoulette.remove(id); bets.remove(id); lastResults.remove(id);
    }

    public void onQuit(Player player) { cancelSpin(player, true); }

    public void onInventoryClose(Player player) { cancelSpin(player, true); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { lang.send(sender, "players-only"); return true; }
        if (!player.hasPermission("octaeconomy.casino.use")) { lang.send(player, "no-permission"); return true; }
        if (!config.getBoolean("enabled", true) || currencies.get("vault") == null) { lang.send(player, "casino-disabled"); return true; }
        boolean direct = args.length == 1 && args[0].equalsIgnoreCase("roulette");
        if (args.length > 1 || (args.length == 1 && !direct)) { lang.send(player, "usage", Map.of("usage", "/casino [roulette]")); return true; }
        if (direct && !player.hasPermission("octaeconomy.casino.roulette")) { lang.send(player, "no-permission"); return true; }
        if (!ageAccepted.contains(player.getUniqueId())) {
            if (direct) pendingRoulette.add(player.getUniqueId()); else pendingRoulette.remove(player.getUniqueId());
            openAgeGate(player);
        } else if (direct) openRoulette(player); else openMain(player);
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("roulette") : List.of();
    }

    public void handleClick(Player player, InventoryClickEvent event, OctaMenuHolder holder) {
        if (!config.getBoolean("enabled", true)) { player.closeInventory(); return; }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (holder.type() != MenuType.CASINO_AGE_GATE && !ageAccepted.contains(player.getUniqueId())) { openAgeGate(player); return; }
        if (holder.type() == MenuType.CASINO_ROULETTE && activeSpins.containsKey(player.getUniqueId())) return;
        switch (holder.type()) {
            case CASINO_AGE_GATE -> handleAgeGate(player, slot);
            case CASINO_MAIN -> {
                if (slot == 13) {
                    if (!player.hasPermission("octaeconomy.casino.roulette")) lang.send(player, "no-permission");
                    else openRoulette(player);
                } else if (slot == 22) player.closeInventory();
            }
            case CASINO_ROULETTE -> handleRoulette(player, slot, holder);
            default -> { }
        }
    }

    private void openAgeGate(Player player) {
        OctaMenuHolder holder = new OctaMenuHolder(player.getUniqueId(), MenuType.CASINO_AGE_GATE, "", 0, 0, -1);
        Inventory inv = Bukkit.createInventory(holder, 27, lang.component("casino-age-title")); holder.attach(inv);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, button(Material.GOLD_BLOCK, "<gold>✦ <white>21+ Age Confirmation", lang.raw("casino-age-warning"), "<gray>Self-attestation applies to this session only."));
        inv.setItem(11, button(Material.LIME_CONCRETE, lang.raw("casino-age-accept"), "<gray>Continue to Octa Casino"));
        inv.setItem(15, button(Material.RED_CONCRETE, lang.raw("casino-age-decline"), "<gray>Close this menu"));
        player.openInventory(inv);
    }

    private void handleAgeGate(Player player, int slot) {
        if (slot == 11) {
            ageAccepted.add(player.getUniqueId()); lang.send(player, "casino-age-confirmed");
            if (pendingRoulette.remove(player.getUniqueId())) openRoulette(player); else openMain(player);
        } else if (slot == 15) {
            pendingRoulette.remove(player.getUniqueId()); lang.send(player, "casino-age-denied"); player.closeInventory();
        }
    }

    private void openMain(Player player) {
        if (!ageAccepted.contains(player.getUniqueId())) { openAgeGate(player); return; }
        OctaMenuHolder holder = new OctaMenuHolder(player.getUniqueId(), MenuType.CASINO_MAIN, "", 0, 0, -1);
        Inventory inv = Bukkit.createInventory(holder, 27, lang.component("casino-main-title")); holder.attach(inv);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(13, button(Material.SUNFLOWER, "<gold>✧ Roulette", "<gray>Single-zero European wheel", "<gray>Pick a number or an even-money bet.", "<yellow>Currency: Vault", "<green>Click to enter"));
        inv.setItem(22, button(Material.BARRIER, "<red>Close"));
        player.openInventory(inv);
    }

    private void openRoulette(Player player) {
        UUID id = player.getUniqueId();
        if (!ageAccepted.contains(id)) { pendingRoulette.add(id); openAgeGate(player); return; }
        if (!player.hasPermission("octaeconomy.casino.roulette")) { lang.send(player, "no-permission"); return; }
        Bet bet = bets.computeIfAbsent(id, ignored -> new Bet(chips().get(0), null));
        OctaMenuHolder holder = new OctaMenuHolder(id, MenuType.CASINO_ROULETTE, "", 0, 0, -1);
        Inventory inv = Bukkit.createInventory(holder, 54, lang.component("casino-roulette-title")); holder.attach(inv);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);

        String[] groupKeys = {"red", "black", "even", "odd", "low", "high"};
        String[] groupLabels = {"<red>Red", "<gray>Black", "<white>Even", "<gold>Odd", "<green>Low 1–18", "<gold>High 19–36"};
        Material[] groupIcons = {Material.RED_DYE, Material.BLACK_DYE, Material.IRON_INGOT, Material.GOLD_INGOT, Material.LIME_DYE, Material.ORANGE_DYE};
        int[] groupSlots = {0, 1, 2, 3, 5, 6};
        for (int i = 0; i < groupKeys.length; i++) {
            int slot = groupSlots[i];
            inv.setItem(slot, button(groupIcons[i], groupLabels[i], "<gray>Profit 1:1", "<yellow>Click to select"));
            holder.map(slot, "pick:" + groupKeys[i]);
        }
        inv.setItem(4, button(Material.GREEN_STAINED_GLASS_PANE, "<green>0", "<gray>Straight number • Profit 35:1", "<yellow>Click to select"));
        holder.map(4, "pick:number:0");
        for (int n = 1; n <= 36; n++) {
            int slot = 8 + n; // slots 9-44 form a square 4x9 pocket display
            boolean red = RED_NUMBERS.contains(n);
            inv.setItem(slot, button(red ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE,
                    (red ? "<red>" : "<gray>") + n, "<gray>Straight number • Profit 35:1", "<yellow>Click to select"));
            holder.map(slot, "pick:number:" + n);
        }
        List<BigDecimal> chipValues = chips();
        Material[] chipIcons = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.DIAMOND};
        for (int i = 0; i < 3; i++) {
            BigDecimal chip = chipValues.get(i);
            inv.setItem(45 + i, button(chipIcons[i], "<gold>Chip " + format(chip) + " Vault", "<gray>Choose stake"));
            holder.map(45 + i, "stake:" + chip.toPlainString());
        }
        inv.setItem(48, button(Material.PAPER, "<yellow>Current bet", "<gray>Stake: <white>" + format(bet.stake()) + " Vault", "<gray>Selection: <white>" + label(bet.selection())));
        inv.setItem(49, button(Material.LEVER, "<green>✦ SPIN", "<gray>Debit stake and spin once", "<yellow>Click to play"));
        inv.setItem(50, button(Material.SPYGLASS, "<aqua>Last result", "<gray>" + lastResults.getOrDefault(id, "No spins yet")));
        inv.setItem(53, button(Material.ARROW, "<yellow>Back to Casino"));
        player.openInventory(inv);
    }

    private void handleRoulette(Player player, int slot, OctaMenuHolder holder) {
        UUID id = player.getUniqueId();
        if (slot == 53) { openMain(player); return; }
        String key = holder.key(slot);
        if (key == null) { if (slot == 49) spin(player); return; }
        Bet old = bets.getOrDefault(id, new Bet(chips().get(0), null));
        if (key.startsWith("stake:")) {
            try { bets.put(id, new Bet(new BigDecimal(key.substring(6)), old.selection())); openRoulette(player); }
            catch (NumberFormatException ignored) { }
        } else if (key.startsWith("pick:")) {
            bets.put(id, new Bet(old.stake(), key.substring(5))); openRoulette(player);
        }
    }

    private void spin(Player player) {
        UUID id = player.getUniqueId();
        if (activeSpins.containsKey(id)) return;
        if (!ageAccepted.contains(id)) { pendingRoulette.add(id); openAgeGate(player); return; }
        CurrencyProvider vault = currencies.get("vault");
        if (vault == null) { lang.send(player, "casino-disabled"); player.closeInventory(); return; }
        Bet bet = bets.get(id);
        if (bet == null || bet.selection() == null) { lang.send(player, "casino-bet-invalid"); return; }
        BigDecimal min = BigDecimal.valueOf(gameConfig.getDouble("minimum-bet", config.getDouble("minimum-bet", 100.0)));
        BigDecimal max = BigDecimal.valueOf(gameConfig.getDouble("maximum-bet", config.getDouble("maximum-bet", 100000.0)));
        BigDecimal stake = bet.stake().setScale(2, RoundingMode.HALF_UP);
        if (stake.compareTo(min) < 0 || stake.compareTo(max) > 0 || !Double.isFinite(stake.doubleValue())) {
            lang.send(player, "casino-bet-range", Map.of("minimum", format(min), "maximum", format(max))); return;
        }
        if (!vault.withdraw(player, stake.doubleValue())) { lang.send(player, "casino-insufficient-funds"); return; }

        int result = RANDOM.nextInt(37); // Outcome is fixed before the cosmetic wheel animation begins.
        String color = result == 0 ? "Green" : RED_NUMBERS.contains(result) ? "Red" : "Black";
        SpinSession session = new SpinSession(bet, result, color, stake);
        activeSpins.put(id, session);
        lang.send(player, "casino-spin-start");
        beginRouletteAnimation(player, session);
    }

    private void beginRouletteAnimation(Player player, SpinSession session) {
        UUID id = player.getUniqueId();
        if (!animationConfig.getBoolean("enabled", true)) {
            activeSpins.remove(id);
            settleSpin(player, session);
            return;
        }
        final int totalFrames = Math.max(12, Math.min(100, animationConfig.getInt("frames", 42)));
        final int startInterval = Math.max(1, Math.min(5, animationConfig.getInt("start-interval-ticks", 1)));
        final int increaseEvery = Math.max(1, Math.min(20, animationConfig.getInt("interval-increase-every", 5)));
        final int increaseTicks = Math.max(1, Math.min(4, animationConfig.getInt("interval-increase-ticks", 1)));
        final int maxInterval = Math.max(startInterval, Math.min(12, animationConfig.getInt("maximum-interval-ticks", 8)));
        final int displaySlot = Math.max(0, Math.min(53, animationConfig.getInt("display-slot", 4)));

        BukkitTask task = new BukkitRunnable() {
            private int frame;
            private int tick;
            private int interval = startInterval;
            private int cursor;

            @Override public void run() {
                if (!player.isOnline() || !activeSpins.containsKey(id)) {
                    cancel();
                    animationTasks.remove(id);
                    return;
                }
                if (++tick < interval) return;
                tick = 0;
                frame++;
                int displayNumber = frame >= totalFrames ? session.result() : (cursor * 11 + frame * 7) % 37;
                cursor++;
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof OctaMenuHolder holder
                        && holder.type() == MenuType.CASINO_ROULETTE) {
                    player.getOpenInventory().getTopInventory().setItem(displaySlot, rouletteIndicator(displayNumber, frame, totalFrames));
                    player.getOpenInventory().getTopInventory().setItem(50, button(Material.CLOCK,
                            "<gold>⌛ Wheel in motion", "<gray>Decelerating", "<dark_gray>Frame " + frame + " / " + totalFrames));
                }
                if (frame >= totalFrames) {
                    cancel();
                    animationTasks.remove(id);
                    activeSpins.remove(id);
                    settleSpin(player, session);
                    return;
                }
                if (frame % increaseEvery == 0) interval = Math.min(maxInterval, interval + increaseTicks);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        animationTasks.put(id, task);
        player.getOpenInventory().getTopInventory().setItem(displaySlot, rouletteIndicator(0, 0, totalFrames));
    }

    private ItemStack rouletteIndicator(int number, int frame, int totalFrames) {
        Material material = number == 0 ? Material.GREEN_STAINED_GLASS_PANE
                : RED_NUMBERS.contains(number) ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        return button(material, (number == 0 ? "<green>" : RED_NUMBERS.contains(number) ? "<red>" : "<gray>")
                        + "◉  " + number,
                "<gold>✦ Roulette Wheel", "<gray>Rolling " + Math.min(frame, totalFrames) + " / " + totalFrames);
    }

    private void settleSpin(Player player, SpinSession session) {
        UUID id = player.getUniqueId();
        CurrencyProvider vault = currencies.get("vault");
        if (vault == null) {
            lang.send(player, "casino-round-refund");
            plugin.getLogger().warning("Vault provider disappeared during Roulette spin for " + id + ". Stake recovery requires admin review.");
            return;
        }
        int number = session.result();
        String color = session.color();
        lastResults.put(id, number + " " + color);
        if (wins(session.bet().selection(), number)) {
            int profit = session.bet().selection().startsWith("number:")
                    ? Math.max(1, Math.min(100, gameConfig.getInt("payouts.straight-number-profit", 35)))
                    : Math.max(1, Math.min(100, gameConfig.getInt("payouts.even-money-profit", 1)));
            BigDecimal payout = session.stake().multiply(BigDecimal.valueOf(profit + 1L)).setScale(2, RoundingMode.HALF_UP);
            if (vault.deposit(player, payout.doubleValue())) {
                lang.send(player, "casino-round-win", Map.of("number", number, "color", color, "payout", format(payout)));
            } else {
                boolean refunded = vault.deposit(player, session.stake().doubleValue());
                if (!refunded) plugin.getLogger().log(Level.SEVERE, "Roulette payout and refund both failed for " + id + "; stake=" + session.stake());
                lang.send(player, "casino-round-refund");
            }
        } else {
            lang.send(player, "casino-round-loss", Map.of("number", number, "color", color));
        }
        bets.put(id, new Bet(session.stake(), null));
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof OctaMenuHolder holder
                && holder.type() == MenuType.CASINO_ROULETTE) openRoulette(player);
    }

    private void cancelSpin(Player player, boolean refund) {
        cancelSpin(player.getUniqueId(), player, refund);
    }

    private void cancelSpin(UUID id, Player player, boolean refund) {
        BukkitTask task = animationTasks.remove(id);
        if (task != null) task.cancel();
        SpinSession session = activeSpins.remove(id);
        if (session == null || !refund) return;
        CurrencyProvider vault = currencies.get("vault");
        boolean refunded = vault != null && player != null && vault.deposit(player, session.stake().doubleValue());
        if (refunded) lang.send(player, "casino-round-refund");
        else plugin.getLogger().log(Level.SEVERE, "Could not refund cancelled Roulette spin for " + id + "; stake=" + session.stake());
    }

    private boolean wins(String selection, int result) {
        if (selection.startsWith("number:")) {
            try { return Integer.parseInt(selection.substring(7)) == result; }
            catch (NumberFormatException ignored) { return false; }
        }
        return switch (selection) {
            case "red" -> RED_NUMBERS.contains(result);
            case "black" -> result != 0 && !RED_NUMBERS.contains(result);
            case "even" -> result != 0 && result % 2 == 0;
            case "odd" -> result != 0 && result % 2 == 1;
            case "low" -> result >= 1 && result <= 18;
            case "high" -> result >= 19 && result <= 36;
            default -> false;
        };
    }

    private List<BigDecimal> chips() {
        List<BigDecimal> result = new ArrayList<>();
        for (Object raw : config.getList("chip-values", List.of(100, 500, 1000))) {
            try {
                BigDecimal value = new BigDecimal(String.valueOf(raw));
                if (value.signum() > 0 && Double.isFinite(value.doubleValue())) result.add(value.setScale(2, RoundingMode.HALF_UP));
            } catch (NumberFormatException ignored) { }
        }
        if (result.isEmpty()) result.add(BigDecimal.valueOf(100));
        while (result.size() < 3) result.add(result.get(result.size() - 1));
        return result.subList(0, 3);
    }

    private String label(String selection) {
        if (selection == null) return "Not selected";
        if (selection.startsWith("number:")) return "Number " + selection.substring(7);
        return switch (selection) {
            case "red" -> "Red"; case "black" -> "Black"; case "even" -> "Even";
            case "odd" -> "Odd"; case "low" -> "Low"; case "high" -> "High";
            default -> "Not selected";
        };
    }

    private String format(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private ItemStack button(Material type, String name, String... lore) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(this::text).toList());
        item.setItemMeta(meta);
        return item;
    }

    private Component text(String value) { return mini.deserialize(value).decoration(TextDecoration.ITALIC, false); }

    private void fill(Inventory inv, Material type) {
        ItemStack filler = button(type, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler.clone());
    }
}
