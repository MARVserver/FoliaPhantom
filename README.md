# Folia Phantom

[English](#english) | [日本語 (Japanese)](#日本語-japanese)

---

## English

Folia Phantom is a powerful tool designed to patch Bukkit plugins, ensuring their compatibility with the high-performance Folia server. It operates by dynamically transforming the plugin's bytecode to replace thread-unsafe API calls with their Folia-supported equivalents.

This project is structured as a multi-module Maven application, providing both a standalone command-line interface (CLI) for offline patching and a Bukkit plugin for on-the-fly transformations.

### Project Structure

- `folia-phantom-core`: A library module containing the essential patching logic, including the ASM transformers and the `PluginPatcher` utility.
- `folia-phantom-cli`: A command-line application that depends on the `core` module and produces a runnable JAR for patching plugins.
- `folia-phantom-plugin`: A Bukkit plugin that also depends on the `core` module and is intended for server environments.

### Building the Project

To build the project, you will need [Apache Maven](https://maven.apache.org/install.html) and a Java Development Kit (JDK) version 17 or higher.

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/folia-phantom.git
    cd folia-phantom
    ```

2.  **Build the project using Maven:**
    ```bash
    mvn clean package
    ```

This command will compile the source code, run any tests, and package the artifacts. The resulting JAR files will be located in the `target` directory of their respective modules.

### Usage

#### Command-Line Interface (CLI)

The CLI allows you to patch a single JAR file or an entire directory of JARs.

1.  **Locate the CLI JAR:**
    The runnable CLI JAR, named `Folia-Phantom-CLI-1.0.0.jar`, can be found in the `folia-phantom/folia-phantom-cli/target` directory after a successful build.

2.  **Run the CLI:**
    -   **Interactive Mode:**
        ```bash
        java -jar Folia-Phantom-CLI-1.0.0.jar
        ```
        The application will prompt you to enter the path to the JAR file or directory you wish to patch.

    -   **Direct Mode:**
        ```bash
        java -jar Folia-Phantom-CLI-1.0.0.jar /path/to/your/plugin.jar
        ```
        Replace `/path/to/your/plugin.jar` with the actual path to the plugin file or directory.

Patched plugins will be saved in the `patched-plugins` directory, which is created in the same location where you run the command.

#### Bukkit Plugin

The Bukkit plugin is designed to be installed on a Folia server to provide on-the-fly patching.

1.  **Locate the Plugin JAR:**
    The plugin JAR, named `Folia-Phantom-Plugin-1.0.0.jar`, can be found in the `folia-phantom/folia-phantom-plugin/target` directory.

2.  **Install the Plugin:**
    Copy the JAR file into your server's `plugins` directory and restart the server.

### How It Works

Folia Phantom uses the [ASM bytecode manipulation library](https://asm.ow2.io/) to inspect and modify the class files within a plugin's JAR. It identifies and replaces calls to Bukkit API methods that are not thread-safe with their Folia-compatible counterparts. This process is handled by a series of `ClassTransformer` implementations, each targeting a specific set of API methods.

### Contributing

Contributions to Folia Phantom are welcome! Please feel free to open an issue or submit a pull request on our [GitHub repository](https://github.com/your-username/folia-phantom).

### Contributing Translations
We welcome contributions to translate this documentation into other languages. To contribute:
1. Fork the repository.
2. Create a new file `README.<language_code>.md` (e.g., `README.de.md` for German).
3. Translate the content of this `README.md` file.
4. Submit a pull request with your changes.

### License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

## 日本語 (Japanese)

Folia Phantomは、Bukkitプラグインにパッチを適用し、高性能なFoliaサーバーとの互換性を確保するために設計された強力なツールです。プラグインのバイトコードを動的に変換し、スレッドセーフでないAPI呼び出しをFoliaがサポートするものに置き換えることで動作します。

このプロジェクトは、マルチモジュールのMavenアプリケーションとして構成されており、オフラインでのパッチ適用のためのスタンドアロンなコマンドラインインターフェース（CLI）と、実行時に変換を行うBukkitプラグインの両方を提供します。

### プロジェクト構成

- `folia-phantom-core`: ASMトランスフォーマーや`PluginPatcher`ユーティリティを含む、必要不可欠なパッチ適用ロジックを格納したライブラリモジュール。
- `folia-phantom-cli`: `core`モジュールに依存し、プラグインにパッチを適用するための実行可能なJARを生成するコマンドラインアプリケーション。
- `folia-phantom-plugin`: 同じく`core`モジュールに依存し、サーバー環境での使用を目的としたBukkitプラグイン。

### プロジェクトのビルド

プロジェクトをビルドするには、[Apache Maven](https://maven.apache.org/install.html)とJava Development Kit（JDK）バージョン17以降が必要です。

1.  **リポジトリをクローンする:**
    ```bash
    git clone https://github.com/your-username/folia-phantom.git
    cd folia-phantom
    ```

2.  **Mavenを使用してプロジェクトをビルドする:**
    ```bash
    mvn clean package
    ```

このコマンドは、ソースコードをコンパイルし、テストを実行して、成果物をパッケージ化します。生成されたJARファイルは、各モジュールの`target`ディレクトリに配置されます。

### 使用方法

#### コマンドラインインターフェース（CLI）

CLIを使用すると、単一のJARファイルまたはJARファイルが含まれるディレクトリ全体にパッチを適用できます。

1.  **CLI JARの場所:**
    実行可能なCLI JAR（`Folia-Phantom-CLI-1.0.0.jar`）は、ビルドが成功した後、`folia-phantom/folia-phantom-cli/target`ディレクトリにあります。

2.  **CLIの実行:**
    -   **対話モード:**
        ```bash
        java -jar Folia-Phantom-CLI-1.0.0.jar
        ```
        パッチを適用したいJARファイルまたはディレクトリのパスを入力するよう求められます。

    -   **直接モード:**
        ```bash
        java -jar Folia-Phantom-CLI-1.0.0.jar /path/to/your/plugin.jar
        ```
        `/path/to/your/plugin.jar`を、実際のプラグインファイルまたはディレクトリのパスに置き換えてください。

パッチが適用されたプラグインは、コマンドを実行したのと同じ場所に作成される`patched-plugins`ディレクトリに保存されます。

#### Bukkitプラグイン

Bukkitプラグインは、実行時にパッチを提供するためにFoliaサーバーにインストールするように設計されています。

1.  **プラグインJARの場所:**
    プラグインJAR（`Folia-Phantom-Plugin-1.0.0.jar`）は、`folia-phantom/folia-phantom-plugin/target`ディレクトリにあります。

2.  **プラグインのインストール:**
    JARファイルをサーバーの`plugins`ディレクトリにコピーし、サーバーを再起動します。

### 仕組み

Folia Phantomは、[ASMバイトコード操作ライブラリ](https://asm.ow2.io/)を使用して、プラグインのJAR内のクラスファイルを検査・変更します。スレッドセーフでないBukkit APIメソッドへの呼び出しを特定し、Folia互換のメソッドに置き換えます。このプロセスは、それぞれが特定のAPIメソッド群を対象とする一連の`ClassTransformer`実装によって処理されます。

### 貢献

Folia Phantomへの貢献を歓迎します！ [GitHubリポジトリ](https://github.com/your-username/folia-phantom)で、気軽にissueを立てたり、プルリクエストを送信してください。

### 翻訳への貢献
このドキュメントを他の言語に翻訳するための貢献を歓迎します。貢献するには：
1. リポジトリをフォークしてください。
2. 新しいファイル `README.<言語コード>.md` を作成してください（例：ドイツ語の場合は `README.de.md`）。
3. この`README.md`ファイルの内容を翻訳してください。
4. 変更内容を記載したプルリクエストを送信してください。

### ライセンス

このプロジェクトはMITライセンスの下でライセンスされています。詳細は[LICENSE](LICENSE)ファイルをご覧ください。
