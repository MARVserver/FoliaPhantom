# pasta — 日本語

[言語選択](https://github.com/MARVserver/pasta/wiki/Home) · [はじめに](https://github.com/MARVserver/pasta/wiki/ja-Getting-Started) · [アーキテクチャ](https://github.com/MARVserver/pasta/wiki/ja-Architecture)

## pasta とは

**pasta**（旧 Folia Phantom）は、コンパイル済み Bukkit プラグイン JAR を Folia のリージョンベースのスレッドモデルへ適応させる bytecode 変換ツールです。ASM を使って `.class` を直接書き換えるため、プラグインのソースコードや再コンパイルは不要です。

## 利用方法

- Browser: ローカルでドラッグ＆ドロップ変換
- GitHub Actions: CI で Folia 向け成果物を生成
- CLI: 自動化・一括変換
- GUI: JavaFX デスクトップ操作
- Server plugin: Paper/Bukkit サーバー上から変換

## 必要環境

ビルドと Java ワークフローは **JDK 21+**、ソースからのビルドは Maven 3.8+ を使用します。

## 安全性

pasta は既知の互換パターンを変換しますが、プラグイン固有の共有状態が thread-safe であることまでは証明できません。本番導入前に Folia のステージング環境で検証してください。
