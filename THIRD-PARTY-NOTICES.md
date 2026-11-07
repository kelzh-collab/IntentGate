# 第三方资源与许可证声明

IntentGate 原创代码适用 [MIT License](LICENSE)，项目品牌图形适用 [独立品牌素材授权](BRAND-ASSETS.md)。下列第三方材料适用各自的许可证。

## 随候选源码分发的材料

| 材料 | 用途与位置 | 许可与保留声明 |
| --- | --- | --- |
| Inter 4.001 | `app/src/main/res/font/inter_{regular,medium,semibold,bold}.ttf` | SIL Open Font License 1.1；Copyright 2020 The Inter Project Authors。原文保留在 [Inter-OFL.txt](app/src/main/assets/licenses/Inter-OFL.txt)，字体内嵌版权与许可不变 |
| Gradle Wrapper | `gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar` | Apache License 2.0；保留脚本头部及 JAR 内许可。另从现有 JAR 的 `META-INF/LICENSE` 原样提取 [Gradle-Wrapper-LICENSE.txt](third_party/licenses/Gradle-Wrapper-LICENSE.txt) |

来源：[Inter 项目](https://github.com/rsms/inter)、[SIL OFL](https://openfontlicense.org/)、[Gradle](https://github.com/gradle/gradle)、[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)。此处来源链接不替代随附原始声明。

## 通过 Gradle 获取的依赖

候选源码不包含下载缓存、依赖 AAR/JAR 或 Android SDK/JDK。以下依赖由开发者构建时从配置的公共仓库获取，原许可证不能被项目未来的 MIT 许可证覆盖。

| 依赖组 | 用途 | 上游许可 |
| --- | --- | --- |
| AndroidX / Jetpack Compose，包括 Activity、Core、Lifecycle、Material 3、UI 及测试组件 | Android 运行时、调试和设备测试 | Apache License 2.0 |
| Kotlin 标准库、kotlinx.coroutines 及相应 Kotlin 构建插件 | 语言与协程运行时、构建 | Apache License 2.0 |
| Android Gradle Plugin、Gradle、Foojay resolver convention plugin | 构建工具；不将它们的下载缓存纳入源码仓库 | 以各上游项目及随包声明为准；Gradle 和对应插件项目采用 Apache License 2.0 |
| JUnit 4.13.2 | 本地及设备测试基础设施，不属于正式 App 功能 | Eclipse Public License 1.0；从现有 JUnit JAR 原样提取 [JUnit-EPL-1.0.txt](third_party/licenses/JUnit-EPL-1.0.txt) |
| Hamcrest Core / Library 1.3 | 测试断言及其传递依赖 | BSD；权利人和完整条件见从现有 JAR 原样提取的 [Hamcrest-LICENSE.txt](third_party/licenses/Hamcrest-LICENSE.txt)，Core 与 Library 的许可条目字节一致 |

直接声明的版本见 `gradle/libs.versions.toml`。版本约束可能使传递依赖及部分直接依赖采用其他实际解析版本；本轮配置的解析坐标与 POM 许可信息记录在 [依赖许可清单](docs/DEPENDENCY-LICENSES.md)。该清单包括运行时及测试配置，不等同于 SDK/JDK、Gradle 或所有构建插件传递依赖的完整软件物料清单。

正式分发 APK 前，需要依据最终签名产物复核实际打包的传递依赖、许可证、NOTICE 与署名。

## 图标与未纳入分发的资源

- 已安装第三方 App 的图标仅在设备运行时通过 Android PackageManager 获取；本仓库不声明拥有这些商标或图标，也不将动态读取结果导出或随候选源码分发。
- 原有 `brand_instagram.png`、`brand_youtube.png`、`brand_chrome.png`、`brand_reddit.png` 保留在本机并被 Git 忽略；源码与测试不再依赖它们。
- `design/` 中的 PNG、SVG、HTML、Figma 记录及预览工具均排除，避免连带分发嵌入的品牌图片与来源未确认的非必要设计材料。
- IntentGate 原 Logo、启动图标及其他项目品牌素材适用 [独立品牌素材授权](BRAND-ASSETS.md)。
- 通用占位图、演示应用矢量图及 Canvas 图形属于项目源码的一部分，其代码与资源许可随项目原创材料的权利确认处理。

本轮没有修改任何既有第三方许可证文本；新增许可副本与所读取的上游归档条目逐字节一致。
