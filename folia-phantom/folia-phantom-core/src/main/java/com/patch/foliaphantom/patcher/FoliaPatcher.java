package com.patch.foliaphantom.patcher;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Folia 互換のランタイムブリッジ。
 *
 * <p>パッチ済みプラグインが呼び出す実際の Folia 互換メソッドを提供する。
 * このクラスはパッチ処理後に出力 JAR に同梱され、サーバー上でランタイムにロードされる。</p>
 */
public final class FoliaPatcher {

    private static final Logger log = LoggerFactory.getLogger(FoliaPatcher.class);

    private static final AtomicInteger taskIdCounter = new AtomicInteger(1);

    private static final ConcurrentHashMap<Integer, ScheduledTask> runningTasks =
            new ConcurrentHashMap<>();

    /** リージョンスケジューラ応答待ちタイムアウト（秒）。デッドロック防止に使用。 */
    private static final long FUTURE_TIMEOUT_SECONDS = 5L;

    /**
     * ワールド生成専用のシングルスレッドエグゼキュータ。
     * 非デーモンスレッドを使用してサーバー停止時の中断を防ぐ。
     */
    private static final ExecutorService worldGenExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "FoliaPhantom-WorldGen-Worker");
                t.setDaemon(false);
                return t;
            });

    static {
        Runtime.getRuntime().addShutdownHook(
                new Thread(FoliaPatcher::shutdownWorldGenExecutor,
                        "FoliaPhantom-WorldGen-Shutdown"));
    }

    /** プラグインインスタンス（プラグインモード時に onEnable で設定） */
    public static Plugin plugin;

    private FoliaPatcher() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========================================================================
    // BukkitScheduler → Folia スケジューラ
    // ========================================================================

    public static BukkitTask runTask(
            @SuppressWarnings("unused") BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task) {
        Location fallback = getFallbackLocation();
        if (fallback != null) {
            final Location loc = fallback;
            return scheduleOnce(plugin, task,
                    action -> Bukkit.getRegionScheduler().run(plugin, loc, action));
        }
        return scheduleOnce(plugin, task,
                action -> Bukkit.getGlobalRegionScheduler().run(plugin, action));
    }

    public static BukkitTask runTask(
            BukkitScheduler scheduler,
            Plugin plugin,
            Consumer<? super BukkitTask> task) {
        return runTask(scheduler, plugin, () -> task.accept(currentTaskPlaceholder(plugin)));
    }

    public static BukkitTask runTaskLater(
            @SuppressWarnings("unused") BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task,
            long delay) {
        Location fallback = getFallbackLocation();
        if (fallback != null) {
            final Location loc = fallback;
            return scheduleOnce(plugin, task,
                    action -> Bukkit.getRegionScheduler().runDelayed(
                            plugin, loc, action, delay));
        }
        return scheduleOnce(plugin, task,
                action -> Bukkit.getGlobalRegionScheduler().runDelayed(
                        plugin, action, delay));
    }

    public static BukkitTask runTaskLater(
            BukkitScheduler scheduler,
            Plugin plugin,
            Consumer<? super BukkitTask> task,
            long delay) {
        return runTaskLater(scheduler, plugin,
                () -> task.accept(currentTaskPlaceholder(plugin)), delay);
    }

    public static BukkitTask runTaskTimer(
            @SuppressWarnings("unused") BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task,
            long delay,
            long period) {
        Location fallback = getFallbackLocation();
        if (fallback != null) {
            return wrapTask(plugin,
                    Bukkit.getRegionScheduler().runAtFixedRate(
                            plugin, fallback, st -> task.run(), delay, period));
        }
        return wrapTask(plugin,
                Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                        plugin, st -> task.run(), delay, period));
    }

    public static BukkitTask runTaskTimer(
            BukkitScheduler scheduler,
            Plugin plugin,
            Consumer<? super BukkitTask> task,
            long delay,
            long period) {
        return runTaskTimer(scheduler, plugin,
                () -> task.accept(currentTaskPlaceholder(plugin)), delay, period);
    }

    public static BukkitTask runTaskAsynchronously(
            @SuppressWarnings("unused") BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task) {
        return scheduleOnce(plugin, task,
                action -> Bukkit.getAsyncScheduler().runNow(plugin, action));
    }

    public static BukkitTask runTaskAsynchronously(
            BukkitScheduler scheduler,
            Plugin plugin,
            Consumer<? super BukkitTask> task) {
        return runTaskAsynchronously(scheduler, plugin,
                () -> task.accept(currentTaskPlaceholder(plugin)));
    }

    public static BukkitTask runTaskLaterAsynchronously(
            @SuppressWarnings("unused") BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task,
            long delay) {
        return scheduleOnce(plugin, task,
                action -> Bukkit.getAsyncScheduler().runDelayed(
                        plugin, action, delay * 50L, TimeUnit.MILLISECONDS));
    }

    public static BukkitTask runTaskLaterAsynchronously(
            BukkitScheduler scheduler,
            Plugin plugin,
            Consumer<? super BukkitTask> task,
            long delay) {
        return runTaskLaterAsynchronously(scheduler, plugin,
                () -> task.accept(currentTaskPlaceholder(plugin)), delay);
    }

    public static BukkitTask runTaskTimerAsynchronously(
            @SuppressWarnings("unused") BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task,
            long delay,
            long period) {
        return wrapTask(plugin,
                Bukkit.getAsyncScheduler().runAtFixedRate(
                        plugin, st -> task.run(),
                        delay * 50L, period * 50L, TimeUnit.MILLISECONDS));
    }

    public static BukkitTask runTaskTimerAsynchronously(
            BukkitScheduler scheduler,
            Plugin plugin,
            Consumer<? super BukkitTask> task,
            long delay,
            long period) {
        return runTaskTimerAsynchronously(scheduler, plugin,
                () -> task.accept(currentTaskPlaceholder(plugin)), delay, period);
    }

    // ========================================================================
    // レガシースケジューラ互換
    // ========================================================================

    public static int scheduleSyncDelayedTask(
            BukkitScheduler scheduler, Plugin plugin, Runnable task, long delay) {
        return runTaskLater(scheduler, plugin, task, delay).getTaskId();
    }

    public static int scheduleSyncDelayedTask(
            BukkitScheduler scheduler, Plugin plugin, Runnable task) {
        return runTask(scheduler, plugin, task).getTaskId();
    }

    public static int scheduleAsyncDelayedTask(
            BukkitScheduler scheduler, Plugin plugin, Runnable task, long delay) {
        return runTaskLaterAsynchronously(scheduler, plugin, task, delay).getTaskId();
    }

    public static int scheduleAsyncDelayedTask(
            BukkitScheduler scheduler, Plugin plugin, Runnable task) {
        return runTaskAsynchronously(scheduler, plugin, task).getTaskId();
    }

    public static int scheduleSyncRepeatingTask(
            BukkitScheduler scheduler, Plugin plugin, Runnable task,
            long delay, long period) {
        return runTaskTimer(scheduler, plugin, task, delay, period).getTaskId();
    }

    public static int scheduleAsyncRepeatingTask(
            BukkitScheduler scheduler, Plugin plugin, Runnable task,
            long delay, long period) {
        return runTaskTimerAsynchronously(scheduler, plugin, task, delay, period).getTaskId();
    }

    public static <T> Future<T> callSyncMethod(
            BukkitScheduler scheduler, Plugin plugin, Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runTask(scheduler, plugin, () -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    // ========================================================================
    // BukkitRunnable → 静的メソッド
    // ========================================================================

    public static BukkitTask runTask_onRunnable(Runnable runnable, Plugin plugin) {
        return runTask(null, plugin, runnable);
    }

    public static BukkitTask runTaskLater_onRunnable(
            Runnable runnable, Plugin plugin, long delay) {
        return runTaskLater(null, plugin, runnable, delay);
    }

    public static BukkitTask runTaskTimer_onRunnable(
            Runnable runnable, Plugin plugin, long delay, long period) {
        return runTaskTimer(null, plugin, runnable, delay, period);
    }

    public static BukkitTask runTaskAsynchronously_onRunnable(
            Runnable runnable, Plugin plugin) {
        return runTaskAsynchronously(null, plugin, runnable);
    }

    public static BukkitTask runTaskLaterAsynchronously_onRunnable(
            Runnable runnable, Plugin plugin, long delay) {
        return runTaskLaterAsynchronously(null, plugin, runnable, delay);
    }

    public static BukkitTask runTaskTimerAsynchronously_onRunnable(
            Runnable runnable, Plugin plugin, long delay, long period) {
        return runTaskTimerAsynchronously(null, plugin, runnable, delay, period);
    }

    // ========================================================================
    // Block.setType スレッドセーフラッパー
    // ========================================================================

    public static void safeSetType(Block block, Material material) {
        if (Bukkit.isPrimaryThread()) {
            block.setType(material);
            return;
        }
        Plugin targetPlugin = plugin;
        if (targetPlugin == null) {
            log.warn("FoliaPatcher.plugin is null; executing Block.setType directly");
            block.setType(material);
            return;
        }
        Bukkit.getRegionScheduler().run(
                targetPlugin, block.getLocation(), task -> block.setType(material));
    }

    public static void safeSetTypeWithPhysics(
            Block block, Material material, boolean applyPhysics) {
        if (Bukkit.isPrimaryThread()) {
            block.setType(material, applyPhysics);
            return;
        }
        Plugin targetPlugin = plugin;
        if (targetPlugin == null) {
            log.warn("FoliaPatcher.plugin is null; executing Block.setType directly");
            block.setType(material, applyPhysics);
            return;
        }
        Bukkit.getRegionScheduler().run(
                targetPlugin, block.getLocation(),
                task -> block.setType(material, applyPhysics));
    }

    // ========================================================================
    // Block 操作全般のスレッドセーフラッパー
    // ========================================================================

    public static boolean safeBreakNaturally(Block block) {
        if (isOwningRegion(block.getLocation())) {
            return block.breakNaturally();
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return block.breakNaturally();
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().run(targetPlugin, block.getLocation(),
                task -> future.complete(block.breakNaturally()));
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("breakNaturally region dispatch failed", e);
            return block.breakNaturally();
        }
    }

    public static boolean safeBreakNaturally(Block block, ItemStack tool) {
        if (isOwningRegion(block.getLocation())) {
            return block.breakNaturally(tool);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return block.breakNaturally(tool);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().run(targetPlugin, block.getLocation(),
                task -> future.complete(block.breakNaturally(tool)));
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("breakNaturally region dispatch failed", e);
            return block.breakNaturally(tool);
        }
    }

    public static boolean safeApplyBoneMeal(Block block, BlockFace face) {
        if (isOwningRegion(block.getLocation())) {
            return block.applyBoneMeal(face);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return block.applyBoneMeal(face);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().run(targetPlugin, block.getLocation(),
                task -> future.complete(block.applyBoneMeal(face)));
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("applyBoneMeal region dispatch failed", e);
            return block.applyBoneMeal(face);
        }
    }

    public static void safeSetBlockData(Block block, BlockData data) {
        if (isOwningRegion(block.getLocation())) {
            block.setBlockData(data);
            return;
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            block.setBlockData(data);
            return;
        }
        Bukkit.getRegionScheduler().run(targetPlugin, block.getLocation(),
                task -> block.setBlockData(data));
    }

    public static void safeSetBlockData(Block block, BlockData data, boolean applyPhysics) {
        if (isOwningRegion(block.getLocation())) {
            block.setBlockData(data, applyPhysics);
            return;
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            block.setBlockData(data, applyPhysics);
            return;
        }
        Bukkit.getRegionScheduler().run(targetPlugin, block.getLocation(),
                task -> block.setBlockData(data, applyPhysics));
    }

    public static org.bukkit.block.BlockState safeGetState(Block block) {
        return block.getState();
    }

    public static BlockData safeGetBlockData(Block block) {
        return block.getBlockData();
    }

    public static Collection<ItemStack> safeGetDrops(Block block) {
        return block.getDrops();
    }

    public static Collection<ItemStack> safeGetDrops(Block block, ItemStack tool) {
        return block.getDrops(tool);
    }

    // ========================================================================
    // Entity 操作のスレッドセーフラッパー
    // ========================================================================

    public static boolean safeTeleport(Entity entity, Location location) {
        if (isOwningRegion(entity.getLocation())) {
            return entity.teleport(location);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return entity.teleport(location);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        entity.getScheduler().execute(targetPlugin,
                () -> future.complete(entity.teleport(location)),
                () -> future.completeExceptionally(
                        new IllegalStateException("Entity retired before teleport")),
                1L);
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("teleport region dispatch failed", e);
            return false;
        }
    }

    public static boolean safeTeleport(Entity entity, Location location,
                                       PlayerTeleportEvent.TeleportCause cause) {
        if (isOwningRegion(entity.getLocation())) {
            return entity.teleport(location, cause);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return entity.teleport(location, cause);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        entity.getScheduler().execute(targetPlugin,
                () -> future.complete(entity.teleport(location, cause)),
                () -> future.completeExceptionally(
                        new IllegalStateException("Entity retired before teleport")),
                1L);
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("teleport region dispatch failed", e);
            return false;
        }
    }

    public static void safeRemove(Entity entity) {
        if (isOwningRegion(entity.getLocation())) {
            entity.remove();
            return;
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            entity.remove();
            return;
        }
        entity.getScheduler().execute(targetPlugin, entity::remove, null, 1L);
    }

    public static void safeDamage(LivingEntity entity, double amount) {
        if (isOwningRegion(entity.getLocation())) {
            entity.damage(amount);
            return;
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            entity.damage(amount);
            return;
        }
        entity.getScheduler().execute(targetPlugin,
                () -> entity.damage(amount), null, 1L);
    }

    public static void safeDamage(LivingEntity entity, double amount, Entity damager) {
        if (isOwningRegion(entity.getLocation())) {
            entity.damage(amount, damager);
            return;
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            entity.damage(amount, damager);
            return;
        }
        entity.getScheduler().execute(targetPlugin,
                () -> entity.damage(amount, damager), null, 1L);
    }

    public static void safeSetHealth(LivingEntity entity, double health) {
        if (isOwningRegion(entity.getLocation())) {
            entity.setHealth(health);
            return;
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            entity.setHealth(health);
            return;
        }
        entity.getScheduler().execute(targetPlugin,
                () -> entity.setHealth(health), null, 1L);
    }

    public static boolean safeAddPotionEffect(LivingEntity entity, PotionEffect effect) {
        if (isOwningRegion(entity.getLocation())) {
            return entity.addPotionEffect(effect);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return entity.addPotionEffect(effect);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        entity.getScheduler().execute(targetPlugin,
                () -> future.complete(entity.addPotionEffect(effect)),
                () -> future.completeExceptionally(
                        new IllegalStateException("Entity retired")),
                1L);
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("addPotionEffect region dispatch failed", e);
            return false;
        }
    }

    public static boolean safeAddPotionEffect(LivingEntity entity, PotionEffect effect,
                                              boolean force) {
        if (isOwningRegion(entity.getLocation())) {
            return entity.addPotionEffect(effect, force);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return entity.addPotionEffect(effect, force);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        entity.getScheduler().execute(targetPlugin,
                () -> future.complete(entity.addPotionEffect(effect, force)),
                () -> future.completeExceptionally(
                        new IllegalStateException("Entity retired")),
                1L);
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("addPotionEffect region dispatch failed", e);
            return false;
        }
    }

    public static void safeSetFireTicks(Entity entity, int ticks) {
        if (isOwningRegion(entity.getLocation())) {
            entity.setFireTicks(ticks);
            return;
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            entity.setFireTicks(ticks);
            return;
        }
        entity.getScheduler().execute(targetPlugin,
                () -> entity.setFireTicks(ticks), null, 1L);
    }

    public static void safeSetVelocity(Entity entity, org.bukkit.util.Vector velocity) {
        if (isOwningRegion(entity.getLocation())) {
            entity.setVelocity(velocity);
            return;
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            entity.setVelocity(velocity);
            return;
        }
        entity.getScheduler().execute(targetPlugin,
                () -> entity.setVelocity(velocity), null, 1L);
    }

    // ========================================================================
    // ワールド操作のスレッドセーフラッパー
    // ========================================================================

    public static <T extends Entity> T safeSpawn(Location location, Class<T> clazz) {
        if (isOwningRegion(location)) {
            return location.getWorld().spawn(location, clazz);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return location.getWorld().spawn(location, clazz);
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().run(targetPlugin, location,
                task -> future.complete(location.getWorld().spawn(location, clazz)));
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("spawn region dispatch failed", e);
            return location.getWorld().spawn(location, clazz);
        }
    }

    public static Item safeDropItem(Location location, ItemStack item) {
        if (isOwningRegion(location)) {
            return location.getWorld().dropItem(location, item);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return location.getWorld().dropItem(location, item);
        }
        CompletableFuture<Item> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().run(targetPlugin, location,
                task -> future.complete(location.getWorld().dropItem(location, item)));
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("dropItem region dispatch failed", e);
            return location.getWorld().dropItem(location, item);
        }
    }

    public static Item safeDropItemNaturally(Location location, ItemStack item) {
        if (isOwningRegion(location)) {
            return location.getWorld().dropItemNaturally(location, item);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return location.getWorld().dropItemNaturally(location, item);
        }
        CompletableFuture<Item> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().run(targetPlugin, location,
                task -> future.complete(location.getWorld().dropItemNaturally(location, item)));
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("dropItemNaturally region dispatch failed", e);
            return location.getWorld().dropItemNaturally(location, item);
        }
    }

    public static boolean safeCreateExplosion(Location location, float power) {
        if (isOwningRegion(location)) {
            return location.getWorld().createExplosion(location, power);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return location.getWorld().createExplosion(location, power);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().run(targetPlugin, location,
                task -> future.complete(location.getWorld().createExplosion(location, power)));
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("createExplosion region dispatch failed", e);
            return location.getWorld().createExplosion(location, power);
        }
    }

    public static boolean safeCreateExplosion(Location location, float power, boolean setFire) {
        if (isOwningRegion(location)) {
            return location.getWorld().createExplosion(location, power, setFire);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return location.getWorld().createExplosion(location, power, setFire);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().run(targetPlugin, location,
                task -> future.complete(
                        location.getWorld().createExplosion(location, power, setFire)));
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("createExplosion region dispatch failed", e);
            return location.getWorld().createExplosion(location, power, setFire);
        }
    }

    public static LightningStrike safeStrikeLightning(Location location) {
        if (isOwningRegion(location)) {
            return location.getWorld().strikeLightning(location);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return location.getWorld().strikeLightning(location);
        }
        CompletableFuture<LightningStrike> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().run(targetPlugin, location,
                task -> future.complete(location.getWorld().strikeLightning(location)));
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("strikeLightning region dispatch failed", e);
            return location.getWorld().strikeLightning(location);
        }
    }

    // ========================================================================
    // Player / Inventory 操作のスレッドセーフラッパー
    // ========================================================================

    public static InventoryView safeOpenInventory(Player player, Inventory inventory) {
        if (isOwningRegion(player.getLocation())) {
            return player.openInventory(inventory);
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            return player.openInventory(inventory);
        }
        CompletableFuture<InventoryView> future = new CompletableFuture<>();
        player.getScheduler().execute(targetPlugin,
                () -> future.complete(player.openInventory(inventory)),
                () -> future.completeExceptionally(
                        new IllegalStateException("Player retired")),
                1L);
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("openInventory region dispatch failed", e);
            return player.openInventory(inventory);
        }
    }

    public static void safeCloseInventory(Player player) {
        if (isOwningRegion(player.getLocation())) {
            player.closeInventory();
            return;
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            player.closeInventory();
            return;
        }
        player.getScheduler().execute(targetPlugin, player::closeInventory, null, 1L);
    }

    public static void safeKickPlayer(Player player, String message) {
        if (isOwningRegion(player.getLocation())) {
            player.kickPlayer(message);
            return;
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            player.kickPlayer(message);
            return;
        }
        player.getScheduler().execute(targetPlugin,
                () -> player.kickPlayer(message), null, 1L);
    }

    public static void safeSetGameMode(Player player, GameMode gameMode) {
        if (isOwningRegion(player.getLocation())) {
            player.setGameMode(gameMode);
            return;
        }
        Plugin targetPlugin = resolvePlugin();
        if (targetPlugin == null) {
            player.setGameMode(gameMode);
            return;
        }
        player.getScheduler().execute(targetPlugin,
                () -> player.setGameMode(gameMode), null, 1L);
    }

    // ========================================================================
    // ワールド生成
    // ========================================================================

    public static World createWorld(WorldCreator creator) {
        Future<World> future = worldGenExecutor.submit(creator::createWorld);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("World creation was interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to create world: " + creator.name(), e.getCause());
        }
    }

    public static ChunkGenerator getDefaultWorldGenerator(
            Plugin owner, String worldName, String id) {
        ChunkGenerator original = owner.getDefaultWorldGenerator(worldName, id);
        if (original == null) {
            return null;
        }
        return new FoliaChunkGenerator(original);
    }

    // ========================================================================
    // タスク管理
    // ========================================================================

    public static void cancelTask(
            @SuppressWarnings("unused") BukkitScheduler ignored, int taskId) {
        ScheduledTask task = runningTasks.remove(taskId);
        if (task != null) {
            task.cancel();
            log.debug("Cancelled task id={}", taskId);
        }
    }

    public static void cancelTasks(
            @SuppressWarnings("unused") BukkitScheduler ignored, Plugin target) {
        runningTasks.forEach((id, task) -> {
            if (target.equals(task.getOwningPlugin())) {
                task.cancel();
                runningTasks.remove(id);
            }
        });
        log.info("Cancelled all tasks for plugin '{}'", target.getName());
    }

    public static boolean isCurrentlyRunning(
            @SuppressWarnings("unused") BukkitScheduler ignored, int taskId) {
        ScheduledTask task = runningTasks.get(taskId);
        return task != null && !task.isCancelled();
    }

    public static boolean isQueued(
            @SuppressWarnings("unused") BukkitScheduler ignored, int taskId) {
        ScheduledTask task = runningTasks.get(taskId);
        return task != null && !task.isCancelled();
    }

    public static List<BukkitTask> getPendingTasks(
            @SuppressWarnings("unused") BukkitScheduler ignored) {
        List<BukkitTask> tasks = new ArrayList<>();
        runningTasks.forEach((id, task) -> tasks.add(new FoliaBukkitTask(id, plugin, task)));
        return tasks;
    }

    public static List<BukkitWorker> getActiveWorkers(
            @SuppressWarnings("unused") BukkitScheduler ignored) {
        return List.of();
    }

    public static void cancelAllTasks() {
        runningTasks.forEach((id, task) -> task.cancel());
        runningTasks.clear();
        log.info("Cancelled all running tasks");
    }

    /**
     * FoliaPatcher を安全にシャットダウンする。
     * プラグインモードでは onDisable から呼び出す。
     */
    public static void shutdown() {
        cancelAllTasks();
        shutdownWorldGenExecutor();
        log.info("FoliaPatcher shut down");
    }

    // ========================================================================
    // 内部ヘルパー
    // ========================================================================

    /**
     * 1回限りのタスクをスケジュールし、完了後に runningTasks から自動削除する。
     *
     * <p>taskId を事前に確保してクロージャに取り込み、タスク完了の finally ブロックで
     * マップから削除する。これにより繰り返しタスクではなく単発タスクのメモリリークを防ぐ。
     * put 前にタスクが完了するレースコンディションは put 後の ExecutionState チェックで対処する。</p>
     */
    private static BukkitTask scheduleOnce(Plugin plugin, Runnable task,
            TaskSchedulerFactory factory) {
        int taskId = taskIdCounter.getAndIncrement();
        ScheduledTask st = factory.schedule(ignored -> {
            try {
                task.run();
            } finally {
                runningTasks.remove(taskId);
            }
        });
        FoliaBukkitTask result = new FoliaBukkitTask(taskId, plugin, st);
        runningTasks.put(taskId, st);
        if (st.getExecutionState() == ScheduledTask.ExecutionState.FINISHED) {
            runningTasks.remove(taskId);
        }
        return result;
    }

    /** 繰り返しタスク用のラッパー（自動削除なし）。 */
    private static BukkitTask wrapTask(Plugin plugin, ScheduledTask scheduledTask) {
        int taskId = taskIdCounter.getAndIncrement();
        FoliaBukkitTask task = new FoliaBukkitTask(taskId, plugin, scheduledTask);
        runningTasks.put(taskId, scheduledTask);
        return task;
    }

    private static Plugin resolvePlugin() {
        if (plugin != null) {
            return plugin;
        }
        log.warn("FoliaPatcher.plugin is null; executing operation directly");
        return null;
    }

    private static boolean isOwningRegion(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        try {
            return Bukkit.getServer().isOwnedByCurrentRegion(location);
        } catch (NoSuchMethodError | Exception e) {
            return Bukkit.isPrimaryThread();
        }
    }

    private static Location getFallbackLocation() {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return null;
        }
        return worlds.get(0).getSpawnLocation();
    }

    private static BukkitTask currentTaskPlaceholder(Plugin plugin) {
        return new FoliaBukkitTask(-1, plugin, ScheduledTaskStub.INSTANCE);
    }

    private static void shutdownWorldGenExecutor() {
        worldGenExecutor.shutdown();
        try {
            if (!worldGenExecutor.awaitTermination(30L, TimeUnit.SECONDS)) {
                worldGenExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            worldGenExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ========================================================================
    // 内部型定義
    // ========================================================================

    /** scheduleOnce のスケジューラファクトリ。 */
    @FunctionalInterface
    private interface TaskSchedulerFactory {
        ScheduledTask schedule(Consumer<ScheduledTask> action);
    }

    public static final class FoliaBukkitTask implements BukkitTask {

        private final int taskId;
        private final Plugin owner;
        private final ScheduledTask scheduledTask;
        private volatile boolean cancelled;

        FoliaBukkitTask(int taskId, Plugin owner, ScheduledTask scheduledTask) {
            this.taskId = taskId;
            this.owner = owner;
            this.scheduledTask = scheduledTask;
            this.cancelled = false;
        }

        @Override
        public int getTaskId() {
            return this.taskId;
        }

        @Override
        public Plugin getOwner() {
            return this.owner;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled || this.scheduledTask.isCancelled();
        }

        @Override
        public void cancel() {
            this.cancelled = true;
            runningTasks.remove(this.taskId);
            this.scheduledTask.cancel();
        }
    }

    private enum ScheduledTaskStub implements ScheduledTask {
        INSTANCE;

        @Override
        public Plugin getOwningPlugin() {
            return plugin;
        }

        @Override
        public boolean isRepeatingTask() {
            return false;
        }

        @Override
        public CancelledState cancel() {
            return CancelledState.CANCELLED_BY_CALLER;
        }

        @Override
        public ExecutionState getExecutionState() {
            return ExecutionState.FINISHED;
        }
    }

    public static final class FoliaChunkGenerator extends ChunkGenerator {

        private final ChunkGenerator delegate;

        public FoliaChunkGenerator(ChunkGenerator delegate) {
            this.delegate = delegate;
        }

        public ChunkGenerator getDelegate() {
            return this.delegate;
        }
    }
}
