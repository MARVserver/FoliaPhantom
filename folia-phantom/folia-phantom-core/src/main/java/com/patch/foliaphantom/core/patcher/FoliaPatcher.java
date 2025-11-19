package com.patch.foliaphantom.core.patcher;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
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

public final class FoliaPatcher {
    private static final ExecutorService worldGenExecutor = Executors.newSingleThreadExecutor();
    private static final AtomicInteger taskIdCounter = new AtomicInteger(Integer.MAX_VALUE / 2);
    private static final Map<Integer, ScheduledTask> runningTasks = new ConcurrentHashMap<>();

    public static Plugin plugin;

    public static final Map<String, Boolean> REPLACEMENT_MAP = new ConcurrentHashMap<>(Map.ofEntries(
            Map.entry("runTask(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskLater(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;J)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskTimer(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;JJ)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskAsynchronously(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskLaterAsynchronously(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;J)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskTimerAsynchronously(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;JJ)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskTimerAsynchronously(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scheduler/BukkitRunnable;JJ)Lorg/bukkit/scheduler/BukkitTask;", true),
            // Legacy methods returning int
            Map.entry("scheduleSyncDelayedTask(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;J)I", true),
            Map.entry("scheduleSyncRepeatingTask(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;JJ)I", true),
            Map.entry("scheduleAsyncDelayedTask(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;J)I", true),
            Map.entry("scheduleAsyncRepeatingTask(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;JJ)I", true),
            // Cancel tasks
            Map.entry("cancelTask(I)V", true),
            Map.entry("cancelTasks(Lorg/bukkit/plugin/Plugin;)V", true),
            Map.entry("cancelAllTasks()V", true)
    ));

    public static final Map<String, String> UNSAFE_METHOD_MAP = new ConcurrentHashMap<>();

    static {
        // Format: <Original Method Signature, Patcher Method Name>
        UNSAFE_METHOD_MAP.put("setType(Lorg/bukkit/Material;)V", "safeSetType");
        UNSAFE_METHOD_MAP.put("setType(Lorg/bukkit/Material;Z)V", "safeSetTypeWithPhysics");


        // World Generation related methods
        REPLACEMENT_MAP.put("getDefaultWorldGenerator(Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/generator/ChunkGenerator;", true);
        REPLACEMENT_MAP.put("createWorld(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;", true);
        // BukkitRunnable is handled by transformer, no need for separate map entry if signature is the same
    }

    private FoliaPatcher() {}

    // --- World Generation Wrappers ---

    public static org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(Plugin plugin, String worldName, String id) {
        org.bukkit.generator.ChunkGenerator originalGenerator = plugin.getDefaultWorldGenerator(worldName, id);
        if (originalGenerator == null) return null;
        return new FoliaChunkGenerator(originalGenerator);
    }

    public static World createWorld(org.bukkit.WorldCreator creator) {
        System.out.println("[FoliaPhantom] Intercepted createWorld call. Using creator.createWorld() on a dedicated thread...");
        Callable<World> task = creator::createWorld;
        Future<World> future = worldGenExecutor.submit(task);
        try {
            // Block the calling thread (from the plugin) until our executor thread is done.
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("[FoliaPhantom] Failed to create world via creator.createWorld().");
            e.printStackTrace();
            return null;
        }
    }

    public static class FoliaChunkGenerator extends org.bukkit.generator.ChunkGenerator {
            final org.bukkit.generator.ChunkGenerator original;

        public FoliaChunkGenerator(org.bukkit.generator.ChunkGenerator original) {
            this.original = original;
            System.out.println("[FoliaPhantom-WorldGen] Wrapping ChunkGenerator: " + original.getClass().getName());
        }

        private void log(String methodName) {
            System.out.println("[FoliaPhantom-WorldGen] FoliaChunkGenerator." + methodName + " called on thread: " + Thread.currentThread().getName());
        }

