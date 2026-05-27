package com.patch.foliaphantom.plugin;

import com.patch.foliaphantom.patcher.FoliaPatcher;
import com.patch.foliaphantom.patcher.PluginPatcher;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Folia Phantom の Bukkit サーバープラグイン。
 *
 * <p>サーバー上で他のプラグインを動的にパッチするためのプラグイン。
 * 自動パッチ機能（{@link PluginWatcher}）、コマンド操作、
 * 統計情報表示を提供する。</p>
 *
 * <p>ライフサイクル:
 * <ul>
 *   <li>{@code onEnable()}: FoliaPatcher の初期化、設定読み込み、Watcher 開始、コマンド登録</li>
 *   <li>{@code onDisable()}: 非同期タスク停止、統計出力</li>
 * </ul>
 * </p>
 */
public final class FoliaPhantomPlugin extends JavaPlugin {

    /** ロガーインスタンス */
    private static final Logger log = LoggerFactory.getLogger(FoliaPhantomPlugin.class);

    /** 設定キー: auto-patch.enabled */
    private static final String KEY_AUTO_PATCH_ENABLED = "auto-patch.enabled";

    /** 設定キー: auto-patch.watch-folder */
    private static final String KEY_WATCH_FOLDER = "auto-patch.watch-folder";

    /** 設定キー: auto-patch.output-folder */
    private static final String KEY_OUTPUT_FOLDER = "auto-patch.output-folder";

    /** 設定キー: auto-patch.check-interval */
    private static final String KEY_CHECK_INTERVAL = "auto-patch.check-interval";

    /** 設定キー: logging.verbose */
    private static final String KEY_VERBOSE = "logging.verbose";

    /** 自動パッチ監視ワーカー */
    private PluginWatcher watcher;

    /**
     * プラグイン有効化時の初期化処理。
     *
     * <ol>
     *   <li>FoliaPatcher にプラグインインスタンスを設定</li>
     *   <li>設定ファイルの読み込み</li>
     *   <li>PluginWatcher の初期化と開始</li>
     *   <li>必要なディレクトリの作成</li>
     *   <li>コマンドの登録</li>
     * </ol>
     */
    @Override
    public void onEnable() {
        // FoliaPatcher にプラグインインスタンスを設定
        FoliaPatcher.plugin = this;

        // 設定ファイルの読み込み
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        // ディレクトリの作成
        createDirectories(config);

        // PluginWatcher の初期化と開始
        initializeWatcher(config);

        // コマンド登録
        getCommand("foliapatch").setExecutor(this);

        log.info("FoliaPhantom v{} enabled", getDescription().getVersion());
    }

    /**
     * プラグイン無効化時のクリーンアップ処理。
     *
     * <ol>
     *   <li>PluginWatcher の停止</li>
     *   <li>統計情報の出力</li>
     * </ol>
     */
    @Override
    public void onDisable() {
        if (this.watcher != null) {
            this.watcher.shutdown();
        }
        log.info("FoliaPhantom v{} disabled", getDescription().getVersion());
    }

    /**
     * 必要なディレクトリを作成する。
     *
     * @param config プラグイン設定
     */
    private void createDirectories(FileConfiguration config) {
        String watchFolder = config.getString(KEY_WATCH_FOLDER, "plugins/folia-patch-queue");
        String outputFolder = config.getString(KEY_OUTPUT_FOLDER, "plugins/patched");
        String backupFolder = config.getString(
                "advanced.backup-folder", "plugins/folia-phantom-backups");

        createDirectoryIfNotExists(Paths.get(watchFolder));
        createDirectoryIfNotExists(Paths.get(outputFolder));
        createDirectoryIfNotExists(Paths.get(backupFolder));
    }

