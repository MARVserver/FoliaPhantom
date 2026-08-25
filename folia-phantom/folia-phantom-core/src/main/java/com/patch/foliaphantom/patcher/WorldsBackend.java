package com.patch.foliaphantom.patcher;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Optional runtime adapter for TheNextLvl Worlds.
 *
 * <p>Folia disables CraftServer#createWorld directly. Worlds implements the full
 * Folia-aware NMS lifecycle itself, so patched plugins can delegate world creation
 * to it when the plugin is installed instead of calling Bukkit#createWorld.</p>
 *
 * <p>The Worlds public {@code Level.Builder} models plugin generators through its
 * own {@code Generator} abstraction and does not expose setters for already-resolved
 * Bukkit {@link ChunkGenerator}/{@link BiomeProvider} instances. Bukkit's
 * {@link WorldCreator}, however, stores the final resolved instances. To preserve
 * compatibility with arbitrary generator plugins, this bridge injects those resolved
 * instances into the built Worlds level before creation. Worlds itself consumes them
 * through {@code Level#getChunkGenerator()} and {@code Level#getBiomeProvider()}.</p>
 */
public final class WorldsBackend {

    private WorldsBackend() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static World createWorld(WorldCreator creator) {
        try {
            ClassLoader loader = findWorldsClassLoader();
            Class<?> worldsAccessClass = Class.forName("net.thenextlvl.worlds.WorldsAccess", true, loader);
            Class<?> levelClass = Class.forName("net.thenextlvl.worlds.Level", true, loader);
            Class<?> keyClass = Class.forName("net.kyori.adventure.key.Key", true, loader);
            Class<?> dimensionClass = Class.forName("net.thenextlvl.worlds.Dimension", true, loader);
            Class<?> generatorTypeClass = Class.forName(
                    "net.thenextlvl.worlds.generator.GeneratorType", true, loader);

            NamespacedKey bukkitKey = creator.key();
            Object key = keyClass.getMethod("key", String.class, String.class)
                    .invoke(null, bukkitKey.getNamespace(), bukkitKey.getKey());
            Object builder = levelClass.getMethod("builder", keyClass).invoke(null, key);

            invokeBuilder(builder, "seed", Long.class, creator.seed());
            invokeBuilder(builder, "structures", Boolean.class, creator.generateStructures());
            invokeBuilder(builder, "hardcore", Boolean.class, creator.hardcore());
            invokeBuilder(builder, "bonusChest", Boolean.class, creator.bonusChest());
            invokeBuilder(builder, "dimension", dimensionClass, dimensionFor(creator, dimensionClass));
            invokeBuilder(builder, "generatorType", generatorTypeClass,
                    generatorTypeFor(creator, generatorTypeClass));

            Object level = builder.getClass().getMethod("build").invoke(builder);

            // WorldCreator already contains the final generator instances after Bukkit has
            // resolved plugin:id strings. Preserve them verbatim instead of trying to infer
            // the providing plugin/id again. This supports arbitrary ChunkGenerator and
            // BiomeProvider implementations, including generators not represented by the
            // Worlds Generator abstraction.
            injectResolvedGenerator(level, creator.generator(), creator.biomeProvider());

            Object access = worldsAccessClass.getMethod("access").invoke(null);
            @SuppressWarnings("unchecked")
            CompletableFuture<World> future = (CompletableFuture<World>) worldsAccessClass
                    .getMethod("create", levelClass)
                    .invoke(access, level);
            return future.join();
        } catch (ClassNotFoundException exception) {
            throw new UnsupportedOperationException(
                    "Folia does not support Bukkit.createWorld directly. Install TheNextLvl Worlds "
                            + "to provide the Folia-aware world creation backend.", exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke TheNextLvl Worlds backend", exception);
        }
    }

    private static ClassLoader findWorldsClassLoader() throws ClassNotFoundException {
        // On Paper/Folia, plugin classes are generally not visible through another plugin's
        // class loader. Resolve the installed Worlds plugin first and use its loader.
        org.bukkit.plugin.Plugin worlds = org.bukkit.Bukkit.getPluginManager().getPlugin("Worlds");
        if (worlds != null) {
            return worlds.getClass().getClassLoader();
        }

        ClassLoader context = Thread.currentThread().getContextClassLoader();
        Class.forName("net.thenextlvl.worlds.WorldsAccess", false, context);
        return context;
    }

    private static void injectResolvedGenerator(
            Object level, ChunkGenerator generator, BiomeProvider biomeProvider)
            throws ReflectiveOperationException {
        if (generator != null) {
            setField(level, "chunkGenerator", generator);
        }
        if (biomeProvider != null) {
            setField(level, "biomeProvider", biomeProvider);
        }
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object dimensionFor(WorldCreator creator, Class<?> dimensionClass)
            throws ReflectiveOperationException {
        String fieldName = switch (creator.environment()) {
            case NETHER -> "THE_NETHER";
            case THE_END -> "THE_END";
            default -> "OVERWORLD";
        };
        return staticField(dimensionClass, fieldName);
    }

    private static Object generatorTypeFor(WorldCreator creator, Class<?> generatorTypeClass)
            throws ReflectiveOperationException {
        String fieldName = switch (creator.type()) {
            case FLAT -> "FLAT";
            case AMPLIFIED -> "AMPLIFIED";
            case LARGE_BIOMES -> "LARGE_BIOMES";
            default -> "NORMAL";
        };
        return staticField(generatorTypeClass, fieldName);
    }

    private static Object staticField(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getField(name);
        return field.get(null);
    }

    private static void invokeBuilder(Object builder, String methodName, Class<?> parameterType, Object value)
            throws ReflectiveOperationException {
        Method method = builder.getClass().getMethod(methodName, parameterType);
        method.invoke(builder, value);
    }
}