        @Override
        public ChunkData generateChunkData(World world, java.util.Random random, int x, int z, BiomeGrid biome) {
            log("generateChunkData");
            return original.generateChunkData(world, random, x, z, biome);
        }

        @Override
        public boolean shouldGenerateNoise() {
            log("shouldGenerateNoise");
            return original.shouldGenerateNoise();
        }

        @Override
        public boolean shouldGenerateSurface() {
            log("shouldGenerateSurface");
            return original.shouldGenerateSurface();
        }

        @Override
        public boolean shouldGenerateBedrock() {
            log("shouldGenerateBedrock");
            return original.shouldGenerateBedrock();
        }

        @Override
        public boolean shouldGenerateCaves() {
            log("shouldGenerateCaves");
            return original.shouldGenerateCaves();
        }

        @Override
        public boolean shouldGenerateDecorations() {
            log("shouldGenerateDecorations");
            return original.shouldGenerateDecorations();
        }

        @Override
        public boolean shouldGenerateMobs() {
            log("shouldGenerateMobs");
            return original.shouldGenerateMobs();
        }

        @Override
        public Location getFixedSpawnLocation(World world, java.util.Random random) {
            log("getFixedSpawnLocation");
            return original.getFixedSpawnLocation(world, random);
        }
    }

    private static Location getFallbackLocation() {
        World mainWorld = Bukkit.getWorld("world");
        if (mainWorld != null) {
            return mainWorld.getSpawnLocation();
        }
        List<World> worlds = Bukkit.getWorlds();
        if (!worlds.isEmpty()) {
            return worlds.get(0).getSpawnLocation();
        }
        return null;
    }

    private static void cancelTaskById(int taskId) {
        ScheduledTask task = runningTasks.remove(taskId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private static Runnable wrapRunnable(Runnable original, int taskId, boolean isRepeating) {
        if (isRepeating) {
            return original;
        }
        return () -> {
            try {
                original.run();
            } finally {
                runningTasks.remove(taskId);
            }
        };
    }

    public static BukkitTask runTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrappedRunnable = wrapRunnable(runnable, taskId, false);
        Location loc = getFallbackLocation();
        ScheduledTask foliaTask = loc != null
                ? Bukkit.getRegionScheduler().run(plugin, loc, t -> wrappedRunnable.run())
                : Bukkit.getGlobalRegionScheduler().run(plugin, t -> wrappedRunnable.run());
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
    }

    public static BukkitTask runTaskLater(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrappedRunnable = wrapRunnable(runnable, taskId, false);
        Location loc = getFallbackLocation();
        ScheduledTask foliaTask = loc != null
                ? Bukkit.getRegionScheduler().runDelayed(plugin, loc, t -> wrappedRunnable.run(), Math.max(1, delay))
                : Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> wrappedRunnable.run(), Math.max(1, delay));
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
    }

