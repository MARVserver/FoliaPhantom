package com.patch.foliaphantom.patcher;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
 * still blocking server initialisation. Startup reads therefore use Paper's loaded-chunk-only NMS
 * path and never request, generate, or ticket a chunk.</p>
 */
public final class BlockReadBridge {

    private static final long READ_TIMEOUT_SECONDS = 5L;
    private static final String STARTUP_THREAD_NAME = "Server thread";

    private BlockReadBridge() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Material getType(Block block) {
        if (isStartupThread()) {
            return startupMaterial(block);
        }
        return read(block, block::getType);
    }

    public static BlockData getBlockData(Block block) {
        if (isStartupThread()) {
            return startupBlockData(block);
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

    private static Material startupMaterial(Block block) {
        Object state = startupBlockState(block);
        try {
            Method getBukkitMaterial = state.getClass().getMethod("getBukkitMaterial");
            return (Material) getBukkitMaterial.invoke(state);
        } catch (ReflectiveOperationException exception) {
            throw startupReadFailure(block, exception);
        }
    }

    private static BlockData startupBlockData(Block block) {
        Object state = startupBlockState(block);
        try {
            ClassLoader loader = state.getClass().getClassLoader();
            Class<?> blockStateClass = Class.forName(
                    "net.minecraft.world.level.block.state.BlockState", true, loader);
            Class<?> craftBlockDataClass = Class.forName(
                    "org.bukkit.craftbukkit.block.data.CraftBlockData", true, loader);
            Method createData = craftBlockDataClass.getMethod("createData", blockStateClass);
            return (BlockData) createData.invoke(null, state);
        } catch (ReflectiveOperationException exception) {
            throw startupReadFailure(block, exception);
        }
    }

    private static Object startupBlockState(Block block) {
        try {
            Object craftWorld = block.getWorld();
            Method getHandle = craftWorld.getClass().getMethod("getHandle");
            Object serverLevel = getHandle.invoke(craftWorld);

            Method getChunkSource = serverLevel.getClass().getMethod("getChunkSource");
            Object chunkSource = getChunkSource.invoke(serverLevel);
            Method getLoadedChunk = chunkSource.getClass().getMethod(
                    "getChunkAtIfLoadedImmediately", int.class, int.class);
            Object chunk = getLoadedChunk.invoke(
                    chunkSource, Math.floorDiv(block.getX(), 16), Math.floorDiv(block.getZ(), 16));
            if (chunk == null) {
                throw new IllegalStateException(
                        "Startup block read requires an already-loaded chunk at " + block.getLocation());
            }

            ClassLoader loader = serverLevel.getClass().getClassLoader();
            Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos", true, loader);
            Constructor<?> blockPosConstructor = blockPosClass.getConstructor(
                    int.class, int.class, int.class);
            Object blockPos = blockPosConstructor.newInstance(block.getX(), block.getY(), block.getZ());
            Method getBlockState = chunk.getClass().getMethod("getBlockState", blockPosClass);
            return getBlockState.invoke(chunk, blockPos);
        } catch (ReflectiveOperationException exception) {
            throw startupReadFailure(block, exception);
        }
    }

    private static IllegalStateException startupReadFailure(Block block, Throwable cause) {
        return new IllegalStateException(
                "Failed loaded-chunk startup block read at " + block.getLocation(), cause);
    }

    private static boolean isStartupThread() {
        Thread thread = Thread.currentThread();
        return Bukkit.isPrimaryThread() && STARTUP_THREAD_NAME.equals(thread.getName());
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
