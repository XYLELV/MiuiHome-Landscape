# v5.3.1 Alpha 1 发布说明

发布日期：2026-08-20  
目标设备：Xiaomi 11T Pro（vili）  
目标系统：xiaomi.eu MIUI V14.0.5.0.TKDMIXM / Android 13  
目标桌面：MIUI Home 4.39.12.6764（versionCode 439126764）

## 版本定位

这是 V5 可编译原始源码首次进入公开仓库的 Alpha 预发布。它不沿用 v4.1.43 的反编译恢复源码作为构建基线，而是完整 Gradle 工程。

## 主要内容

- 独立 8×3 横屏桌面、9 格 Dock、分页、文件夹和全局编辑。
- 无色透明 Dock，支持自动、常规、透明外观与三档尺寸。
- 蓝白液态玻璃设置页，应用列表开关直接控制是否加入横屏布局。
- 横屏后台以 MIUI 原生状态机为权威；接管失败或超时会恢复原生后台。
- 仅作用于 `com.miui.home`，不 Hook `android`、`system_server` 或 WMS。
- 布局快照包含 revision、备份、校验和写入失败回滚。

## 发布前代码审查

- 从源码 ZIP 的全新解压目录完成 `assembleDebug`、`assembleRelease` 与 `lintRelease`。
- Android Lint：0 错误；1 条工具链升级提示，无应用代码告警。
- 源码 ZIP：51 个文件，其中 26 个 Java 文件；与本地发布源码逐项一致。
- 归档检查：74 个 ZIP 条目，无路径穿越、绝对路径、规范化重复或构建缓存。
- Manifest：无网络、存储、无障碍或系统级权限；Xposed scope 仅 `com.miui.home`。
- 未发现旧反编译残留、硬编码密钥、本机绝对路径或全局 WMS/cutout Hook。

## APK 与签名

本次公开 APK 是目标机已安装验证的 **Debug 签名 Alpha 构建**，包名为 `com.hoshinoriji.miuihomelandscape.vili`，`versionCode=50301`，`versionName=5.3.1-vili`。它带 `android:debuggable=true`，只用于受控测试，不应作为生产签名或通用稳定版分发。

## 已知限制

- 只对上方指定的 vili、Android 13 与 MIUI Home versionCode 开启硬门禁。
- 后台任务、手势动画、Activity 重建和横竖屏往返依赖 MIUI Home 私有实现，仍需持续真机回归。
- 非目标系统默认拒绝运行；“允许未验证系统”仅供能自行恢复桌面的高级测试者使用。
- 首次升级后需要重启 `com.miui.home`，让 LSPosed 注入新版本。

## AI 开发说明

V5 重构至本 Alpha 的源码实现、构建、静态审查、源码 ZIP 独立复建与 vili 真机联调由 **OpenAI Codex GPT-5.6 Sol** 全程执行；需求、设备操作、视觉验收与发布决定由维护者 XYLELV 负责。
