# 架構

[首頁](https://github.com/MARVserver/pasta/wiki/zh-TW-Home) · [快速開始](https://github.com/MARVserver/pasta/wiki/zh-TW-Getting-Started)

```text
輸入 JAR
  ↓
移除失效簽章 + 更新 plugin.yml
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

`core` 負責 ASM visitor、轉換鏈與 runtime bridge；`cli`、`gui`、`plugin`、`web` 提供不同前端。Transformer 順序經過刻意設計，後續階段可能依賴先前的正規化。執行時仍需驗證外掛自身共享狀態的執行緒安全性。
