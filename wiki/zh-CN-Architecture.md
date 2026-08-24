# 架构

[首页](https://github.com/MARVserver/pasta/wiki/zh-CN-Home) · [快速开始](https://github.com/MARVserver/pasta/wiki/zh-CN-Getting-Started)

```text
输入 JAR
  ↓
删除失效签名 + 更新 plugin.yml
  ↓
ScanningClassVisitor
  ↓
ThreadSafetyTransformer
WorldGenClassTransformer
EntitySchedulerTransformer
PlayerSafetyTransformer
SchedulerClassTransformer
  ↓
打包 runtime bridge
  ↓
patched JAR
```

`core` 负责 ASM visitor、转换链和 runtime bridge；`cli`、`gui`、`plugin`、`web` 提供不同前端。Transformer 顺序是有意设计的，后续阶段可能依赖前面的标准化。运行时仍需验证插件自身共享状态的线程安全性。
