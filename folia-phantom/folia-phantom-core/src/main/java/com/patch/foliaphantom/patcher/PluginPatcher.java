package com.patch.foliaphantom.patcher;

import com.patch.foliaphantom.transformer.ClassTransformer;
import com.patch.foliaphantom.transformer.EntitySchedulerTransformer;
import com.patch.foliaphantom.transformer.PlayerSafetyTransformer;
import com.patch.foliaphantom.transformer.SchedulerClassTransformer;
import com.patch.foliaphantom.transformer.ScanningClassVisitor;
import com.patch.foliaphantom.transformer.ThreadSafetyTransformer;
import com.patch.foliaphantom.transformer.WorldGenClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * JAR 全体のパッチ処理を統括するオーケストレーター。
 *
 * <p>v2.1 では、入力の読み取りと出力順序を決定的に保ちながら、CPU 負荷の高い
 * ASM クラス変換だけを並列化する。非 class エントリはコピーし、署名ファイルは
 * 除去し、plugin.yml とランタイムブリッジを出力時に処理する。</p>
 */
public final class PluginPatcher {

    private static final Logger log = LoggerFactory.getLogger(PluginPatcher.class);
    private static final int ASM_API = Opcodes.ASM9;
    private static final String[] RUNTIME_CLASSES = {
        "com/patch/foliaphantom/patcher/FoliaPatcher.class",
        "com/patch/foliaphantom/patcher/FoliaPatcher$FoliaBukkitTask.class",
        "com/patch/foliaphantom/patcher/FoliaPatcher$FoliaChunkGenerator.class",
        "com/patch/foliaphantom/patcher/FoliaPatcher$ScheduledTaskStub.class",
        "com/patch/foliaphantom/patcher/FoliaPatcher$TaskSchedulerFactory.class"
    };

    private final List<ClassTransformer> transformers;
    private final ForkJoinPool forkJoinPool;
    private final Path outputDir;
    private final boolean verbose;
    private final AtomicInteger patchedClassCount = new AtomicInteger();
    private final AtomicInteger skippedClassCount = new AtomicInteger();

    public PluginPatcher(Path outputDir, boolean verbose) {
        this.outputDir = outputDir;
        this.verbose = verbose;
        int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.forkJoinPool = new ForkJoinPool(parallelism);
        this.transformers = List.of(
                new ThreadSafetyTransformer(),
                new WorldGenClassTransformer(),
                new EntitySchedulerTransformer(),
                new PlayerSafetyTransformer(),
                new SchedulerClassTransformer());
    }

