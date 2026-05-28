# Folia Phantom — 完全ドキュメント

> **プロフェッショナル向け Bukki → Folia 互換化バイトコード変換ツール**
>
> レガシーな Bukkit プラグインを [Folia](https://github.com/PaperMC/Folia)（PaperMC 製のリージョンマルチスレッドサーバー）で動作するよう、動的にバイトコード変換します。ASM ライブラリを用いてクラスファイルを書き換え、スレッドセーフでない API 呼び出しを Folia のリージョンベース・非同期スケジューラに自動で置き換えます。

| 項目 | 内容 |
|------|------|
| **作者** | Marv |
| **ライセンス** | MARV License v1.0.0 |
| **言語 / 環境** | Java 17+, Maven |
| **コア技術** | ASM 9.7（バイトコード操作） |
| **対応サーバー** | Folia（PaperMC フォーク） |

---

## 目次

1. [プロジェクト構造](#1-プロジェクト構造)
2. [folia-phantom-core — コアパッチエンジン](#2-folia-phantom-core--コアパッチエンジン)
3. [folia-phantom-cli — コマンドライン CLI](#3-folia-phantom-cli--コマンドライン-cli)
4. [folia-phantom-gui — JavaFX デスクトップ GUI](#4-folia-phantom-gui--javafx-デスクトップ-gui)
5. [folia-phantom-plugin — Bukkit サーバープラグイン](#5-folia-phantom-plugin--bukkit-サーバープラグイン)
6. [バイトコード変換の詳細](#6-バイトコード変換の詳細)
7. [ビルド方法](#7-ビルド方法)
8. [アーキテクチャ図](#8-アーキテクチャ図)
9. [注意事項](#9-注意事項)
10. [ライセンス](#10-ライセンス)

---

## 1. プロジェクト構造

```
folia-phantom（parent pom）
├── folia-phantom-core       ← コアパッチエンジン
├── folia-phantom-cli        ← コマンドラインインターフェース
├── folia-phantom-gui        ← JavaFX デスクトップ GUI
└── folia-phantom-plugin     ← Bukkit サーバープラグイン
```

---

## 2. folia-phantom-core — コアパッチエンジン

**依存**: ASM 9.7, Paper API 1.21.1-R0.1-SNAPSHOT（provided）

`pom.xml` 抜粋:

```xml
<artifactId>folia-phantom-core</artifactId>
<dependencies>
    <dependency>
        <groupId>org.ow2.asm</groupId>
        <artifactId>asm</artifactId>
        <version>${asm.version}</version>
    </dependency>
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.21.1-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### 2.1 主なクラス

| クラス | 役割 |
|--------|------|
| `PluginPatcher` | JAR 全体のパッチ処理を統括。並列変換、`plugin.yml` 編集、署名除去、ランタイムクラスバンドル |
| `FoliaPatcher` | **ランタイムブリッジ**。パッチ済みプラグインが呼び出す実際の Folia 互換メソッドを提供 |
| `ClassTransformer` | トランスフォーマーのインターフェース |
| `ScanningClassVisitor` | 高速スキャン用 AST ビジター（要パッチ判定のための fast-fail） |

### 2.2 トランスフォーマー一覧（適用順）

| 順序 | トランスフォーマー | 役割 |
|------|-------------------|------|
| 1st | `ThreadSafetyTransformer` | Block 書き込み操作全般の安全ラッピング（`setType`, `setBlockData`, `breakNaturally`, `applyBoneMeal`） |
| 2nd | `WorldGenClassTransformer` | World 生成・操作の非同期化（`createWorld`, `spawn`, `dropItem`, `createExplosion`, `strikeLightning`） |
| 3rd | `EntitySchedulerTransformer` | Entity / LivingEntity 操作のスレッドセーフ化（`teleport`, `remove`, `damage`, `setHealth`, `addPotionEffect`） |
| 4th | `PlayerSafetyTransformer` | Player / Inventory 操作のスレッドセーフ化（`openInventory`, `closeInventory`, `kickPlayer`, `setGameMode`） |
| 5th | `SchedulerClassTransformer` | `BukkitScheduler` / `BukkitRunnable` の置き換え |

### 2.2.1 ScanningClassVisitor が検出する変換トリガー（拡張版）

以下いずれかのメソッド呼び出しがクラス内に存在する場合、`needsPatching = true`:

**基本検出セット:**
- `org/bukkit/scheduler/BukkitScheduler` の任意メソッド
- `org/bukkit/scheduler/BukkitRunnable` の任意メソッド
- `org/bukkit/WorldCreator` の任意メソッド
- `org/bukkit/block/Block` の `setType` / `setBlockData` / `breakNaturally` / `applyBoneMeal`
- `org/bukkit/Bukkit` の任意メソッド
- `org/bukkit/plugin/Plugin` の任意メソッド

**拡張検出セット（Folia API 全体対応）:**
- `org/bukkit/entity/Entity`: `teleport` / `remove` / `setFireTicks` / `setVelocity` / `setGravity` / `setInvulnerable` / `setGlowing` / `setSilent`
- `org/bukkit/entity/LivingEntity`: `damage` / `setHealth` / `addPotionEffect` / `removePotionEffect` / `setMaxHealth`
- `org/bukkit/entity/Player`: `openInventory` / `closeInventory` / `kickPlayer` / `setGameMode` / `setAllowFlight` / `setFlying`
- `org/bukkit/World`: `spawn` / `dropItem` / `dropItemNaturally` / `createExplosion` / `strikeLightning` / `setTime` / `setStorm` / `setThundering` / `setGameRule`

### 2.3 FoliaPatcher（ランタイムブリッジ）の提供する機能

#### スケジューラ変換（BukkitScheduler → Folia スケジューラ）

| 変換元 | 変換先 |
|--------|--------|
| `BukkitScheduler.runTask()` | `RegionScheduler.run()` / `GlobalRegionScheduler.run()` |
| `BukkitScheduler.runTaskLater()` | `RegionScheduler.runDelayed()` / `GlobalRegionScheduler.runDelayed()` |
| `BukkitScheduler.runTaskTimer()` | `RegionScheduler.runAtFixedRate()` / `GlobalRegionScheduler.runAtFixedRate()` |
| `BukkitScheduler.runTaskAsynchronously()` | `AsyncScheduler.runNow()` |
| `BukkitScheduler.runTaskLaterAsynchronously()` | `AsyncScheduler.runDelayed()` |
| `BukkitScheduler.runTaskTimerAsynchronously()` | `AsyncScheduler.runAtFixedRate()` |
| レガシー `scheduleSyncDelayedTask()` | `runTaskLater().getTaskId()` |
| レガシー `scheduleAsyncDelayedTask()` | `runTaskLaterAsynchronously().getTaskId()` |

#### BukkitRunnable インスタンスメソッドの変換

| 変換元（インスタンスメソッド） | 変換先（静的メソッド） |
|-------------------------------|------------------------|
| `runnable.runTask(plugin)` | `FoliaPatcher.runTask_onRunnable(runnable, plugin)` |
| `runnable.runTaskLater(plugin, delay)` | `FoliaPatcher.runTaskLater_onRunnable(runnable, plugin, delay)` |
| `runnable.runTaskTimer(plugin, delay, period)` | `FoliaPatcher.runTaskTimer_onRunnable(runnable, plugin, delay, period)` |
| `runnable.runTaskAsynchronously(plugin)` | `FoliaPatcher.runTaskAsynchronously_onRunnable(runnable, plugin)` |
| `runnable.runTaskLaterAsynchronously(plugin, delay)` | `FoliaPatcher.runTaskLaterAsynchronously_onRunnable(runnable, plugin, delay)` |
| `runnable.runTaskTimerAsynchronously(plugin, delay, period)` | `FoliaPatcher.runTaskTimerAsynchronously_onRunnable(runnable, plugin, delay, period)` |

#### スレッドセーフな Block 操作

```java
// Block.setType(Material) の安全版
public static void safeSetType(Block block, Material material) {
    if (Bukkit.isPrimaryThread()) {
        block.setType(material);
    } else {
        Bukkit.getRegionScheduler().run(plugin, block.getLocation(), task -> block.setType(material));
    }
}

// Block.setType(Material, boolean) の安全版
public static void safeSetTypeWithPhysics(Block block, Material material, boolean applyPhysics) {
    // 同様のスレッドチェック + RegionScheduler
}
```

#### Block 操作全般のスレッドセーフラッパー

`ThreadSafetyTransformer` により以下が変換される:

| 変換元（Block インスタンスメソッド） | 変換先（FoliaPatcher 静的メソッド） |
|---------------------------------------|--------------------------------------|
| `block.setType(material)` | `FoliaPatcher.safeSetType(block, material)` |
| `block.setType(material, applyPhysics)` | `FoliaPatcher.safeSetTypeWithPhysics(block, material, applyPhysics)` |
| `block.setBlockData(data)` | `FoliaPatcher.safeSetBlockData(block, data)` |
| `block.setBlockData(data, applyPhysics)` | `FoliaPatcher.safeSetBlockData(block, data, applyPhysics)` |
| `block.breakNaturally()` | `FoliaPatcher.safeBreakNaturally(block)` |
| `block.breakNaturally(tool)` | `FoliaPatcher.safeBreakNaturally(block, tool)` |
| `block.applyBoneMeal(face)` | `FoliaPatcher.safeApplyBoneMeal(block, face)` |

戻り値を伴う操作（`breakNaturally`, `applyBoneMeal`）は `CompletableFuture` を用いて同期的に結果を返す。
読み取り専用操作（`getState`, `getBlockData`, `getDrops`）はそのまま直接実行される。

#### Entity 操作のスレッドセーフラッパー

`EntitySchedulerTransformer` により以下が変換される:

| 変換元（インスタンスメソッド） | 変換先（FoliaPatcher 静的メソッド） |
|-------------------------------|--------------------------------------|
| `entity.teleport(location)` | `FoliaPatcher.safeTeleport(entity, location)` |
| `entity.teleport(location, cause)` | `FoliaPatcher.safeTeleport(entity, location, cause)` |
| `entity.remove()` | `FoliaPatcher.safeRemove(entity)` |
| `entity.setFireTicks(ticks)` | `FoliaPatcher.safeSetFireTicks(entity, ticks)` |
| `entity.setVelocity(vector)` | `FoliaPatcher.safeSetVelocity(entity, vector)` |
| `livingEntity.damage(amount)` | `FoliaPatcher.safeDamage(entity, amount)` |
| `livingEntity.damage(amount, damager)` | `FoliaPatcher.safeDamage(entity, amount, damager)` |
| `livingEntity.setHealth(health)` | `FoliaPatcher.safeSetHealth(entity, health)` |
| `livingEntity.addPotionEffect(effect)` | `FoliaPatcher.safeAddPotionEffect(entity, effect)` |
| `livingEntity.addPotionEffect(effect, force)` | `FoliaPatcher.safeAddPotionEffect(entity, effect, force)` |

内部で `Entity.getScheduler().execute()` を使用し、エンティティを所有する正しいリージョンスレッドにルーティングされる。

#### World 操作のスレッドセーフラッパー

`WorldGenClassTransformer` により以下が変換される:

| 変換元（World インスタンスメソッド） | 変換先（FoliaPatcher 静的メソッド） |
|---------------------------------------|--------------------------------------|
| `world.spawn(location, class)` | `FoliaPatcher.safeSpawn(location, class)` |
| `world.dropItem(location, item)` | `FoliaPatcher.safeDropItem(location, item)` |
| `world.dropItemNaturally(location, item)` | `FoliaPatcher.safeDropItemNaturally(location, item)` |
| `world.createExplosion(location, power)` | `FoliaPatcher.safeCreateExplosion(location, power)` |
| `world.createExplosion(location, power, fire)` | `FoliaPatcher.safeCreateExplosion(location, power, fire)` |
| `world.strikeLightning(location)` | `FoliaPatcher.safeStrikeLightning(location)` |

内部で `RegionScheduler.run()` を使用し、Location を所有するリージョンスレッドにルーティングされる。

#### Player / Inventory 操作のスレッドセーフラッパー

`PlayerSafetyTransformer` により以下が変換される:

| 変換元（Player インスタンスメソッド） | 変換先（FoliaPatcher 静的メソッド） |
|----------------------------------------|--------------------------------------|
| `player.openInventory(inventory)` | `FoliaPatcher.safeOpenInventory(player, inventory)` |
| `player.closeInventory()` | `FoliaPatcher.safeCloseInventory(player)` |
| `player.kickPlayer(message)` | `FoliaPatcher.safeKickPlayer(player, message)` |
| `player.setGameMode(gameMode)` | `FoliaPatcher.safeSetGameMode(player, gameMode)` |

内部で `Entity.getScheduler().execute()` を使用し、プレイヤーエンティティを所有する正しいリージョンスレッドにルーティングされる。

#### ワールド生成

```java
// Plugin.getDefaultWorldGenerator → FoliaChunkGenerator でラップ
public static ChunkGenerator getDefaultWorldGenerator(Plugin plugin, String worldName, String id) {
    ChunkGenerator original = plugin.getDefaultWorldGenerator(worldName, id);
    return (original == null) ? null : new FoliaChunkGenerator(original);
}

// Bukkit.createWorld / WorldCreator.createWorld → 専用スレッドで実行
public static World createWorld(WorldCreator creator) {
    Future<World> future = worldGenExecutor.submit(creator::createWorld);
    return future.get(); // 単一スレッドのワールド生成エグゼキュータ
}
```

#### タスク管理

```java
// 内部で ConcurrentHashMap<Integer, ScheduledTask> により管理
public static void cancelTask(BukkitScheduler ignored, int taskId) { ... }
public static void cancelTasks(BukkitScheduler ignored, Plugin plugin) { ... }
public static void cancelAllTasks() { ... }
```

### 2.4 PluginPatcher のパッチ処理フロー

```
JAR 読み込み
    │
    ├── 署名ファイル (.SF, .DSA, .RSA) を除去
    ├── plugin.yml に folia-supported: true を追加
    ├── .class ファイルを ForkJoinPool で並列変換
    │       │
    │       ├── ScanningClassVisitor（fast-fail スキャン）
    │       │      └─ 要パッチ判定: BukkitScheduler, BukkitRunnable,
    │       │         WorldCreator, Block.setType, Bukkit.createWorld,
    │       │         Plugin.getDefaultWorldGenerator の呼び出し有無
    │       │
    │       └── トランスフォーマーチェーン（要パッチの場合のみ）
    │               ├── ThreadSafetyTransformer
    │               ├── WorldGenClassTransformer
    │               ├── EntitySchedulerTransformer
    │               └── SchedulerClassTransformer
    │
    ├── FoliaPatcher ランタイムクラスをバンドル
    │   ├── FoliaPatcher.class
    │   ├── FoliaPatcher$FoliaBukkitTask.class
    │   └── FoliaPatcher$FoliaChunkGenerator.class
    │
    └── 出力 JAR 書き出し
```

#### ScanningClassVisitor が検出する変換トリガー

以下いずれかのメソッド呼び出しがクラス内に存在する場合、`needsPatching = true`:

- `org/bukkit/scheduler/BukkitScheduler` の任意メソッド
- `org/bukkit/scheduler/BukkitRunnable` の任意メソッド
- `org/bukkit/WorldCreator` の任意メソッド
- `org/bukkit/block/Block.setType`
- `org/bukkit/Bukkit.createWorld`
- `org/bukkit/plugin/Plugin.getDefaultWorldGenerator`
- `BukkitRunnable` のインスタンスメソッド（`runTask`, `runTaskLater` など 6 種）

---

## 3. folia-phantom-cli — コマンドライン CLI

**メインクラス**: `com.patch.foliaphantom.cli.CLI`

**ビルド**: Maven shade plugin で fat JAR（ASM を `com.patch.foliaphantom.lib.asm` にリロケーション）

### 使用法

```bash
# 1つの JAR をパッチ
java -jar Folia-Phantom-CLI-1.0.0.jar path/to/plugin.jar

# ディレクトリ内の全 JAR を一括パッチ
java -jar Folia-Phantom-CLI-1.0.0.jar path/to/jars/

# 引数なし → 対話モード（パスを入力）
java -jar Folia-Phantom-CLI-1.0.0.jar
```

### 出力

- デフォルト出力先: `./patched-plugins/patched-<元のファイル名>.jar`
- 既に `folia-supported: true` のプラグインも警告付きでパッチ可能

### コード構造

```java
public static void main(String[] args) {
    // 1. ロガー設定 + バナー表示
    // 2. 入力ファイル/ディレクトリ取得（引数 or 対話入力）
    // 3. 出力ディレクトリ作成（./patched-plugins）
    // 4. PluginPatcher を生成
    // 5. 単一JAR or ディレクトリ内全JAR をパッチ
}
```

---

## 4. folia-phantom-gui — JavaFX デスクトップ GUI

**メインクラス**: `com.patch.foliaphantom.gui.Launcher` → `FoliaPhantomApp`

**依存**: JavaFX 21.0.1（controls + fxml）

### UI の特徴

- **ガラスモーフィズム（Glassmorphism）デザイン**
- **カスタムタイトルバー**: ドラッグ移動、最小化、閉じる
- **ダークテーマ**: `#0f172a`（スレート 900）ベース
- **ドラッグ＆ドロップ**対応ファイルリスト
- **プログレスバー**: 並列ジョブの進捗をリアルタイム表示
- **コンソール**: 緑色 `#10b981` のモノスペースログ出力、タイムスタンプ付き
- **結果フォルダを開く**ボタン

### UI 構成

```
┌──────────────────────────────────────────────────────┐
│ [Folia Phantom 👻]              [─] [✕]             │ ← カスタムタイトルバー
├────────────┬─────────────────────────────────────────┤
│            │  Welcome to Folia Phantom               │
│ PLUGINS    │  Professional bytecode transformer...   │
│ TO PATCH   │  ┌──────────────────────────────────┐   │
│            │  │ Configuration                     │   │
│ [File1.jar]│  │ [☐ Verbose logging] [Output Dir]  │   │
│ [File2.jar]│  └──────────────────────────────────┘   │
│            │  ┌──────────────────────────────────┐   │
│ [+ Add]    │  │ Process Task                     │   │
│ [Clear]    │  │ ████████████████░░░░ 75%         │   │
│            │  │ Processing 3/4...                │   │
│            │  └──────────────────────────────────┘   │
│            │  [EXECUTE PATCH] [Open Results] 3 files │
│            │  ┌──────────────────────────────────┐   │
│            │  │ CONSOLE                          │   │
│            │  │ [12:34:56] [INFO] Starting...     │   │
│            │  └──────────────────────────────────┘   │
└────────────┴─────────────────────────────────────────┘
```

### 使い方

1. **JAR を追加**: ドラッグ＆ドロップ、または「+ Add」ボタンでファイル選択
2. **出力先フォルダ**: 「Change Output Folder」で変更可能（デフォルト: JAR と同じフォルダ）
3. **Verbose logging**: チェックでパッチャーの詳細ログをコンソールに表示
4. **「EXECUTE PATCH」**: 全 JAR を 4 スレッド並列でパッチ
5. **結果**: 「Open Results」ボタンで出力フォルダをエクスプローラ表示

### スタイルシート主要カラー

| トークン | カラー | 用途 |
|----------|--------|------|
| `#0f172a` | Slate 900 | メイン背景 |
| `#020617` | Slate 950 | サイドバー、コンソール背景 |
| `#1e293b` | Slate 800 | カード、タイトルバー |
| `#3b82f6` | Blue 500 | アクセント、メインボタングラデーション |
| `#10b981` | Emerald 500 | コンソール文字色、成功ボタン |

---

## 5. folia-phantom-plugin — Bukkit サーバープラグイン

**メインクラス**: `com.patch.foliaphantom.plugin.FoliaPhantomPlugin`

### plugin.yml

```yaml
name: FoliaPhantom
version: 1.0.0
main: com.patch.foliaphantom.plugin.FoliaPhantomPlugin
api-version: '1.21'
author: Patch
folia-supported: true

commands:
  foliapatch:
    description: Manage plugin patching operations
    usage: |
      /<command> <plugin-name> - Patch a specific plugin
      /<command> list - List all patchable plugins
      /<command> status - Show patching statistics
    permission: foliaphantom.patch
    aliases: [fpatch, fp]

permissions:
  foliaphantom.patch:
    description: Allows patching plugins
    default: op
  foliaphantom.admin:
    description: Full access to all FoliaPhantom features
    default: op
    children:
      foliaphantom.patch: true
```

### コマンド一覧

| コマンド | 権限 | 説明 |
|----------|------|------|
| `/foliapatch <plugin>` | `foliaphantom.patch` | 特定プラグインをパッチ |
| `/foliapatch list` | `foliaphantom.patch` | 監視フォルダ内の JAR 一覧 |
| `/foliapatch status` | `foliaphantom.patch` | 統計情報表示 |
| `/foliapatch reload` | `foliaphantom.patch` | 設定再読み込み |

**エイリアス**: `/fpatch`, `/fp`

### 自動パッチ機能（PluginWatcher）

`PluginWatcher` は Folia の `AsyncScheduler` で定期的に監視フォルダをスキャンし、新規/更新ファイルを自動パッチします。

#### config.yml 完全設定

```yaml
# FoliaPhantom Configuration

auto-patch:
  enabled: true
  # 監視するフォルダ（サーバールートからの相対パス）
  watch-folder: 'plugins/folia-patch-queue'
  # パッチ済みプラグインの出力先
  output-folder: 'plugins/patched'
  # チェック間隔（秒）
  check-interval: 5

logging:
  level: INFO
  verbose: false
  log-success: true
  log-skipped: true

filters:
  # 既に folia-supported のプラグインをスキップ
  skip-folia-supported: true
  # ホワイトリスト（空 = すべて許可）
  whitelist: []
  # ブラックリスト
  blacklist: []

advanced:
  # パッチ成功後に元ファイルを削除
  delete-original: false
  # パッチ前にバックアップを作成
  create-backup: true
  # バックアップ保存先
  backup-folder: 'plugins/folia-phantom-backups'
```

#### PluginWatcher の内部動作

```
AsyncScheduler が check-interval 秒ごとに run() を呼び出し
    │
    └─ scanAndPatchPlugins()
           │
           ├── 監視フォルダの *.jar を列挙
           ├── 最終更新日時で重複排除（processedFiles マップ）
           ├── shouldPatchPlugin() でフィルタリング
           │   ├── ブラックリストチェック（glob → regex）
           │   ├── ホワイトリストチェック
           │   └── skip-folia-supported チェック
           │
           └── patchPlugin()
               ├── バックアップ（create-backup が true の場合）
               ├── PluginPatcher.patchPlugin() を実行
               └── 元ファイル削除（delete-original が true の場合）
```

### Plugin ライフサイクル

```java
onEnable() {
    // 1. FoliaPatcher.plugin = this の設定
    // 2. 設定ファイル読み込み
    // 3. PluginWatcher 初期化
    // 4. フォルダ作成（watch, output, backup）
    // 5. AsyncScheduler で定期タスク開始
    // 6. コマンド登録
}

onDisable() {
    // 1. AsyncScheduler タスク停止
    // 2. 統計情報出力
}
```

---

## 6. バイトコード変換の詳細

### 6.1 BukkitScheduler → Folia スケジューラ

**変換前**:
```java
Bukkit.getScheduler().runTask(plugin, () -> { ... });
```

**変換後**:
```java
FoliaPatcher.runTask(null, plugin, () -> { ... });
```

内部でフォールバック Location（メインワールドのスポーン地点）を取得し、`RegionScheduler` または `GlobalRegionScheduler` にルーティング。

### 6.2 BukkitRunnable → 静的メソッド

**変換前**:
```java
new BukkitRunnable() {
    @Override
    public void run() { ... }
}.runTaskLater(plugin, 20L);
```

**変換後**:
```java
FoliaPatcher.runTaskLater_onRunnable(runnable, plugin, 20L);
```

**ASM での変換ロジック（抜粋）**:
```java
// INVOKEVIRTUAL → INVOKESTATIC に書き換え
if (opcode == Opcodes.INVOKEVIRTUAL && isBukkitRunnableInstanceMethod(name, desc)) {
    String newName = name + "_onRunnable";
    String newDesc = "(Ljava/lang/Runnable;" + desc.substring(1);  // 先頭の引数を Runnable に
    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER, newName, newDesc, false);
    return;
}
```

### 6.3 Block.setType → スレッドセーフラッパー

**変換前**:
```java
block.setType(Material.STONE);
```

**変換後**:
```java
FoliaPatcher.safeSetType(block, Material.STONE);
```

**変換前（applyPhysics あり）**:
```java
block.setType(Material.STONE, false);
```

**変換後**:
```java
FoliaPatcher.safeSetTypeWithPhysics(block, Material.STONE, false);
```

### 6.4 ワールド生成の非同期化

**変換前**:
```java
Bukkit.createWorld(new WorldCreator("world"));
// または
new WorldCreator("world").createWorld();
```

**変換後**:
```java
FoliaPatcher.createWorld(new WorldCreator("world"));
```

専用の `SingleThreadExecutor`（`FoliaPhantom-WorldGen-Worker`）で実行。

**変換前**:
```java
plugin.getDefaultWorldGenerator(worldName, id);
```

**変換後**:
```java
FoliaPatcher.getDefaultWorldGenerator(plugin, worldName, id);
// FoliaChunkGenerator でラップして返す
```

### 6.5 Block 操作全般のスレッドセーフ化

**変換前**:
```java
block.setBlockData(data);
block.breakNaturally(tool);
block.applyBoneMeal(face);
```

**変換後**:
```java
FoliaPatcher.safeSetBlockData(block, data);
FoliaPatcher.safeBreakNaturally(block, tool);
FoliaPatcher.safeApplyBoneMeal(block, face);
```

内部で `isOwningRegion()` により現在のスレッドがブロックのリージョンを所有しているか判定し、
所有していなければ `RegionScheduler.run()` で正しいリージョンにルーティングする。

### 6.6 Entity 操作のスレッドセーフ化

**変換前**:
```java
entity.teleport(location);
livingEntity.damage(10.0);
livingEntity.setHealth(20.0);
```

**変換後**:
```java
FoliaPatcher.safeTeleport(entity, location);
FoliaPatcher.safeDamage(livingEntity, 10.0);
FoliaPatcher.safeSetHealth(livingEntity, 20.0);
```

内部で `isOwningRegion()` により現在のスレッドがエンティティのリージョンを所有しているか判定し、
所有していなければ `Entity.getScheduler().execute()` でエンティティのリージョンスレッドにルーティングする。

**EntityScheduler 使用の利点**:
- エンティティがテレポートしても正しいスレッドで実行される
- エンティティが削除された場合はフォールバックで直接実行
- Folia が推奨するエンティティ操作パターン

### 6.7 World 操作のスレッドセーフ化

**変換前**:
```java
world.spawn(location, Zombie.class);
world.dropItem(location, itemStack);
world.createExplosion(location, 4.0f);
```

**変換後**:
```java
FoliaPatcher.safeSpawn(location, Zombie.class);
FoliaPatcher.safeDropItem(location, itemStack);
FoliaPatcher.safeCreateExplosion(location, 4.0f);
```

内部で `RegionScheduler.run()` を使用し、Location を所有するリージョンにルーティングする。
戻り値が必要な操作は `CompletableFuture` で同期待ちする。

### 6.8 Player / Inventory 操作のスレッドセーフ化

**変換前**:
```java
player.openInventory(chestInventory);
player.closeInventory();
player.kickPlayer("Goodbye!");
```

**変換後**:
```java
FoliaPatcher.safeOpenInventory(player, chestInventory);
FoliaPatcher.safeCloseInventory(player);
FoliaPatcher.safeKickPlayer(player, "Goodbye!");
```

Player は Entity のサブクラスであるため、内部で `Entity.getScheduler().execute()` を使用する。

---

## 7. ビルド方法

```bash
# 前提条件: JDK 17+, Maven 3.8+

# リポジトリのクローン
git clone <repository-url>
cd pasta/folia-phantom

# 全モジュールをビルド
mvn clean package

# 生成される JAR 一覧
#   folia-phantom-cli/target/Folia-Phantom-CLI-1.0.0.jar
#   folia-phantom-gui/target/folia-phantom-gui-1.0.0.jar
#   folia-phantom-plugin/target/folia-phantom-plugin-1.0.0.jar
```

### モジュール個別ビルド

```bash
# コアのみ
mvn clean package -pl folia-phantom-core

# CLI のみ（依存コアも自動ビルド）
mvn clean package -pl folia-phantom-cli -am

# GUI のみ
mvn clean package -pl folia-phantom-gui -am

# プラグインのみ
mvn clean package -pl folia-phantom-plugin -am
```

---

## 8. アーキテクチャ図

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ユーザー入力                                  │
│          (GUI / CLI / サーバープラグインコマンド)                      │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                         PluginPatcher                                │
│                                                                      │
│  ① JAR 読み込み (ZipInputStream)                                    │
│     ├── 署名ファイル (.SF/.DSA/.RSA) を除去                         │
│     └── plugin.yml に folia-supported: true を追加                   │
│                                                                      │
│  ② 並列クラス変換 (ForkJoinPool)                                    │
│     ├── ScanningClassVisitor (fast-fail)                             │
│     │    └─ 要パッチ判定                                             │
│     └── トランスフォーマーチェーン (要パッチのみ)                      │
│          ├── ThreadSafetyTransformer                                 │
│          ├── WorldGenClassTransformer                                 │
│          ├── EntitySchedulerTransformer                               │
│          └── SchedulerClassTransformer                                │
│                                                                      │
│  ③ FoliaPatcher ランタイム同梱                                      │
│     ├── FoliaPatcher.class                                           │
│     ├── FoliaPatcher$FoliaBukkitTask.class                           │
│     └── FoliaPatcher$FoliaChunkGenerator.class                       │
│                                                                      │
│  ④ JAR 書き出し (ZipOutputStream)                                   │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                   出力 JAR（Folia 互換）                              │
│   ・plugin.yml に folia-supported: true                              │
│   ・変換済み .class ファイル                                        │
│   ・FoliaPatcher ランタイム同梱                                      │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                   サーバー上で実行時                                  │
│                                                                      │
│  FoliaPatcher（ランタイムブリッジ）                                  │
│                                                                      │
│  BukkitScheduler 呼び出し                                            │
│         │                                                            │
│         ├── Location 取得可能？                                      │
│         │   ├── Yes → RegionScheduler                                │
│         │   └── No  → GlobalRegionScheduler                          │
│         │                                                            │
│         ├── Async 系統 → AsyncScheduler                              │
│         │                                                            │
│  Block.setType 呼び出し                                              │
│         │                                                            │
│         ├── プライマリスレッド？                                      │
│         │   ├── Yes → 直接実行                                       │
│         │   └── No  → RegionScheduler 経由                          │
│         │                                                            │
│  ワールド生成                                                         │
│         │                                                            │
│         └── WorldGen 専用スレッドで実行                              │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 9. 注意事項

### 9.1 FoliaPatcher.plugin の設定

プラグインモード使用時は `onEnable()` で必ず `FoliaPatcher.plugin = this;` を設定してください。ランタイムブリッジがスケジューリングにこの参照を使用します。

### 9.2 BukkitRunnable の this 参照

匿名クラス内での `this` 参照は、変換後も正しく動作するよう ASM のスタック操作で調整されます。インスタンスメソッド呼び出しは自身の `Runnable` 参照を最初の引数として受け取る静的メソッドに変換されます。

### 9.3 署名付き JAR の処理

MythicMobs などの署名付きプラグインの署名ファイル（`.SF`, `.DSA`, `.RSA`）は自動除去されます。バイトコード変換により署名が無効になるためです。

### 9.4 FoliaBukkitTask

`FoliaPatcher.FoliaBukkitTask` は完全な `BukkitTask` インターフェース実装です:

```java
public final class FoliaBukkitTask implements BukkitTask {
    public int getTaskId() { ... }
    public Plugin getOwner() { ... }
    public boolean isSync() { ... }
    public boolean isCancelled() { ... }
    public void cancel() { ... }  // 内部で runningTasks から削除し、ScheduledTask.cancel() を呼ぶ
}
```

### 9.5 タスク管理

実行中タスクは `ConcurrentHashMap<Integer, ScheduledTask>` で管理され、以下の操作に対応します:

- `cancelTask(int taskId)` — ID 指定でキャンセル
- `cancelTasks(Plugin plugin)` — プラグイン単位で全タスクキャンセル
- `cancelAllTasks()` — 全タスクキャンセル + マップクリア

### 9.6 繰り返しタスクの注意

`wrapRunnable()` により、単発タスク（`runTask`, `runTaskLater`）は実行後に自動的に `runningTasks` マップから削除されますが、繰り返しタスク（`runTaskTimer`）は削除されません。明示的に `cancel()` する必要があります。

---

## 10. ライセンス

本ソフトウェアは **MARV License v1.0.0** のもとで提供されます。

### 主な条件

| 区分 | 内容 |
|------|------|
| ✅ **許可されること** | 非商用利用、私的・公開サーバーでの使用、改変、非商用再配布 |
| ❌ **禁止されること** | 商用利用、有料配布、クレジット削除、敵対プロジェクトへの提供、別ライセンスでの再配布 |
| ℹ️ **クレジット表示** | `"This service uses software provided by Marv (marvgame.com)"` をエンドユーザーが操作する場所に表示 |
| ℹ️ **派生作品の提出** | 公開または配布から **7 日以内** に公式 GitHub リポジトリへ PR を送信し、同時に `marvsystem@gmail.com` へメール |

### 免責事項

```
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
EXCEPT WHERE PROHIBITED BY LAW, THE LICENSOR SHALL NOT BE LIABLE
FOR ANY DAMAGES ARISING FROM USE OR INABILITY TO USE THE SOFTWARE.
```

詳細は同梱の `LICENSE` ファイルを参照してください。

---

> **Copyright © 2025 Marv. All rights reserved.**
