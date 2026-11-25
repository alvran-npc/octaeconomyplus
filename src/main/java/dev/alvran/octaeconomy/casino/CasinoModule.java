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
import java.util.stream.Stream;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

/** 21+ self-attestation menu and Vault-only virtual Roulette game. */
public final class CasinoModule implements Module, CommandExecutor, TabCompleter {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<Integer> RED_NUMBERS = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    private record Bet(BigDecimal stake, String selection) { }
    private record SpinSession(Bet bet, int result, String color, BigDecimal stake) { }
    // European wheel order; the ring below shows a rotating window of this sequence.
    private static final int[] WHEEL_ORDER = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    // Clockwise border of a 6x9 inventory, starting at the top-center pointer (slot 4).
    private static final int[] RING_SLOTS = {4, 5, 6, 7, 8, 17, 26, 35, 44, 53, 52, 51, 50, 49, 48, 47, 46, 45, 36, 27, 18, 9, 0, 1, 2, 3};
    private static final int WHEEL_STATUS_SLOT = 31;
    private static final int WHEEL_BACK_SLOT = 40;
    private static final String DIVIDER = "<dark_gray>━━━━━ <gold>✦ <dark_gray>━━━━━";

    private final OctaEconomy plugin;
    private final LangManager lang;
    private final CurrencyManager currencies;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Set<UUID> ageAccepted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingRoulette = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SpinSession> activeSpins = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> animationTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Bet> bets = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> returnTasks = new ConcurrentHashMap<>();
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
        returnTasks.values().forEach(BukkitTask::cancel); returnTasks.clear();
        ageAccepted.clear(); pendingRoulette.clear(); activeSpins.clear(); animationTasks.clear(); bets.clear(); lastResults.clear();
    }
    @Override public void reload() {
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "Casino/settings.yml"));
        gameConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "Casino/Games/roulette.yml"));
        animationConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "Casino/Animation/roulette.yml"));
    }

    public void clearPlayer(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) cancelSpin(player, true); else cancelSpin(id, null, false);
        BukkitTask back = returnTasks.remove(id); if (back != null) back.cancel();
        ageAccepted.remove(id); pendingRoulette.remove(id); bets.remove(id); lastResults.remove(id);
    }

    public void onQuit(Player player) { cancelSpin(player, true); }

    /**
     * Called when the Roulette wheel menu closes. Closing mid-spin reveals the already locked-in
     * result immediately instead of refunding, so a player cannot dodge a losing spin.
     */
    public void onInventoryClose(Player player) {
        UUID id = player.getUniqueId();
        BukkitTask back = returnTasks.remove(id); if (back != null) back.cancel();
        SpinSession session = activeSpins.remove(id);
        if (session == null) return;
        BukkitTask task = animationTasks.remove(id); if (task != null) task.cancel();
        settleSpin(player, session);
    }

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
            case CASINO_WHEEL -> {
                if (slot == WHEEL_BACK_SLOT && !activeSpins.containsKey(player.getUniqueId())) openRoulette(player);
            }
            default -> { }
        }
    }

    private void openAgeGate(Player player) {
        OctaMenuHolder holder = new OctaMenuHolder(player.getUniqueId(), MenuType.CASINO_AGE_GATE, "", 0, 0, -1);
        Inventory inv = Bukkit.createInventory(holder, 27, lang.component("casino-age-title")); holder.attach(inv);
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE);
        List<String> warning = new ArrayList<>();
        warning.add(DIVIDER);
        warning.addAll(wrap(lang.raw("casino-age-warning"), 36));
        warning.add(DIVIDER);
        warning.add("<gray>Self-attestation applies to this session only.");
        inv.setItem(4, button(Material.GOLD_BLOCK, "<gold>✦ <white>21+ Age Confirmation", warning.toArray(new String[0])));
        inv.setItem(11, button(Material.LIME_CONCRETE, lang.raw("casino-age-accept"), "<gray>Continue to Octa Casino", "<yellow>➜ <white>Click to confirm"));
        inv.setItem(15, button(Material.RED_CONCRETE, lang.raw("casino-age-decline"), "<gray>Close this menu", "<yellow>➜ <white>Click to leave"));
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
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE);
        inv.setItem(4, button(Material.GOLD_BLOCK, "<gold>✦ <white>OCTA CASINO <gold>✦", "<gray>Virtual Vault currency only"));
        inv.setItem(11, walletItem(player));
        inv.setItem(13, button(Material.SUNFLOWER, "<gold>✧ Roulette",
                DIVIDER,
                "<aqua>❖ <gray>Wheel <dark_gray>▸ <white>European single-zero",
                "<green>◆ <gray>Straight number <dark_gray>▸ <white>" + straightProfit() + ":1",
                "<green>◇ <gray>Even-money bets <dark_gray>▸ <white>" + evenProfit() + ":1",
                "<yellow>✦ <gray>Currency <dark_gray>▸ <white>Vault",
                DIVIDER,
                "<yellow>➜ <white>Click to enter"));
        inv.setItem(15, button(Material.BOOK, "<yellow>❖ Play responsibly",
                DIVIDER, "<gray>Virtual Vault balance only,", "<gray>never real money.", "<gray>Stop if it stops being fun.", DIVIDER));
        inv.setItem(22, button(Material.BARRIER, "<red>✖ <white>Close"));
        player.openInventory(inv);
    }

    private ItemStack walletItem(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(player);
        CurrencyProvider vault = currencies.get("vault");
        double balance = vault == null ? Double.NaN : vault.balance(player);
        String shown = Double.isFinite(balance) ? "<gold>" + String.format(Locale.US, "%,.2f", balance) + " <gray>vault" : "<dark_gray>Unavailable";
        meta.displayName(text("<gold>✦ <white>Your Wallet"));
        meta.lore(Stream.of(DIVIDER,
                "<aqua>❖ <gray>Player <dark_gray>▸ <white>" + player.getName().replace("<", "\\<"),
                "<green>◇ <gray>Balance <dark_gray>▸ " + shown,
                DIVIDER).map(this::text).toList());
        head.setItemMeta(meta);
        return head;
    }

    private int straightProfit() { return Math.max(1, Math.min(100, gameConfig.getInt("payouts.straight-number-profit", 35))); }
    private int evenProfit() { return Math.max(1, Math.min(100, gameConfig.getInt("payouts.even-money-profit", 1))); }

    private void openRoulette(Player player) {
        UUID id = player.getUniqueId();
        if (!ageAccepted.contains(id)) { pendingRoulette.add(id); openAgeGate(player); return; }
        if (!player.hasPermission("octaeconomy.casino.roulette")) { lang.send(player, "no-permission"); return; }
        Bet bet = bets.computeIfAbsent(id, ignored -> new Bet(chips().get(0), null));
        String selection = bet.selection();
        OctaMenuHolder holder = new OctaMenuHolder(id, MenuType.CASINO_ROULETTE, "", 0, 0, -1);
        Inventory inv = Bukkit.createInventory(holder, 54, lang.component("casino-roulette-title")); holder.attach(inv);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        for (int corner : new int[]{0, 8, 45, 53}) inv.setItem(corner, button(Material.ORANGE_STAINED_GLASS_PANE, " "));

        // Top row: outside bets mirrored around the green zero.
        String[] groupKeys = {"low", "even", "red", "black", "odd", "high"};
        String[] groupLabels = {"<green>◆ Low 1–18", "<white>◆ Even", "<red>◆ Red", "<gray>◆ Black", "<gold>◆ Odd", "<gold>◆ High 19–36"};
        Material[] groupIcons = {Material.LIME_DYE, Material.IRON_INGOT, Material.RED_DYE, Material.BLACK_DYE, Material.GOLD_INGOT, Material.ORANGE_DYE};
        int[] groupSlots = {1, 2, 3, 5, 6, 7};
        for (int i = 0; i < groupKeys.length; i++) {
            boolean on = groupKeys[i].equals(selection);
            inv.setItem(groupSlots[i], glow(button(groupIcons[i], groupLabels[i],
                    "<gray>Payout <dark_gray>▸ <white>" + evenProfit() + ":1",
                    on ? "<green>✔ Selected" : "<yellow>➜ <white>Click to select"), on));
            holder.map(groupSlots[i], "pick:" + groupKeys[i]);
        }
        boolean zeroOn = "number:0".equals(selection);
        ItemStack zero = button(Material.GREEN_CONCRETE, "<green>◉ 0", "<gray>Straight number <dark_gray>▸ <white>" + straightProfit() + ":1",
                zeroOn ? "<green>✔ Selected" : "<yellow>➜ <white>Click to select");
        inv.setItem(4, glow(zero, zeroOn));
        holder.map(4, "pick:number:0");

        // Rows 1-4: the 36 numbers; the stack size shows the number without hovering.
        for (int n = 1; n <= 36; n++) {
            int slot = 8 + n;
            boolean red = RED_NUMBERS.contains(n);
            boolean on = ("number:" + n).equals(selection);
            ItemStack item = button(red ? Material.RED_CONCRETE : Material.BLACK_CONCRETE,
                    (red ? "<red>◉ " : "<gray>◉ ") + n,
                    "<gray>Straight number <dark_gray>▸ <white>" + straightProfit() + ":1",
                    on ? "<green>✔ Selected" : "<yellow>➜ <white>Click to select");
            item.setAmount(n);
            inv.setItem(slot, glow(item, on));
            holder.map(slot, "pick:number:" + n);
        }

        // Bottom row: chips, spin, status cards and navigation; the corners stay decorative.
        List<BigDecimal> chipValues = chips();
        Material[] chipIcons = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.DIAMOND};
        for (int i = 0; i < 3; i++) {
            BigDecimal chip = chipValues.get(i);
            boolean on = chip.compareTo(bet.stake()) == 0;
            inv.setItem(46 + i, glow(button(chipIcons[i], "<gold>● Chip " + format(chip) + " Vault",
                    "<gray>Choose stake",
                    on ? "<green>✔ Selected" : "<yellow>➜ <white>Click to select"), on));
            holder.map(46 + i, "stake:" + chip.toPlainString());
        }
        inv.setItem(49, button(Material.LEVER, "<green>✦ SPIN",
                DIVIDER,
                "<gold>● <gray>Stake <dark_gray>▸ <white>" + format(bet.stake()) + " Vault",
                "<aqua>❖ <gray>Bet <dark_gray>▸ <white>" + label(selection),
                DIVIDER,
                selection == null ? "<red>✖ Pick a bet first" : "<yellow>➜ <white>Click to spin the wheel"));
        inv.setItem(50, button(Material.PAPER, "<yellow>❖ Current bet",
                "<gray>Stake <dark_gray>▸ <white>" + format(bet.stake()) + " Vault",
                "<gray>Selection <dark_gray>▸ <white>" + label(selection)));
        inv.setItem(51, button(Material.SPYGLASS, "<aqua>◇ Last result", "<gray>" + lastResults.getOrDefault(id, "No spins yet")));
        inv.setItem(52, button(Material.ARROW, "<yellow>« <white>Back to Casino"));
        player.openInventory(inv);
    }

    private void handleRoulette(Player player, int slot, OctaMenuHolder holder) {
        UUID id = player.getUniqueId();
        if (slot == 52) { openMain(player); return; }
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
        if (!animationConfig.getBoolean("enabled", true)) {
            activeSpins.remove(id);
            settleSpin(player, session);
            return;
        }
        openWheel(player, session);
    }

    /** Opens the dedicated wheel menu; the outcome is already fixed, the animation is cosmetic only. */
    private void openWheel(Player player, SpinSession session) {
        UUID id = player.getUniqueId();
        OctaMenuHolder holder = new OctaMenuHolder(id, MenuType.CASINO_WHEEL, "", 0, 0, -1);
        Inventory inv = Bukkit.createInventory(holder, 54, mini.deserialize("<gold>✦ <white>Roulette Wheel <gold>✦")); holder.attach(inv);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(13, button(Material.ARROW, "<gold>▲ <white>Winning pocket", "<gray>The pocket at the top center", "<gray>decides your bet."));
        inv.setItem(20, button(Material.PAPER, "<yellow>❖ Your bet", "<gray>" + label(session.bet().selection())));
        inv.setItem(24, button(Material.GOLD_NUGGET, "<gold>● Stake", "<gray>" + format(session.stake()) + " Vault"));
        inv.setItem(WHEEL_STATUS_SLOT, button(Material.CLOCK, "<gold>✦ Wheel in motion", "<gray>Good luck!"));
        player.openInventory(inv);
        beginWheelAnimation(player, inv, session);
    }

    private void beginWheelAnimation(Player player, Inventory inv, SpinSession session) {
        UUID id = player.getUniqueId();
        final int startInterval = clamp(animationConfig.getInt("start-interval-ticks", 1), 1, 5);
        final int maxInterval = Math.max(startInterval, Math.min(12, animationConfig.getInt("maximum-interval-ticks", 8)));
        final int slowdown = clamp(animationConfig.getInt("slowdown-steps", 14), 4, 30);
        final int laps = clamp(animationConfig.getInt("laps", 1), 0, 3);
        final int pockets = WHEEL_ORDER.length;
        final int startIndex = RANDOM.nextInt(pockets);
        int travel = Math.floorMod(pocketIndex(session.result()) - startIndex, pockets) + pockets * laps;
        while (travel < slowdown + 6) travel += pockets;
        final int totalSteps = travel;
        paintWheel(inv, startIndex);

        BukkitTask task = new BukkitRunnable() {
            private int step;
            private int tick;
            private int interval = startInterval;

            @Override public void run() {
                if (!player.isOnline() || !activeSpins.containsKey(id)) {
                    cancel();
                    animationTasks.remove(id);
                    return;
                }
                if (++tick < interval) return;
                tick = 0;
                step++;
                paintWheel(inv, (startIndex + step) % pockets);
                wheelTick(player, step, totalSteps);
                if (step >= totalSteps) {
                    cancel();
                    animationTasks.remove(id);
                    finishWheel(player, inv, session);
                    return;
                }
                int remaining = totalSteps - step;
                interval = remaining > slowdown ? startInterval
                        : Math.min(maxInterval, startInterval + (int) Math.ceil((double) (slowdown - remaining + 1) * (maxInterval - startInterval) / slowdown));
            }
        }.runTaskTimer(plugin, 1L, 1L);
        animationTasks.put(id, task);
    }

    private void finishWheel(Player player, Inventory inv, SpinSession session) {
        UUID id = player.getUniqueId();
        activeSpins.remove(id);
        BigDecimal payout = settleSpin(player, session);
        boolean won = wins(session.bet().selection(), session.result());
        if (won && payout.signum() > 0) {
            inv.setItem(WHEEL_STATUS_SLOT, glow(button(Material.EMERALD, "<green>✔ YOU WON",
                    DIVIDER, "<gray>Result <dark_gray>▸ <white>" + session.result() + " " + session.color(),
                    "<gray>Payout <dark_gray>▸ <gold>" + format(payout) + " Vault", DIVIDER), true));
        } else if (won) {
            inv.setItem(WHEEL_STATUS_SLOT, button(Material.YELLOW_DYE, "<yellow>✦ Payout refunded",
                    "<gray>Result <dark_gray>▸ <white>" + session.result() + " " + session.color()));
        } else {
            inv.setItem(WHEEL_STATUS_SLOT, button(Material.REDSTONE, "<red>✖ No luck this time",
                    DIVIDER, "<gray>Result <dark_gray>▸ <white>" + session.result() + " " + session.color(),
                    "<gray>Lost <dark_gray>▸ <red>" + format(session.stake()) + " Vault", DIVIDER));
        }
        inv.setItem(WHEEL_BACK_SLOT, button(Material.ARROW, "<yellow>« <white>Back to table", "<gray>Returning automatically..."));
        if (plugin.getConfig().getBoolean("sound.enabled", true)) {
            player.playSound(player.getLocation(), won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 1.0f);
        }
        final int hold = clamp(animationConfig.getInt("result-hold-ticks", 60), 10, 200);
        BukkitTask back = new BukkitRunnable() {
            @Override public void run() {
                returnTasks.remove(id);
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() instanceof OctaMenuHolder open
                        && open.type() == MenuType.CASINO_WHEEL) openRoulette(player);
            }
        }.runTaskLater(plugin, hold);
        returnTasks.put(id, back);
    }

    /** Draws the rotating pocket ring; the pocket in RING_SLOTS[0] sits under the fixed pointer. */
    private void paintWheel(Inventory inv, int index) {
        for (int k = 0; k < RING_SLOTS.length; k++) {
            int number = WHEEL_ORDER[(index + k) % WHEEL_ORDER.length];
            inv.setItem(RING_SLOTS[k], k == 0 ? pointerPocket(number) : pocket(number));
        }
        int current = WHEEL_ORDER[index % WHEEL_ORDER.length];
        ItemStack center = button(pocketBlock(current), pocketTag(current) + "◉ " + current + " " + colorName(current),
                "<gray>Current pocket");
        center.setAmount(Math.max(1, current));
        inv.setItem(22, center);
    }

    private ItemStack pocket(int number) {
        Material pane = number == 0 ? Material.GREEN_STAINED_GLASS_PANE
                : RED_NUMBERS.contains(number) ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        return button(pane, pocketTag(number) + number);
    }

    private ItemStack pointerPocket(int number) {
        ItemStack item = button(pocketBlock(number), pocketTag(number) + "◉ " + number + " " + colorName(number), "<gold>▼ Pointer pocket");
        item.setAmount(Math.max(1, number));
        return glow(item, true);
    }

    private Material pocketBlock(int number) {
        return number == 0 ? Material.GREEN_CONCRETE : RED_NUMBERS.contains(number) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
    }

    private String pocketTag(int number) { return number == 0 ? "<green>" : RED_NUMBERS.contains(number) ? "<red>" : "<gray>"; }
    private String colorName(int number) { return number == 0 ? "Green" : RED_NUMBERS.contains(number) ? "Red" : "Black"; }

    private int pocketIndex(int number) {
        for (int i = 0; i < WHEEL_ORDER.length; i++) if (WHEEL_ORDER[i] == number) return i;
        return 0;
    }

    private void wheelTick(Player player, int step, int totalSteps) {
        if (!plugin.getConfig().getBoolean("sound.enabled", true)) return;
        float pitch = 0.8f + 1.2f * Math.min(step, totalSteps) / Math.max(1, totalSteps);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, pitch);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    /** Pays out or records the loss; returns the payout credited (zero for a loss or failed payout). */
    private BigDecimal settleSpin(Player player, SpinSession session) {
        UUID id = player.getUniqueId();
        CurrencyProvider vault = currencies.get("vault");
        if (vault == null) {
            lang.send(player, "casino-round-refund");
            plugin.getLogger().warning("Vault provider disappeared during Roulette spin for " + id + ". Stake recovery requires admin review.");
            return BigDecimal.ZERO;
        }
        int number = session.result();
        String color = session.color();
        lastResults.put(id, number + " " + color);
        BigDecimal paid = BigDecimal.ZERO;
        if (wins(session.bet().selection(), number)) {
            int profit = session.bet().selection().startsWith("number:") ? straightProfit() : evenProfit();
            BigDecimal payout = session.stake().multiply(BigDecimal.valueOf(profit + 1L)).setScale(2, RoundingMode.HALF_UP);
            if (vault.deposit(player, payout.doubleValue())) {
                paid = payout;
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
        return paid;
    }

    private void cancelSpin(Player player, boolean refund) {
        cancelSpin(player.getUniqueId(), player, refund);
    }

    private void cancelSpin(UUID id, Player player, boolean refund) {
        BukkitTask back = returnTasks.remove(id);
        if (back != null) back.cancel();
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

    private ItemStack glow(ItemStack item, boolean on) {
        if (!on) return item;
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Word-wraps a configured line so long warnings fit a tooltip; leading color tags repeat on every line. */
    private List<String> wrap(String raw, int width) {
        List<String> lines = new ArrayList<>();
        if (raw == null || raw.isBlank()) return lines;
        String trimmed = raw.trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^((?:<[^<>]+>)*)(.*)$", java.util.regex.Pattern.DOTALL).matcher(trimmed);
        String prefix = "";
        String body = trimmed;
        if (matcher.matches()) { prefix = matcher.group(1); body = matcher.group(2); }
        StringBuilder line = new StringBuilder();
        for (String word : body.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (line.length() > 0 && line.length() + 1 + word.length() > width) { lines.add(prefix + line); line.setLength(0); }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(prefix + line);
        return lines;
    }

    /** Fills the inventory and draws a one-pane border around it. */
    private void frame(Inventory inv, Material filler, Material border) {
        int rows = inv.getSize() / 9;
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9, col = slot % 9;
            boolean edge = row == 0 || row == rows - 1 || col == 0 || col == 8;
            inv.setItem(slot, button(edge ? border : filler, " "));
        }
    }

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