    public static BukkitTask runTaskTimer(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay, long period) {
        Location loc = getFallbackLocation();
        ScheduledTask foliaTask = loc != null
                ? Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc, t -> runnable.run(), Math.max(1, delay), Math.max(1, period))
                : Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> runnable.run(), Math.max(1, delay), Math.max(1, period));
        int taskId = taskIdCounter.getAndIncrement();
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
    }

    public static BukkitTask runTaskAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrappedRunnable = wrapRunnable(runnable, taskId, false);
        ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runNow(plugin, t -> wrappedRunnable.run());
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
    }

    public static BukkitTask runTaskLaterAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrappedRunnable = wrapRunnable(runnable, taskId, false);
        ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runDelayed(plugin, t -> wrappedRunnable.run(), delay * 50, TimeUnit.MILLISECONDS);
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
    }

    public static BukkitTask runTaskTimerAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay, long period) {
        // This now handles both Runnable and BukkitRunnable, as BukkitRunnable is a Runnable.
        ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> runnable.run(), delay * 50, period * 50, TimeUnit.MILLISECONDS);
        int taskId = taskIdCounter.getAndIncrement();
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
    }

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

        @Override public int getTaskId() { return taskId; }
        @Override public Plugin getOwner() { return owner; }
        @Override public boolean isSync() { return isSync; }
        @Override public boolean isCancelled() { return underlyingTask.isCancelled(); }

        @Override public void cancel() {
            if (!isCancelled()) {
                cancellationCallback.accept(this.taskId);
            }
        }
    }

    public static int scheduleSyncDelayedTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay) {
        return runTaskLater(ignored, plugin, runnable, delay).getTaskId();
    }

    public static int scheduleSyncRepeatingTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay, long period) {
        return runTaskTimer(ignored, plugin, runnable, delay, period).getTaskId();
    }

    public static int scheduleAsyncDelayedTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay) {
        return runTaskLaterAsynchronously(ignored, plugin, runnable, delay).getTaskId();
    }

    public static int scheduleAsyncRepeatingTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay, long period) {
        return runTaskTimerAsynchronously(ignored, plugin, runnable, delay, period).getTaskId();
    }

    public static void cancelTask(BukkitScheduler ignored, int taskId) {
        cancelTaskById(taskId);
    }

    public static void cancelTasks(BukkitScheduler ignored, Plugin plugin) {
        runningTasks.entrySet().removeIf(entry -> {
            ScheduledTask scheduledTask = entry.getValue();
            boolean ownedByPlugin = scheduledTask.getOwningPlugin().equals(plugin);
            if (ownedByPlugin && !scheduledTask.isCancelled()) {
                scheduledTask.cancel();
            }
            return ownedByPlugin;
        });
    }

    public static void cancelAllTasks() {
        runningTasks.values().forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        runningTasks.clear();
    }

    // --- Entity Scheduler Wrappers ---

    public static BukkitTask folia_runTask(Entity entity, Plugin plugin, Runnable runnable) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrappedRunnable = wrapRunnable(runnable, taskId, false);
        ScheduledTask foliaTask = entity.getScheduler().run(plugin, t -> wrappedRunnable.run(), null);
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
    }

    public static BukkitTask folia_runTaskLater(Entity entity, Plugin plugin, Runnable runnable, long delay) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrappedRunnable = wrapRunnable(runnable, taskId, false);
        ScheduledTask foliaTask = entity.getScheduler().runDelayed(plugin, t -> wrappedRunnable.run(), null, Math.max(1, delay));
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
    }

    public static BukkitTask folia_runTaskTimer(Entity entity, Plugin plugin, Runnable runnable, long delay, long period) {
        ScheduledTask foliaTask = entity.getScheduler().runAtFixedRate(plugin, t -> runnable.run(), null, Math.max(1, delay), Math.max(1, period));
        int taskId = taskIdCounter.getAndIncrement();
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
    }

    public static int folia_scheduleSyncDelayedTask(Entity entity, Plugin plugin, Runnable runnable, long delay) {
        return folia_runTaskLater(entity, plugin, runnable, delay).getTaskId();
    }

    public static int folia_scheduleSyncRepeatingTask(Entity entity, Plugin plugin, Runnable runnable, long delay, long period) {
        return folia_runTaskTimer(entity, plugin, runnable, delay, period).getTaskId();
    }

    // --- Thread-Safety Wrappers ---

    public static void safeSetType(Block block, org.bukkit.Material material) {
        if (Bukkit.isPrimaryThread()) {
            block.setType(material);
        } else {
            Bukkit.getRegionScheduler().run(plugin, block.getLocation(), task -> {
                block.setType(material);
            });
        }
    }

    public static void safeSetTypeWithPhysics(Block block, org.bukkit.Material material, boolean applyPhysics) {
        if (Bukkit.isPrimaryThread()) {
            block.setType(material, applyPhysics);
        } else {
            Bukkit.getRegionScheduler().run(plugin, block.getLocation(), task -> {
                block.setType(material, applyPhysics);
            });
        }
    }
}
