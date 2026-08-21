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

import java.io.ByteArrayOutputStream;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * JAR 全体のパッチ処理を統括するオーケストレーター。
 *
 * <p>CPU 負荷の高い ASM クラス変換のみを並列化し、非 class エントリは
 * 不要なタスク生成を行わず直接コピーする。JAR 出力は速度優先の圧縮を使用する。</p>
 */
public final class PluginPatcher {

    private static final Logger log = LoggerFactory.getLogger(PluginPatcher.class);
    private static final int ASM_API = Opcodes.ASM9;
    private static final JarResourceLimits DEFAULT_RESOURCE_LIMITS = new JarResourceLimits(
            256L * 1024 * 1024,
            64L * 1024 * 1024,
            512L * 1024 * 1024,
            100_000);
    private static final byte[] BUKKIT_CONSTANT_POOL_MARKER =
            "org/bukkit/".getBytes(StandardCharsets.US_ASCII);
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
    private final boolean parallel;
    private final JarResourceLimits resourceLimits;
    private final AtomicInteger patchedClassCount = new AtomicInteger();
    private final AtomicInteger skippedClassCount = new AtomicInteger();
    private final AtomicInteger failedClassCount = new AtomicInteger();
    private final ConcurrentLinkedQueue<String> patchedClassNames = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TransformationFailure> transformationFailures =
            new ConcurrentLinkedQueue<>();

    public PluginPatcher(Path outputDir, boolean verbose) {
        this(outputDir, verbose, true);
    }

    /**
     * @param parallel whether class transformations should use a ForkJoinPool. Browser runtimes can
     *                 disable this to avoid worker/thread emulation overhead and improve stability.
     */
    public PluginPatcher(Path outputDir, boolean verbose, boolean parallel) {
        this(outputDir, verbose, parallel, DEFAULT_RESOURCE_LIMITS);
    }

