/*
 * Folia Phantom - Runtime Patcher Bridge
 * 
 * This class provides the redirected method implementations that patched plugins
 * call at runtime. It bridges standard Bukkit/Paper API calls to Folia-specific
 * region-based or async schedulers.
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.patcher;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

/**
 * Runtime bridge for Folia compatibility.
 * 
 * <p>
 * This class is bundled into patched plugins and serves as the destination
 * for redirected method calls. It handles the complexity of mapping global
 * scheduler calls to Folia's region-based or asynchronous schedulers.
 * </p>
 */
public final class FoliaPatcher {
    private static final Logger LOGGER = Logger.getLogger("FoliaPhantom-Patcher");
    private static final ExecutorService worldGenExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FoliaPhantom-WorldGen-Worker");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger taskIdCounter = new AtomicInteger(1000000);
    private static final Map<Integer, ScheduledTask> runningTasks = new ConcurrentHashMap<>();

    /**
     * The plugin instance used for scheduling.
     * This is typically the patched plugin itself.
     */
    public static Plugin plugin;

    private FoliaPatcher() {
    }

    // --- World Generation Wrappers ---

    /**
     * Wraps a ChunkGenerator to ensure Folia compatibility.
     */
    public static org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(Plugin plugin, String worldName,
            String id) {
        org.bukkit.generator.ChunkGenerator originalGenerator = plugin.getDefaultWorldGenerator(worldName, id);
        if (originalGenerator == null)
            return null;
        return new FoliaChunkGenerator(originalGenerator);
    }

