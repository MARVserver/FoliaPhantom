# 快速开始

[首页](https://github.com/MARVserver/pasta/wiki/zh-CN-Home) · [架构](https://github.com/MARVserver/pasta/wiki/zh-CN-Architecture)

## Browser

1. 打开 pasta Web 应用。
2. 拖入一个或多个插件 JAR。
3. 检查可转换与被跳过的文件。
4. 转换并下载 `patched-*.jar`。
5. 批量处理时可下载 `pasta-report.csv`。

## GitHub Actions

```yaml
- name: Patch for Folia
  uses: MARVserver/pasta@develop
  with:
    input: target/my-plugin.jar
    output: build/pasta
```

生产 CI 请固定到 release tag 或不可变 commit SHA。

## CLI / Build

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean verify
```

需要 JDK 21+ 和 Maven 3.8+。
