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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * JAR 全体のパッチ処理を統括するオーケストレーター。
 *
 * <p>入力 JAR に対して以下の処理を順次実行する:
 * <ol>
 *   <li>署名ファイル (.SF, .DSA, .RSA) の除去</li>
 *   <li>{@code plugin.yml} への {@code folia-supported: true} 追加</li>
 *   <li>.class ファイルの並列変換（ForkJoinPool 使用）</li>
 *   <li>FoliaPatcher ランタイムクラスのバンドル</li>
 *   <li>出力 JAR の書き出し</li>
 * </ol>
 * </p>
 *
 * <p>変換は {@link ScanningClassVisitor} による事前スキャンで
 * パッチ要否を判定し、必要なクラスのみトランスフォーマーチェーンを適用する。
 * これにより不要な変換処理をスキップし、パフォーマンスを最適化する。</p>
 */
public final class PluginPatcher {

    /** ロガーインスタンス */
    private static final Logger log = LoggerFactory.getLogger(PluginPatcher.class);

    /** ASM API バージョン */
    private static final int ASM_API = Opcodes.ASM9;

    /** トランスフォーマーチェーン（適用順） */
    private final List<ClassTransformer> transformers;

    /** 並列変換に使用するスレッドプール */
    private final ForkJoinPool forkJoinPool;

    /** 出力先ディレクトリ */
    private final Path outputDir;

    /** バーボースログ出力フラグ */
    private final boolean verbose;

    /** 処理済みクラス数 */
    private final AtomicInteger patchedClassCount;

    /** スキップされたクラス数 */
    private final AtomicInteger skippedClassCount;

    /**
     * 出力ディレクトリと Verbose フラグを指定して生成する。
     *
     * @param outputDir パッチ済みJARの出力先ディレクトリ
     * @param verbose   詳細ログを出力する場合は true
     */
    public PluginPatcher(Path outputDir, boolean verbose) {
        this.outputDir = outputDir;
        this.verbose = verbose;
        this.forkJoinPool = ForkJoinPool.commonPool();
        this.patchedClassCount = new AtomicInteger(0);
        this.skippedClassCount = new AtomicInteger(0);
        this.transformers = new ArrayList<>();
        initializeTransformers();
    }

    /**
     * トランスフォーマーチェーンを初期化する。
     *
     * <p>適用順序:
     * <ol>
     *   <li>{@link ThreadSafetyTransformer} — Block 書き込み操作のスレッドセーフ化</li>
     *   <li>{@link WorldGenClassTransformer} — World 生成/操作の非同期化</li>
     *   <li>{@link EntitySchedulerTransformer} — Entity/LivingEntity 操作のスレッドセーフ化</li>
     *   <li>{@link PlayerSafetyTransformer} — Player/Inventory 操作のスレッドセーフ化</li>
     *   <li>{@link SchedulerClassTransformer} — BukkitScheduler/BukkitRunnable の置き換え</li>
     * </ol>
     * </p>
     */
    private void initializeTransformers() {
        this.transformers.add(new ThreadSafetyTransformer());
        this.transformers.add(new WorldGenClassTransformer());
        this.transformers.add(new EntitySchedulerTransformer());
        this.transformers.add(new PlayerSafetyTransformer());
        this.transformers.add(new SchedulerClassTransformer());
    }

    /**
     * 単一の JAR ファイルにパッチを適用する。
     *
     * @param jarPath 入力 JAR のパス
     * @return パッチ済み JAR の出力パス
     * @throws IOException JAR の読み書きに失敗した場合
     */
    public Path patchPlugin(Path jarPath) throws IOException {
        log.info("Patching plugin: {}", jarPath.getFileName());
        if (this.verbose) {
            log.info("Input path: {}", jarPath.toAbsolutePath());
        }

        // 出力ディレクトリが存在しない場合は作成
        Files.createDirectories(this.outputDir);

        // 出力ファイル名の生成
        String outputFileName = "patched-" + jarPath.getFileName().toString();
        Path outputPath = this.outputDir.resolve(outputFileName);

        // パッチ処理の実行
        try (JarInputStream jis = new JarInputStream(
                Files.newInputStream(jarPath));
             JarOutputStream jos = new JarOutputStream(
                     Files.newOutputStream(outputPath))) {

            processJar(jis, jos);
        }

        log.info("Patch complete for: {} (output: {})",
                jarPath.getFileName(), outputPath.getFileName());
        log.info("  Patched classes: {}, Skipped classes: {}",
                this.patchedClassCount.get(), this.skippedClassCount.get());

        return outputPath;
    }

    /**
     * JAR エントリを逐次処理する。
     *
     * @param jis 入力 JAR ストリーム
     * @param jos 出力 JAR ストリーム
     * @throws IOException 入出力エラーが発生した場合
     */
    private void processJar(JarInputStream jis, JarOutputStream jos) throws IOException {
        JarEntry entry;
        while ((entry = jis.getNextJarEntry()) != null) {
            String entryName = entry.getName();
            if (isSignatureFile(entryName)) {
                log.debug("Removing signature file: {}", entryName);
                continue;
            }
            if (entryName.equals("plugin.yml")) {
                byte[] modifiedYml = modifyPluginYml(jis);
                addJarEntry(jos, entryName, modifiedYml);
                continue;
            }
            if (entryName.endsWith(".class")) {
                byte[] originalBytes = readAllBytes(jis);
                byte[] patchedBytes = transformClass(entryName, originalBytes);
                addJarEntry(jos, entryName, patchedBytes);
                continue;
            }
            // 通常ファイルはそのままコピー
            byte[] rawBytes = readAllBytes(jis);
            addJarEntry(jos, entryName, rawBytes);
        }
        // FoliaPatcher ランタイムクラスをバンドル
        bundleRuntimeClasses(jos);
    }

