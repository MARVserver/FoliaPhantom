package com.patch.foliaphantom.patcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPatcherResourceLimitTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsHighlyCompressedEntryThatExpandsPastLimit() throws Exception {
        Path input = tempDir.resolve("zip-bomb.jar");
        byte[] block = new byte[1024 * 1024];
        try (OutputStream file = Files.newOutputStream(input);
             JarOutputStream jar = new JarOutputStream(file)) {
            jar.putNextEntry(new JarEntry("payload.bin"));
            for (int i = 0; i < 65; i++) {
                jar.write(block);
            }
            jar.closeEntry();
        }

        PluginPatcher patcher = new PluginPatcher(tempDir.resolve("out"), false, false);
        IOException exception = assertThrows(IOException.class, () -> patcher.patchPlugin(input));
        assertTrue(exception.getMessage().contains("maximum expanded size"));
    }

    @Test
    void ordinaryPluginJarStillPatches() throws Exception {
        Path input = tempDir.resolve("ordinary.jar");
        try (OutputStream file = Files.newOutputStream(input);
             JarOutputStream jar = new JarOutputStream(file)) {
            jar.putNextEntry(new JarEntry("plugin.yml"));
            jar.write("name: Ordinary\nversion: 1.0\nmain: example.Main\n".getBytes());
            jar.closeEntry();
        }

        Path output = new PluginPatcher(tempDir.resolve("out"), false, false).patchPlugin(input);
        assertTrue(Files.isRegularFile(output));
    }
}
