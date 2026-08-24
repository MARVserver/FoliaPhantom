# アーキテクチャ

[ホーム](Home.md) · [はじめに](Getting-Started.md) · [English](../en/Architecture.md)

pasta はプラグイン JAR をバイトコードレベルで変換し、必要なランタイムブリッジを変換後の JAR に組み込みます。

## 処理パイプライン

```text
Input JAR
  │
  ▼
JarInputStream ──► 無効になる署名を削除
  │
  ├──────────────► plugin.yml の互換性メタデータを更新
  │
  ▼
ScanningClassVisitor
  │
  ├── 対象となる Bukkit バイトコードなし ──► そのままコピー
  │
  ▼
Transformer chain
  ├─ ThreadSafetyTransformer
  ├─ WorldGenClassTransformer
  ├─ EntitySchedulerTransformer
  ├─ PlayerSafetyTransformer
  └─ SchedulerClassTransformer
  │
  ▼
ランタイムブリッジを組み込み
  │
  ▼
JarOutputStream ──► patched JAR
```

## モジュール

| モジュール | 役割 |
| --- | --- |
| `core` | ASM visitor、変換エンジン、ランタイムブリッジ |
| `cli` | コマンドライン用フロントエンド |
| `gui` | JavaFX デスクトップ用フロントエンド |
| `plugin` | Bukkit/Paper サーバープラグイン用フロントエンド |
| `web` | ブラウザアプリが利用する Java ブリッジ |

## Transformer の順序

Transformer は意図的に次の順序で実行されます。

1. `ThreadSafetyTransformer`
2. `WorldGenClassTransformer`
3. `EntitySchedulerTransformer`
4. `PlayerSafetyTransformer`
5. `SchedulerClassTransformer`

後段の変換が前段の正規化結果に依存する可能性があるため、順序変更は単なるリファクタリングではなく、動作上の変更として扱い、十分に検証してください。

## ランタイムブリッジ

変換済み JAR には pasta 所有のランタイムアダプターが含まれます。これらは Folia の region/global/async scheduler への処理の振り分け、BukkitRunnable 形式のスケジューリング変換、スレッド制約のある変更処理を適切な region に載せる処理、world generation の互換処理、ラップされたタスクの追跡などを担当します。

## 安全性モデル

Transformer は既知のバイトコードパターンを書き換えられますが、任意のプラグイン内部状態がスレッドセーフであることまでは証明できません。scheduler 呼び出しが正しく変換されても、プラグイン固有の共有可変状態が安全でない場合があります。

互換性確認にはステージング環境でのテストを必ず含めてください。
