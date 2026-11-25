/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.alvran.octaeconomy.core.Module;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Small inbound webhook receiver. It performs HTTP, signature checks and disk writes away from
 * the Bukkit main thread; only player-facing broadcasts are scheduled back onto the main thread.
 */
public final class WebhookModule implements Module {
    private static final int MAX_REMEMBERED_EVENTS = 10_000;
    private final JavaPlugin plugin;
    private final Set<String> processed = new LinkedHashSet<>();
    private final MiniMessage mini = MiniMessage.miniMessage();
    private volatile YamlConfiguration config;
    private volatile HttpServer server;
    private volatile ExecutorService executor;
    private Path eventFile;

    public WebhookModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "DonationAndStoreWebhooks"; }

    @Override public void enable() {
        reload();
    }

    @Override public void reload() {
        stopServer();
        config = YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("integrations/saweria-tebex.yml").toFile());
        eventFile = plugin.getDataFolder().toPath().resolve(config.getString("webhooks.dedupe-file", "data/webhook-events.log"));
        loadProcessedIds();
        startServer();
    }

    @Override public void disable() {
        stopServer();
    }

    private void startServer() {
        if (!config.getBoolean("webhooks.enabled", false)) return;
        boolean saweriaEnabled = config.getBoolean("webhooks.saweria.enabled", false)
                && !config.getString("webhooks.saweria.streaming-key", "").isBlank();
        boolean tebexEnabled = config.getBoolean("webhooks.tebex.enabled", false)
                && !config.getString("webhooks.tebex.secret", "").isBlank();
        if (!saweriaEnabled && !tebexEnabled) {
            plugin.getLogger().info("Donation/store webhooks are disabled until at least one integration secret is configured.");
            return;
        }
        try {
            String host = config.getString("webhooks.bind-address", "127.0.0.1");
            int port = Math.max(1024, Math.min(65535, config.getInt("webhooks.port", 8765)));
            HttpServer created = HttpServer.create(new InetSocketAddress(host, port), 16);
            ThreadFactory factory = task -> {
                Thread thread = new Thread(task, "OctaEconomy-Webhooks");
                thread.setDaemon(true);
                return thread;
            };
            ExecutorService pool = Executors.newFixedThreadPool(2, factory);
            created.setExecutor(pool);
            created.createContext(path("saweria"), exchange -> handle(exchange, "saweria", saweriaEnabled));
            created.createContext(path("tebex"), exchange -> handle(exchange, "tebex", tebexEnabled));
            created.start();
            executor = pool;
            server = created;
            plugin.getLogger().info("Donation/store webhook listener started on " + host + ":" + port + ". Use an HTTPS reverse proxy for public callbacks.");
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not start the webhook listener: " + ex.getMessage());
        }
    }

    private String path(String name) {
        String configured = config.getString("webhooks.paths." + name, "/" + name);
        if (configured == null || configured.isBlank()) configured = "/" + name;
        return configured.startsWith("/") ? configured : "/" + configured;
    }

    private void stopServer() {
        HttpServer current = server;
        server = null;
        if (current != null) current.stop(0);
        ExecutorService pool = executor;
        executor = null;
        if (pool != null) pool.shutdownNow();
    }

    private void handle(HttpExchange exchange, String integration, boolean enabled) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }
            if (!enabled) {
                respond(exchange, 503, "{\"error\":\"integration_not_configured\"}");
                return;
            }
            int maxBytes = Math.max(1024, Math.min(262_144, config.getInt("webhooks.max-body-bytes", 65_536)));
            byte[] body = exchange.getRequestBody().readNBytes(maxBytes + 1);
            if (body.length > maxBytes) {
                respond(exchange, 413, "{\"error\":\"payload_too_large\"}");
                return;
            }
            if (integration.equals("saweria")) handleSaweria(exchange, body);
            else handleTebex(exchange, body);
        } catch (IllegalArgumentException ex) {
            safeRespond(exchange, 400, "{\"error\":\"invalid_payload\"}");
        } catch (SecurityException ex) {
            safeRespond(exchange, 401, "{\"error\":\"invalid_signature\"}");
        } catch (Exception ex) {
            plugin.getLogger().warning("Webhook request failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            safeRespond(exchange, 500, "{\"error\":\"processing_failed\"}");
        } finally {
            exchange.close();
        }
    }

    private void handleSaweria(HttpExchange exchange, byte[] rawBody) throws Exception {
        JsonObject json = parseObject(rawBody);
        String version = value(json, "version");
        String id = required(json, "id");
        String amountRaw = required(json, "amount_raw");
        String donator = value(json, "donator_name");
        String email = value(json, "donator_email");
        String secret = config.getString("webhooks.saweria.streaming-key", "");
        String expected = hmacHex("HmacSHA256", secret,
                (version + id + amountRaw + donator + email).getBytes(StandardCharsets.UTF_8));
        String supplied = exchange.getRequestHeaders().getFirst("Saweria-Callback-Signature");
        if (!constantTimeEquals(expected, supplied)) throw new SecurityException("invalid Saweria signature");

        BigDecimal amount;
        try { amount = new BigDecimal(amountRaw); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("invalid amount_raw", ex); }
        if (amount.signum() < 0 || amount.scale() > 0) throw new IllegalArgumentException("amount_raw must be a non-negative integer");
        if (remember("saweria:" + id)) {
            String message = value(json, "message");
            String formatted = "Rp " + NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID")).format(amount);
            broadcast("notifications.saweria", replacements(
                    "donator", safe(donator.isBlank() ? "Anonymous" : donator),
                    "amount", formatted,
                    "message", safe(compact(message, 100)),
                    "id", safe(id)));
        }
        respond(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleTebex(HttpExchange exchange, byte[] rawBody) throws Exception {
        String secret = config.getString("webhooks.tebex.secret", "");
        String bodyHash = sha256Hex(rawBody);
        String expected = hmacHex("HmacSHA256", secret, bodyHash.getBytes(StandardCharsets.UTF_8));
        String supplied = exchange.getRequestHeaders().getFirst("X-Signature");
        if (!constantTimeEquals(expected, supplied)) throw new SecurityException("invalid Tebex signature");

        JsonObject json = parseObject(rawBody);
        String id = required(json, "id");
        String type = value(json, "type");
        if ("validation.webhook".equals(type)) {
            respond(exchange, 200, "{\"id\":\"" + jsonEscape(id) + "\"}");
            return;
        }
        if (!"payment.completed".equals(type)) {
            respond(exchange, 200, "{\"status\":\"ignored\"}");
            return;
        }

        JsonObject subject = object(json, "subject");
        JsonObject customer = object(subject, "customer");
        String player = username(customer.get("username"));
        JsonArray products = subject.has("products") && subject.get("products").isJsonArray()
                ? subject.getAsJsonArray("products") : new JsonArray();
        List<String> productNames = new ArrayList<>();
        for (JsonElement element : products) {
            if (!element.isJsonObject()) continue;
            JsonObject product = element.getAsJsonObject();
            String productPlayer = username(product.get("username"));
            if (player.isBlank() && !productPlayer.isBlank()) player = productPlayer;
            String name = value(product, "name");
            if (!name.isBlank() && productNames.size() < 3) productNames.add(compact(name, 24));
        }
        if (player.isBlank()) player = "Unknown player";
        String transaction = value(subject, "transaction_id");
        JsonObject paid = object(subject, "price_paid");
        String amount = value(paid, "amount");
        String currency = value(paid, "currency");
        if (remember("tebex:" + id)) {
            broadcast("notifications.tebex", replacements(
                    "player", safe(compact(player, 16)),
                    "packages", safe(productNames.isEmpty() ? "Store purchase" : String.join(", ", productNames)),
                    "amount", safe(amount + " " + currency),
                    "transaction", safe(compact(transaction, 32)),
                    "id", safe(id)));
        }
        respond(exchange, 200, "{\"status\":\"ok\"}");
    }

    private JsonObject parseObject(byte[] body) {
        try {
            JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("JSON object expected");
            return parsed.getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid JSON", ex);
        }
    }

    private JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return new JsonObject();
        return parent.getAsJsonObject(key);
    }

    private String username(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (value.isJsonPrimitive()) return value.getAsString();
        if (value.isJsonObject()) return value.getAsJsonObject().has("username")
                ? value.getAsJsonObject().get("username").getAsString() : "";
        return "";
    }

    private String required(JsonObject object, String key) {
        String result = value(object, key);
        if (result.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return result;
    }

    private String value(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        JsonElement value = object.get(key);
        return value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private synchronized boolean remember(String id) throws IOException {
        if (processed.contains(id)) return false;
        Path parent = eventFile.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(eventFile, id + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        processed.add(id);
        if (processed.size() > MAX_REMEMBERED_EVENTS) {
            while (processed.size() > MAX_REMEMBERED_EVENTS) processed.remove(processed.iterator().next());
            Files.write(eventFile, processed, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        return true;
    }

    private void loadProcessedIds() {
        synchronized (processed) {
            processed.clear();
            try {
                if (!Files.exists(eventFile)) return;
                List<String> lines = Files.readAllLines(eventFile, StandardCharsets.UTF_8);
                int from = Math.max(0, lines.size() - MAX_REMEMBERED_EVENTS);
                for (int i = from; i < lines.size(); i++) if (!lines.get(i).isBlank()) processed.add(lines.get(i).trim());
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not load webhook deduplication history: " + ex.getMessage());
            }
        }
    }

    private void broadcast(String path, java.util.Map<String, String> values) {
        String template = config.getString(path, "");
        if (template.isBlank()) return;
        for (var entry : values.entrySet()) template = template.replace("{" + entry.getKey() + "}", entry.getValue());
        var component = mini.deserialize(template);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(component));
    }

    private java.util.Map<String, String> replacements(String... pairs) {
        java.util.Map<String, String> values = new java.util.HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) values.put(pairs[i], pairs[i + 1]);
        return values;
    }

    private String safe(String input) { return MiniMessage.miniMessage().escapeTags(input == null ? "" : input); }
    private String compact(String value, int max) {
        String clean = value == null ? "" : value.replaceAll("[\\r\\n]", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(1, max - 1)) + "…";
    }

    private String hmacHex(String algorithm, String secret, byte[] message) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
        return toHex(mac.doFinal(message));
    }

    private String sha256Hex(byte[] body) throws NoSuchAlgorithmException {
        return toHex(MessageDigest.getInstance("SHA-256").digest(body));
    }

    private String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) hex.append(String.format(Locale.ROOT, "%02x", value));
        return hex.toString();
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (supplied == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), supplied.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void safeRespond(HttpExchange exchange, int status, String body) {
        try { respond(exchange, status, body); } catch (IOException ignored) { }
    }
}
