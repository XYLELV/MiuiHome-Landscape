# MiuiHome-Landscape

适用于小米 11T Pro（MIUI EU 14.0.5.0）的 LSPosed 模块。

横屏时在 MIUI Home 上叠加独立桌面，提供 8x3 分页网格、Dock、拖动交换和模块自有数据库；竖屏保持 MIUI 原版桌面不动。目前仍在开发中。

## 模块网站

[MiuiHome-Landscape 项目网站](https://rizu-landscape.edgeone.app/)

网站用于查看项目路线、版本历史、阶段变化和当前已知风险。

## 当前状态

- 当前主线：横屏独立 overlay 桌面
- 目标桌面：MIUI Home
- 目标机型：小米 11T Pro
- 已知环境：MIUI EU 14.0.5.0
- 框架：LSPosed
- 状态：Alpha / 开发中

## 已实现方向

- 横屏进入独立桌面 overlay，不直接改写竖屏 MIUI Home 布局
- 横屏 8x3 分页网格
- 独立 Dock 区域
- 横屏 app 点击启动
- 横屏图标拖动、交换、分页移动
- 横屏移除 app 仅影响模块自己的横屏桌面
- 模块自有数据库 `miui_home_landscape_overlay.db`
- 竖屏尽量保持 MIUI Home 原版 Workspace、Hotseats、DragLayer 和 ScreenContent

## 不走的路线

本项目已经放弃或明确禁止以下路线：

- Magisk 资源覆盖或系统旋转开关
- 修改 MIUI Launcher 原生数据库
- 通过 DeviceConfig 强行改原生网格
- 直接改造 MIUI Home 的 Workspace / CellLayout 作为主方案
- 自动迁移竖屏桌面布局到横屏

## 已知风险

- 当前版本仍需要更多真机测试确认稳定性
- 横屏 Recents 相关显示和关闭逻辑可能受不同 MIUI Home 版本影响
- 非小米 11T Pro、非 MIUI EU 14.0.5.0 环境未保证可用
- 任何影响竖屏原版桌面的改动都需要额外谨慎验证

## 使用提示

这是一个面向特定 ROM 和机型开发的实验性模块。安装、启用或测试前，请确认自己了解 LSPosed 模块的风险，并保留可恢复手段。

如果只是想了解项目进展，建议先查看模块网站：

[https://rizu-landscape.edgeone.app/](https://rizu-landscape.edgeone.app/)

## 项目目标

MiuiHome-Landscape 的目标不是把 MIUI Home 原生桌面强行改成横屏，而是在横屏状态下提供一个独立、可维护、可回滚的桌面层。

核心原则：

- 横屏独立
- 竖屏不动
- 数据分离
- 少碰 MIUI 原生布局
- 以真机测试结果为准
