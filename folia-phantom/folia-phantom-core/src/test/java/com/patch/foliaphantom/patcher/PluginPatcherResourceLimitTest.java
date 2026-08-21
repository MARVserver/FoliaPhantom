package com.patch.foliaphantom.patcher;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PluginPatcherResourceLimitTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsHighlyCompressedEntryThatExpandsPastPerEntryLimit() throws Exception {
        Path input = createJarWithRepeatedBytes("zip-bomb.jar", "payload.bin", 4096);
        PluginPatcher patcher = patcherWithLimits(1_000_000, 1024, 8192, 10);

        IOException exception = assertThrows(IOException.class, () -> patcher.patchPlugin(input));

        assertTrue(exception.getMessage().contains("JAR entry exceeds maximum expanded size"));
    }

    @Test
    public void rejectsCombinedExpansionPastTotalLimit() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("combined.jar");
        try (OutputStream file = Files.newOutputStream(input);
             JarOutputStream jar = new JarOutputStream(file)) {
            writeEntry(jar, "first.bin", new byte[700]);
            writeEntry(jar, "second.bin", new byte[700]);
        }
        PluginPatcher patcher = patcherWithLimits(1_000_000, 1024, 1200, 10);

        IOException exception = assertThrows(IOException.class, () -> patcher.patchPlugin(input));

        assertTrue(exception.getMessage().contains("Plugin JAR exceeds maximum expanded size"));
    }

    @Test
    public void appliesExpansionLimitsToSignatureEntriesThatWillBeDiscarded() throws Exception {
        Path input = createJarWithRepeatedBytes("signature-bomb.jar", "META-INF/ATTACK.SF", 4096);
        PluginPatcher patcher = patcherWithLimits(1_000_000, 1024, 8192, 10);

        IOException exception = assertThrows(IOException.class, () -> patcher.patchPlugin(input));

        assertTrue(exception.getMessage().contains("JAR entry exceeds maximum expanded size"));
    }

    @Test
    public void countsDiscardedEntriesTowardEntryLimit() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("many-entries.jar");
        try (OutputStream file = Files.newOutputStream(input);
             JarOutputStream jar = new JarOutputStream(file)) {
            jar.putNextEntry(new JarEntry("directory/"));
            jar.closeEntry();
            writeEntry(jar, "META-INF/IGNORED.SF", new byte[] {1});
            writeEntry(jar, "plugin.yml", pluginYml());
        }
        PluginPatcher patcher = patcherWithLimits(1_000_000, 1024, 8192, 2);

        IOException exception = assertThrows(IOException.class, () -> patcher.patchPlugin(input));

        assertTrue(exception.getMessage().contains("maximum entry count"));
    }

    @Test
    public void rejectsCompressedInputPastFileSizeLimit() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("input-too-large.jar");
        try (OutputStream file = Files.newOutputStream(input);
             JarOutputStream jar = new JarOutputStream(file)) {
            writeEntry(jar, "plugin.yml", pluginYml());
        }
        PluginPatcher patcher = patcherWithLimits(32, 1024, 8192, 10);

        IOException exception = assertThrows(IOException.class, () -> patcher.patchPlugin(input));

        assertTrue(exception.getMessage().contains("maximum input size"));
    }

    @Test
    public void ordinaryPluginJarStillPatchesAndGetsFoliaMetadata() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("ordinary.jar");
        try (OutputStream file = Files.newOutputStream(input);
             JarOutputStream jar = new JarOutputStream(file)) {
            writeEntry(jar, "plugin.yml", pluginYml());
        }
        PluginPatcher patcher = patcherWithLimits(4096, 2048, 4096, 10);

        Path output = patcher.patchPlugin(input);

        assertTrue(Files.isRegularFile(output));
        try (JarFile jar = new JarFile(output.toFile())) {
            String pluginYml = new String(
                    jar.getInputStream(jar.getJarEntry("plugin.yml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(pluginYml.contains("folia-supported: true"));
        }
        assertEquals(0, patcher.getFailedClassCount());
    }

    private PluginPatcher patcherWithLimits(
            long maxInputBytes,
            long maxEntryBytes,
            long maxTotalBytes,
            int maxEntryCount) {
        PluginPatcher.JarResourceLimits limits = new PluginPatcher.JarResourceLimits(
                maxInputBytes,
                maxEntryBytes,
                maxTotalBytes,
                maxEntryCount);
        return new PluginPatcher(
                temporaryFolder.getRoot().toPath().resolve("out"),
                false,
                false,
                limits);
    }

    private Path createJarWithRepeatedBytes(String fileName, String entryName, int bytes)
            throws IOException {
        Path input = temporaryFolder.getRoot().toPath().resolve(fileName);
        try (OutputStream file = Files.newOutputStream(input);
             JarOutputStream jar = new JarOutputStream(file)) {
            writeEntry(jar, entryName, new byte[bytes]);
        }
        return input;
    }

    private static void writeEntry(JarOutputStream jar, String name, byte[] content)
            throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(content);
        jar.closeEntry();
    }

    private static byte[] pluginYml() {
        return "name: Ordinary\nversion: 1.0\nmain: example.Main\n"
                .getBytes(StandardCharsets.UTF_8);
    }
}
