# 液态玻璃 UI 说明

## 目标

设置 App 使用蓝白色、半透明、圆润且具有边缘高光的界面语言。竖屏是单列，横屏是设置与应用管理双栏。桌面 Overlay 的顶部编辑栏已移除，应用加入横屏布局统一由设置 App 的独立开关管理。

## GitHub 调研

- [Dimezis/BlurView](https://github.com/Dimezis/BlurView)：成熟的 Android View 背景模糊方案，Apache-2.0。
- [Abdullajon1881/LiquidGlass](https://github.com/Abdullajon1881/LiquidGlass)：包含传统 View 与 AGSL 分级渲染，Apache-2.0。
- [QmDeve/AndroidLiquidGlassView](https://github.com/QmDeve/AndroidLiquidGlassView)：Android 13 液态玻璃折射/色散实现，MIT。

本项目没有复制或打包上述库的代码，也没有加入 Maven/JitPack 依赖。原因是模块需要离线可重建、安装包小且故障面有限。当前 `LiquidGlassDrawable` 与 `LiquidBackgroundView` 是独立实现，只使用 API 33 的 Canvas、Gradient、Drawable 和系统动画：

- 四段蓝白透明渐变表达玻璃体；
- 径向色晕表达背景折射；
- 外缘与内缘两层高光表达厚度；
- 设置页可见时才运行低速背景动画，离开窗口立即停止；
- 不做持续 View 层级截图，因此不会产生背景捕获递归或大位图内存压力。

这属于“仿 iOS 26 的液态玻璃视觉”，不是 Apple 私有材质的像素级复刻。

## 应用开关事务

设置 App 只能通过签名级权限向 `com.miui.home` 发显式命令。组件键包含 package、activity 与 userSerial。桌面进程在自己的单线程 worker 中原子执行：

- 打开：加入第一个空 Grid 位置；
- 关闭：从 Grid、Dock 或文件夹移除；
- 两成员文件夹移除一项后自动折叠；
- 使用随机 nonce 返回最终实际状态，设置 App 不把未经桌面确认的乐观状态当作成功。
