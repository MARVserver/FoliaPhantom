package com.patch.foliaphantom.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPatcherTest {

    private final PluginPatcher patcher = new PluginPatcher(Logger.getLogger("test"));

    @Test
    void addFoliaSupportedFlagAppendsWhenMissing() {
        String input = "name: DemoPlugin\nversion: 1.0.0\n";
        String output = patcher.addFoliaSupportedFlag(input);

        assertTrue(output.contains("folia-supported: true"));
    }

    @Test
    void addFoliaSupportedFlagUpdatesExistingValue() {
        String input = "name: DemoPlugin\nfolia-supported: false\n";
        String output = patcher.addFoliaSupportedFlag(input);

        assertTrue(output.contains("folia-supported: true"));
        assertFalse(output.contains("folia-supported: false"));
    }

    @Test
    void isFoliaSupportedDetectsFlagInJar() throws IOException {
        Path jarPath = createJarWithPluginYml("folia-supported: true\n");
        assertTrue(PluginPatcher.isFoliaSupported(jarPath.toFile()));
    }

    @Test
    void isFoliaSupportedReturnsFalseWhenMissingFlag() throws IOException {
        Path jarPath = createJarWithPluginYml("name: DemoPlugin\nversion: 1.0.0\n");
        assertFalse(PluginPatcher.isFoliaSupported(jarPath.toFile()));
    }

    private Path createJarWithPluginYml(String ymlContent) throws IOException {
        Path jarPath = Files.createTempFile("folia-phantom-test", ".jar");
        jarPath.toFile().deleteOnExit();

        try (OutputStream out = Files.newOutputStream(jarPath);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("plugin.yml"));
            zos.write(ymlContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        return jarPath;
    }
}
