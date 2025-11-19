# Folia Phantom  
*General-purpose compatibility patcher for running Bukkit plugins on Folia servers*

## Overview

**Folia Phantom (General Edition)** is an open-source tool designed to make Bukkit plugins compatible with the **Folia multi-threaded server**.  
It analyzes plugin classes and replaces thread-unsafe Bukkit API calls with their Folia-safe counterparts.

Bytecode transformation is handled through **ASM**, enabling many plugins to run safely on Folia without manual modifications.

The project is structured into the following modules:

- **Core Library** — Bytecode transformation engine and plugin patcher  
- **CLI Tool** — Stand-alone JAR patching utility  
- **Bukkit Plugin Edition** — Applies patches dynamically on the server  
- **GUI Edition** — Desktop application for one-click patching

---

## Project Structure

### `folia-phantom-core`
The central library containing the transformation logic, including:
- ASM-based transformers  
- Bytecode analyzers  
- Bukkit → Folia API mapping rules  
- JAR patching utilities

### `folia-phantom-cli`
A command-line tool providing:
- Single-file or directory-wide JAR patching  
- Batch processing  
- Local Folia-compatibility preparation workflows

### `folia-phantom-plugin`
A Bukkit plugin that dynamically patches other plugins at load time:
- Analyzes plugin bytecode on load  
- Automatically replaces incompatible Folia API calls  
- Ensures safer behavior within Folia’s region-threading model

### `folia-phantom-GUI`
A desktop software edition featuring:
- One-click plugin patching  
- User-friendly interface for non-technical operators

---

## Legacy Code Notice
- Modification of legacy components is allowed under the project’s license.  
- Patched output must be made publicly visible to ensure transparency for all users.

# Folia Phantom  
*Folia サーバー向け Bukkit プラグイン互換パッチツール（一般版）*

## 概要

**Folia Phantom（一般版）** は、Bukkit プラグインを **Folia マルチスレッドサーバー** と互換化するためのオープンツールです。  
プラグイン内のクラスを解析し、スレッドセーフでない Bukkit API 呼び出しを Folia 対応 API へ置換する仕組みを備えています。

バイトコード変換には **ASM** を使用し、手動修正なしで多くのプラグインが Folia 上で安全に動作するようになります。

本プロジェクトは以下の構成で提供されます：

- **Core ライブラリ** — バイトコード変換ロジックとプラグインパッチャ
- **CLI ツール** — JAR ファイルのパッチ適用ツール
- **Bukkit プラグイン版** — サーバーで動的にパッチを適用

---

## プロジェクト構成

### `folia-phantom-core`
変換ロジックの中核となるライブラリで、以下を含みます：
- ASM トランスフォーマー  
- バイトコード解析器  
- Bukkit → Folia API マッピング  
- プラグイン JAR へのパッチ適用ユーティリティ

### `folia-phantom-cli`
コマンドラインツールとして利用可能で、主に以下に使用します：
- 単体/ディレクトリ単位の JAR パッチ  
- パッチ適用のバッチ処理  
- ローカル環境での Folia 互換化作業

### `folia-phantom-plugin`
サーバー上で動作し、プラグインロード時に変換を実行：
- プラグインのバイトコードをロード時に解析  
- Folia 非対応 API を自動置換  
- Folia のリージョンスレッドモデルに対する安全性を補助

### `folia-phantom-GUI`
GUIで簡単にパッチできるソフトウェア版
- ワンクリック

### 古いコード
- 改造を許可しますがライセンスを守ってください
- パッチしたものはすべての人が見れるように開示してください

---