    public Path patchPlugin(Path jarPath) throws IOException {
        String fileName = jarPath.getFileName().toString();
        if (fileName.startsWith("patched-")) {
            log.warn("Skipping already-patched JAR: {}", fileName);
            return jarPath;
        }

        patchedClassCount.set(0);
        skippedClassCount.set(0);
        Files.createDirectories(outputDir);
        Path outputPath = outputDir.resolve("patched-" + fileName);
        log.info("Patching plugin: {} with {} worker(s)", fileName, forkJoinPool.getParallelism());

        List<InputEntry> entries = readEntries(jarPath);
        List<PreparedEntry> prepared = prepareEntries(entries);

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(outputPath))) {
            Set<String> writtenNames = new HashSet<>();
            for (PreparedEntry entry : prepared) {
                byte[] data = entry.contentTask().join();
                addJarEntry(output, entry.name(), data);
                writtenNames.add(entry.name());
            }
            bundleRuntimeClasses(output, writtenNames);
        } catch (RuntimeException exception) {
            Files.deleteIfExists(outputPath);
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }

        log.info("Patch complete for: {} (patched: {}, skipped: {})",
                fileName, patchedClassCount.get(), skippedClassCount.get());
        return outputPath;
    }

    private List<InputEntry> readEntries(Path jarPath) throws IOException {
        List<InputEntry> entries = new ArrayList<>();
        try (JarInputStream input = new JarInputStream(Files.newInputStream(jarPath))) {
            JarEntry entry;
            while ((entry = input.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || isSignatureFile(name)) {
                    continue;
                }
                entries.add(new InputEntry(name, input.readAllBytes()));
            }
        }
        return entries;
    }

    private List<PreparedEntry> prepareEntries(List<InputEntry> entries) {
        List<PreparedEntry> prepared = new ArrayList<>(entries.size());
        for (InputEntry entry : entries) {
            if ("plugin.yml".equals(entry.name())) {
                byte[] modified = modifyPluginYml(entry.content());
                prepared.add(new PreparedEntry(entry.name(),
                        forkJoinPool.submit(() -> modified)));
            } else if (entry.name().endsWith(".class")) {
                ForkJoinTask<byte[]> task = forkJoinPool.submit(
                        () -> transformClass(entry.name(), entry.content()));
                prepared.add(new PreparedEntry(entry.name(), task));
            } else {
                prepared.add(new PreparedEntry(entry.name(),
                        forkJoinPool.submit(entry::content)));
            }
        }
        return prepared;
    }

    private byte[] transformClass(String entryName, byte[] classBytes) {
        String className = entryName.substring(0, entryName.length() - ".class".length())
                .replace('/', '.');

        if (!needsPatching(classBytes)) {
            skippedClassCount.incrementAndGet();
            if (verbose) {
                log.debug("Skipping class (no patch needed): {}", className);
            }
            return classBytes;
        }

        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode(ASM_API);
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        byte[] result = classBytes;
        for (ClassTransformer transformer : transformers) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            byte[] transformed = transformer.transform(classNode, className, writer);
            if (transformed != null) {
                result = transformed;
                reader = new ClassReader(result);
                classNode = new ClassNode(ASM_API);
                reader.accept(classNode, ClassReader.EXPAND_FRAMES);
            }
        }

        patchedClassCount.incrementAndGet();
        if (verbose) {
            log.debug("Patched class: {}", className);
        }
        return result;
    }

    private boolean needsPatching(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ScanningClassVisitor scanner = new ScanningClassVisitor(ASM_API, null);
        // Method instructions are the primary detection signal; SKIP_CODE would disable them.
        reader.accept(scanner, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return scanner.needsPatching();
    }

    private byte[] modifyPluginYml(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.contains("folia-supported: true")) {
            return bytes;
        }
        if (content.contains("folia-supported:")) {
            content = content.replaceAll("(?m)^folia-supported:.*$", "folia-supported: true");
        } else {
            String lineSeparator = content.contains("\r\n") ? "\r\n" : "\n";
            content = content.stripTrailing() + lineSeparator
                    + "folia-supported: true" + lineSeparator;
        }
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private void bundleRuntimeClasses(JarOutputStream output, Set<String> writtenNames)
            throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        for (String classPath : RUNTIME_CLASSES) {
            if (writtenNames.contains(classPath)) {
                continue;
            }
            try (InputStream classStream = loader.getResourceAsStream(classPath)) {
                if (classStream == null) {
                    log.warn("Runtime class not found in classpath: {}", classPath);
                    continue;
                }
                addJarEntry(output, classPath, classStream.readAllBytes());
            }
        }
    }

    private static boolean isSignatureFile(String entryName) {
        String upper = entryName.toUpperCase(Locale.ROOT);
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".SF")
                || upper.endsWith(".DSA")
                || upper.endsWith(".RSA"));
    }

    private static void addJarEntry(JarOutputStream output, String name, byte[] data)
            throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setSize(data.length);
        output.putNextEntry(entry);
        output.write(data);
        output.closeEntry();
    }

    public int getPatchedClassCount() {
        return patchedClassCount.get();
    }

    public int getSkippedClassCount() {
        return skippedClassCount.get();
    }

    private record InputEntry(String name, byte[] content) {
    }

    private record PreparedEntry(String name, ForkJoinTask<byte[]> contentTask) {
    }
}
