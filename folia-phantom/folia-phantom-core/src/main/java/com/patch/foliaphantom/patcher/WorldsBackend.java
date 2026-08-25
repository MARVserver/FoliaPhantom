package com.patch.foliaphantom.patcher;

import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Optional runtime adapter for TheNextLvl Worlds.
 *
 * <p>Folia disables CraftServer#createWorld directly. Worlds implements the full
 * Folia-aware NMS lifecycle itself, so patched plugins can delegate world creation
 * to it when the plugin is installed instead of calling Bukkit#createWorld.</p>
 */
public final class WorldsBackend {

    private WorldsBackend() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static World createWorld(WorldCreator creator) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> worldsAccessClass = Class.forName("net.thenextlvl.worlds.WorldsAccess", true, loader);
            Class<?> levelClass = Class.forName("net.thenextlvl.worlds.Level", true, loader);
            Class<?> keyClass = Class.forName("net.kyori.adventure.key.Key", true, loader);
            Class<?> dimensionClass = Class.forName("net.thenextlvl.worlds.Dimension", true, loader);
            Class<?> generatorTypeClass = Class.forName("net.thenextlvl.worlds.generator.GeneratorType", true, loader);

            Object key = keyClass.getMethod("key", String.class, String.class)
                    .invoke(null, "minecraft", sanitizeKey(creator.name()));
            Object builder = levelClass.getMethod("builder", keyClass).invoke(null, key);

            invokeBuilder(builder, "seed", Long.class, creator.seed());
            invokeBuilder(builder, "structures", Boolean.class, creator.generateStructures());
            invokeBuilder(builder, "dimension", dimensionClass, dimensionFor(creator, dimensionClass));
            invokeBuilder(builder, "generatorType", generatorTypeClass,
                    generatorTypeFor(creator, generatorTypeClass));

            Object level = builder.getClass().getMethod("build").invoke(builder);
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

    private static String sanitizeKey(String name) {
        String normalized = name.toLowerCase(java.util.Locale.ROOT)
                .replace(' ', '_')
                .replaceAll("[^a-z0-9._/-]", "_");
        return normalized.isEmpty() ? "world" : normalized;
    }
}
