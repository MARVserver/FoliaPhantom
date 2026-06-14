package com.patch.foliaphantom.plugin;

import com.patch.foliaphantom.patcher.PluginPatcher;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 自動パッチ監視ワーカー。
 *
 * <p>Folia の {@code AsyncScheduler} を使用して定期的に監視フォルダをスキャンし、
 * 新規または更新された JAR ファイルを自動的にパッチする。
 * 最終更新日時で重複排除を行い、既に処理済みのファイルはスキップする。</p>
 *
 * <p>フィルタリング機能:
 * <ul>
 *   <li>ブラックリスト（glob パターン対応）</li>
 *   <li>ホワイトリスト（空の場合はすべて許可）</li>
 *   <li>既存 {@code folia-supported} プラグインのスキップ</li>
 * </ul>
 * </p>
 */
public final class PluginWatcher {

    /** ロガーインスタンス */
    private static final Logger log = LoggerFactory.getLogger(PluginWatcher.class);

    /** プラグインインスタンス */
    private final FoliaPhantomPlugin plugin;

    /** 監視対象ディレクトリ */
    private final Path watchFolder;

    /** パッチ済みファイルの出力先ディレクトリ */
    private final Path outputFolder;

    /** 詳細ログ出力フラグ */
    private final boolean verbose;

    /** スキャン間隔（秒） */
    private final int checkInterval;

    /** 処理済みファイルとその最終更新日時のマップ */
    private final Map<Path, FileTime> processedFiles;

    /** 処理済みファイル数 */
    private final AtomicInteger processedFileCount;

    /** Folia AsyncScheduler に登録したスケジュールタスク（shutdown 時にキャンセルする） */
    private volatile ScheduledTask watcherTask;

    private volatile boolean running;

    /**
     * @param plugin        プラグインインスタンス
     * @param watchFolder   監視対象ディレクトリ
     * @param outputFolder  パッチ済みファイルの出力先
     * @param verbose       詳細ログ出力
     * @param checkInterval スキャン間隔（秒）
     */
    public PluginWatcher(
            FoliaPhantomPlugin plugin,
            Path watchFolder,
            Path outputFolder,
            boolean verbose,
            int checkInterval) {
        this.plugin = plugin;
        this.watchFolder = watchFolder;
        this.outputFolder = outputFolder;
        this.verbose = verbose;
        this.checkInterval = checkInterval;
        this.processedFiles = new HashMap<>();
        this.processedFileCount = new AtomicInteger(0);
        this.running = false;
    }

