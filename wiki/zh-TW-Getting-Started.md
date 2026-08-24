# 快速開始

[首頁](https://github.com/MARVserver/pasta/wiki/zh-TW-Home) · [架構](https://github.com/MARVserver/pasta/wiki/zh-TW-Architecture)

## Browser

1. 開啟 pasta Web 應用程式。
2. 拖入一個或多個外掛 JAR。
3. 檢查可轉換與略過的檔案。
4. 轉換並下載 `patched-*.jar`。
5. 批次處理時可下載 `pasta-report.csv`。

## GitHub Actions

```yaml
- name: Patch for Folia
  uses: MARVserver/pasta@develop
  with:
    input: target/my-plugin.jar
    output: build/pasta
```

正式 CI 請固定到 release tag 或不可變 commit SHA。

## CLI / Build

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean verify
```

需要 JDK 21+ 與 Maven 3.8+。
