# FoliaPhantom-extra
![ロゴ](logo.png)
# **日本語 (Japanese)** | [English](#english)
# windows only
- Support for Linux will be added in the future.

[![GitHub release](https://img.shields.io/github/v/release/MARVserver/FoliaPhantom.svg)](https://github.com/MARVserver/FoliaPhantom/releases)
# 重要
- licenseが変更されました


## 🔗 日本語解説動画 クリック⇩ AI音声
[![動画を見る](https://img.youtube.com/vi/l1Tjgye6z6Q/0.jpg)](https://youtu.be/l1Tjgye6z6Q)


**FoliaPhantom-extra** は、旧来の Bukkit / Spigot / Paper プラグインを、Folia サーバー（PaperMC のマルチスレッド対応版）で動作させるための、手動パッチ適用ユーティリティです。

このプラグインは、対象プラグインの JAR ファイルを修正し、Folia のスレッドモデルに適合しないスケジューラAPI呼び出しを、FoliaネイティブのAPI呼び出しに置き換えます。これにより、開発者が Folia 対応を施していないプラグインでも、多くの場合で動作させることが可能になります。

---

## 🚀 主な特徴

- **バイトコード変換技術**: プラグインのクラスファイルを直接解析し、`BukkitScheduler` の呼び出しを Folia の `RegionScheduler` や `AsyncScheduler` を使うコードに書き換えます。
- **`plugin.yml` の自動パッチ**: パッチ処理中に、対象プラグインの `plugin.yml` に `folia-supported: true` フラグを自動的に追加・修正し、Folia サーバーに正式対応プラグインとして認識させます。
- **幅広いスケジューラ対応**: `runTask`, `runTaskTimer` といった主要なメソッドに加え、`scheduleSyncDelayedTask` などの古いメソッドにも対応。同期・非同期タスクの両方を適切に変換します。
- **地形・ワールド生成の互換性**: `ChunkGenerator`や同期的な`createWorld`呼び出しをラップし、Foliaの非同期環境で動作するように試みます。

---

## ⚙️ 導入と使用手順

`FoliaPhantom-extra` は、他のプラグインを自動的にスキャンするのではなく、指定したJARファイルを手動で修正するツールです。

1.  **FoliaPhantom-extra の導入**: [Releasesページ](https://github.com/MARVserver/FoliaPhantom/releases)から最新版の `FoliaPhantom-extra.jar` をダウンロードし、サーバーの `plugins` フォルダに配置します。
2.  **ディレクトリの生成**: サーバーを一度起動します。`plugins/FoliaPhantom-extra/` フォルダ内に `input` と `output` という2つのディレクトリが自動で生成されます。
3.  **パッチ対象の配置**: サーバーを停止し、パッチを適用したいプラグインのJARファイルを `input` ディレクトリに移動します。
4.  **パッチの実行**: 再度サーバーを起動します。`FoliaPhantom-extra` は `input` ディレクトリ内のすべてのJARファイルを検出し、パッチ処理を実行します。
5.  **成果物の確認**: パッチが完了したJARファイルは `output` ディレクトリに保存されます。処理が成功すると、`input` ディレクトリ内の元のファイルは削除されます。
6.  **プラグインの導入**: `output` ディレクトリからパッチ済みのJARファイルを取り出し、サーバーのメインの `plugins` フォルダに配置して使用してください。

---

## ⚠️ 制限事項と注意点

-   **同期的なワールド生成のリスク**: `createWorld`のような、完了までに時間のかかる処理を同期的に呼び出すプラグインをラップした場合、サーバーが一時的にフリーズ（ハングアップ）する可能性があります。Foliaの設計上、これは避けられないリスクであり、Watchdogが警告を出すことがあります。
-   **NMS/CB依存コード**: このプラグインはスケジューラや一部のワールド生成APIの互換性を解決しますが、`net.minecraft.server` (NMS) や `org.bukkit.craftbukkit` (CB) のコードに直接依存するプラグインの互換性までは保証しません。
-   **高度なクラスローダー操作**: 一部のセキュリティ系プラグインや、特殊なクラスローダー処理を行うプラグインとは競合する可能性があります。
-   **100%の互換性保証ではない**: このプラグインは多くのケースで有効ですが、全てのプラグインの動作を保証するものではありません。問題が発生した場合は、[GitHub Issues](https://github.com/MARVserver/FoliaPhantom/issues) での報告をお願いします。

---

## 🛠️ 技術的な仕組み

FoliaPhantom-extra は、高度なバイトコードエンジニアリング技術を採用しています。

1.  **JARの検出**: `onEnable` フェーズで `plugins/FoliaPhantom-extra/input/` ディレクトリ内のJARファイルをスキャンします。
2.  **出力JARの生成**: パッチを適用するための出力用JARファイルを `plugins/FoliaPhantom-extra/output/` 内に作成します。
3.  **バイトコード変換 (ASM)**: 各クラスファイル（`.class`）をバイトコードレベルで解析し、以下のAPI呼び出しを発見すると、それを内部のパッチャーロジックを呼び出すコードに置き換えます。
    -   `org.bukkit.scheduler.BukkitScheduler` の各種メソッド
    -   `org.bukkit.plugin.Plugin#getDefaultWorldGenerator`
    -   `org.bukkit.Server#createWorld`
4.  **パッチャーの役割**: 内部ロジックは、元の呼び出しに対応する Folia のAPIを呼び出します。
    -   **スケジューラ**: `RegionScheduler`や`AsyncScheduler`を適切に使い分けます。
    -   **ワールド生成**: `getDefaultWorldGenerator`が返す`ChunkGenerator`をラッパーで包み、`createWorld`の呼び出しを専用のスレッドで実行してデッドロックを回避します。
5.  **`plugin.yml` のパッチ**: `folia-supported: true` をYAMLに追加・上書きします。
6.  **保存**: 全ての変換とパッチが完了したJARを `output` ディレクトリに保存します。

---

## 📦 ソースからのビルド

このプロジェクトをソースからビルドするには、Mavenが必要です。

### プラグインのみ

```bash
mvn clean package
```

### CLIバージョン

CLI（コマンドラインインターフェース）バージョンをビルドするには、`-Pcli`プロファイルを指定します。

```bash
mvn clean package -Pcli
```

---
---

# English

[![GitHub release](https://img.shields.io/github/v/release/MARVserver/FoliaPhantom.svg)](https://github.com/MARVserver/FoliaPhantom/releases)
# Important
- License has changed

**FoliaPhantom-extra** is a manual patching utility designed to run legacy Bukkit, Spigot, and Paper plugins on a Folia server (the multi-threaded version of PaperMC).

This plugin modifies a target plugin's JAR file, replacing scheduler API calls incompatible with Folia's threading model with their native Folia equivalents. This allows many plugins, even those not updated by their developers for Folia, to run successfully.

---

## 🚀 Key Features

-   **Bytecode Transformation Technology**: Directly analyzes plugin class files and rewrites `BukkitScheduler` calls to use Folia's `RegionScheduler` and `AsyncScheduler`.
-   **Automatic `plugin.yml` Patching**: During the patching process, it automatically adds or corrects the `folia-supported: true` flag in the target plugin's `plugin.yml`, ensuring it is recognized as a compliant plugin by the Folia server.
-   **Broad Scheduler Compatibility**: Supports major methods like `runTask` and `runTaskTimer`, as well as legacy methods like `scheduleSyncDelayedTask`, properly converting both synchronous and asynchronous tasks.
-   **Terrain & World Gen Compatibility**: Attempts to wrap calls to `ChunkGenerator` and synchronous `createWorld` to work within Folia's asynchronous environment.

---

## ⚙️ Installation and Usage

`FoliaPhantom-extra` is a tool that manually patches specified JAR files, rather than automatically scanning them.

1.  **Install FoliaPhantom-extra**: Download the latest `FoliaPhantom-extra.jar` from the [Releases page](https://github.com/MARVserver/FoliaPhantom/releases) and place it in your server's `plugins` folder.
2.  **Generate Directories**: Start the server once. This will automatically create two directories: `input` and `output` inside the `plugins/FoliaPhantom-extra/` folder.
3.  **Place Target JARs**: Stop the server. Move the plugin JAR files you want to patch into the `input` directory.
4.  **Run the Patcher**: Start the server again. `FoliaPhantom-extra` will detect all JARs in the `input` directory and perform the patching process.
5.  **Retrieve Patched JARs**: The patched JAR files will be saved to the `output` directory. Once a JAR is successfully processed, the original file in the `input` directory is deleted.
6.  **Install the Patched Plugin**: Take the patched JAR file from the `output` directory and move it to your main server `plugins` folder for use.

---

## ⚠️ Limitations & Disclaimers

-   **Risk of Synchronous World Generation**: Wrapping a plugin that calls time-consuming methods like `createWorld` synchronously may cause the server to freeze or hang temporarily. This is an unavoidable risk due to Folia's design, and you may see warnings from the Watchdog.
-   **NMS/CB Dependencies**: While this plugin resolves scheduler and some world-generation API issues, it does not guarantee compatibility for plugins that depend directly on `net.minecraft.server` (NMS) or `org.bukkit.craftbukkit` (CB) code.
-   **Advanced Class-loading**: May conflict with certain security plugins or other plugins that perform complex class-loader manipulations.
-   **Not a 100% Guarantee**: Although effective in many scenarios, this plugin does not guarantee that every plugin will work. If you encounter issues, please report them on our [GitHub Issues](https://github.com/MARVserver/FoliaPhantom/issues).

---

## 🛠️ How It Works (Technical Deep Dive)

FoliaPhantom-extra employs sophisticated bytecode engineering.

1.  **JAR Detection**: During the `onEnable` phase, it scans for JAR files in the `plugins/FoliaPhantom-extra/input/` directory.
2.  **Output JAR Creation**: It creates an output JAR file for patching inside `plugins/FoliaPhantom-extra/output/`.
3.  **Bytecode Transformation (ASM)**: It parses each class file (`.class`) at the bytecode level. When it finds a call to a targeted API, it replaces it with code that calls internal patcher logic. Targeted APIs include:
    -   Methods in `org.bukkit.scheduler.BukkitScheduler`
    -   `org.bukkit.plugin.Plugin#getDefaultWorldGenerator`
    -   `org.bukkit.Server#createWorld`
4.  **The Patcher's Role**: The internal logic invokes the appropriate Folia-native API.
    -   **Schedulers**: It intelligently redirects calls to `RegionScheduler` or `AsyncScheduler`.
    -   **World Generation**: It wraps the `ChunkGenerator` returned by `getDefaultWorldGenerator` and dispatches `createWorld` calls to a dedicated thread to prevent deadlocks.
5.  **`plugin.yml` Patching**: It adds or overwrites the YAML file to include `folia-supported: true`.
6.  **Saving**: The fully transformed and patched JAR is saved to the `output` directory.

---

## 📦 Building from Source

To build this project from source, you will need Maven.

### Plugin Only

```bash
mvn clean package
```

### CLI Version

To build the CLI (Command-Line Interface) version, specify the `-Pcli` profile:

```bash
mvn clean package -Pcli
```
