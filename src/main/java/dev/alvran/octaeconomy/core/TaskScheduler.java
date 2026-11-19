/*
 * OctaEconomy - Copyright (c) Alvran (https://github.com/alvran-npc)
 * Free software. Resale is prohibited. See LICENSE.
 */
package dev.alvran.octaeconomy.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Serial I/O executor plus explicit main-thread callbacks. */
public final class TaskScheduler {
    private final JavaPlugin plugin;
    private final ExecutorService io;
    public TaskScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        AtomicInteger number = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "OctaEconomy-IO-" + number.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.io = Executors.newSingleThreadExecutor(factory);
    }
    public void async(Runnable task) { io.execute(task); }
    public <T> void supply(Supplier<T> supplier, Consumer<T> success, Consumer<Throwable> failure) {
        io.execute(() -> {
            try {
                T result = supplier.get();
                Bukkit.getScheduler().runTask(plugin, () -> success.accept(result));
            } catch (Throwable error) {
                Bukkit.getScheduler().runTask(plugin, () -> failure.accept(error));
            }
        });
    }
    public void main(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }
    public void shutdown() { io.shutdown(); }
}
