# v1.0.0 Beta 发布前确认清单

本文件是准备说明，不会自动创建仓库、提交、推送或发布。

## 需要所有者批准

- [x] 所有者批准 MIT，版权为 © 2026 kelzh-collab。见 [LICENSE](../LICENSE)。
- [x] 所有者确认项目品牌素材适用独立许可。见 [品牌素材授权声明](../BRAND-ASSETS.md)。
- [ ] 确认拟发布的是经过忽略规则筛选的源码，而非包含原图、设计稿、日志、私钥及模拟器数据的整个工作目录。
- [x] 核对 Android 版本 `1.0.0-beta.2`、versionCode `3` 和预定标签 `v1.0.0-beta.2`。
- [x] 已确认公开别名和 GitHub Settings 提供的 noreply 地址。
- [x] 检查项目和父目录的 Git 状态，首提审查开始时未发现已有仓库、暂存区或提交历史。
- [x] 当前候选通过 Release 构建、68 项定向 JVM 测试、4 项模拟器界面测试及后台 Continue / Finish 回归。
- [x] 本地正式签名 APK 通过验签及代码、资源、Manifest 内容比对。见 [安装说明](INSTALLATION.md)。
- [ ] 完成真机首次安装、保留数据的兼容升级和功能验收。见 [设备验收计划](DEVICE-TEST-PLAN.md)。
- [ ] 确认正式签名证书的主体字段适合公开。根据最终 APK 复核第三方声明。
- [ ] 获得发布批准后检查 GitHub 附件的实际下载、哈希与可安装性。

## 提交身份：在实际仓库存在后手动配置

先在 GitHub 的 Settings → Emails 中启用邮箱隐私，复制页面显示的 noreply 地址。不要根据用户名猜测地址，也不要使用未经批准的个人邮箱。

以下是仓库级配置示例。替换两个占位值后在 IntentGate 仓库内执行：

```sh
git config --local user.name "<APPROVED_PUBLIC_NAME>"
git config --local user.email "<APPROVED_GITHUB_NOREPLY_ADDRESS>"
git config --local user.useConfigOnly true
```

仓库级配置只影响当前仓库。`user.useConfigOnly` 可以阻止 Git 在身份配置缺失时自动猜测，但不能阻止环境变量或显式命令覆盖身份；提交前仍需检查有效作者和提交者信息。更改配置不会更改旧提交中的邮箱。

GitHub 的 “Block command line pushes that expose my email” 可作为额外保护，但不能替代全历史审查。

官方说明：[配置提交邮箱](https://docs.github.com/en/account-and-profile/how-tos/email-preferences/setting-your-commit-email-address)、[阻止暴露个人邮箱的推送](https://docs.github.com/en/account-and-profile/how-tos/email-preferences/blocking-command-line-pushes-that-expose-your-personal-email-address)。

## 资源分发边界

- 四个 `app/src/main/res/drawable/brand_*.png` 保留本地但不纳入 Git，公开源码已无对应资源符号依赖。
- 整个 `design/` 不纳入 Git，包括嵌入品牌图片的 SVG、PNG 预览、HTML、Figma 记录和本地预览工具。
- `.verification/`、内部阶段说明和本地安全复查记录不属于正式发布文档。
- `app/release/` 的 APK、R8 映射和构建元数据只留在本地。APK 和校验文件在批准后作为 Release 附件单独分发。
- 原 Logo、启动图标及其他项目品牌素材保留原样，分发遵循 [品牌素材授权声明](../BRAND-ASSETS.md)。
- 已确认采用 OFL 的 Inter 字体及原始许可证继续随 Android 源码分发；第三方许可不因未来 MIT 方案而改变。

忽略规则只约束普通 Git 添加流程。实际仓库若已跟踪上述文件，需另行审查索引与全部历史；本阶段不自动改写历史，也不创建提交。
