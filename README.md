# MiuiHome-Landscape

适用于小米 11T Pro（MIUI EU 14.0.5.0）的 LSPosed 横屏桌面实验模块。

本项目并不是强行修改 MIUI Home 原生桌面，而是在横屏状态下为 MIUI Home 叠加一个独立的横屏桌面 overlay。横屏模式使用模块自维护的布局、Dock、分页、拖动和数据层；竖屏模式尽量保持 MIUI Home 原版桌面不受影响。

## 模块网站

[MiuiHome-Landscape 项目网站](https://rizu-landscape.edgeone.app/)

网站用于查看项目路线、版本历史、阶段变化和当前已知风险。

## 当前状态

- 最终归档版本：v4.1.43 stable alpha
- 当前主线：已关闭
- 目标桌面：MIUI Home
- 目标机型：小米 11T Pro
- 已知环境：MIUI EU 14.0.5.0
- 框架：LSPosed
- 状态：Alpha / 项目关闭 / 失败归档
- 后续恢复时间：无计划

> v4.1.43 是目前实测最稳定的公开 APK 构建版本，也是本项目最终归档版本。  
> MIHL 项目已经停止继续开发。后续版本在尝试优化横屏后台任务、Recents、返回桌面和横竖屏同步时触及 MIUI Home / SystemUI / framework 级状态机，继续用 LSPosed 小补丁推进会反复引入系统级回归。

## 已实现方向

- 横屏进入独立桌面 overlay，不直接改写竖屏 MIUI Home 布局
- 横屏 8x3 分页网格
- 独立 Dock 区域
- 横屏 app 点击启动
- 横屏图标拖动、交换、分页移动
- 横屏移除 app 仅影响模块自己的横屏桌面
- 横屏文件夹基础框架
- 模块自有数据库 `miui_home_landscape_overlay.db`
- 竖屏尽量保持 MIUI Home 原版 Workspace、Hotseats、DragLayer 和 ScreenContent

## 关于源码与 APK

当前归档版本为：

- `MIHL_V4.1.43.apk`：最终 stable alpha APK
- `MiuiHomeLandscape_v4.1.43-phone-recovered-src.zip`：由手机 APK 反编译恢复的参考源码，不是原始源码

v4.1.43 对应的原始源码快照目前已丢失。仓库中的源码、APK 拆包文件或反编译结果只能作为参考，不能视为完整原源码。

如需分析 v4.1.43 的具体实现，只能以 APK、反编译结果、文件时间、实机反馈和后续残留文件作为参考。

## 项目失败原因

本项目失败点不在横屏桌面 overlay 本身，而在横屏后台任务。

v4.1.43 之后继续优化 Recents、返回桌面、横竖屏同步和系统动画时，问题已经不再是普通 UI bug，而是 MIUI Home 原生 Recents、系统手势动画、窗口过渡、桌面状态机、blur、orientation 和横竖屏同步之间的深层冲突。

LSPosed 可以 hook 方法，但无法完整拥有系统状态机。实际结果是：

- hook 少了，MIUI 原生竖屏后台、动画和布局会漏出来
- hook 多了，MIUI Home 状态机会被冻住或错判
- 强杀 blur 可以压住模糊，但容易带来卡顿
- 强锁横屏可以压住竖屏闪现，但可能污染竖屏恢复
- 修一个后台问题，容易引出小白条、桌面模糊、后台打不开、MIUI Home 重启或横竖屏错乱

因此，“只靠一个 LSPosed 模块彻底重构 MIUI Home 横屏后台任务”不具备稳定闭环。要彻底解决，需要系统级补丁、SystemUI / MiuiHome / framework 联动修改，甚至 ROM 级改造。

## 不走的路线

本项目已经放弃或明确禁止以下路线：

- Magisk 资源覆盖或系统旋转开关
- 修改 MIUI Launcher 原生数据库
- 通过 DeviceConfig 强行改原生网格
- 直接改造 MIUI Home 的 Workspace / CellLayout 作为主方案
- 自动迁移竖屏桌面布局到横屏
- 基于 v4.1.53 / v5.0 继续叠加 Recents、返回桌面和横竖屏补丁
- 继续尝试用 LSPosed 完整接管 MIUI Home 原生 Recents 状态机

## 已知风险

- 当前模块仍属于 Alpha 实验项目
- v4.1.43 是最终可保留版本，不代表完全稳定
- 横屏 Recents、返回桌面、横竖屏同步和系统动画冲突仍是高风险区域
- 后台任务显示、关闭和返回桌面动画可能受不同 MIUI Home 版本影响
- 非小米 11T Pro、非 MIUI EU 14.0.5.0 环境未保证可用
- 任何影响竖屏原版桌面的改动都需要额外谨慎验证

## 使用提示

这是一个面向特定 ROM 和机型开发的实验性 LSPosed 模块。安装、启用或测试前，请确认自己了解 LSPosed 模块的风险，并保留可恢复手段。

建议先备份当前桌面布局，并准备好 ADB 或其他恢复方式。

如果桌面异常，可以尝试重启 MIUI Home：

`adb shell am force-stop com.miui.home`

## 项目目标

MiuiHome-Landscape 的目标不是把 MIUI Home 原生桌面强行改成横屏，而是在横屏状态下提供一个独立、可维护、可回滚的桌面层。

核心原则：

- 横屏独立
- 竖屏不动
- 数据分离
- 少碰 MIUI 原生布局
- 以真机测试结果为准

## 项目现状说明

本项目已经关闭。

v4.1 已经完成横屏 overlay 桌面的主框架，v4.1.43 是目前实测最稳定的 stable alpha APK。后续继续优化 Recents、返回桌面、横竖屏同步和系统动画时，问题会触及 MIUI Home 更底层的任务管理、手势动画、布局重算和系统状态同步逻辑，已经超出普通 LSPosed overlay 模块可以稳定控制的范围。

这些问题不再只是简单的 UI bug，而是 MIUI Home 原生 Recents、系统返回动画、窗口过渡、桌面状态机和横竖屏同步之间的深层冲突。继续推进需要系统级逆向、完整 ROM/系统组件验证和更高维护成本。

因此项目封存，保留 v4.1.43 作为最终稳定成果。v4.1.53 及后续版本只作为失败证据和事故分析参考，不作为继续开发基线。

## Release 说明

GitHub Release 中提供以下文件：

- `MIHL_V4.1.43.apk`
- `MiuiHomeLandscape_v4.1.43_apk_reference_raw_unpacked.zip`
- `MiuiHomeLandscape_v4.1.43-phone-recovered-src.zip`
- 开发 MD 文档路线

其中：

- APK 用于安装和测试
- ZIP 仅作为 APK 拆包或反编译参考，不是完整源码
- v4.1.43 是最终 stable alpha 发布版本
- 项目已关闭，后续无恢复计划

## 开源说明 / Credits

本项目以个人研究和技术实验为主，源码与相关资料公开仅供学习、参考和交流使用。

Maintainer / Developer: **XYLELV**

项目开发过程中参考了真机测试结果、AI 辅助分析和多轮调试记录。感谢所有关注、测试和支持本项目的人。

## License / 说明

本项目为个人实验性模块，主要用于研究 MIUI Home 横屏 overlay 桌面实现方式。使用前请自行评估 LSPosed 模块、系统桌面 hook 和特定 ROM 兼容性风险。
