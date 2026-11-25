/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Creates the organized data tree and performs non-destructive migrations from older layouts. */
public final class ConfigManager {
    private static final List<String> CATEGORIES = List.of("blocks", "colored_blocks", "decoration", "redstone", "farming_food", "misc", "mobs_drop", "mmoitems", "vanilla_tools", "spawners");
    private final JavaPlugin plugin;
    private final File root;
    public ConfigManager(JavaPlugin plugin) { this.plugin=plugin; this.root=plugin.getDataFolder(); }

    public void ensureFiles() {
        root.mkdirs();
        for(String dir:List.of("Data","Lang/Message","Shops/Settings","Shops/Category","Auctions","Casino/Animation","Casino/Games","GUI","integrations","logs")) new File(root,dir).mkdirs();
        migrateLegacyFiles();
        for(String p:List.of("config.yml","Data/PlayerData.yml","Auctions/auctions.yml","Auctions/theme.yml","Casino/settings.yml","Casino/Animation/roulette.yml","Casino/Games/roulette.yml","GUI/Shop.yml","GUI/Auction.yml","Shops/Settings/discount.yml","integrations/discordsrv.yml","integrations/discordwebhook.yml","integrations/saweria-tebex.yml")) saveIfMissing(p);
        for(String lang:List.of("en","id","zh")) saveIfMissing("Lang/Message/"+lang+".yml");
        for(String category:CATEGORIES) saveIfMissing("Shops/Category/"+category+".yml");
        for(String p:List.of("config.yml","Data/PlayerData.yml","Auctions/auctions.yml","Auctions/theme.yml","Casino/settings.yml","Casino/Animation/roulette.yml","Casino/Games/roulette.yml","GUI/Shop.yml","GUI/Auction.yml","Shops/Settings/discount.yml","integrations/discordsrv.yml","integrations/discordwebhook.yml","integrations/saweria-tebex.yml")) mergeDefaults(p);
        normalizeDatabasePath();
    }

    private void migrateLegacyFiles() {
        for(String[] pair:List.of(
                new String[]{"auctions/auctions.yml","Auctions/auctions.yml"},
                new String[]{"auctions/theme.yml","Auctions/theme.yml"},
                new String[]{"casino/settings.yml","Casino/settings.yml"},
                new String[]{"casino/animation/roulette.yml","Casino/Animation/roulette.yml"},
                new String[]{"casino/games/roulette.yml","Casino/Games/roulette.yml"},
                new String[]{"data/Playerdata.yml","Data/PlayerData.yml"},
                new String[]{"gui/shop.yml","GUI/Shop.yml"},
                new String[]{"gui/auction.yml","GUI/Auction.yml"},
                new String[]{"shops/discounts.yml","Shops/Settings/discount.yml"},
                new String[]{"discounts.yml","Shops/Settings/discount.yml"},
                new String[]{"auction.yml","Auctions/auctions.yml"},
                new String[]{"gui.yml","GUI/Shop.yml"},
                new String[]{"casino.yml","Casino/settings.yml"})) moveIfMissing(pair[0],pair[1]);
        for(String lang:List.of("en","id","zh")) moveIfMissing("lang/"+lang+".yml","Lang/Message/"+lang+".yml");
        for(String category:CATEGORIES) {
            moveIfMissing("shop/"+category+".yml","Shops/Category/"+category+".yml");
            moveIfMissing("shops/"+category+".yml","Shops/Category/"+category+".yml");
        }
        moveIfMissing("data/auction.db","Data/database.db");
        moveIfMissing("data/database.db","Data/database.db");
        migrateShopSettings();
    }

    private void migrateShopSettings() {
        File configFile=new File(root,"config.yml");
        YamlConfiguration config=YamlConfiguration.loadConfiguration(configFile);
        for(File oldFile:List.of(new File(root,"shops/settings.yml"),new File(root,"Shops/Settings/settings.yml"))) {
            if(!oldFile.isFile()) continue;
            YamlConfiguration old=YamlConfiguration.loadConfiguration(oldFile);
            ConfigurationSection section=old.getConfigurationSection("shop");
            if(section==null) continue;
            boolean changed=false;
            for(String key:section.getKeys(true)) if(!section.isConfigurationSection(key)&&!config.contains("shop."+key)) {
                config.set("shop."+key,section.get(key)); changed=true;
            }
            if(changed) try { config.save(configFile); oldFile.delete(); }
            catch(IOException ex) { plugin.getLogger().warning("Could not migrate Shop settings: "+ex.getMessage()); }
        }
    }

    private void moveIfMissing(String oldPath,String newPath) {
        File source=new File(root,oldPath), target=new File(root,newPath);
        if(!source.isFile()||target.exists()||sameFile(source,target)) return;
        File parent=target.getParentFile(); if(parent!=null) parent.mkdirs();
        try { Files.copy(source.toPath(),target.toPath(),StandardCopyOption.COPY_ATTRIBUTES); source.delete(); }
        catch(IOException ex) { plugin.getLogger().warning("Could not migrate "+oldPath+" to "+newPath+": "+ex.getMessage()); }
    }
    private boolean sameFile(File a,File b) {
        try { return a.exists()&&b.exists()&&Files.isSameFile(a.toPath(),b.toPath()); }
        catch(IOException ex) { return a.getAbsolutePath().equalsIgnoreCase(b.getAbsolutePath()); }
    }
    private void normalizeDatabasePath() {
        File file=new File(root,"config.yml");
        YamlConfiguration config=YamlConfiguration.loadConfiguration(file);
        String path=config.getString("database.sqlite-file","Data/database.db");
        if(path.equalsIgnoreCase("data/database.db")||path.equalsIgnoreCase("data/auction.db")) {
            config.set("database.sqlite-file","Data/database.db");
            try { config.save(file); } catch(IOException ex) { plugin.getLogger().warning("Could not update SQLite path: "+ex.getMessage()); }
        }
    }
    private void saveIfMissing(String path) {
        File file=new File(root,path); if(file.exists()) return;
        File parent=file.getParentFile(); if(parent!=null) parent.mkdirs();
        try {
            if(plugin.getResource(path)!=null) plugin.saveResource(path,false);
            else if(path.equals("Data/PlayerData.yml")) Files.writeString(file.toPath(),"schema-version: 1\nplayers: {}\n",java.nio.charset.StandardCharsets.UTF_8);
        } catch(Exception ex) { plugin.getLogger().warning("Could not create "+path+": "+ex.getMessage()); }
    }
    private void mergeDefaults(String path) {
        File target=new File(root,path); if(!target.isFile()||plugin.getResource(path)==null) return;
        YamlConfiguration user=YamlConfiguration.loadConfiguration(target), defaults;
        try(java.io.Reader reader=new java.io.InputStreamReader(plugin.getResource(path),java.nio.charset.StandardCharsets.UTF_8)) {
            defaults=YamlConfiguration.loadConfiguration(reader);
        } catch(Exception ex) { plugin.getLogger().warning("Could not read default "+path+": "+ex.getMessage()); return; }
        boolean changed=false;
        for(String key:defaults.getKeys(true)) if(!defaults.isConfigurationSection(key)&&!user.contains(key)) { user.set(key,defaults.get(key)); changed=true; }
        if(changed) try { user.save(target); } catch(IOException ex) { plugin.getLogger().warning("Could not update "+path+": "+ex.getMessage()); }
    }
}
