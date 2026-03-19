# Folia Phantom 👻
# ⚠Before transitioning to the next-generation project
[English](#english) | [日本語 (Japanese)](#日本語-japanese)

---

## English

**Folia Phantom** is a professional-grade bytecode transformation tool designed to bridge the gap between legacy Bukkit plugins and the high-performance [Folia](https://github.com/PaperMC/Folia) server.

By dynamically rewriting class files, Folia Phantom automatically converts thread-unsafe API calls (such as global schedulers and direct block modifications) into Folia-compatible region-based or asynchronous operations.

### ✨ Key Features

- **Automated Patching**: Seamlessly converts `BukkitScheduler` and `BukkitRunnable` to Folia schedulers.
- **Thread Safety Enforcement**: Automatically wraps `Block.setType` and other world-modifying calls to execute on the correct region threads.
- **Modern Pro GUI**: A premium, glassmorphism-styled Desktop UI for easy batch processing.
- **High Performance**: Parallel processing with `ForkJoinPool` and fast-fail bytecode scanning for lightning-fast patching.
- **Compatibility First**: Automatically handles JAR signatures and updates `plugin.yml` with the `folia-supported` flag.
- **CLI & Plugin Support**: Available as a standalone GUI, CLI tool, or a server-side plugin for on-the-fly patching.

### 🏗️ Project Structure

- `folia-phantom-core`: The heart of the project containing ASM transformers and patching logic.
- `folia-phantom-gui`: Modern JavaFX application for desktop environments.
- `folia-phantom-cli`: Command-line tool for automated workflows and headless environments.
- `folia-phantom-plugin`: Bukkit plugin implementation for real-time server-side transformation.

### 🚀 Getting Started

#### Building from Source
Requires JDK 21+ and Maven or Gradle.
```bash
# Maven
mvn -f folia-phantom/pom.xml clean package

# Gradle (wrapper bootstrap in binary-restricted environments)
gradle wrapper --gradle-version 8.14.3 --no-validate-url
./gradlew clean build
```
Binary artifacts will be available in each module's build output directories (`target` for Maven, `build/libs` for Gradle).

#### Using the GUI
1. Run `Folia-Phantom-GUI-1.0.0.jar`.
2. Drag and drop your plugin JARs into the window.
3. Click **Patch All Plugins**.

---

## 日本語 (Japanese)

**Folia Phantom** は、レガシーな Bukkit プラグインと高性能な [Folia](https://github.com/PaperMC/Folia) サーバーの互換性を確保するためのプロフェッショナル向けバイトコード変換ツールです。

クラスファイルを動的に書き換えることで、スレッドセーフでない API 呼び出し（グローバルスケジューラや直接的なブロック操作など）を、Folia がサポートするリージョンベースまたは非同期の操作に自動的に変換します。

### ✨ 主な機能

- **自動パッチ適用**: `BukkitScheduler` や `BukkitRunnable` を Folia のスケジューラにシームレスに変換。
- **スレッド安全性の強化**: `Block.setType` などの世界操作を、正しいリージョンスレッドで実行するように自動的にラッピング。
- **モダンな GUI**: 一括処理を容易にする、グラスモーフィズムデザインのプレミアムなデスクトップ UI。
- **高いパフォーマンス**: `ForkJoinPool` による並列処理と、高速なバイトコードスキャニングによる圧倒的な処理速度。
- **高い互換性**: JAR 署名を自動的に処理し、`plugin.yml` に `folia-supported` フラグを自動追加。
- **多様な実行形態**: GUI、CLI、およびサーバーサイドプラグイン（リアルタイム変換）の全形態をサポート。

### 🏗️ プロジェクト構成

- `folia-phantom-core`: ASM トランスフォーマーとパッチロジックを含むコアライブラリ。
- `folia-phantom-gui`: デスクトップ環境向けのモダンな JavaFX アプリケーション。
- `folia-phantom-cli`: 自動化ワークフローやヘッドレス環境向けの CLI ツール。
- `folia-phantom-plugin`: サーバー上でのリアルタイム変換を実現する Bukkit プラグイン。

### 🚀 はじめかた

#### ビルド
JDK 21 以上と Maven または Gradle が必要です。
```bash
# Maven
mvn -f folia-phantom/pom.xml clean package

# Gradle（バイナリ制限環境向けに wrapper を先に生成）
gradle wrapper --gradle-version 8.14.3 --no-validate-url
./gradlew clean build
```
ビルドされた JAR は各モジュールの出力ディレクトリ（Maven は `target`、Gradle は `build/libs`）に生成されます。

#### GUI の使用方法
1. `Folia-Phantom-GUI-1.0.0.jar` を実行します。
2. プラグインの JAR ファイルをウィンドウにドラッグ＆ドロップします。
3. **Patch All Plugins** をクリックします。

---

### 📄 License
Licensed under the **MARV License**. See `LICENSE` for more details.
Copyright © 2025 **Marv**.
