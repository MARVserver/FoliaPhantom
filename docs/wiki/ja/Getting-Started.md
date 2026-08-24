# はじめに

[ホーム](Home.md) · [English](../en/Getting-Started.md)

pasta の利用形態に合わせて手順を選んでください。

## Browser

デスクトップアプリをインストールせず、手元で素早く変換したい場合に向いています。

1. 公開されている pasta Web アプリを開きます。
2. 1 個以上のプラグイン JAR をパッチ領域へドロップするか、ファイル選択を使います。
3. 変換対象とスキップ対象を確認します。
4. パッチ処理を実行します。
5. 生成された `patched-*.jar` をダウンロードします。
6. 複数ファイル処理では、必要に応じて `pasta-report.csv` も取得します。

Browser 版は、プラグイン JAR のバイト列をユーザー端末内に保持する設計です。

## GitHub Actions

プラグイン側のワークフローから composite action を利用できます。

```yaml
- name: Patch for Folia
  id: pasta
  uses: MARVserver/pasta@develop
  with:
    input: target/my-plugin.jar
    output: build/pasta

- name: Upload patched artifact
  uses: actions/upload-artifact@v4
  with:
    name: my-plugin-folia
    path: ${{ steps.pasta.outputs.output_directory }}/patched-my-plugin.jar
```

未リリースの変更を評価する場合は `develop` を利用できます。本番 CI では、対象リリースが公開された後にリリースタグまたは変更されないコミット SHA を固定して使うことを推奨します。

## CLI

1 個の JAR を変換:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
```

ディレクトリ内の JAR をまとめて変換:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/jars/
```

出力先を指定:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar --output ./converted path/to/plugin.jar
```

ヘルプ表示:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar --help
```

入力パスを指定しない場合は対話モードになります。既定の出力ディレクトリは `patched-plugins/` です。

## ソースからビルド

必要環境:

- JDK 21+
- Maven 3.8+

```bash
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean verify
```

配布用に近い成果物を作る場合:

```bash
mvn clean package
```

## 本番導入前の確認

変換済みプラグインを、自動的に Folia 安全性が保証されたプラグインとして扱わないでください。特にスケジューリング、エンティティ操作、ワールド/ブロック変更、プラグイン固有の共有状態をステージングサーバーで確認してください。
