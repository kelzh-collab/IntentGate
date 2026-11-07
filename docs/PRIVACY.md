# 权限与隐私 / Permissions and privacy

适用于 IntentGate `1.0.0-beta.2` 的发布候选版本。

| Android 权限或服务 | 用途及关闭后的行为 |
| --- | --- |
| 使用情况访问 | 读取系统记录中的应用包名、Activity 类名、时间戳和事件类型，用于统计、每日限额和按实际前台用量计时。Stats 明细可显示系统可查询的非受管应用。撤销后停止读取系统用量，依赖记录核实的会话计时暂停。 |
| 无障碍服务 | 在用户同意并到系统设置开启后，只处理窗口状态变化及包名。服务结合系统用量记录确认应用切换，显示 Intent Check 和到期悬浮窗。用户在到期窗口点击 Finish 后，服务可返回桌面。关闭后停用该后台检测和悬浮窗路径。服务不读取其他应用的屏幕文字、聊天、输入框或密码，不注入手势。 |
| 显示在其他应用上层 | 无障碍悬浮窗不可用时，作为到期确认的备选路径。关闭后仍可使用已获授权的无障碍悬浮窗或通知。 |
| 通知 | 显示 Intent Check、会话到期和每日限额提醒。关闭后停止这些通知。锁屏是否显示应用名或时长取决于系统通知设置。 |

原始无障碍事件和 Usage Access 事件流在内存中处理。管理名单、Intent Check 请求、你填写的用途、会话、限额与同意状态保存在应用的私有存储。应用不要求云端账号，当前清单未声明互联网权限。旧版 Android 备份与 Android 12+ 云备份及设备迁移规则只允许语言偏好。旧版本已生成的备份由系统管理。

关闭全局 Intent Check 会撤下未确认的请求及其提醒，阻止新会话。已开始的会话仍按原规则计时并到期。重新开启后可创建新会话，也可通过 Continue 继续使用。

## English

These notes apply to the IntentGate `1.0.0-beta.2` release candidate.

| Android permission or service | Use and effect of turning it off |
| --- | --- |
| Usage Access | Reads package names, activity class names, timestamps, and event types from Android to show statistics, apply daily limits, and count verified foreground time. Detailed stats can include queryable unmanaged apps. Revoking access stops system usage reads and suspends timing that depends on those records. |
| Accessibility | After in-app consent and activation in Android settings, processes window-state changes and package names. It checks the transition against Android usage records, shows Intent Check and expiry overlays, and can return to the launcher after the user taps Finish. Disabling it stops this background detection and its overlay. It does not read screen text, messages, input fields, or passwords from other apps or inject gestures. |
| Display over other apps | Provides an expiry overlay when the accessibility overlay is unavailable. The accessibility overlay or notifications can still work if separately enabled. |
| Notifications | Delivers Intent Check, session expiry, and daily limit alerts. Turning notifications off stops these alerts. Android settings control whether app names or usage times appear on the lock screen. |

Raw accessibility and usage events are processed in memory. Managed apps, Intent Check requests, intentions you enter, sessions, limits, and consent are stored in private app storage. The app requires no cloud account and does not declare the Android Internet permission. The backup and device-transfer rules allow only the language preference. Android manages backups created by older versions.

Turning off global Intent Check dismisses unconfirmed requests and reminders and stops new sessions. Started sessions retain their timing and expiry handling. Turn it on again to start or continue a session.
