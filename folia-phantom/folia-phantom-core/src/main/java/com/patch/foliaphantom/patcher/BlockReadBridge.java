package com.patch.foliaphantom.patcher;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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
 * global thread. CraftBlock ultimately touches region-owned Level state, so these reads must be
 * executed by the owning region. The bridge preserves the original synchronous Bukkit API contract
 * by dispatching the read and waiting for the result.</p>
 */
public final class BlockReadBridge {

    private static final long READ_TIMEOUT_SECONDS = 5L;

    private BlockReadBridge() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Material getType(Block block) {
        return read(block, block::getType);
    }

    public static BlockData getBlockData(Block block) {
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

    private static <T> T read(Block block, Supplier<T> operation) {
        Location location = block.getLocation();
        if (isOwningRegion(location)) {
            return operation.get();
        }

        Plugin targetPlugin = FoliaPatcher.plugin;
        if (targetPlugin == null) {
            throw new IllegalStateException(
                    "FoliaPatcher plugin is not initialized for region-owned block read");
        }

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
