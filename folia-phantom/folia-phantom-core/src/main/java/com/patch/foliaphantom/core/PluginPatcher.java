/*
 * Folia Phantom - Plugin Patcher Core
 * 
 * This module provides the core bytecode transformation logic for converting
 * Bukkit plugins to be compatible with Folia's region-based threading model.
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import com.patch.foliaphantom.core.transformer.ScanningClassVisitor;
import com.patch.foliaphantom.core.transformer.impl.EntitySchedulerTransformer;
import com.patch.foliaphantom.core.transformer.impl.SchedulerClassTransformer;
import com.patch.foliaphantom.core.transformer.impl.ThreadSafetyTransformer;
import com.patch.foliaphantom.core.transformer.impl.WorldGenClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Main plugin patching utility for Folia Phantom.
 * 
 * <p>
 * This class orchestrates the transformation of Bukkit plugin JAR files,
 * replacing thread-unsafe API calls with Folia-compatible alternatives using
 * ASM bytecode manipulation.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 * <li>Parallel class transformation using ForkJoinPool</li>
 * <li>Automatic plugin.yml modification to add folia-supported flag</li>
 * <li>Signature file removal for compatibility with signed JARs</li>
 * <li>Bundle FoliaPatcher runtime classes into output JAR</li>
 * <li>Fast-fail scanning to skip classes that don't need patching</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * Logger logger = Logger.getLogger("FoliaPhantom");
 * PluginPatcher patcher = new PluginPatcher(logger);
 * patcher.patchPlugin(inputJar, outputJar);
 * }</pre>
 * 
 * @author Marv
 * @version 1.0.0
 */
public class PluginPatcher {

    /** Buffer size for stream copying (64KB for optimal I/O performance) */
    private static final int BUFFER_SIZE = 65536;

    /** Compression level for output JAR (1 = fastest) */
    private static final int COMPRESSION_LEVEL = 1;

    /** Logger instance for this patcher */
    private final Logger logger;

    /** Ordered list of class transformers to apply */
    private final List<ClassTransformer> transformers;

    /** Statistics: number of classes scanned */
    private final AtomicInteger classesScanned = new AtomicInteger(0);

    /** Statistics: number of classes transformed */
    private final AtomicInteger classesTransformed = new AtomicInteger(0);

    /** Statistics: number of classes skipped (no changes needed) */
    private final AtomicInteger classesSkipped = new AtomicInteger(0);

    /**
     * Creates a new PluginPatcher instance with the specified logger.
     * 
     * <p>
     * Registers all available transformers in the correct order.
     * Transformer order is critical as some transformers may depend on
     * previous modifications.
     * </p>
     * 
     * @param logger Logger for outputting patching progress and diagnostics
     */
    public PluginPatcher(Logger logger) {
        this.logger = logger;
        this.transformers = new ArrayList<>();

        // Register transformers in order of priority
        // Order is critical: ThreadSafety → WorldGen → Entity → Scheduler
        transformers.add(new ThreadSafetyTransformer(logger));
        transformers.add(new WorldGenClassTransformer(logger));
        transformers.add(new EntitySchedulerTransformer(logger));
        transformers.add(new SchedulerClassTransformer(logger));

        logger.fine("Initialized PluginPatcher with " + transformers.size() + " transformers");
    }

    /**
     * Patches a plugin JAR file for Folia compatibility.
     * 
     * <p>
     * This method:
     * </p>
     * <ol>
     * <li>Reads the original JAR file</li>
     * <li>Transforms all .class files using registered transformers</li>
     * <li>Modifies plugin.yml to add folia-supported: true</li>
     * <li>Removes JAR signature files for compatibility</li>
     * <li>Bundles FoliaPatcher runtime classes</li>
     * <li>Writes the patched JAR to the output location</li>
     * </ol>
     * 
     * @param originalJar Source JAR file to patch
     * @param outputJar   Destination for the patched JAR
     * @throws IOException If an I/O error occurs during patching
     */
    public void patchPlugin(File originalJar, File outputJar) throws IOException {
        // Reset statistics
        classesScanned.set(0);
        classesTransformed.set(0);
        classesSkipped.set(0);

        logger.info("[FoliaPhantom] ─────────────────────────────────────────");
        logger.info("[FoliaPhantom] Patching: " + originalJar.getName());
        logger.info("[FoliaPhantom] Output:   " + outputJar.getName());

        long startTime = System.currentTimeMillis();
        createPatchedJar(originalJar.toPath(), outputJar.toPath());
        long duration = System.currentTimeMillis() - startTime;

        logger.info("[FoliaPhantom] ─────────────────────────────────────────");
        logger.info("[FoliaPhantom] Completed in " + duration + "ms");
        logger.info("[FoliaPhantom]   Classes scanned:     " + classesScanned.get());
        logger.info("[FoliaPhantom]   Classes transformed: " + classesTransformed.get());
        logger.info("[FoliaPhantom]   Classes skipped:     " + classesSkipped.get());
        logger.info("[FoliaPhantom] ─────────────────────────────────────────");
    }

