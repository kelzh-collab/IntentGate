# Changelog

## v1.0.0-beta.2 (versionCode 3) — Unreleased

### 修复

- 保留 Release 启动所需的 Kotlin 集合类关系，修复 AndroidX Startup 初始化期间的跨包访问崩溃。
- 保留运行时通过反射访问的中英文字符串，修复 Settings 在优化构建中访问已裁剪资源时的崩溃。
- 全局 Intent Check 开关关闭时撤下未确认请求与提醒，阻止新会话。已有会话正常计时、到期并可 Finish，重新开启后可 Continue。

### 隐私

- 旧版 Android 备份及 Android 12+ 云备份和设备迁移仅允许语言偏好。
- 无障碍服务仍只订阅窗口状态变化，窗口内容读取与手势注入保持关闭。计时日志移除了会话标识和用量。
- 更新中英文 [权限与隐私说明](docs/PRIVACY.md)。

### 开源准备

- 原创代码采用 MIT。IntentGate 品牌素材适用 [独立授权](BRAND-ASSETS.md)，第三方材料继续保留各自的许可证。
- 将本地签名产物、混淆映射及 IDE 状态排除在首提范围外，保留 Gradle Wrapper。

### 验证

- Release 构建、68 项定向 JVM 测试及 4 项模拟器界面测试通过。优化构建已在模拟器验证四个主页面、全局开关及后台 Continue / Finish。
- 本地正式签名包已通过验签，并与最后通过测试的构建比对代码、资源和清单。该签名包的真机验收及 GitHub 附件下载待验证。

## v1.0.0-beta.1 (versionCode 2) — 历史候选

早期 Beta 候选，现已由 beta.2 接替。

### 功能基线

- 已安装应用选择、独立管理设置与每日限额。
- Android 使用情况统计、用途记录、本地会话和到期提醒。
- 中文与英文界面。

### 开源准备

- 应用图标统一使用设备 PackageManager 和通用占位图，演示数据与测试夹具不再依赖第三方品牌 PNG。
- 四个品牌 PNG、设计目录及内部实施记录排除在普通 Git 添加范围外；保留本地原件和原有 IntentGate Logo。
- 补齐敏感配置、私钥、日志、数据库、构建缓存和安装包的忽略规则，保留 Gradle Wrapper。
- 移除公开候选文件中的本机路径，补充 README、第三方声明及发布确认清单。
- MIT 方案等待版权信息及所有者最终批准，尚未添加项目 LICENSE。

### 验证范围

- 更新两项过时的会话测试，覆盖不同 App 的独立 Session，以及到期后通过 Continue 创建新 Session 并保留旧记录。
- 候选源码的 152 项 JVM 测试和 Release Kotlin 编译通过。模拟器检查与真机待测项目见 [设备验收计划](docs/DEVICE-TEST-PLAN.md)。
- 后续在真机发现 Release 启动和 Settings 资源崩溃，修复见 beta.2。
