# はじめに

[ホーム](https://github.com/MARVserver/pasta/wiki/ja-Home) · [アーキテクチャ](https://github.com/MARVserver/pasta/wiki/ja-Architecture)

## Browser

1. pasta Web アプリを開きます。
2. 1 個以上のプラグイン JAR をドロップします。
3. 変換対象とスキップ対象を確認します。
4. パッチ後に `patched-*.jar` を取得します。
5. 一括処理では必要に応じて `pasta-report.csv` を取得します。

Browser 版は JAR をユーザー端末内で処理する設計です。

## GitHub Actions

```yaml
- name: Patch for Folia
  id: pasta
  uses: MARVserver/pasta@develop
  with:
    input: target/my-plugin.jar
    output: build/pasta
```

本番 CI では release tag または不変の commit SHA へ固定してください。

## CLI

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
java -jar Folia-Phantom-CLI-2.0.0.jar --output ./converted path/to/plugin.jar
```

## ビルド

```bash
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean verify
```

JDK 21+ と Maven 3.8+ が必要です。
