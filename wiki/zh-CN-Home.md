# pasta — 简体中文

[语言选择](https://github.com/MARVserver/pasta/wiki/Home) · [快速开始](https://github.com/MARVserver/pasta/wiki/zh-CN-Getting-Started) · [架构](https://github.com/MARVserver/pasta/wiki/zh-CN-Architecture)

## pasta 是什么？

**pasta**（原 Folia Phantom）将已编译的 Bukkit 插件 JAR 适配到 Folia 的区域线程模型。它使用 ASM 直接重写字节码，不需要插件源代码或重新编译。

## 使用方式

- Browser：本地拖放转换
- GitHub Actions：在 CI 中生成 Folia 适配产物
- CLI：自动化和批量转换
- GUI：JavaFX 桌面界面
- Server plugin：在 Paper/Bukkit 服务端环境中转换

## 要求与安全边界

构建和 Java 工作流使用 **JDK 21+**，源码构建需要 Maven 3.8+。pasta 能处理已知兼容模式，但不能证明插件自己的共享状态一定线程安全。生产前请在 Folia 测试环境中验证。
