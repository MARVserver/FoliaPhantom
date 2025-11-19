# Folia Phantom --- 

*High-Performance Folia Compatibility Layer for Legacy Bukkit Plugins*

## Overview

**Folia Phantom --- Enterprise Edition** is a proprietary compatibility
and transformation engine for running legacy Bukkit plugins safely on
the **Folia multi-threaded server architecture**.\
The system performs **runtime-safe bytecode patching**, replacing
thread-unsafe Bukkit API calls with Folia-compatible equivalents while
preserving original plugin behavior.

This edition is closed-source and licensed exclusively for internal or
contracted deployment.

The platform consists of a multi-module Maven application featuring:

-   **Core Engine** --- The transformation runtime and ASM-based
    patching logic.
-   **Command-Line Processor** --- A standalone tool for offline binary
    patch generation.
-   **Server-Side Integration Plugin** --- A Folia plugin providing
    on-the-fly transformation.

## Modules

### `folia-phantom-core`

Internal library containing: - the bytecode analyzers - the instruction
transformers - the deterministic plugin patcher - compatibility rule
sets for Folia's scheduler and region-threading model

Not distributed independently.

### `folia-phantom-cli`

Standalone CLI application used for: - batch-patching plugin JARs -
validating compatibility - generating pre-patched optimized builds

Distributed as a sealed binary.

### `folia-phantom-plugin`

Server-side integration layer: - performs dynamic transformation during
plugin load - guarantees region-thread safety - ensures consistency with
Folia's concurrency model

Licensed for deployment on authorized Folia servers only.

## Building (Internal Use Only)

A JDK 17+ environment and Apache Maven are required.\
Source code access is restricted; only authorized maintainers may build
artifacts.

    mvn clean package

Generated modules appear under each module's `target/` directory.

## Usage

### Command-Line Processor

#### Interactive Mode

    java -jar Folia-Phantom-CLI.jar

The processor will request a target file or directory, then generate
transformed plugins inside:

    patched-plugins/

#### Direct Invocation

    java -jar Folia-Phantom-CLI.jar /path/to/plugin.jar

### Server Integration Plugin

1.  Place `Folia-Phantom-Plugin.jar` in the server's `plugins/`
    directory.
2.  Restart the server.
3.  The system will intercept plugin load events and apply
    transformations automatically.

## Technology

The engine uses **ASM 9+** to inspect and mutate bytecode at class-load
time.\
It replaces: - synchronous Bukkit scheduler calls - direct world access
from non-region threads - unsafe entity or block interaction APIs

with Folia-compliant equivalents, while maintaining compatibility and
execution-order guarantees.

The transformation pipeline is fully deterministic and trace-logged for
audit purposes.

## Licensing

Folia Phantom --- Enterprise Edition is a **closed-source commercial
product**.\
All rights reserved. Redistribution, reverse-engineering, decompilation,
or modification is strictly prohibited unless explicitly permitted in a
written agreement.

For licensing information, contract extensions, or internal distribution
rights, refer to the official license document included with this
product.

## Support

Enterprise customers may contact the designated support channel included
in the license pack.\
Bug reports, compatibility issues, and integration requests are handled
under a private SLA, not through any public tracker.

# Folia Phantom --- エンタープライズ版

*レガシー Bukkit プラグインを Folia
環境に安全適合させる高性能パッチエンジン*

## 概要

**Folia Phantom --- エンタープライズ版** は、レガシー Bukkit
プラグインを **Folia のマルチスレッドサーバーアーキテクチャ**
上で安全に動作させるための専用パッチエンジンです。\
本システムは **ランタイム安全なバイトコード変換**
を行い、スレッドセーフでない Bukkit API 呼び出しを Folia
互換の実装へ置換しつつ、元のプラグイン動作を維持します。

このエディションはクローズドソースであり、内部利用または契約者のみが使用できます。

本プロダクトは以下のマルチモジュール構成で提供されます：

-   **Core Engine** --- ASM ベースのパッチロジックおよび変換ランタイム\
-   **Command-Line Processor** --- オフラインでの JAR
    パッチ生成を行う単体アプリケーション\
-   **Server-Side Integration Plugin** --- 実行時に動的パッチを適用する
    Folia プラグイン

------------------------------------------------------------------------

## モジュール構成

### `folia-phantom-core`

内部ライブラリであり、以下を含みます： - バイトコードアナライザ\
- 命令トランスフォーマ\
- 決定論的パッチャ\
- Folia のスレッド・リージョンモデルに基づく互換ルールセット

外部配布はされません。

### `folia-phantom-cli`

スタンドアロン CLI ツールであり、以下を提供します： - プラグイン JAR
の一括パッチ生成\
- 互換性検証\
- 最適化済みの事前パッチビルド生成

封印バイナリとして提供されます。

### `folia-phantom-plugin`

サーバ統合プラグイン： - プラグイン読み込み時の動的変換\
- リージョンスレッド安全性の保証\
- Folia の並列実行モデルとの整合性維持

許可された Folia サーバでのみ使用可能。

------------------------------------------------------------------------

## ビルド方法（内部専用）

JDK 17 以上と Apache Maven が必要です。\
ソースコードへのアクセスは許可されたメンテナのみ行えます。

    mvn clean package

生成物は各モジュールの `target/` に配置されます。

------------------------------------------------------------------------

## 使用方法

### コマンドラインプロセッサ

#### 対話モード

    java -jar Folia-Phantom-CLI.jar

対象となるファイルまたはディレクトリの入力を求められ、変換後の JAR
は次のフォルダに出力されます：

    patched-plugins/

#### 直接指定モード

    java -jar Folia-Phantom-CLI.jar /path/to/plugin.jar

### サーバ統合プラグイン

1.  `Folia-Phantom-Plugin.jar` をサーバの `plugins/` に配置\
2.  サーバ再起動\
3.  読み込み時に変換処理が自動適用

------------------------------------------------------------------------

## 技術仕様

エンジンは **ASM 9+**
を用いてクラスロード時にバイトコードを解析・変換します。\
以下のような非スレッドセーフ API を検出し置換します：

-   同期的 Bukkit スケジューラ呼び出し\
-   リージョンスレッド外からのワールドアクセス\
-   安全でないエンティティ／ブロック操作

Folia 互換 API への置換は決定論的であり、追跡ログが生成されます。

------------------------------------------------------------------------

## ライセンス

Folia Phantom --- エンタープライズ版は **クローズドソース商用製品**
です。\
すべての権利は保護されています。再配布・逆コンパイル・改変は禁止されます（契約書で許可された場合を除く）。

ライセンス、再配布権、契約拡張に関する情報は、本製品に含まれる公式文書を参照してください。

------------------------------------------------------------------------

## サポート

エンタープライズ契約ユーザーは、ライセンスパッケージに記載された専用チャンネルからサポートへアクセスできます。\
互換性問題、動作異常、機能追加要望は SLA に基づき非公開で対応します。