    /**
     * Creates the patched JAR file with parallel class transformation.
     * 
     * @param source      Path to source JAR
     * @param destination Path to output JAR
     * @throws IOException If an I/O error occurs
     */
    private void createPatchedJar(Path source, Path destination) throws IOException {
        ForkJoinPool executor = ForkJoinPool.commonPool();

        // Container for async class patching results
        List<ClassPatchFuture> classFutures = new ArrayList<>(256);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(source));
                ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destination))) {

            // Configure output compression
            zos.setMethod(ZipOutputStream.DEFLATED);
            zos.setLevel(COMPRESSION_LEVEL);

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                boolean isDirectory = entry.isDirectory();

                // Skip JAR signature files (for signed JARs like MythicMobs)
                if (isSignatureFile(name)) {
                    logger.fine("[FoliaPhantom] Removing signature file: " + name);
                    continue;
                }

                if (!isDirectory && name.endsWith(".class")) {
                    // Queue class for parallel transformation
                    byte[] classBytes = zis.readAllBytes();
                    classesScanned.incrementAndGet();
                    classFutures.add(new ClassPatchFuture(
                            name,
                            executor.submit(() -> patchClass(classBytes, name))));
                } else if (!isDirectory && (name.equals("paper-plugin.yml") || name.equals("plugin.yml"))) {
                    // Modify plugin manifest to add Folia support flag
                    String originalYml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    String modifiedYml = addFoliaSupportedFlag(originalYml);
                    writeEntry(zos, name, modifiedYml.getBytes(StandardCharsets.UTF_8));
                    logger.fine("[FoliaPhantom] Modified " + name + " with folia-supported: true");
                } else {
                    // Copy other resources directly
                    zos.putNextEntry(new ZipEntry(name));
                    if (!isDirectory) {
                        copyStream(zis, zos);
                    }
                    zos.closeEntry();
                }
            }

            // Write transformed classes
            for (ClassPatchFuture cpf : classFutures) {
                try {
                    ClassPatchResult result = cpf.future.get();
                    writeEntry(zos, cpf.name, result.bytes);

                    if (result.wasTransformed) {
                        classesTransformed.incrementAndGet();
                    } else {
                        classesSkipped.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new IOException("Failed to patch class: " + cpf.name, e);
                }
            }

            // Bundle FoliaPatcher runtime classes
            bundleFoliaPatcherClasses(zos);
        }
        // Note: ForkJoinPool.commonPool() should not be shut down
    }

    /**
     * Checks if a file is a JAR signature file.
     */
    private boolean isSignatureFile(String name) {
        return name.startsWith("META-INF/") &&
                (name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA"));
    }

    /**
     * Writes an entry to the ZIP output stream.
     */
    private void writeEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    /**
     * Bundles the FoliaPatcher runtime classes into the output JAR.
     * 
     * <p>
     * These classes are required at runtime by the patched plugin
     * to handle Folia's scheduler API calls.
     * </p>
     * 
     * @param zos The output ZIP stream
     * @throws IOException If bundling fails
     */
    private void bundleFoliaPatcherClasses(ZipOutputStream zos) throws IOException {
        Class<?> patcherClass = com.patch.foliaphantom.core.patcher.FoliaPatcher.class;
        String classPath = patcherClass.getName().replace('.', '/');

        // Bundle main class and inner classes
        String[] classesToBundle = {
                classPath + ".class",
                classPath + "$FoliaBukkitTask.class",
                classPath + "$FoliaChunkGenerator.class"
        };

        int bundled = 0;
        for (String clazz : classesToBundle) {
            if (bundleClass(zos, clazz)) {
                bundled++;
            }
        }

        logger.info("[FoliaPhantom] Bundled " + bundled + " runtime class(es)");
    }

    /**
     * Bundles a single class file into the output JAR.
     * 
     * @param zos       The output ZIP stream
     * @param classPath The internal class path
     * @return true if bundled successfully, false otherwise
     */
    private boolean bundleClass(ZipOutputStream zos, String classPath) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(classPath)) {
            if (is == null) {
                logger.warning("[FoliaPhantom] Runtime class not found: " + classPath);
                return false;
            }
            zos.putNextEntry(new ZipEntry(classPath));
            copyStream(is, zos);
            zos.closeEntry();
            return true;
        }
    }

    /**
     * Copies data from an input stream to an output stream.
     * 
     * @param in  Source stream
     * @param out Destination stream
     * @throws IOException If an I/O error occurs
     */
    private void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
    }

    /**
     * Patches a single class using the registered transformers.
     * 
     * <p>
     * Uses a fast-fail heuristic: first scans the class to determine
     * if any patching is needed. If not, returns the original bytes
     * immediately without full transformation.
     * </p>
     * 
     * @param originalBytes The original class bytecode
     * @param className     The class name (for logging)
     * @return The patching result containing transformed bytes
     */
    private ClassPatchResult patchClass(byte[] originalBytes, String className) {
        try {
            ClassReader cr = new ClassReader(originalBytes);

            // Fast-fail scan: check if this class needs patching
            ScanningClassVisitor scanner = new ScanningClassVisitor();
            cr.accept(scanner, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            if (!scanner.needsPatching()) {
                return new ClassPatchResult(originalBytes, false);
            }

            // Full transformation needed
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = cw;

            // Apply transformers in reverse order (so first registered runs first)
            for (int i = transformers.size() - 1; i >= 0; i--) {
                cv = transformers.get(i).createVisitor(cv);
            }

            cr.accept(cv, ClassReader.EXPAND_FRAMES);

            logger.fine("[FoliaPhantom] Transformed: " + className);
            return new ClassPatchResult(cw.toByteArray(), true);

        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "[FoliaPhantom] Failed to transform " + className + ", using original", e);
            return new ClassPatchResult(originalBytes, false);
        }
    }

    /**
     * Adds or updates the folia-supported flag in plugin.yml.
     * 
     * @param pluginYml Original plugin.yml content
     * @return Modified plugin.yml content
     */
    String addFoliaSupportedFlag(String pluginYml) {
        if (pluginYml.lines().anyMatch(line -> line.trim().startsWith("folia-supported:"))) {
            // Update existing flag
            return pluginYml.replaceAll("(?m)^\\s*folia-supported:.*$", "folia-supported: true");
        } else {
            // Add new flag
            return pluginYml.trim() + "\nfolia-supported: true\n";
        }
    }

    // =========================================================================
    // Static Utility Methods
    // =========================================================================

    /**
     * Extracts the plugin name from a JAR file's plugin.yml.
     * 
     * @param jarFile The plugin JAR file
     * @return The plugin name, or null if not found
     * @throws IOException If an I/O error occurs
     */
    public static String getPluginNameFromJar(File jarFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarFile.toPath()))) {
            ZipEntry entry;
            String paperPluginName = null;
            String pluginName = null;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                if ("paper-plugin.yml".equals(entryName)) {
                    paperPluginName = extractNameFromYml(new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                } else if ("plugin.yml".equals(entryName)) {
                    pluginName = extractNameFromYml(new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return paperPluginName != null ? paperPluginName : pluginName;
        }
    }

    /**
     * Extracts the name field from a YAML string.
     */
    private static String extractNameFromYml(String content) {
        for (String line : content.lines().toList()) {
            if (line.trim().startsWith("name:")) {
                return line.substring(line.indexOf(":") + 1).trim();
            }
        }
        return null;
    }

    /**
     * Checks if a plugin JAR already has folia-supported: true.
     * 
     * @param jarFile The plugin JAR file
     * @return true if the plugin already supports Folia
     * @throws IOException If an I/O error occurs
     */
    public static boolean isFoliaSupported(File jarFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarFile.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if ("paper-plugin.yml".equals(name) || "plugin.yml".equals(name)) {
                    String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    if (content.lines().anyMatch(line -> {
                        String trimmed = line.trim();
                        return trimmed.toLowerCase().startsWith("folia-supported:")
                                && trimmed.toLowerCase().endsWith("true");
                    })) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns the current patching statistics.
     * 
     * @return Array of [scanned, transformed, skipped]
     */
    public int[] getStatistics() {
        return new int[] {
                classesScanned.get(),
                classesTransformed.get(),
                classesSkipped.get()
        };
    }

    // =========================================================================
    // Inner Classes
    // =========================================================================

    /**
     * Container for a class patching future.
     */
    private static class ClassPatchFuture {
        final String name;
        final Future<ClassPatchResult> future;

        ClassPatchFuture(String name, Future<ClassPatchResult> future) {
            this.name = name;
            this.future = future;
        }
    }

    /**
     * Result of a class patching operation.
     */
    private static class ClassPatchResult {
        final byte[] bytes;
        final boolean wasTransformed;

        ClassPatchResult(byte[] bytes, boolean wasTransformed) {
            this.bytes = bytes;
            this.wasTransformed = wasTransformed;
        }
    }
}