    /**
     * Safely creates a world by offloading to a dedicated thread, as world creation
     * in Folia/Paper can be sensitive to the calling thread context.
     */
    public static World createWorld(org.bukkit.WorldCreator creator) {
        LOGGER.info("[FoliaPhantom] Intercepting world creation: " + creator.name());
        Future<World> future = worldGenExecutor.submit(creator::createWorld);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("[FoliaPhantom] Failed to create world '" + creator.name() + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Internal wrapper for Folia's ChunkGenerator.
     */
    public static class FoliaChunkGenerator extends org.bukkit.generator.ChunkGenerator {
        private final org.bukkit.generator.ChunkGenerator original;

        public FoliaChunkGenerator(org.bukkit.generator.ChunkGenerator original) {
            this.original = original;
        }

        @Override
        public ChunkData generateChunkData(World world, java.util.Random random, int x, int z, BiomeGrid biome) {
            return original.generateChunkData(world, random, x, z, biome);
        }

        @Override
        public boolean shouldGenerateNoise() {
            return original.shouldGenerateNoise();
        }

        @Override
        public boolean shouldGenerateSurface() {
            return original.shouldGenerateSurface();
        }

        @Override
        public boolean shouldGenerateBedrock() {
            return original.shouldGenerateBedrock();
        }

        @Override
        public boolean shouldGenerateCaves() {
            return original.shouldGenerateCaves();
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return original.shouldGenerateDecorations();
        }

        @Override
        public boolean shouldGenerateMobs() {
            return original.shouldGenerateMobs();
        }

        @Override
        public Location getFixedSpawnLocation(World world, java.util.Random random) {
            return original.getFixedSpawnLocation(world, random);
        }
    }

    // --- Helper Methods ---

    private static Location getFallbackLocation() {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return null;
        }
        World mainWorld = worlds.get(0);
        return mainWorld != null ? mainWorld.getSpawnLocation() : null;
    }

    private static void cancelTaskById(int taskId) {
        ScheduledTask task = runningTasks.remove(taskId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private static Runnable wrapRunnable(Runnable original, int taskId, boolean isRepeating) {
        if (isRepeating)
            return original;
        return () -> {
            try {
                original.run();
            } finally {
                runningTasks.remove(taskId);
            }
        };
    }

    // --- Scheduler Redirections ---

    public static BukkitTask runTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrapped = wrapRunnable(runnable, taskId, false);
        Location loc = getFallbackLocation();

        ScheduledTask foliaTask = (loc != null)
                ? Bukkit.getRegionScheduler().run(plugin, loc, t -> wrapped.run())
                : Bukkit.getGlobalRegionScheduler().run(plugin, t -> wrapped.run());

        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
    }

    public static BukkitTask runTaskLater(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrapped = wrapRunnable(runnable, taskId, false);
        Location loc = getFallbackLocation();
        long finalDelay = Math.max(1, delay);

        ScheduledTask foliaTask = (loc != null)
                ? Bukkit.getRegionScheduler().runDelayed(plugin, loc, t -> wrapped.run(), finalDelay)
                : Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> wrapped.run(), finalDelay);

        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
    }

    public static BukkitTask runTaskTimer(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay,
            long period) {
        int taskId = taskIdCounter.getAndIncrement();
        Location loc = getFallbackLocation();
        long d = Math.max(1, delay);
        long p = Math.max(1, period);

        ScheduledTask foliaTask = (loc != null)
                ? Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc, t -> runnable.run(), d, p)
                : Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> runnable.run(), d, p);

        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
    }

    public static BukkitTask runTaskAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrapped = wrapRunnable(runnable, taskId, false);
        ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runNow(plugin, t -> wrapped.run());
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
    }

    public static BukkitTask runTaskLaterAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable,
            long delay) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrapped = wrapRunnable(runnable, taskId, false);
        ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runDelayed(plugin, t -> wrapped.run(), delay * 50,
                TimeUnit.MILLISECONDS);
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
    }

    public static BukkitTask runTaskTimerAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable,
            long delay, long period) {
        int taskId = taskIdCounter.getAndIncrement();
        ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> runnable.run(), delay * 50,
                period * 50, TimeUnit.MILLISECONDS);
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
    }

    // --- BukkitRunnable Instance Method Wrappers ---

    public static BukkitTask runTask_onRunnable(Runnable runnable, Plugin plugin) {
        return runTask(null, plugin, runnable);
    }

    public static BukkitTask runTaskLater_onRunnable(Runnable runnable, Plugin plugin, long delay) {
        return runTaskLater(null, plugin, runnable, delay);
    }

    public static BukkitTask runTaskTimer_onRunnable(Runnable runnable, Plugin plugin, long delay, long period) {
        return runTaskTimer(null, plugin, runnable, delay, period);
    }

    public static BukkitTask runTaskAsynchronously_onRunnable(Runnable runnable, Plugin plugin) {
        return runTaskAsynchronously(null, plugin, runnable);
    }

    public static BukkitTask runTaskLaterAsynchronously_onRunnable(Runnable runnable, Plugin plugin, long delay) {
        return runTaskLaterAsynchronously(null, plugin, runnable, delay);
    }

    public static BukkitTask runTaskTimerAsynchronously_onRunnable(Runnable runnable, Plugin plugin, long delay,
            long period) {
        return runTaskTimerAsynchronously(null, plugin, runnable, delay, period);
    }

    /**
     * Implementation of BukkitTask for Folia.
     */
    public static final class FoliaBukkitTask implements BukkitTask {
        private final int taskId;
        private final Plugin owner;
        private final IntConsumer cancellationCallback;
        private final boolean isSync;
        private final ScheduledTask underlyingTask;

        public FoliaBukkitTask(int taskId, Plugin owner, IntConsumer cc, boolean isSync, ScheduledTask underlyingTask) {
            this.taskId = taskId;
            this.owner = owner;
            this.cancellationCallback = cc;
            this.isSync = isSync;
            this.underlyingTask = underlyingTask;
        }

        @Override
        public int getTaskId() {
            return taskId;
        }

        @Override
        public Plugin getOwner() {
            return owner;
        }

        @Override
        public boolean isSync() {
            return isSync;
        }

        @Override
        public boolean isCancelled() {
            return underlyingTask.isCancelled();
        }

        @Override
        public void cancel() {
            if (!isCancelled()) {
                cancellationCallback.accept(this.taskId);
            }
        }
    }

    // --- Thread-Safe Block Operations ---

    public static void safeSetType(Block block, org.bukkit.Material material) {
        if (Bukkit.isPrimaryThread()) {
            block.setType(material);
        } else {
            Bukkit.getRegionScheduler().run(plugin, block.getLocation(), task -> block.setType(material));
        }
    }

    public static void safeSetTypeWithPhysics(Block block, org.bukkit.Material material, boolean applyPhysics) {
        if (Bukkit.isPrimaryThread()) {
            block.setType(material, applyPhysics);
        } else {
            Bukkit.getRegionScheduler().run(plugin, block.getLocation(), task -> block.setType(material, applyPhysics));
        }
    }

    // --- Legacy / Int-returning Method Mappings ---

    public static int scheduleSyncDelayedTask(BukkitScheduler s, Plugin p, Runnable r, long d) {
        return runTaskLater(s, p, r, d).getTaskId();
    }

    public static int scheduleSyncDelayedTask(BukkitScheduler s, Plugin p, Runnable r) {
        return runTask(s, p, r).getTaskId();
    }

    public static int scheduleSyncRepeatingTask(BukkitScheduler s, Plugin p, Runnable r, long d, long pr) {
        return runTaskTimer(s, p, r, d, pr).getTaskId();
    }

    public static int scheduleAsyncDelayedTask(BukkitScheduler s, Plugin p, Runnable r, long d) {
        return runTaskLaterAsynchronously(s, p, r, d).getTaskId();
    }

    public static int scheduleAsyncDelayedTask(BukkitScheduler s, Plugin p, Runnable r) {
        return runTaskAsynchronously(s, p, r).getTaskId();
    }

    public static int scheduleAsyncRepeatingTask(BukkitScheduler s, Plugin p, Runnable r, long d, long pr) {
        return runTaskTimerAsynchronously(s, p, r, d, pr).getTaskId();
    }

    public static void cancelTask(BukkitScheduler ignored, int taskId) {
        cancelTaskById(taskId);
    }

    public static void cancelTasks(BukkitScheduler ignored, Plugin plugin) {
        runningTasks.entrySet().removeIf(entry -> {
            ScheduledTask task = entry.getValue();
            if (task.getOwningPlugin().equals(plugin)) {
                if (!task.isCancelled())
                    task.cancel();
                return true;
            }
            return false;
        });
    }

    public static void cancelAllTasks() {
        runningTasks.values().forEach(task -> {
            if (!task.isCancelled())
                task.cancel();
        });
        runningTasks.clear();
    }
}
