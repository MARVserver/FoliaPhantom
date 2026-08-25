package com.patch.foliaphantom.patcher;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Region-owned synchronous Block reads used by patched plugins.
 *
 * <p>Legacy plugins frequently read block state during lifecycle callbacks that run on Folia's
 * startup/global thread. During normal ticking, CraftBlock reads must execute on the owning region.
 * During server startup, however, region tasks cannot make progress while plugin enablement is
 * still blocking server initialisation. For immutable block data that ChunkSnapshot can represent,
 * this bridge therefore reads from a snapshot on the non-ticking startup thread instead of
 * dispatching a region task and deadlocking.</p>
 */
public final class BlockReadBridge {

    private static final long READ_TIMEOUT_SECONDS = 5L;
    private static final String FOLIA_TICK_THREAD_RUNNER =
            "io.papermc.paper.threadedregions.TickRegionScheduler$TickThreadRunner";

    private BlockReadBridge() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Material getType(Block block) {
        if (isStartupThread()) {
            return snapshot(block).getBlockType(localX(block), block.getY(), localZ(block));
        }
        return read(block, block::getType);
    }

    public static BlockData getBlockData(Block block) {
        if (isStartupThread()) {
            return snapshot(block).getBlockData(localX(block), block.getY(), localZ(block));
        }
        return read(block, block::getBlockData);
    }

    public static BlockState getState(Block block) {
        return read(block, block::getState);
    }

    public static Collection<ItemStack> getDrops(Block block) {
        return read(block, block::getDrops);
    }

    public static Collection<ItemStack> getDrops(Block block, ItemStack tool) {
        return read(block, () -> block.getDrops(tool));
    }

    private static ChunkSnapshot snapshot(Block block) {
        // Plugin enablement runs before region ticking starts. At that point there is no
        // concurrent region mutation for the startup world, and a snapshot avoids touching
        // Level#getCurrentWorldData through CraftBlock#getType/getBlockData.
        return block.getChunk().getChunkSnapshot(false, false, false);
    }

    private static int localX(Block block) {
        return block.getX() & 15;
    }

    private static int localZ(Block block) {
        return block.getZ() & 15;
    }

    private static boolean isStartupThread() {
        Thread thread = Thread.currentThread();
        Class<?> type = thread.getClass();
        while (type != null) {
            if (FOLIA_TICK_THREAD_RUNNER.equals(type.getName())) {
                return false;
            }
            type = type.getSuperclass();
        }
        return Bukkit.isPrimaryThread();
    }

    private static <T> T read(Block block, Supplier<T> operation) {
        Location location = block.getLocation();
        if (isOwningRegion(location)) {
            return operation.get();
        }

        Plugin targetPlugin = JavaPlugin.getProvidingPlugin(BlockReadBridge.class);
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().run(targetPlugin, location, task -> {
            try {
                future.complete(operation.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });

        try {
            return future.get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for region-owned block read", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Region-owned block read failed", cause);
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    "Timed out waiting for region-owned block read at " + location,
                    exception);
        }
    }

    private static boolean isOwningRegion(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        try {
            return Bukkit.getServer().isOwnedByCurrentRegion(location);
        } catch (NoSuchMethodError | Exception exception) {
            return Bukkit.isPrimaryThread();
        }
    }
}
