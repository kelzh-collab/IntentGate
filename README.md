# IntentGate

IntentGate 是一款用 Kotlin 和 Jetpack Compose 编写的 Android 专注管理应用。当前候选版本为 **1.0.0-beta.2**，应用 ID 为 `com.intentgate.app`，版本代码为 `3`。计划使用 GitHub 标签 `v1.0.0-beta.2`。

## 功能

- 从当前用户可见的已安装应用中选择管理对象，搜索应用并设置独立的每日限额。
- 经用户授权后读取 Android 使用记录，展示今日用量及可用历史的统计。
- 为使用会话记录用途和时长，提供暂停、继续、到期提醒及相关悬浮确认。
- 提供中英文界面。

第三方应用图标从设备的 Android `PackageManager` 动态读取。应用未安装、包不可见或读取失败时显示通用占位图。

## 环境与编译

- Android 8.0 / API 26 或更高版本运行环境。
- Android SDK Platform 37；当前 compileSdk / targetSdk 均为 37。
- JDK 25，用于仓库配置的 Gradle 工具链。Java/Kotlin 字节码目标与 Gradle 运行 JDK 是不同设置。
- 使用随源码提供的 Gradle Wrapper；依赖版本见 `gradle/libs.versions.toml`。

在 Android Studio 中打开仓库根目录，配置本机 SDK；或通过环境变量 `ANDROID_HOME` 指定 SDK。`local.properties` 仅留在本机，不能提交。

只检查主源码编译，不生成 APK：

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

图标、应用选择及统计模型的针对性 JVM 测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.intentgate.app.InstalledAppIconTest --tests com.intentgate.app.InstalledAppManagementTest --tests com.intentgate.app.AppRepositoryTest --tests com.intentgate.app.StatsAppBreakdownTest
```

首次配置可能需要下载公开构建依赖；依赖缓存完整时可添加 `--offline`。不需要 API Key、云端账号或生产签名密码。

## 安装与权限

正式签名 APK 将在真机验收和发布审批通过后作为 GitHub Release 附件提供，并附 SHA-256 校验文件。安装前请核对包名、版本、签名与哈希。覆盖旧安装需要 Android 接受原签名与版本；遇到签名不匹配时保留现有安装和数据。见 [安装与验签](docs/INSTALLATION.md)。

应用使用 Android 的使用情况访问权限读取系统用量。后台确认可选用无障碍服务，后备提示由悬浮窗和通知提供。会话与管理设置保存在本机，系统备份和迁移规则仅允许语言偏好。权限用途、关闭后的行为与数据边界见 [权限与隐私 / Permissions and privacy](docs/PRIVACY.md)。

## Beta 限制

- Android 使用记录的保留时间、权限、包可见性、厂商后台策略和多窗口行为会影响统计与提醒；不保证完整历史或强制阻止所有应用使用。
- 部分演示内容用于展示，不能视为设备实测使用记录。
- 图标加载失败时的占位图属于预期行为，不会改变应用的选择、统计或管理状态。
- 当前候选已通过 Release 构建、68 项定向 JVM 测试及 4 项模拟器界面测试。优化构建在模拟器通过四个主页面、全局开关和后台 Continue / Finish 检查。
- 本地正式签名包已通过签名及内容比对。该签名包的真机安装、关键交互和公开下载仍待验收。见 [真机验收计划](docs/DEVICE-TEST-PLAN.md)。

## 许可证与发布状态

项目原创代码按 [MIT License](LICENSE) 授权，版权为 © 2026 kelzh-collab。IntentGate 的 Logo、启动图标、通知品牌图标及应用内品牌图形适用 [独立品牌素材授权](BRAND-ASSETS.md)。修改后的 Fork 或衍生 App 应替换这些素材，除非另获授权。非品牌源代码继续适用 MIT。

第三方字体、Gradle Wrapper 和其他依赖适用各自的许可证，见 [第三方声明](THIRD-PARTY-NOTICES.md)。

发布前事项与 GitHub noreply 邮箱要求见 [发布确认清单](docs/RELEASE-CHECKLIST.md)。不要将整个本地工作目录直接压缩发布，忽略的文件中可能包含私钥、日志、模拟器数据和未授权设计素材。