    PluginPatcher(
            Path outputDir,
            boolean verbose,
            boolean parallel,
            JarResourceLimits resourceLimits) {
        this.outputDir = outputDir;
        this.verbose = verbose;
        this.parallel = parallel;
        this.resourceLimits = resourceLimits;
        int processors = Runtime.getRuntime().availableProcessors();
        int parallelism = Math.max(1, processors > 2 ? processors - 1 : processors);
        this.forkJoinPool = parallel ? new ForkJoinPool(parallelism) : null;
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
        long inputSize = Files.size(jarPath);
        if (inputSize > resourceLimits.maxInputBytes()) {
            throw new IOException("Plugin JAR exceeds maximum input size of "
                    + resourceLimits.maxInputBytes() + " bytes: " + fileName);
        }

        long startedAt = System.nanoTime();
        patchedClassCount.set(0);
        skippedClassCount.set(0);
        failedClassCount.set(0);
        patchedClassNames.clear();
        transformationFailures.clear();
        Files.createDirectories(outputDir);
        Path outputPath = outputDir.resolve("patched-" + fileName);
        int workers = parallel ? forkJoinPool.getParallelism() : 1;
        log.info("Patching plugin: {} with {} worker(s)", fileName, workers);

        List<PreparedEntry> prepared = readAndPrepareEntries(jarPath);

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(outputPath))) {
            output.setLevel(Deflater.BEST_SPEED);
            Set<String> writtenNames = new HashSet<>();
            for (PreparedEntry entry : prepared) {
                addJarEntry(output, entry.name(), entry.content());
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

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        log.info("Patch complete for: {} (patched: {}, skipped: {}, unchanged after errors: {}, elapsed: {} ms)",
                fileName, patchedClassCount.get(), skippedClassCount.get(), failedClassCount.get(), elapsedMs);
        return outputPath;
    }

    private List<PreparedEntry> readAndPrepareEntries(Path jarPath) throws IOException {
        List<PreparedEntry> entries = new ArrayList<>();
        long totalUncompressedBytes = 0;
        int entryCount = 0;
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(jarPath))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > resourceLimits.maxEntryCount()) {
                    throw new IOException("Plugin JAR exceeds maximum entry count of "
                            + resourceLimits.maxEntryCount());
                }

                String name = entry.getName();
                boolean retainContent = !entry.isDirectory() && !isSignatureFile(name);
                long remainingTotalBytes = resourceLimits.maxTotalUncompressedBytes()
                        - totalUncompressedBytes;
                ReadEntryResult readResult = readEntryBounded(
                        input,
                        entry,
                        remainingTotalBytes,
                        retainContent);
                totalUncompressedBytes += readResult.uncompressedBytes();

                if (!retainContent) {
                    continue;
                }

                byte[] content = readResult.content();
                if ("plugin.yml".equals(name)) {
                    entries.add(PreparedEntry.direct(name, modifyPluginYml(content)));
                } else if (name.endsWith(".class")) {
                    if (parallel) {
                        ForkJoinTask<byte[]> task = forkJoinPool.submit(
                                () -> transformClass(name, content));
                        entries.add(PreparedEntry.async(name, task));
                    } else {
                        entries.add(PreparedEntry.direct(name, transformClass(name, content)));
                    }
                } else {
                    entries.add(PreparedEntry.direct(name, content));
                }
            }
        }
        return entries;
    }

    private ReadEntryResult readEntryBounded(
            InputStream input,
            ZipEntry entry,
            long remainingTotalBytes,
            boolean retainContent) throws IOException {
        long declaredSize = entry.getSize();
        if (declaredSize > resourceLimits.maxEntryUncompressedBytes()) {
            throw entrySizeLimitException();
        }
        if (declaredSize > remainingTotalBytes) {
            throw totalSizeLimitException();
        }

        ByteArrayOutputStream output = retainContent ? new ByteArrayOutputStream() : null;
        byte[] buffer = new byte[8192];
        long entryBytes = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read == 0) {
                continue;
            }
            entryBytes += read;
            if (entryBytes > resourceLimits.maxEntryUncompressedBytes()) {
                throw entrySizeLimitException();
            }
            if (entryBytes > remainingTotalBytes) {
                throw totalSizeLimitException();
            }
            if (output != null) {
                output.write(buffer, 0, read);
            }
        }
        return new ReadEntryResult(output == null ? null : output.toByteArray(), entryBytes);
    }

    private IOException entrySizeLimitException() {
        return new IOException("JAR entry exceeds maximum expanded size of "
                + resourceLimits.maxEntryUncompressedBytes() + " bytes");
    }

    private IOException totalSizeLimitException() {
        return new IOException("Plugin JAR exceeds maximum expanded size of "
                + resourceLimits.maxTotalUncompressedBytes() + " bytes");
    }

    private byte[] transformClass(String entryName, byte[] classBytes) {
        String className = entryName.substring(0, entryName.length() - ".class".length())
                .replace('/', '.');

        try {
            if (!containsBytes(classBytes, BUKKIT_CONSTANT_POOL_MARKER) || !needsPatching(classBytes)) {
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
                ClassWriter writer = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
                byte[] transformed = transformer.transform(classNode, className, writer);
                if (transformed != null) {
                    result = transformed;
                    reader = new ClassReader(result);
                    classNode = new ClassNode(ASM_API);
                    reader.accept(classNode, ClassReader.EXPAND_FRAMES);
                }
            }

            patchedClassCount.incrementAndGet();
            patchedClassNames.add(className);
            if (verbose) {
                log.debug("Patched class: {}", className);
            }
            return result;
        } catch (RuntimeException | LinkageError exception) {
            failedClassCount.incrementAndGet();
            transformationFailures.add(new TransformationFailure(
                    className,
                    exception.getClass().getName(),
                    exception.getMessage() == null ? "" : exception.getMessage()));
            log.warn("Leaving class unmodified after transform failure: {} ({})",
                    className, exception.toString());
            return classBytes;
        }
    }

    private boolean needsPatching(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ScanningClassVisitor scanner = new ScanningClassVisitor(ASM_API, null);
        reader.accept(scanner, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return scanner.needsPatching();
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        int limit = haystack.length - needle.length;
        outer:
        for (int i = 0; i <= limit; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
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

    public int getFailedClassCount() {
        return failedClassCount.get();
    }

    public List<String> getPatchedClassNames() {
        return patchedClassNames.stream().sorted().toList();
    }

    public List<TransformationFailure> getTransformationFailures() {
        return transformationFailures.stream()
                .sorted(java.util.Comparator.comparing(TransformationFailure::className))
                .toList();
    }

    static record JarResourceLimits(
            long maxInputBytes,
            long maxEntryUncompressedBytes,
            long maxTotalUncompressedBytes,
            int maxEntryCount) {

        JarResourceLimits {
            if (maxInputBytes <= 0
                    || maxEntryUncompressedBytes <= 0
                    || maxTotalUncompressedBytes <= 0
                    || maxEntryCount <= 0) {
                throw new IllegalArgumentException("JAR resource limits must be positive");
            }
        }
    }

    private record ReadEntryResult(byte[] content, long uncompressedBytes) {
    }

    private static final class SafeClassWriter extends ClassWriter {

        private SafeClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                return super.getCommonSuperClass(type1, type2);
            } catch (TypeNotPresentException | LinkageError exception) {
                return "java/lang/Object";
            }
        }
    }

    public record TransformationFailure(String className, String exceptionType, String message) {
    }

    private record PreparedEntry(
            String name,
            byte[] directContent,
            ForkJoinTask<byte[]> contentTask) {

        private static PreparedEntry direct(String name, byte[] content) {
            return new PreparedEntry(name, content, null);
        }

        private static PreparedEntry async(String name, ForkJoinTask<byte[]> task) {
            return new PreparedEntry(name, null, task);
        }

        private byte[] content() {
            return contentTask == null ? directContent : contentTask.join();
        }
    }
}
