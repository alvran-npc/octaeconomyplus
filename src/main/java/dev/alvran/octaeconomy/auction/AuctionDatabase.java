/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.auction;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.plugin.java.JavaPlugin;

/** All JDBC work is serialized on one dedicated daemon thread. */
public final class AuctionDatabase {
    public record Payout(long id, String currency, double amount) { }
    public record Delivery(long id, byte[] itemData) { }
    private final JavaPlugin plugin;
    private final File dataFolder;
    private final String databaseType, sqliteFile, mysqlHost, mysqlDatabase, mysqlUser, mysqlPassword;
    private final int mysqlPort;
    private final boolean mysqlSsl;
    private final ExecutorService executor;
    private volatile Connection connection;
    private volatile boolean ready;
    private volatile boolean mysql;

    public AuctionDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.databaseType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        this.sqliteFile = plugin.getConfig().getString("database.sqlite-file", "data/database.db");
        this.mysqlHost = plugin.getConfig().getString("database.mysql.host", "127.0.0.1");
        this.mysqlPort = plugin.getConfig().getInt("database.mysql.port", 3306);
        this.mysqlDatabase = plugin.getConfig().getString("database.mysql.database", "octaeconomy");
        this.mysqlUser = plugin.getConfig().getString("database.mysql.username", "root");
        this.mysqlPassword = plugin.getConfig().getString("database.mysql.password", "");
        this.mysqlSsl = plugin.getConfig().getBoolean("database.mysql.use-ssl", false);
        AtomicInteger id = new AtomicInteger();
        ThreadFactory factory = task -> { Thread t = new Thread(task, "OctaEconomy-Database-" + id.incrementAndGet()); t.setDaemon(true); return t; };
        executor = Executors.newSingleThreadExecutor(factory);
    }

    public CompletableFuture<Void> initialize() {
        return submit(conn -> {
            String idType = mysql ? "BIGINT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
            String blobType = mysql ? "LONGBLOB" : "BLOB";
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS auction_listings (id " + idType + ", seller_uuid VARCHAR(36) NOT NULL, seller_name VARCHAR(16) NOT NULL, item_name VARCHAR(128) NOT NULL, currency VARCHAR(64) NOT NULL, price DOUBLE NOT NULL, created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, status VARCHAR(16) NOT NULL, item_data " + blobType + " NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS auction_payouts (id " + idType + ", player_uuid VARCHAR(36) NOT NULL, currency VARCHAR(64) NOT NULL, amount DOUBLE NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS auction_deliveries (id " + idType + ", player_uuid VARCHAR(36) NOT NULL, item_data " + blobType + " NOT NULL)");
            }
            createIndex(conn, "idx_auction_status_expiry", "auction_listings", "status, expires_at");
            createIndex(conn, "idx_auction_seller_status", "auction_listings", "seller_uuid, status");
            createIndex(conn, "idx_auction_search", "auction_listings", "status, item_name");
            createIndex(conn, "idx_auction_payout_player", "auction_payouts", "player_uuid");
            createIndex(conn, "idx_auction_delivery_player", "auction_deliveries", "player_uuid");
            ready = true;
            return null;
        });
    }

    private Connection open() throws SQLException {
        if (connection != null && !connection.isClosed()) return connection;
        String type = databaseType;
        try {
            if (type.equals("mysql") || type.equals("mariadb")) {
                mysql = true;
                try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (ClassNotFoundException ex) { throw new SQLException("MySQL JDBC library is not available. Check the Paper libraries section in plugin.yml.", ex); }
                connection = DriverManager.getConnection("jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase + "?useSSL=" + mysqlSsl + "&serverTimezone=UTC", mysqlUser, mysqlPassword);
            } else {
                mysql = false;
                File file = new File(dataFolder, sqliteFile);
                File parent = file.getParentFile(); if (parent != null) parent.mkdirs();
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                try (Statement st = connection.createStatement()) { st.execute("PRAGMA journal_mode=WAL"); st.execute("PRAGMA synchronous=NORMAL"); st.execute("PRAGMA busy_timeout=5000"); }
            }
        } catch (ClassNotFoundException ex) { throw new SQLException("JDBC driver is missing for database type '" + type + "'.", ex); }
        return connection;
    }

    private void createIndex(Connection conn, String name, String table, String columns) throws SQLException {
        try (Statement st = conn.createStatement()) {
            if (mysql) st.executeUpdate("CREATE INDEX " + name + " ON " + table + "(" + columns + ")");
            else st.executeUpdate("CREATE INDEX IF NOT EXISTS " + name + " ON " + table + "(" + columns + ")");
        } catch (SQLException ex) {
            // MySQL has no CREATE INDEX IF NOT EXISTS on all supported versions; an existing index is harmless.
            if (!mysql || !String.valueOf(ex.getMessage()).toLowerCase().contains("duplicate")) throw ex;
        }
    }

    @FunctionalInterface private interface SqlWork<T> { T apply(Connection connection) throws Exception; }
    private <T> CompletableFuture<T> submit(SqlWork<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try { return work.apply(open()); }
            catch (RuntimeException ex) { throw ex; }
            catch (Exception ex) { throw new RuntimeException(ex); }
        }, executor);
    }

    public boolean isReady() { return ready; }

    public CompletableFuture<Long> createListing(UUID seller, String sellerName, String itemName,
                                                   String currency, double price, long createdAt,
                                                   long expiresAt, byte[] itemData) {
        return submit(conn -> {
            String sql = "INSERT INTO auction_listings(seller_uuid,seller_name,item_name,currency,price,created_at,expires_at,status,item_data) VALUES(?,?,?,?,?,?,?,'ACTIVE',?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, seller.toString()); ps.setString(2, sellerName); ps.setString(3, itemName);
                ps.setString(4, currency); ps.setDouble(5, price); ps.setLong(6, createdAt); ps.setLong(7, expiresAt); ps.setBytes(8, itemData);
                ps.executeUpdate(); try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getLong(1) : -1L; }
            }
        });
    }

    public CompletableFuture<List<AuctionListing>> activeListings(String search, int limit, int offset) {
        return submit(conn -> {
            List<AuctionListing> result = new ArrayList<>();
            String sql = "SELECT * FROM auction_listings WHERE status='ACTIVE' AND expires_at>? AND (?='' OR LOWER(item_name) LIKE ?) ORDER BY created_at DESC LIMIT ? OFFSET ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                String needle = search == null ? "" : search.toLowerCase();
                ps.setLong(1, System.currentTimeMillis()); ps.setString(2, needle); ps.setString(3, "%" + needle + "%"); ps.setInt(4, limit); ps.setInt(5, offset);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(readListing(rs)); }
            }
            return result;
        });
    }

    public CompletableFuture<List<AuctionListing>> ownListings(UUID seller, int limit, int offset) {
        return submit(conn -> {
            List<AuctionListing> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM auction_listings WHERE seller_uuid=? AND status='ACTIVE' ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
                ps.setString(1, seller.toString()); ps.setInt(2, limit); ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(readListing(rs)); }
            }
            return result;
        });
    }

    public CompletableFuture<Integer> countActive(UUID seller) {
        return submit(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM auction_listings WHERE seller_uuid=? AND status='ACTIVE'")) {
                ps.setString(1, seller.toString()); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
            }
        });
    }

    public CompletableFuture<AuctionListing> find(long id) {
        return submit(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM auction_listings WHERE id=? AND status='ACTIVE' AND expires_at>?")) {
                ps.setLong(1, id); ps.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? readListing(rs) : null; }
            }
        });
    }

    /** Status transition, buyer delivery and seller payout are committed atomically. */
    public CompletableFuture<Boolean> purchase(long listingId, UUID buyer, double taxPercent) {
        return submit(conn -> {
            boolean auto = conn.getAutoCommit(); conn.setAutoCommit(false);
            try {
                AuctionListing listing;
                try (PreparedStatement select = conn.prepareStatement("SELECT * FROM auction_listings WHERE id=? AND status='ACTIVE' AND expires_at>?")) {
                    select.setLong(1, listingId); select.setLong(2, System.currentTimeMillis());
                    try (ResultSet rs = select.executeQuery()) { if (!rs.next()) { conn.rollback(); return false; } listing = readListing(rs); }
                }
                if (listing.sellerId().equals(buyer)) { conn.rollback(); return false; }
                try (PreparedStatement update = conn.prepareStatement("UPDATE auction_listings SET status='SOLD' WHERE id=? AND status='ACTIVE'")) {
                    update.setLong(1, listingId); if (update.executeUpdate() != 1) { conn.rollback(); return false; }
                }
                double tax = Math.max(0.0, Math.min(100.0, taxPercent));
                double payout = listing.price() * (100.0 - tax) / 100.0;
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO auction_payouts(player_uuid,currency,amount) VALUES(?,?,?)")) {
                    ps.setString(1, listing.sellerId().toString()); ps.setString(2, listing.currency()); ps.setDouble(3, payout); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO auction_deliveries(player_uuid,item_data) VALUES(?,?)")) {
                    ps.setString(1, buyer.toString()); ps.setBytes(2, listing.itemData()); ps.executeUpdate();
                }
                conn.commit(); return true;
            } catch (Exception ex) { conn.rollback(); throw ex; }
            finally { conn.setAutoCommit(auto); }
        });
    }

    public CompletableFuture<Boolean> cancel(long listingId, UUID owner) {
        return submit(conn -> {
            boolean auto = conn.getAutoCommit(); conn.setAutoCommit(false);
            try {
                byte[] data;
                try (PreparedStatement ps = conn.prepareStatement("SELECT item_data FROM auction_listings WHERE id=? AND seller_uuid=? AND status='ACTIVE'")) {
                    ps.setLong(1, listingId); ps.setString(2, owner.toString());
                    try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) { conn.rollback(); return false; } data = rs.getBytes(1); }
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE auction_listings SET status='CANCELLED' WHERE id=? AND seller_uuid=? AND status='ACTIVE'")) {
                    ps.setLong(1, listingId); ps.setString(2, owner.toString()); if (ps.executeUpdate() != 1) { conn.rollback(); return false; }
                }
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO auction_deliveries(player_uuid,item_data) VALUES(?,?)")) {
                    ps.setString(1, owner.toString()); ps.setBytes(2, data); ps.executeUpdate();
                }
                conn.commit(); return true;
            } catch (Exception ex) { conn.rollback(); throw ex; }
            finally { conn.setAutoCommit(auto); }
        });
    }

    public CompletableFuture<List<AuctionListing>> expireBatch() {
        return submit(conn -> {
            boolean auto = conn.getAutoCommit(); conn.setAutoCommit(false);
            try {
                List<AuctionListing> expired = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM auction_listings WHERE status='ACTIVE' AND expires_at<=? LIMIT 100")) {
                    ps.setLong(1, System.currentTimeMillis());
                    try (ResultSet rs = ps.executeQuery()) { while (rs.next()) expired.add(readListing(rs)); }
                }
                try (PreparedStatement delivery = conn.prepareStatement("INSERT INTO auction_deliveries(player_uuid,item_data) VALUES(?,?)");
                     PreparedStatement update = conn.prepareStatement("UPDATE auction_listings SET status='EXPIRED' WHERE id=? AND status='ACTIVE'")) {
                    for (AuctionListing listing : expired) {
                        delivery.setString(1, listing.sellerId().toString()); delivery.setBytes(2, listing.itemData()); delivery.executeUpdate();
                        update.setLong(1, listing.id()); update.executeUpdate();
                    }
                }
                conn.commit(); return expired;
            } catch (Exception ex) { conn.rollback(); throw ex; }
            finally { conn.setAutoCommit(auto); }
        });
    }

    public CompletableFuture<List<AuctionListing>> expiredListings(UUID owner, int limit, int offset) {
        return submit(conn -> {
            List<AuctionListing> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM auction_listings WHERE seller_uuid=? AND status='EXPIRED' ORDER BY expires_at DESC LIMIT ? OFFSET ?")) {
                ps.setString(1, owner.toString()); ps.setInt(2, limit); ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(readListing(rs)); }
            }
            return result;
        });
    }

    public CompletableFuture<List<Delivery>> deliveries(UUID owner) {
        return submit(conn -> {
            List<Delivery> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT id,item_data FROM auction_deliveries WHERE player_uuid=? ORDER BY id LIMIT 100")) {
                ps.setString(1, owner.toString()); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) items.add(new Delivery(rs.getLong(1), rs.getBytes(2))); }
            }
            return items;
        });
    }

    public CompletableFuture<Void> deleteDelivery(long id) {
        return submit(conn -> { try (PreparedStatement ps = conn.prepareStatement("DELETE FROM auction_deliveries WHERE id=?")) { ps.setLong(1, id); ps.executeUpdate(); } return null; });
    }

    public CompletableFuture<List<Payout>> payouts(UUID owner) {
        return submit(conn -> {
            List<Payout> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT id,currency,amount FROM auction_payouts WHERE player_uuid=? ORDER BY id LIMIT 100")) {
                ps.setString(1, owner.toString()); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(new Payout(rs.getLong(1), rs.getString(2), rs.getDouble(3))); }
            }
            return result;
        });
    }

    public CompletableFuture<Void> deletePayout(long id) {
        return submit(conn -> { try (PreparedStatement ps = conn.prepareStatement("DELETE FROM auction_payouts WHERE id=?")) { ps.setLong(1, id); ps.executeUpdate(); } return null; });
    }

    public CompletableFuture<Integer> expiredCount(UUID owner) {
        return submit(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM auction_listings WHERE seller_uuid=? AND status='EXPIRED'")) {
                ps.setString(1, owner.toString()); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
            }
        });
    }

    private AuctionListing readListing(ResultSet rs) throws SQLException {
        return new AuctionListing(rs.getLong("id"), UUID.fromString(rs.getString("seller_uuid")), rs.getString("seller_name"), rs.getString("item_name"), rs.getString("currency"), rs.getDouble("price"), rs.getLong("created_at"), rs.getLong("expires_at"), rs.getString("status"), rs.getBytes("item_data"));
    }

    public void close() {
        ready = false;
        try {
            executor.execute(() -> { try { if (connection != null) connection.close(); } catch (SQLException ignored) { } });
        } catch (java.util.concurrent.RejectedExecutionException ignored) { }
        executor.shutdown();
    }
}
