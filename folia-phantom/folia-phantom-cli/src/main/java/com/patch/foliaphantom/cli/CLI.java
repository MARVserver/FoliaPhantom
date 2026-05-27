package com.patch.foliaphantom.cli;

import com.patch.foliaphantom.patcher.PluginPatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

/**
 * Folia Phantom のコマンドラインインターフェース。
 *
 * <p>Bukkit プラグイン JAR を Folia 互換にパッチするための
 * コマンドラインツール。単一 JAR のパッチ、ディレクトリ内の
 * 全 JAR の一括パッチ、対話モードの3つの動作モードを提供する。</p>
 *
 * <p>使用法:
 * <pre>
 *   java -jar Folia-Phantom-CLI-1.0.0.jar path/to/plugin.jar
 *   java -jar Folia-Phantom-CLI-1.0.0.jar path/to/jars/
 *   java -jar Folia-Phantom-CLI-1.0.0.jar
 * </pre>
 * </p>
 */
public final class CLI {

    /** ロガーインスタンス */
    private static final Logger log = LoggerFactory.getLogger(CLI.class);

    /** デフォルトの出力ディレクトリ名 */
    private static final String DEFAULT_OUTPUT_DIR = "patched-plugins";

    /** JAR ファイルの拡張子 */
    private static final String JAR_EXTENSION = ".jar";

    private CLI() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * エントリポイント。
     *
     * <p>コマンドライン引数に応じて動作を切り替える:
     * <ul>
     *   <li>引数あり: ファイルまたはディレクトリとして処理</li>
     *   <li>引数なし: 対話モードで起動</li>
     * </ul>
     * </p>
     *
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        // ロガー設定 + バナー表示
        configureLogger();
        displayBanner();

        // 入力パスの解決
        Path inputPath = resolveInputPath(args);
        if (inputPath == null) {
            return;
        }

        // 出力ディレクトリの生成
        Path outputDir = Paths.get(DEFAULT_OUTPUT_DIR);
        PluginPatcher patcher = new PluginPatcher(outputDir, true);

        try {
            // 単一JAR または ディレクトリ内全JAR のパッチ
            if (Files.isDirectory(inputPath)) {
                patchDirectory(patcher, inputPath);
            } else {
                patchSingleFile(patcher, inputPath);
            }
            log.info("All patching operations completed successfully.");
        } catch (IOException e) {
            log.error("Patching failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * コマンドライン引数または対話入力から入力パスを解決する。
     *
     * @param args コマンドライン引数
     * @return 解決された入力パス、キャンセル時は null
     */
    private static Path resolveInputPath(String[] args) {
        if (args.length > 0) {
            Path path = Paths.get(args[0]);
            if (!Files.exists(path)) {
                log.error("Input path does not exist: {}", path.toAbsolutePath());
                return null;
            }
            return path;
        }
        // 対話モード
        return promptForPath();
    }

    /**
     * 対話モードでパスを入力させる。
     *
     * @return 入力されたパス、キャンセル時は null
     */
    private static Path promptForPath() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Enter path to plugin JAR or directory: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                log.error("No path provided.");
                return null;
            }
            Path path = Paths.get(input);
            if (!Files.exists(path)) {
                log.error("Path does not exist: {}", path.toAbsolutePath());
                return null;
            }
            return path;
        }
    }

    /**
     * ディレクトリ内の全 JAR ファイルを一括パッチする。
     *
     * @param patcher PluginPatcher インスタンス
     * @param dir     対象ディレクトリ
     * @throws IOException パッチ処理に失敗した場合
     */
    private static void patchDirectory(PluginPatcher patcher, Path dir) throws IOException {
        log.info("Patching all JAR files in directory: {}", dir.toAbsolutePath());
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> jarFiles = files
                    .filter(f -> f.toString().endsWith(JAR_EXTENSION))
                    .toList();
            if (jarFiles.isEmpty()) {
                log.warn("No JAR files found in directory: {}", dir.toAbsolutePath());
                return;
            }
            for (Path jarFile : jarFiles) {
                patcher.patchPlugin(jarFile);
            }
        }
    }

    /**
     * 単一の JAR ファイルをパッチする。
     *
     * @param patcher PluginPatcher インスタンス
     * @param jarFile 対象 JAR ファイル
     * @throws IOException パッチ処理に失敗した場合
     */
    private static void patchSingleFile(PluginPatcher patcher, Path jarFile) throws IOException {
        if (!jarFile.toString().endsWith(JAR_EXTENSION)) {
            log.warn("File is not a JAR: {}", jarFile.getFileName());
            return;
        }
        patcher.patchPlugin(jarFile);
    }

    /**
     * 簡易ロガー設定を行う。
     *
     * <p>SLF4J のシンプルロガーを使って標準出力にログを出力する。</p>
     */
    private static void configureLogger() {
        System.setProperty(
                "org.slf4j.simpleLogger.defaultLogLevel", "info");
        System.setProperty(
                "org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty(
                "org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss");
        System.setProperty(
                "org.slf4j.simpleLogger.showThreadName", "false");
    }

    /**
     * 起動バナーを表示する。
     */
    private static void displayBanner() {
        System.out.println();
        System.out.println("  ███████╗ ██████╗ ██╗     ██╗ █████╗ ");
        System.out.println("  ██╔════╝██╔═══██╗██║     ██║██╔══██╗");
        System.out.println("  █████╗  ██║   ██║██║     ██║███████║");
        System.out.println("  ██╔══╝  ██║   ██║██║     ██║██╔══██║");
        System.out.println("  ██║     ╚██████╔╝███████╗██║██║  ██║");
        System.out.println("  ╚═╝      ╚═════╝ ╚══════╝╚═╝╚═╝  ╚═╝");
        System.out.println("  Folia Phantom CLI — pasta v1.0.0");
        System.out.println("  Bukkit → Folia bytecode transformer");
        System.out.println();
    }
}
