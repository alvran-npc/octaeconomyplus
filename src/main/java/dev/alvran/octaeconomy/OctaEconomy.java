/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy;

import dev.alvran.octaeconomy.auction.AuctionModule;
import dev.alvran.octaeconomy.integration.WebhookModule;
import dev.alvran.octaeconomy.core.ConfigManager;
import dev.alvran.octaeconomy.core.LangManager;
import dev.alvran.octaeconomy.core.ModuleManager;
import dev.alvran.octaeconomy.core.TaskScheduler;
import dev.alvran.octaeconomy.gui.MenuListener;
import dev.alvran.octaeconomy.gui.MenuType;
import dev.alvran.octaeconomy.gui.OctaMenuHolder;
import dev.alvran.octaeconomy.hook.CurrencyManager;
import dev.alvran.octaeconomy.hook.MMOItemsHook;
import dev.alvran.octaeconomy.hook.SmartSpawnerHook;
import dev.alvran.octaeconomy.shop.ShopModule;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class OctaEconomy extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private ConfigManager configManager;
    private LangManager lang;
    private TaskScheduler scheduler;
    private ModuleManager modules;
    private CurrencyManager currencies;
    private MMOItemsHook mmoItems;
    private SmartSpawnerHook smartSpawner;
    private ShopModule shop;
    private AuctionModule auction;
    private WebhookModule webhooks;

    @Override public void onEnable() {
        getLogger().info("OctaEconomy v" + getDescription().getVersion() + " by Alvran");
        configManager = new ConfigManager(this); configManager.ensureFiles(); reloadConfig();
        lang = new LangManager(this); lang.reload();
        scheduler = new TaskScheduler(this);
        mmoItems = new MMOItemsHook(this); mmoItems.setup();
        smartSpawner = new SmartSpawnerHook(this); smartSpawner.setup();
        currencies = new CurrencyManager(this); currencies.setup();
        modules = new ModuleManager(this);
        shop = new ShopModule(this, lang, currencies, scheduler, mmoItems, smartSpawner);
        auction = new AuctionModule(this, lang, currencies, scheduler);
        webhooks = new WebhookModule(this);
        modules.register(shop); modules.register(auction); modules.register(webhooks);
        modules.enableAll();
        Bukkit.getPluginManager().registerEvents(new MenuListener(shop, auction), this);
        Bukkit.getPluginManager().registerEvents(this, this);
        register("shop", (sender, command, label, args) -> shop.onShopCommand(sender, command, label, args), (sender, command, alias, args) -> shop.tabCompleteShop(args));
        register("sellgui", shop::onSellGuiCommand, (sender, command, alias, args) -> List.of());
        register("sell", shop::onSellCommand, (sender, command, alias, args) -> shop.tabCompleteSell(args));
        register("ah", auction, auction);
        register("octaeconomy", this, this);
        register("donate", (sender, command, label, args) -> { sendDonationLink(sender); return true; }, (sender, command, alias, args) -> List.of());
        for (Player player : Bukkit.getOnlinePlayers()) Bukkit.getScheduler().runTask(this, () -> auction.onJoin(player));
    }

    private void register(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command != null) { command.setExecutor(executor); command.setTabCompleter(completer); }
    }

    @Override public void onDisable() {
        if (Bukkit.getOnlinePlayers().size() > 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof OctaMenuHolder holder && holder.type() == MenuType.SELL) player.closeInventory();
            }
        }
        if (modules != null) modules.disableAll();
        if (scheduler != null) scheduler.shutdown();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { lang.send(sender, "help"); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "version" -> lang.send(sender, "version", java.util.Map.of("version", getDescription().getVersion()));
            case "donate" -> sendDonationLink(sender);
            case "reload" -> {
                if (!sender.hasPermission("octaeconomy.admin")) { lang.send(sender, "no-permission"); return true; }
                reloadConfig(); configManager.ensureFiles(); lang.reload(); mmoItems.setup(); smartSpawner.setup(); currencies.setup(); modules.reloadAll(); lang.send(sender, "reload");
            }
            case "debug" -> {
                if (!sender.hasPermission("octaeconomy.admin")) { lang.send(sender, "no-permission"); return true; }
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>OctaEconomy debug <gray>| shop item cache: <white>" + shop.manager().allItems().size() + " <gray>| categories: <white>" + shop.manager().categories().size()));
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Auction database ready: <white>" + auction.isDatabaseReady()));
            }
            default -> lang.send(sender, "help");
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("octaeconomy.admin")) return args.length == 1 ? List.of("version", "help") : List.of();
        return args.length == 1 ? List.of("reload", "version", "help", "debug", "donate") : List.of();
    }

    private void sendDonationLink(CommandSender sender) {
        String url = getConfig().getString("donation-link", "https://saweria.co/AlvranNPC");
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gold><bold>Support OctaEconomy</bold></gold> <gray>· <yellow>Click here to support Alvran")
                .clickEvent(ClickEvent.openUrl(url)));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Donation page: <white>" + MiniMessage.miniMessage().escapeTags(url))
                .clickEvent(ClickEvent.openUrl(url)));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { Bukkit.getScheduler().runTask(this, () -> auction.onJoin(event.getPlayer())); }
    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof OctaMenuHolder holder && holder.type() == MenuType.SELL) event.setCancelled(true);
    }

    public CurrencyManager currencies() { return currencies; }
    public LangManager language() { return lang; }
}
