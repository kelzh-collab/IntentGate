# APK 签名、安装与下载验收

目标标签为 `v1.0.0-beta.2`。本地签名候选已通过验签与内容比对，真机验收与公开下载待验证。

## 构建与签名

使用所有者保管的现有 Release 密钥。保持密钥库和密码在仓库及同步目录之外，密码只在本机安全输入框中填写。证书字段随 APK 分发，公开前应检查其内容。

需要重新打包时，从经过审查的源码副本或全新检出目录构建。Gradle 仍可读取本地工作区里被 Git 忽略的品牌 PNG，因此应使用干净副本。保留一份受保护的离线密钥备份，密码与备份分开保管。

Android Studio 中选择 **Build → Generate Signed Bundle / APK → APK**，使用现有 keystore 和别名，选择 `release`。发布附件应为通过下节检查的正式签名包。

官方参考：[Android 应用签名](https://developer.android.com/studio/publish/app-signing)、[apksigner](https://developer.android.com/tools/apksigner)。

## 签名与完整性检查

预期应用 ID 为 `com.intentgate.app`，versionName 为 `1.0.0-beta.2`，versionCode 为 `3`，minSdk 为 `26`，targetSdk 为 `37`。

签名完成后，将 APK 命名为 `IntentGate-v1.0.0-beta.2.apk`。在配置 `ANDROID_HOME` 并核对正式证书 SHA-256 指纹后执行：

```powershell
.\scripts\Verify-ReleaseApk.ps1 -ApkPath "<SIGNED_RELEASE_APK>" -ExpectedSignerSha256 "<APPROVED_64_HEX_CERTIFICATE_FINGERPRINT>" -OutputDirectory ".\artifacts\v1.0.0-beta.2"
```

脚本验证签名、证书指纹、包名、版本、SDK 和不可调试状态，拒绝 Android Debug 证书；只在成功后生成 `SHA256SUMS`。证书指纹与 APK 文件 SHA-256 是两个不同的值。此脚本不会安装或上传 APK。

## 保留现有数据的安装原则

1. **最稳妥的方式是保留原手机上的 Debug 安装，在未装同包名的另一台真机首次安装正式 APK。** 记录机型、Android 版本和已装包信息。这样原手机的管理名单、限额和 Session 历史可继续保留。
2. 两个版本的包名都是 `com.intentgate.app`。旧 Debug APK 已确认使用 Android Debug 证书。新正式证书不同，且没有有效签名轮换证明时，Android 会拒绝覆盖，即使提高 versionCode 或使用 `adb install -r` 也一样。遇到不匹配时保留原安装，停止卸载、清数据或强装操作。
3. 当前设置中的数据导出为占位功能，跨签名迁移需要单独验证可恢复的导出与导入方案，再由所有者批准。只有签名兼容、版本允许更新时才执行覆盖，并核对原有数据仍在。
4. 在独立真机按系统提示允许受信来源安装，再通过应用入口验证 Usage Access、通知及实际提醒路径所需权限。见 [Android 更新签名规则](https://developer.android.com/google/play/app-updates#how-android-handles-app-updates)。

安装完成后逐项执行 [设备验收计划](DEVICE-TEST-PLAN.md)。模拟器结果不能替代真机结果。

## GitHub Release 附件与下载验收

本轮只准备说明，不创建标签、提交、推送或 Release。所有审批、签名与设备验收通过后，另由所有者最终确认发布。

- Release 标签使用 `v1.0.0-beta.2`，标记 Pre-release。
- 附件包含签名 `IntentGate-v1.0.0-beta.2.apk` 和对应 `SHA256SUMS`。APK 提供给需要直接安装的用户。
- 公开后使用未登录浏览器或独立环境访问仓库 Release 页面，点击 APK 附件，确认可下载且不是登录页、HTML 错误页或源码压缩包。
- 从实际下载的文件重新计算 SHA-256，与发布时原始签名 APK 和 `SHA256SUMS` 三方比较，再运行签名/包信息检查。
- 在授权的干净测试设备执行首次安装；在同签名旧版本设备执行保留数据的覆盖安装，并执行完整功能验收。
- 将实际 URL、下载时间、文件大小、哈希、签名和设备结果记录在发布验收记录中；没有实际执行时均标记“待测”。

官方参考：[GitHub Releases 与附件](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)。