    /**
     * 監視を開始する。
     *
     * <p>Folia の {@code AsyncScheduler} に定期タスクを登録し、
     * 指定された間隔で {@link #scanAndPatchPlugins()} を実行する。</p>
     */
    public void start() {
        this.running = true;
        this.watcherTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                this.plugin,
                scheduledTask -> { if (this.running) scanAndPatchPlugins(); },
                0,
                (long) this.checkInterval * 1000L,
                TimeUnit.MILLISECONDS);
        log.info("PluginWatcher started: interval={}s", this.checkInterval);
    }

    /**
     * 監視を停止する。
     */
    public void shutdown() {
        this.running = false;
        ScheduledTask task = this.watcherTask;
        if (task != null) {
            task.cancel();
            this.watcherTask = null;
        }
        log.info("PluginWatcher stopped. Total processed: {}",
                this.processedFileCount.get());
    }

    /**
     * 監視が稼働中かを返す。
     *
     * @return 稼働中であれば true
     */
    public boolean isRunning() {
        return this.running;
    }

    /**
     * これまでに処理したファイル数を返す。
     *
     * @return 処理済みファイル数
     */
    public int getProcessedFileCount() {
        return this.processedFileCount.get();
    }

    /**
     * 監視フォルダをスキャンし、新規/更新ファイルをパッチする。
     *
     * <p>以下の処理フローで動作する:
     * <ol>
     *   <li>監視フォルダの *.jar ファイルを列挙</li>
     *   <li>最終更新日時で重複排除</li>
     *   <li>フィルタリング（ブラックリスト、ホワイトリスト、
     *       skip-folia-supported）</li>
     *   <li>バックアップ作成（設定による）</li>
     *   <li>パッチ実行</li>
     *   <li>元ファイル削除（設定による）</li>
     * </ol>
     * </p>
     */
    public void scanAndPatchPlugins() {
        if (!Files.exists(this.watchFolder)) {
            if (this.verbose) {
                log.debug("Watch folder does not exist: {}", this.watchFolder);
            }
            return;
        }

        try (Stream<Path> files = Files.list(this.watchFolder)) {
            List<Path> jarFiles = files
                    .filter(f -> f.toString().endsWith(".jar"))
                    .toList();

            for (Path jarFile : jarFiles) {
                processJarFile(jarFile);
            }
        } catch (IOException e) {
            log.error("Error scanning watch folder '{}': {}",
                    this.watchFolder, e.getMessage());
        }
    }

    /**
     * 単一の JAR ファイルを処理する。
     *
     * @param jarFile 処理対象の JAR ファイルパス
     */
    private void processJarFile(Path jarFile) {
        try {
            // 最終更新日時の取得
            FileTime lastModified = Files.getLastModifiedTime(jarFile);

            // 重複チェック
            FileTime previousModified = this.processedFiles.get(jarFile);
            if (previousModified != null
                    && previousModified.toMillis() >= lastModified.toMillis()) {
                if (this.verbose) {
                    log.debug("Skipping already processed file: {}",
                            jarFile.getFileName());
                }
                return;
            }

            // フィルタリングチェック
            if (!shouldPatchPlugin(jarFile)) {
                log.debug("Skipping filtered file: {}", jarFile.getFileName());
                return;
            }

            // バックアップ作成
            boolean createBackup = this.plugin.getConfig()
                    .getBoolean("advanced.create-backup", true);
            if (createBackup) {
                createBackup(jarFile);
            }

            // パッチ実行
            PluginPatcher patcher = new PluginPatcher(
                    this.outputFolder, this.verbose);
            patcher.patchPlugin(jarFile);
            this.processedFiles.put(jarFile, lastModified);
            this.processedFileCount.incrementAndGet();
            log.info("Patched: {}", jarFile.getFileName());

            // 元ファイル削除
            boolean deleteOriginal = this.plugin.getConfig()
                    .getBoolean("advanced.delete-original", false);
            if (deleteOriginal) {
                Files.deleteIfExists(jarFile);
                log.debug("Deleted original file: {}", jarFile.getFileName());
            }

        } catch (IOException e) {
            log.error("Failed to process file '{}': {}",
                    jarFile.getFileName(), e.getMessage());
        }
    }

    /**
     * 指定された JAR ファイルをパッチすべきか判定する。
     *
     * <p>以下の条件で判定:
     * <ul>
     *   <li>ブラックリストに一致する場合はスキップ</li>
     *   <li>ホワイトリストが空でない場合、一致しなければスキップ</li>
     *   <li>既に {@code folia-supported} のプラグインをスキップする設定の場合</li>
     * </ul>
     * </p>
     *
     * @param jarFile 判定対象の JAR ファイル
     * @return パッチすべきであれば true
     */
    private boolean shouldPatchPlugin(Path jarFile) {
        String fileName = jarFile.getFileName().toString();

        // ブラックリストチェック
        List<String> blacklist = this.plugin.getConfig()
                .getStringList("filters.blacklist");
        for (String pattern : blacklist) {
            if (matchesGlob(fileName, pattern)) {
                log.debug("Blacklisted: {} (matched '{}')", fileName, pattern);
                return false;
            }
        }

        // ホワイトリストチェック
        List<String> whitelist = this.plugin.getConfig()
                .getStringList("filters.whitelist");
        if (!whitelist.isEmpty()) {
            boolean matched = false;
            for (String pattern : whitelist) {
                if (matchesGlob(fileName, pattern)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                log.debug("Not whitelisted: {}", fileName);
                return false;
            }
        }

        // skip-folia-supported チェック
        boolean skipFoliaSupported = this.plugin.getConfig()
                .getBoolean("filters.skip-folia-supported", true);
        if (skipFoliaSupported && isFoliaSupported(jarFile)) {
            log.debug("Already folia-supported: {}", fileName);
            return false;
        }

        return true;
    }

    /**
     * 簡易的な glob パターンマッチングを行う。
     *
     * @param text    検査対象の文字列
     * @param pattern glob パターン（* と ? をサポート）
     * @return パターンに一致すれば true
     */
    private static boolean matchesGlob(String text, String pattern) {
        // glob パターンを正規表現に変換
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return text.matches(regex);
    }

    /**
     * JAR ファイルが既に folia-supported かを簡易判定する。
     *
     * @param jarFile 判定対象の JAR ファイル
     * @return folia-supported であれば true
     */
    private static boolean isFoliaSupported(Path jarFile) {
        try (java.util.jar.JarInputStream jis =
                     new java.util.jar.JarInputStream(
                             Files.newInputStream(jarFile))) {
            java.util.jar.JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.getName().equals("plugin.yml")) {
                    // entry.getSize() は -1 を返すことがあるため readAllBytes を使用
                    byte[] content = jis.readAllBytes();
                    String yml = new String(content, java.nio.charset.StandardCharsets.UTF_8);
                    return yml.contains("folia-supported: true");
                }
            }
        } catch (IOException e) {
            log.warn("Error reading JAR '{}': {}", jarFile.getFileName(), e.getMessage());
        }
        return false;
    }

    /**
     * JAR ファイルのバックアップを作成する。
     *
     * @param jarFile バックアップ対象の JAR ファイル
     */
    private void createBackup(Path jarFile) {
        try {
            String backupFolder = this.plugin.getConfig()
                    .getString("advanced.backup-folder",
                            "plugins/folia-phantom-backups");
            Path backupDir = Paths.get(backupFolder);
            Files.createDirectories(backupDir);

            Path backupFile = backupDir.resolve(
                    jarFile.getFileName() + ".backup");
            Files.copy(jarFile, backupFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.debug("Backup created: {}", backupFile);
        } catch (IOException e) {
            log.warn("Failed to create backup for '{}': {}",
                    jarFile.getFileName(), e.getMessage());
        }
    }
}