    /**
     * ディレクトリが存在しない場合に作成する。
     *
     * @param path 作成するディレクトリのパス
     */
    private static void createDirectoryIfNotExists(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            log.warn("Failed to create directory '{}': {}", path, e.getMessage());
        }
    }

    /**
     * PluginWatcher を初期化して開始する。
     *
     * @param config プラグイン設定
     */
    private void initializeWatcher(FileConfiguration config) {
        boolean enabled = config.getBoolean(KEY_AUTO_PATCH_ENABLED, true);
        if (!enabled) {
            log.info("Auto-patching is disabled");
            return;
        }

        String watchFolder = config.getString(KEY_WATCH_FOLDER, "plugins/folia-patch-queue");
        String outputFolder = config.getString(KEY_OUTPUT_FOLDER, "plugins/patched");
        boolean verbose = config.getBoolean(KEY_VERBOSE, false);
        int checkInterval = config.getInt(KEY_CHECK_INTERVAL, 5);

        this.watcher = new PluginWatcher(
                this,
                Paths.get(watchFolder),
                Paths.get(outputFolder),
                verbose,
                checkInterval);
        this.watcher.start();
        log.info("Auto-patch watcher started: folder='{}', interval={}s",
                watchFolder, checkInterval);
    }

    /**
     * コマンド実行時のハンドラー。
     *
     * <p>対応コマンド:
     * <ul>
     *   <li>{@code /foliapatch <plugin-name>} — 指定プラグインをパッチ</li>
     *   <li>{@code /foliapatch list} — パッチ可能なプラグイン一覧</li>
     *   <li>{@code /foliapatch status} — パッチ統計情報</li>
     *   <li>{@code /foliapatch reload} — 設定再読み込み</li>
     * </ul>
     * </p>
     *
     * @param sender  コマンド実行者
     * @param command 実行されたコマンド
     * @param label   コマンドのエイリアス
     * @param args    コマンド引数
     * @return コマンドが正常に処理された場合は true
     */
    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /foliapatch <plugin-name|list|status|reload>");
            return false;
        }

        return switch (args[0].toLowerCase()) {
            case "list" -> handleListCommand(sender);
            case "status" -> handleStatusCommand(sender);
            case "reload" -> handleReloadCommand(sender);
            default -> handlePatchCommand(sender, args[0]);
        };
    }

    /**
     * 指定されたプラグインをパッチする。
     *
     * @param sender     コマンド実行者
     * @param pluginName プラグイン名またはJARファイル名
     * @return 処理結果
     */
    private boolean handlePatchCommand(CommandSender sender, String pluginName) {
        Path jarPath = findPluginJar(pluginName);
        if (jarPath == null) {
            sender.sendMessage("§cPlugin not found: " + pluginName);
            return false;
        }

        String outputFolder = getConfig().getString(KEY_OUTPUT_FOLDER, "plugins/patched");
        boolean verbose = getConfig().getBoolean(KEY_VERBOSE, false);
        PluginPatcher patcher = new PluginPatcher(Paths.get(outputFolder), verbose);

        try {
            Path result = patcher.patchPlugin(jarPath);
            sender.sendMessage("§aPatched successfully: " + result.getFileName());
        } catch (IOException e) {
            sender.sendMessage("§cFailed to patch: " + e.getMessage());
            log.error("Failed to patch plugin '{}'", pluginName, e);
        }
        return true;
    }

    /**
     * パッチ可能なプラグイン一覧を表示する。
     *
     * @param sender コマンド実行者
     * @return 処理結果
     */
    private boolean handleListCommand(CommandSender sender) {
        String watchFolder = getConfig().getString(
                KEY_WATCH_FOLDER, "plugins/folia-patch-queue");
        Path watchPath = Paths.get(watchFolder);

        if (!Files.exists(watchPath)) {
            sender.sendMessage("§eWatch folder does not exist: " + watchFolder);
            return true;
        }

        try (Stream<Path> files = Files.list(watchPath)) {
            List<Path> jars = files
                    .filter(f -> f.toString().endsWith(".jar"))
                    .toList();
            if (jars.isEmpty()) {
                sender.sendMessage("§eNo JAR files found in watch folder");
            } else {
                sender.sendMessage("§aPatchable plugins (" + jars.size() + "):");
                for (Path jar : jars) {
                    sender.sendMessage(" §7- §f" + jar.getFileName());
                }
            }
        } catch (IOException e) {
            sender.sendMessage("§cError listing plugins: " + e.getMessage());
        }
        return true;
    }

    /**
     * パッチ統計情報を表示する。
     *
     * @param sender コマンド実行者
     * @return 処理結果
     */
    private boolean handleStatusCommand(CommandSender sender) {
        sender.sendMessage("§6=== FoliaPhantom Status ===");
        sender.sendMessage("§7Auto-patch: §f"
                + (getConfig().getBoolean(KEY_AUTO_PATCH_ENABLED, true)
                        ? "§aenabled" : "§cdisabled"));
        if (this.watcher != null) {
            sender.sendMessage("§7Watcher running: §a" + this.watcher.isRunning());
            sender.sendMessage("§7Processed files: §f" + this.watcher.getProcessedFileCount());
        } else {
            sender.sendMessage("§7Watcher: §cnot initialized");
        }
        return true;
    }

    /**
     * 設定を再読み込みする。
     *
     * @param sender コマンド実行者
     * @return 処理結果
     */
    private boolean handleReloadCommand(CommandSender sender) {
        reloadConfig();
        // Watcher を再起動
        if (this.watcher != null) {
            this.watcher.shutdown();
        }
        initializeWatcher(getConfig());
        sender.sendMessage("§aConfiguration reloaded");
        return true;
    }

    /**
     * プラグイン名またはJARファイル名からパスを解決する。
     *
     * @param name プラグイン名またはJARファイル名
     * @return 解決されたパス、見つからない場合は null
     */
    private static Path findPluginJar(String name) {
        // plugins フォルダから検索
        Path pluginsDir = Paths.get("plugins");
        if (!Files.exists(pluginsDir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(pluginsDir)) {
            return files
                    .filter(f -> f.toString().endsWith(".jar"))
                    .filter(f -> f.getFileName().toString().contains(name))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Error searching for plugin JAR: {}", e.getMessage());
            return null;
        }
    }
}
