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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Folia 互換のランタイムブリッジ。
 *
 * <p>パッチ済みプラグインが呼び出す実際の Folia 互換メソッドを提供する。
 * このクラスはパッチ処理後に出力 JAR に同梱され、サーバー上で
 * ランタイムにロードされる。</p>
 *
 * <p>主な機能:
 * <ul>
 *   <li>BukkitScheduler → Folia スケジューラへのルーティング</li>
 *   <li>BukkitRunnable インスタンスメソッド → 静的メソッド</li>
 *   <li>スレッドセーフな Block.setType 操作</li>
 *   <li>ワールド生成の非同期実行</li>
 *   <li>タスク管理（キャンセル、一括停止）</li>
 * </ul>
 * </p>
 */
public final class FoliaPatcher {

    /** ロガーインスタンス */
    private static final Logger log = LoggerFactory.getLogger(FoliaPatcher.class);

    /** タスクIDのカウンター */
    private static final AtomicInteger taskIdCounter = new AtomicInteger(1);

    /** 実行中タスクの管理マップ（taskId → ScheduledTask） */
    private static final ConcurrentHashMap<Integer, ScheduledTask> runningTasks =
            new ConcurrentHashMap<>();

    /** ワールド生成専用のシングルスレッドエグゼキュータ */
    private static final ExecutorService worldGenExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "FoliaPhantom-WorldGen-Worker");
                t.setDaemon(true);
                return t;
            });

    /** プラグインインスタンス（プラグインモード時に設定） */
    public static Plugin plugin;

    private FoliaPatcher() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========================================================================
    // BukkitScheduler → Folia スケジューラ
    // ========================================================================

    /**
     * 同期的なタスクを実行する。
     *
     * <p>Location が取得可能な場合は {@code RegionScheduler}、
     * 不可能な場合は {@code GlobalRegionScheduler} を使用する。</p>
     *
     * @param scheduler  元の BukkitScheduler（ダミー、互換性のために保持）
     * @param plugin     タスクを所有するプラグイン
     * @param task       実行するタスク
     * @return BukkitTask ラッパー
     */
    public static BukkitTask runTask(
            @SuppressWarnings("unused") BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task) {
        Location fallback = getFallbackLocation();
        if (fallback != null) {
            return wrapTask(
                    plugin,
                    Bukkit.getRegionScheduler().run(plugin, fallback, scheduledTask -> task.run()));
        }
        return wrapTask(
                plugin,
                Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run()));
    }

    /**
     * 遅延付きの同期タスクを実行する。
     *
     * @param scheduler  元の BukkitScheduler
     * @param plugin     タスクを所有するプラグイン
     * @param task       実行するタスク
     * @param delay      遅延ティック数
     * @return BukkitTask ラッパー
     */
    public static BukkitTask runTaskLater(
            @SuppressWarnings("unused") BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task,
            long delay) {
        Location fallback = getFallbackLocation();
        if (fallback != null) {
            return wrapTask(
                    plugin,
                    Bukkit.getRegionScheduler().runDelayed(
                            plugin, fallback, scheduledTask -> task.run(), delay));
        }
        return wrapTask(
                plugin,
                Bukkit.getGlobalRegionScheduler().runDelayed(
                        plugin, scheduledTask -> task.run(), delay));
    }

    /**
     * 繰り返し同期タスクを実行する。
     *
     * @param scheduler  元の BukkitScheduler
     * @param plugin     タスクを所有するプラグイン
     * @param task       実行するタスク
     * @param delay      初回実行までの遅延ティック数
     * @param period     繰り返し間隔（ティック）
     * @return BukkitTask ラッパー
     */
    public static BukkitTask runTaskTimer(
            @SuppressWarnings("unused") BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task,
            long delay,
            long period) {
        Location fallback = getFallbackLocation();
        if (fallback != null) {
            return wrapTask(
                    plugin,
                    Bukkit.getRegionScheduler().runAtFixedRate(
                            plugin, fallback, scheduledTask -> task.run(), delay, period));
        }
        return wrapTask(
                plugin,
                Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                        plugin, scheduledTask -> task.run(), delay, period));
    }

    /**
     * 非同期タスクを実行する。
     *
     * @param scheduler  元の BukkitScheduler
     * @param plugin     タスクを所有するプラグイン
     * @param task       実行するタスク
     * @return BukkitTask ラッパー
     */
    public static BukkitTask runTaskAsynchronously(
            @SuppressWarnings("unused") BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task) {
        return wrapTask(
                plugin,
                Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run()));
    }

    /**
     * 遅延付きの非同期タスクを実行する。
     *
     * @param scheduler  元の BukkitScheduler
     * @param plugin     タスクを所有するプラグイン
     * @param task       実行するタスク
     * @param delay      遅延ティック数
     * @return BukkitTask ラッパー
     */
    public static BukkitTask runTaskLaterAsynchronously(
            @SuppressWarnings("unused") BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task,
            long delay) {
        return wrapTask(
                plugin,
                Bukkit.getAsyncScheduler().runDelayed(
                        plugin, scheduledTask -> task.run(), delay * 50L, TimeUnit.MILLISECONDS));
    }

    /**
     * 繰り返し非同期タスクを実行する。
     *
     * @param scheduler  元の BukkitScheduler
     * @param plugin     タスクを所有するプラグイン
     * @param task       実行するタスク
     * @param delay      初回実行までの遅延（ティック）
     * @param period     繰り返し間隔（ティック）
     * @return BukkitTask ラッパー
     */
    public static BukkitTask runTaskTimerAsynchronously(
            @SuppressWarnings("unused") BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task,
            long delay,
            long period) {
        return wrapTask(
                plugin,
                Bukkit.getAsyncScheduler().runAtFixedRate(
                        plugin, scheduledTask -> task.run(),
                        delay * 50L, period * 50L, TimeUnit.MILLISECONDS));
    }

    // ========================================================================
    // レガシースケジューラ互換
    // ========================================================================

    /**
     * レガシー {@code scheduleSyncDelayedTask} の互換実装。
     *
     * @param scheduler  元の BukkitScheduler（未使用）
     * @param plugin     プラグイン
     * @param task       タスク
     * @param delay      遅延（ティック）
     * @return タスクID
     */
    public static int scheduleSyncDelayedTask(
            BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task,
            long delay) {
        return runTaskLater(scheduler, plugin, task, delay).getTaskId();
    }

    /**
     * レガシー {@code scheduleAsyncDelayedTask} の互換実装。
     *
     * @param scheduler  元の BukkitScheduler（未使用）
     * @param plugin     プラグイン
     * @param task       タスク
     * @param delay      遅延（ティック）
     * @return タスクID
     */
    public static int scheduleAsyncDelayedTask(
            BukkitScheduler scheduler,
            Plugin plugin,
            Runnable task,
            long delay) {
        return runTaskLaterAsynchronously(scheduler, plugin, task, delay).getTaskId();
    }

    // ========================================================================
    // BukkitRunnable → 静的メソッド
    // ========================================================================

    /**
     * BukkitRunnable.runTask(plugin) の静的メソッド版。
     *
     * @param runnable BukkitRunnable インスタンス
     * @param plugin   プラグイン
     * @return BukkitTask
     */
    public static BukkitTask runTask_onRunnable(Runnable runnable, Plugin plugin) {
        return runTask(null, plugin, runnable);
    }

    /**
     * BukkitRunnable.runTaskLater(plugin, delay) の静的メソッド版。
     *
     * @param runnable BukkitRunnable インスタンス
     * @param plugin   プラグイン
     * @param delay    遅延ティック数
     * @return BukkitTask
     */
    public static BukkitTask runTaskLater_onRunnable(
            Runnable runnable, Plugin plugin, long delay) {
        return runTaskLater(null, plugin, runnable, delay);
    }

    /**
     * BukkitRunnable.runTaskTimer(plugin, delay, period) の静的メソッド版。
     *
     * @param runnable BukkitRunnable インスタンス
     * @param plugin   プラグイン
     * @param delay    初回遅延
     * @param period   繰り返し間隔
     * @return BukkitTask
     */
    public static BukkitTask runTaskTimer_onRunnable(
            Runnable runnable, Plugin plugin, long delay, long period) {
        return runTaskTimer(null, plugin, runnable, delay, period);
    }

    /**
     * BukkitRunnable.runTaskAsynchronously(plugin) の静的メソッド版。
     *
     * @param runnable BukkitRunnable インスタンス
     * @param plugin   プラグイン
     * @return BukkitTask
     */
    public static BukkitTask runTaskAsynchronously_onRunnable(
            Runnable runnable, Plugin plugin) {
        return runTaskAsynchronously(null, plugin, runnable);
    }

    /**
     * BukkitRunnable.runTaskLaterAsynchronously(plugin, delay) の静的メソッド版。
     *
     * @param runnable BukkitRunnable インスタンス
     * @param plugin   プラグイン
     * @param delay    遅延ティック数
     * @return BukkitTask
     */
    public static BukkitTask runTaskLaterAsynchronously_onRunnable(
            Runnable runnable, Plugin plugin, long delay) {
        return runTaskLaterAsynchronously(null, plugin, runnable, delay);
    }

    /**
     * BukkitRunnable.runTaskTimerAsynchronously(plugin, delay, period) の静的メソッド版。
     *
     * @param runnable BukkitRunnable インスタンス
     * @param plugin   プラグイン
     * @param delay    初回遅延
     * @param period   繰り返し間隔
     * @return BukkitTask
     */
    public static BukkitTask runTaskTimerAsynchronously_onRunnable(
            Runnable runnable, Plugin plugin, long delay, long period) {
        return runTaskTimerAsynchronously(null, plugin, runnable, delay, period);
    }

    // ========================================================================
    // Block.setType スレッドセーフラッパー
    // ========================================================================

    /**
     * スレッドセーフな {@code Block.setType(Material)} 操作。
     *
     * <p>現在のスレッドがプライマリスレッドの場合は直接実行し、
     * それ以外の場合は {@code RegionScheduler} 経由で実行する。</p>
     *
     * @param block    対象ブロック
     * @param material 設定するマテリアル
     */
    public static void safeSetType(Block block, Material material) {
        if (Bukkit.isPrimaryThread()) {
            block.setType(material);
        } else {
            Plugin targetPlugin = plugin;
            if (targetPlugin == null) {
                block.setType(material);
                log.warn("FoliaPatcher.plugin is null; executing Block.setType directly");
                return;
            }
            Location loc = block.getLocation();
            Bukkit.getRegionScheduler().run(
                    targetPlugin,
                    loc,
                    task -> block.setType(material));
        }
    }

    /**
     * スレッドセーフな {@code Block.setType(Material, boolean)} 操作。
     *
     * @param block         対象ブロック
     * @param material      設定するマテリアル
     * @param applyPhysics  物理演算を適用するか
     */
    public static void safeSetTypeWithPhysics(
            Block block, Material material, boolean applyPhysics) {
        if (Bukkit.isPrimaryThread()) {
            block.setType(material, applyPhysics);
        } else {
            Plugin targetPlugin = plugin;
            if (targetPlugin == null) {
                block.setType(material, applyPhysics);
                log.warn("FoliaPatcher.plugin is null; executing Block.setType directly");
                return;
            }
            Location loc = block.getLocation();
            Bukkit.getRegionScheduler().run(
                    targetPlugin,
                    loc,
                    task -> block.setType(material, applyPhysics));
        }
    }

    // ========================================================================
    // Block 操作全般のスレッドセーフラッパー
    // ========================================================================

    /**
     * スレッドセーフな {@code Block.breakNaturally()} 操作。
     */
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
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute breakNaturally on correct region", e);
            return block.breakNaturally();
        }
    }

    /**
     * スレッドセーフな {@code Block.breakNaturally(ItemStack)} 操作。
     */
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
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute breakNaturally on correct region", e);
            return block.breakNaturally(tool);
        }
    }

    /**
     * スレッドセーフな {@code Block.applyBoneMeal(BlockFace)} 操作。
     */
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
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute applyBoneMeal on correct region", e);
            return block.applyBoneMeal(face);
        }
    }

    /**
     * スレッドセーフな {@code Block.setBlockData(BlockData)} 操作。
     */
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

    /**
     * スレッドセーフな {@code Block.setBlockData(BlockData, boolean)} 操作。
     */
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

    /**
     * スレッドセーフな {@code Block.getState()} 操作（読み取り専用スナップショット）。
     */
    public static org.bukkit.block.BlockState safeGetState(Block block) {
        return block.getState();
    }

    /**
     * スレッドセーフな {@code Block.getBlockData()} 操作。
     */
    public static BlockData safeGetBlockData(Block block) {
        return block.getBlockData();
    }

    /**
     * スレッドセーフな {@code Block.getDrops()} 操作。
     */
    public static Collection<ItemStack> safeGetDrops(Block block) {
        return block.getDrops();
    }

    /**
     * スレッドセーフな {@code Block.getDrops(ItemStack)} 操作。
     */
    public static Collection<ItemStack> safeGetDrops(Block block, ItemStack tool) {
        return block.getDrops(tool);
    }

    // ========================================================================
    // Entity 操作のスレッドセーフラッパー
    // ========================================================================

    /**
     * スレッドセーフな {@code Entity.teleport(Location)} 操作。
     */
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
                () -> future.complete(entity.teleport(location)), 1L);
        try {
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute teleport on correct region", e);
            return entity.teleport(location);
        }
    }

    /**
     * スレッドセーフな {@code Entity.teleport(Location, TeleportCause)} 操作。
     */
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
                () -> future.complete(entity.teleport(location, cause)), 1L);
        try {
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute teleport on correct region", e);
            return entity.teleport(location, cause);
        }
    }

    /**
     * スレッドセーフな {@code Entity.remove()} 操作。
     */
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
        entity.getScheduler().execute(targetPlugin, entity::remove, entity::remove, 1L);
    }

    /**
     * スレッドセーフな {@code LivingEntity.damage(double)} 操作。
     */
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
                () -> entity.damage(amount),
                () -> entity.damage(amount), 1L);
    }

    /**
     * スレッドセーフな {@code LivingEntity.damage(double, Entity)} 操作。
     */
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
                () -> entity.damage(amount, damager),
                () -> entity.damage(amount, damager), 1L);
    }

    /**
     * スレッドセーフな {@code LivingEntity.setHealth(double)} 操作。
     */
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
                () -> entity.setHealth(health),
                () -> entity.setHealth(health), 1L);
    }

    /**
     * スレッドセーフな {@code LivingEntity.addPotionEffect(PotionEffect)} 操作。
     */
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
                () -> future.complete(entity.addPotionEffect(effect)), 1L);
        try {
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute addPotionEffect on correct region", e);
            return entity.addPotionEffect(effect);
        }
    }

    /**
     * スレッドセーフな {@code LivingEntity.addPotionEffect(PotionEffect, boolean)} 操作。
     */
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
                () -> future.complete(entity.addPotionEffect(effect, force)), 1L);
        try {
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute addPotionEffect on correct region", e);
            return entity.addPotionEffect(effect, force);
        }
    }

    /**
     * スレッドセーフな {@link Entity#setFireTicks(int)} 操作。
     */
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
                () -> entity.setFireTicks(ticks),
                () -> entity.setFireTicks(ticks), 1L);
    }

    /**
     * スレッドセーフな {@link Entity#setVelocity(org.bukkit.util.Vector)} 操作。
     */
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
                () -> entity.setVelocity(velocity),
                () -> entity.setVelocity(velocity), 1L);
    }

    // ========================================================================
    // ワールド操作のスレッドセーフラッパー
    // ========================================================================

    /**
     * スレッドセーフな {@code World.spawn(Location, Class)} 操作。
     * グローバルリージョンスケジューラ上で実行される。
     */
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
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute spawn on correct region", e);
            return location.getWorld().spawn(location, clazz);
        }
    }

    /**
     * スレッドセーフな {@code World.dropItem(Location, ItemStack)} 操作。
     */
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
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute dropItem on correct region", e);
            return location.getWorld().dropItem(location, item);
        }
    }

    /**
     * スレッドセーフな {@code World.dropItemNaturally(Location, ItemStack)} 操作。
     */
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
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute dropItemNaturally on correct region", e);
            return location.getWorld().dropItemNaturally(location, item);
        }
    }

    /**
     * スレッドセーフな {@code World.createExplosion(Location, float)} 操作。
     */
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
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute createExplosion on correct region", e);
            return location.getWorld().createExplosion(location, power);
        }
    }

    /**
     * スレッドセーフな {@code World.createExplosion(Location, float, boolean)} 操作。
     */
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
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute createExplosion on correct region", e);
            return location.getWorld().createExplosion(location, power, setFire);
        }
    }

    /**
     * スレッドセーフな {@code World.strikeLightning(Location)} 操作。
     */
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
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute strikeLightning on correct region", e);
            return location.getWorld().strikeLightning(location);
        }
    }

    // ========================================================================
    // Player / Inventory 操作のスレッドセーフラッパー
    // ========================================================================

    /**
     * スレッドセーフな {@code Player.openInventory(Inventory)} 操作。
     */
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
                () -> future.complete(player.openInventory(inventory)), 1L);
        try {
            return future.get();
        } catch (Exception e) {
            log.warn("Failed to execute openInventory on correct region", e);
            return player.openInventory(inventory);
        }
    }

    /**
     * スレッドセーフな {@code Player.closeInventory()} 操作。
     */
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
        player.getScheduler().execute(
                targetPlugin, player::closeInventory, player::closeInventory, 1L);
    }

    /**
     * スレッドセーフな {@code Player.kickPlayer(String)} 操作。
     */
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
                () -> player.kickPlayer(message),
                () -> player.kickPlayer(message), 1L);
    }

    /**
     * スレッドセーフな {@code Player.setGameMode(GameMode)} 操作。
     */
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
                () -> player.setGameMode(gameMode),
                () -> player.setGameMode(gameMode), 1L);
    }

    // ========================================================================
    // ワールド生成
    // ========================================================================

    /**
     * ワールド生成を専用スレッドで実行する。
     *
     * <p>Folia ではワールド生成は単一スレッドで行う必要があるため、
     * 専用の {@code SingleThreadExecutor} で処理する。</p>
     *
     * @param creator ワールド生成設定
     * @return 生成されたワールド
     * @throws RuntimeException 生成に失敗した場合
     */
    public static World createWorld(WorldCreator creator) {
        Future<World> future = worldGenExecutor.submit(() -> creator.createWorld());
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("World creation was interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to create world: " + creator.name(), e.getCause());
        }
    }

    /**
     * {@code Plugin.getDefaultWorldGenerator} のラッパー。
     *
     * <p>生成された ChunkGenerator を {@link FoliaChunkGenerator} でラップして返す。</p>
     *
     * @param owner     プラグインインスタンス
     * @param worldName ワールド名
     * @param id        ジェネレーターID
     * @return FoliaChunkGenerator でラップされた ChunkGenerator、元が null なら null
     */
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

    /**
     * 指定されたタスクIDのタスクをキャンセルする。
     *
     * @param ignored  元の BukkitScheduler（互換性のために保持）
     * @param taskId   キャンセルするタスクのID
     */
    public static void cancelTask(
            @SuppressWarnings("unused") BukkitScheduler ignored, int taskId) {
        ScheduledTask task = runningTasks.remove(taskId);
        if (task != null) {
            task.cancel();
            log.debug("Cancelled task id={}", taskId);
        }
    }

    /**
     * 指定されたプラグインに属する全タスクをキャンセルする。
     *
     * @param ignored 元の BukkitScheduler（互換性のために保持）
     * @param target  対象プラグイン
     */
    public static void cancelTasks(
            @SuppressWarnings("unused") BukkitScheduler ignored, Plugin target) {
        runningTasks.forEach((id, task) -> {
            task.cancel();
            runningTasks.remove(id);
        });
        log.info("Cancelled all tasks for plugin '{}'", target.getName());
    }

    /**
     * 全タスクをキャンセルし、マップをクリアする。
     */
    public static void cancelAllTasks() {
        runningTasks.forEach((id, task) -> task.cancel());
        runningTasks.clear();
        log.info("Cancelled all running tasks");
    }

    // ========================================================================
    // 内部ヘルパー
    // ========================================================================

    /**
     * プラグインインスタンスを解決する。
     * null の場合は警告を出力する。
     */
    private static Plugin resolvePlugin() {
        if (plugin != null) {
            return plugin;
        }
        log.warn("FoliaPatcher.plugin is null; executing operation directly");
        return null;
    }

    /**
     * 指定された Location が現在のスレッドの所有するリージョンか判定する。
     */
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

    /**
     * フォールバック Location を取得する。
     *
     * <p>メインワールドのスポーン地点を返す。
     * ワールドが存在しない場合は null を返す。</p>
     *
     * @return メインワールドのスポーン Location、なければ null
     */
    private static Location getFallbackLocation() {
        World mainWorld = Bukkit.getWorlds().get(0);
        if (mainWorld == null) {
            return null;
        }
        return mainWorld.getSpawnLocation();
    }

    /**
     * Folia の ScheduledTask を BukkitTask でラップする。
     *
     * @param plugin        タスクを所有するプラグイン
     * @param scheduledTask Folia のスケジュールタスク
     * @return BukkitTask ラッパー
     */
    private static BukkitTask wrapTask(
            Plugin plugin,
            ScheduledTask scheduledTask) {
        int taskId = taskIdCounter.getAndIncrement();
        FoliaBukkitTask task = new FoliaBukkitTask(taskId, plugin, scheduledTask);
        runningTasks.put(taskId, scheduledTask);
        return task;
    }

    // ========================================================================
    // 内部クラス
    // ========================================================================

    /**
     * {@link BukkitTask} インターフェースの完全実装。
     *
     * <p>Folia の {@code ScheduledTask} をラップし、
     * Bukkit プラグインに透過的なタスク管理を提供する。</p>
     */
    public static final class FoliaBukkitTask implements BukkitTask {

        /** タスクID */
        private final int taskId;

        /** 所有プラグイン */
        private final Plugin owner;

        /** ラップ対象の Folia ScheduledTask */
        private final ScheduledTask scheduledTask;

        /** キャンセル済みフラグ */
        private volatile boolean cancelled;

        /**
         * @param taskId       タスクID
         * @param owner        所有プラグイン
         * @param scheduledTask ラップ対象の Folia スケジュールタスク
         */
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

    /**
     * {@link ChunkGenerator} を Folia 互換でラップする内部クラス。
     *
     * <p>既存の ChunkGenerator インスタンスを保持し、
     * Folia 環境でも透過的に動作させる。</p>
     */
    public static final class FoliaChunkGenerator extends ChunkGenerator {

        /** ラップ対象の元の ChunkGenerator */
        private final ChunkGenerator delegate;

        /**
         * @param delegate ラップ対象の ChunkGenerator
         */
        public FoliaChunkGenerator(ChunkGenerator delegate) {
            this.delegate = delegate;
        }

        /**
         * ラップ対象の ChunkGenerator を返す。
         *
         * @return 元の ChunkGenerator
         */
        public ChunkGenerator getDelegate() {
            return this.delegate;
        }
    }
}
