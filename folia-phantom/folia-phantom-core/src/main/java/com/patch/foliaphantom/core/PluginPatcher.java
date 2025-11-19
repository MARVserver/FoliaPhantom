package com.patch.foliaphantom.core;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import com.patch.foliaphantom.core.transformer.impl.SchedulerClassTransformer;
import com.patch.foliaphantom.core.transformer.impl.WorldGenClassTransformer;
import com.patch.foliaphantom.core.transformer.impl.EntitySchedulerTransformer;
import com.patch.foliaphantom.core.transformer.impl.ThreadSafetyTransformer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PluginPatcher {

    private final Logger logger;
    private final List<ClassTransformer> transformers = new ArrayList<>();

    public PluginPatcher(Logger logger) {
        this.logger = logger;
        // Register all transformers here. Order is critical.
        this.transformers.add(new ThreadSafetyTransformer(logger));
        this.transformers.add(new WorldGenClassTransformer(logger));
        this.transformers.add(new EntitySchedulerTransformer(logger));
        this.transformers.add(new SchedulerClassTransformer(logger));
    }

    public void patchPlugin(File originalJar, File tempJar) throws IOException {
        logger.info(String.format("[FoliaPhantom] Creating patched JAR at: %s", tempJar.getPath()));
        createPatchedJar(originalJar.toPath(), tempJar.toPath());
    }

    private void createPatchedJar(Path source, Path destination) throws IOException {
        try (
            ZipInputStream zis = new ZipInputStream(Files.newInputStream(source));
            ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destination))
        ) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));

                if (!entry.isDirectory()) {
                    if (entry.getName().equals("plugin.yml")) {
                        String originalYml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                        String modifiedYml = addFoliaSupportedFlag(originalYml);
                        zos.write(modifiedYml.getBytes(StandardCharsets.UTF_8));
                    } else if (entry.getName().endsWith(".class")) {
                        byte[] classBytes = zis.readAllBytes();
                        for (ClassTransformer transformer : transformers) {
                            classBytes = transformer.transform(classBytes);
                        }
                        zos.write(classBytes);
                    } else {
                        copyStream(zis, zos);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private String addFoliaSupportedFlag(String pluginYml) {
        if (pluginYml.lines().anyMatch(line -> line.trim().startsWith("folia-supported:"))) {
            return pluginYml.replaceAll("(?m)^\\s*folia-supported:.*$", "folia-supported: true");
        } else {
            return pluginYml.trim() + "\nfolia-supported: true\n";
        }
    }

    private void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
    }

    public static String getPluginNameFromJar(File jarFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarFile.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("plugin.yml")) {
                    String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    for (String line : content.lines().toList()) {
                        if (line.trim().startsWith("name:")) {
                            return line.substring(line.indexOf(":") + 1).trim();
                        }
                    }
                }
            }
        }
        return null;
    }

    public static boolean isFoliaSupported(File jarFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarFile.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("plugin.yml")) {
                    String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    return content.lines().anyMatch(line -> line.trim().equalsIgnoreCase("folia-supported: true"));
                }
            }
        }
        return false;
    }
}