    /**
     * 単一の .class ファイルを変換する。
     *
     * <p>まず {@link ScanningClassVisitor} で事前スキャンを行い、
     * パッチが必要な場合のみトランスフォーマーチェーンを適用する。</p>
     *
     * @param entryName   JAR 内のエントリ名
     * @param classBytes  元のクラスバイト配列
     * @return 変換後のクラスバイト配列
     */
    private byte[] transformClass(String entryName, byte[] classBytes) {
        // クラスの内部名を取得
        String className = entryName.replace('/', '.');
        if (className.endsWith(".class")) {
            className = className.substring(0, className.length() - 6);
        }

        // 事前スキャン: パッチが必要か判定
        if (!needsPatching(classBytes)) {
            this.skippedClassCount.incrementAndGet();
            if (this.verbose) {
                log.debug("Skipping class (no patch needed): {}", className);
            }
            return classBytes;
        }

        // クラスノードの構築
        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        // トランスフォーマーチェーンの並列適用
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        byte[] result = classBytes;
        for (ClassTransformer transformer : this.transformers) {
            byte[] transformed = transformer.transform(classNode, className, writer);
            if (transformed != null) {
                result = transformed;
            }
        }

        this.patchedClassCount.incrementAndGet();
        if (this.verbose) {
            log.debug("Patched class: {}", className);
        }
        return result;
    }

    /**
     * {@link ScanningClassVisitor} を用いて、
     * クラスにパッチが必要か事前判定する。
     *
     * @param classBytes クラスバイト配列
     * @return パッチが必要であれば true
     */
    private boolean needsPatching(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ScanningClassVisitor scanner = new ScanningClassVisitor(ASM_API, null);
        reader.accept(scanner, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                | ClassReader.SKIP_FRAMES);
        return scanner.needsPatching();
    }

    /**
     * {@code plugin.yml} に {@code folia-supported: true} を追加する。
     *
     * @param jis 入力ストリーム（plugin.yml の内容）
     * @return 修正後の YAML バイト配列
     * @throws IOException 読み取りエラーが発生した場合
     */
    private byte[] modifyPluginYml(JarInputStream jis) throws IOException {
        String content = new String(readAllBytes(jis), "UTF-8");
        if (content.contains("folia-supported:")) {
            log.warn("plugin.yml already contains folia-supported flag");
        } else {
            // 改行コードを検出して適切に追記
            String lineSeparator = content.contains("\r\n") ? "\r\n" : "\n";
            content = content.trim() + lineSeparator + "folia-supported: true" + lineSeparator;
        }
        return content.getBytes("UTF-8");
    }

    /**
     * 出力 JAR に FoliaPatcher のランタイムクラスをバンドルする。
     *
     * <p>以下のクラスを同梱する:
     * <ul>
     *   <li>{@code FoliaPatcher.class}</li>
     *   <li>{@code FoliaPatcher$FoliaBukkitTask.class}</li>
     *   <li>{@code FoliaPatcher$FoliaChunkGenerator.class}</li>
     * </ul>
     * </p>
     *
     * @param jos 出力 JAR ストリーム
     * @throws IOException 書き出しエラーが発生した場合
     */
    private void bundleRuntimeClasses(JarOutputStream jos) throws IOException {
        String[] runtimeClasses = {
            "com/patch/foliaphantom/patcher/FoliaPatcher.class",
            "com/patch/foliaphantom/patcher/FoliaPatcher$FoliaBukkitTask.class",
            "com/patch/foliaphantom/patcher/FoliaPatcher$FoliaChunkGenerator.class"
        };
        for (String classPath : runtimeClasses) {
            InputStream classStream = getClass().getClassLoader()
                    .getResourceAsStream(classPath);
            if (classStream == null) {
                log.warn("Runtime class not found in classpath: {}", classPath);
                continue;
            }
            byte[] classBytes = readAllBytes(classStream);
            classStream.close();
            addJarEntry(jos, classPath, classBytes);
            log.debug("Bundled runtime class: {}", classPath);
        }
    }

    /**
     * ファイルが JAR 署名ファイルか判定する。
     *
     * @param entryName JAR エントリ名
     * @return 署名ファイルであれば true
     */
    private static boolean isSignatureFile(String entryName) {
        String upper = entryName.toUpperCase();
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".SF")
                || upper.endsWith(".DSA")
                || upper.endsWith(".RSA"));
    }

    /**
     * JAR 出力ストリームにエントリを追加する。
     *
     * @param jos  出力 JAR ストリーム
     * @param name エントリ名
     * @param data エントリのバイトデータ
     * @throws IOException 書き出しエラーが発生した場合
     */
    private static void addJarEntry(JarOutputStream jos, String name, byte[] data)
            throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setSize(data.length);
        jos.putNextEntry(entry);
        jos.write(data);
        jos.closeEntry();
    }

    /**
     * 入力ストリームから全バイトを読み取る。
     *
     * @param is 入力ストリーム
     * @return バイト配列
     * @throws IOException 読み取りエラーが発生した場合
     */
    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = is.read(tmp)) != -1) {
            buffer.write(tmp, 0, n);
        }
        return buffer.toByteArray();
    }

    // ========================================================================
    // アクセッサ（統計情報）
    // ========================================================================

    /**
     * パッチ適用済みのクラス数を返す。
     *
     * @return パッチ済みクラス数
     */
    public int getPatchedClassCount() {
        return this.patchedClassCount.get();
    }

    /**
     * スキップされたクラス数を返す。
     *
     * @return スキップされたクラス数
     */
    public int getSkippedClassCount() {
        return this.skippedClassCount.get();
    }
}
