# pasta — 繁體中文

[語言選擇](https://github.com/MARVserver/pasta/wiki/Home) · [快速開始](https://github.com/MARVserver/pasta/wiki/zh-TW-Getting-Started) · [架構](https://github.com/MARVserver/pasta/wiki/zh-TW-Architecture)

## pasta 是什麼？

**pasta**（原 Folia Phantom）會將已編譯的 Bukkit 外掛 JAR 適配到 Folia 的區域執行緒模型。它使用 ASM 直接重寫 bytecode，不需要外掛原始碼或重新編譯。

## 使用方式

- Browser：本機拖放轉換
- GitHub Actions：在 CI 中產生 Folia 適配成果
- CLI：自動化與批次轉換
- GUI：JavaFX 桌面介面
- Server plugin：在 Paper/Bukkit 伺服器環境中轉換

## 要求與安全界線

建置與 Java 工作流程使用 **JDK 21+**，從原始碼建置需要 Maven 3.8+。pasta 能處理已知相容模式，但無法證明外掛自身共享狀態一定具執行緒安全性。正式使用前請在 Folia 測試環境驗證。
