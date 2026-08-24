# アーキテクチャ

[ホーム](https://github.com/MARVserver/pasta/wiki/ja-Home) · [はじめに](https://github.com/MARVserver/pasta/wiki/ja-Getting-Started)

## 処理パイプライン

```text
入力 JAR
  ↓
無効化される署名の削除 + plugin.yml 更新
  ↓
ScanningClassVisitor
  ↓
ThreadSafetyTransformer
WorldGenClassTransformer
EntitySchedulerTransformer
PlayerSafetyTransformer
SchedulerClassTransformer
  ↓
runtime bridge を同梱
  ↓
patched JAR
```

## モジュール

| Module | 役割 |
| --- | --- |
| `core` | ASM visitor、変換チェーン、runtime bridge |
| `cli` | コマンドライン frontend |
| `gui` | JavaFX desktop frontend |
| `plugin` | Bukkit/Paper server frontend |
| `web` | browser bridge |

Transformer の順序は意図的です。後段が前段の正規化に依存することがあります。また bytecode 変換だけでは任意の共有状態の thread-safety は証明できないため、実行時検証が必要です。
