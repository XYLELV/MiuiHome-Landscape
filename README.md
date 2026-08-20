# MiuiHome Landscape V5 · Vili

> 当前公开测试版本：**v5.3.1 Alpha 1**（应用内版本 `5.3.1-vili`）。这是面向单一机型与固件的预发布版本，不是通用 MIUI Home 模块，也不标记为 Stable。

这是为 **小米 11T Pro（vili）** 重做的 MIUI Home / LSPosed 横屏模块源码。它不是把竖屏桌面简单旋转 90°，而是在横屏时提供一套独立的平板式桌面呈现，同时把竖屏 MIUI 桌面保留为系统权威实现。

当前源码专门锁定以下环境：

- 设备：Xiaomi 11T Pro，型号 `2107113SG`，代号 `vili`
- 系统：xiaomi.eu MIUI `V14.0.5.0.TKDMIXM`
- 固件指纹：`Xiaomi/vili/vili:13/RKQ1.211001.001/V14.0.5.0.TKDMIXM:user/release-keys`
- Android：13 / API 33
- MIUI Home：`RELEASE-4.39.12.6764-09151558`，versionCode `439126764`
- Launcher：`com.miui.home.launcher.Launcher`
- Recents：`com.miui.home.recents.views.RecentsContainer`

硬门禁只校验 `vili + Android 13 + MIUI Home versionCode 439126764`。xiaomi.eu
可能改写型号与固件 fingerprint，因此两者只用于诊断显示，不再要求开启“未验证环境”才能运行。

## 已实现

- 8 列 × 3 行分页横屏桌面，页面吸附与边缘拖拽翻页。
- 底部 9 格圆角悬浮 Dock，可在 Grid、Dock、文件夹之间拖放。
- 长按桌面空白进入全局编辑：Grid 与 Dock 同时显示移除按钮，可连续拖动；点击空白退出。
- 应用文件夹：创建、改名、添加、内部排序、成员移出、两文件夹间移动。
- 工作资料/多用户启动，布局键使用 `package + activity + userSerial`。
- 三种旋转策略：
  - 自动旋转：`SCREEN_ORIENTATION_SENSOR`，按传感器切换。
  - 强制横屏：`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`。
  - 跟随系统：`SCREEN_ORIENTATION_FULL_USER`，遵循系统旋转开关。
- 横屏后台任务界面：读取成功且自定义界面完成布局后才隐藏 MIUI 原生后台；任何反射、Binder、布局或超时错误均恢复原生后台。
- 壁纸轻微暗化、应用名称、手势提示条均可单独设置。
- 设置与桌面进程使用签名级权限广播，带随机 nonce 和桌面 ACK。
- 布局为带 revision 的单快照事务，保留上一版备份；主/备份都损坏时拒绝普通写入，不自动清空。

## 5.0.1 真机修正

- xiaomi.eu 改写的型号/fingerprint 不再触发误判，目标 vili 无需开启“未验证系统”。
- 圆形、方形和 Adaptive Icon 统一放入同尺寸 MIUI 圆角方形图标底板。
- 后台接管等待 MIUI 的手势完成事件；进入后台导致的 Launcher pause 不再释放横屏会话，Activity 重建不再短暂恢复竖屏。

## 5.0.2 真机修正

- 图标改为无底板、无描边的全幅圆角裁切；旧式圆形位图轻微放大，避免透明区露出白边。
- MIUI 原生后台变为可见的同一主线程周期就被透明遮蔽，任务读取仍等待手势完成，因此慢上滑不再先闪出原生后台。
- 单卡删除或全部清理期间忽略 `RecentsContainer` 的内部隐藏信号，刷新后继续停留在横屏后台。

## 5.0.3 真机修正

- 单卡关闭改走目标 MIUI Home 自己的 `TaskViewDismissedEvent`，由原生任务数据层和 ProcessManager 完成关闭，不再只调用 Android `removeTask()`。
- 删除后轮询系统最近任务并确认目标 taskId 已消失；未确认时保留横屏后台与原卡片，不再退回空白的原生背景。
- 自定义后台期间原生容器保持 `VISIBLE + alpha=0`，既维持 MIUI overview 状态，也杜绝原生空背景重新露出。

## 5.0.4 真机修正

- 离开横屏后台或切入竖屏前调用目标 `RecentsContainer.dismissRecentsToHome()`，不再只隐藏后台 View。
- 同步确认 `LauncherStateManager` 已提交 `LauncherState.NORMAL`，避免清理后台后横转竖时重新弹出竖屏后台。
- 回主页时把原生后台容器固定为 `GONE`，再释放自定义后台和横屏主页。

## 5.1.0 编辑模式与 Hook 优先级

- 桌面空白处使用系统标准长按时长进入全局编辑，不再直接弹出应用选择器。
- 编辑期间 Grid 与 Dock 同时显示移除按钮；移除只影响横屏布局，不卸载应用，也不修改竖屏桌面。
- 编辑状态在连续拖放后保持，可通过顶部“添加应用”“完成”按钮或点击空白处退出。
- 所有关键 Xposed 方法回调使用 `XC_MethodHook.PRIORITY_HIGHEST`。这是 Xposed 方法回调优先级；LSPosed 没有对模块公开全局“最先加载”顺序接口。

## 5.2.0 液态玻璃设置中心

- 移除横屏桌面顶部编辑工具栏，长按空白仍可进入全局编辑，点击空白退出。
- 设置 App 全面改成蓝白色液态玻璃界面：透明叠色、折射色晕、双层边缘高光和轻量动态背景。
- 竖屏采用单列滚动，横屏自动变成设置/应用双栏，并分别滚动。
- “添加应用”改为完整应用列表开关：打开即加入横屏 Grid，关闭会从 Grid、Dock 或文件夹原子移除；不卸载应用，也不修改竖屏桌面。
- 新增 Dock 启用开关、液态玻璃材质开关和 20%–90% 透明度调节。
- 视觉实现完全使用 Android 13 原生 Canvas/Drawable，没有增加第三方运行依赖；设计调研见 [液态玻璃 UI 说明](docs/LIQUID_GLASS_UI.md)。

## 5.2.1 真机启动修正

- xiaomi.eu 的 `PhoneWindow` 在 `setContentView()` 前可能尚未创建 `DecorView`；浅色系统栏配置现改到内容视图创建后执行，并对 `DecorView`/`WindowInsetsController` 做空值保护。

## 5.3.0 Dock 调校

- 移除难以稳定调校的“玻璃开关 + 原始透明度滑杆”，改为 `自动 / 常规 / 透明` 三档玻璃外观。
- 自动档读取系统壁纸主色亮度：亮壁纸使用对比度更强的常规材质，暗壁纸使用更通透的透明材质。
- 新增 `紧凑 / 标准 / 大号` 三档尺寸；每档会同时调整 Dock 高度、最大宽度、圆角和图标大小，不再只是修改设置页数值。
- 设置仍通过签名权限广播同步到 `com.miui.home`，桌面进程会确认接收；升级后需重启一次桌面进程，让 LSPosed 注入新版本。

## 5.3.1 无色透明 Dock

- 移除 Dock 背景中的蓝色渐变与蓝灰叠色；三种外观档位均只使用无色透明层和轻微白色边缘高光。
- 自动模式只根据壁纸亮度调整透明强弱，不再改变 Dock 色相，避免污染壁纸原色。

## 安全边界

- LSPosed 作用域只有 `com.miui.home`。
- 不 Hook `android`、`system_server`、WMS 或所有应用的 Window。
- 不全局 Hook `View`；后台只 Hook 已核对的 `RecentsContainer#setVisibility(int)`。
- 不替换 MIUI 原生触摸/拖拽 Listener。
- 修改原生 View、系统栏和方向时保存旧值，并在退出横屏或销毁时恢复。
- 竖屏由 MIUI Home 原生界面负责；模块 Overlay 为 `GONE`。

## 构建

要求 Java 17、Android SDK Platform 33。项目包含 Gradle 8.13 wrapper；首次在新电脑构建时仍可能联网下载 Gradle、AGP 8.10.1 与 Xposed API 82。

Windows：

```powershell
./build_apk.bat
```

或直接执行：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_SDK_ROOT = "$env:LOCALAPPDATA\Android\Sdk"
./gradlew.bat :app:assembleDebug --no-daemon
./gradlew.bat :app:assembleRelease --no-daemon
```

Linux/macOS：

```sh
JAVA_HOME=/path/to/jdk17 ANDROID_SDK_ROOT=/path/to/sdk sh ./build_apk.sh
```

本源码包不包含已构建 APK，也不包含机器相关的 `local.properties`。

## 安装与首次试验

1. 构建并安装 APK。
2. 在 LSPosed 中只勾选 `系统桌面 / com.miui.home`，不要勾选 Android 系统。
3. 不要同时启用旧版 MiuiHome Landscape 模块。
4. 打开模块设置页，确认显示“模块已连接，设置已由桌面确认”。
5. 先选“跟随系统”，开启系统自动旋转，再横置手机。
6. 依照 [真机验收清单](docs/DEVICE_TEST_CHECKLIST.md) 逐项验证，再决定是否使用强制横屏与自定义后台。

若桌面异常，先转回竖屏；仍未恢复时在电脑执行：

```sh
adb shell am force-stop com.miui.home
```

如需彻底回退，在 LSPosed 关闭本模块并重启桌面进程。布局数据独立于 MIUI 竖屏桌面，不会删除或移动原桌面图标。

## 代码结构

```text
app/src/main/java/com/hoshinoriji/miuihomelandscape/
├─ MiuiHomeLandscapeModule.java       精确 Hook、兼容门禁、进程通信
├─ MainActivity.java                  原生设置页
├─ core/                              设置、设备档案、可回滚租约
├─ model/                             Grid / Dock / Component / Folder 模型
├─ store/LandscapeStore.java          校验、事务快照、备份和迁移
└─ overlay/
   ├─ LandscapeController.java        单 Activity 状态机与拖放路由
   ├─ LandscapePagedGridView.java     8×3 分页输入模型
   ├─ LandscapeDockView.java          9 格 Dock
   └─ recents/                        原生权威、失败回退的后台任务层
```

更详细的设计与边界见 [架构说明](docs/ARCHITECTURE.md) 和 [安全与回滚](docs/SECURITY_AND_ROLLBACK.md)。

## 当前验证状态

- 已从交付源码 ZIP 的全新解压目录，在 Java 17 + Android SDK 33 下完成 Debug、Release 与 Release Lint 复建；Lint 为 0 错误，仅有 1 条 Gradle 工具链升级提示。
- Manifest、资源、DEX、Xposed scope、ZIP CRC、重复路径与路径穿越在交付前自动检查。
- 已与目标 `MiuiHome.apk` 的 Launcher 生命周期声明和 `RecentsContainer#setVisibility(int)` 做静态交叉核对。
- 已在目标 Xiaomi 11T Pro（vili）上安装 v5.3.1，确认 LSPosed 注入、设置连接、横屏桌面与无色透明 Dock 可运行。
- 横屏后台清理、横竖屏往返、手势中断与 MIUI Home Activity 重建仍属于高风险状态机路径；Alpha 发布不代表这些组合已完成穷尽测试。

## AI 开发与审查说明

V5 重构至 v5.3.1 Alpha 1 的源码整理、实现、Gradle 编译、静态代码审查、源码 ZIP 独立复建与 vili 真机联调由 **OpenAI Codex GPT-5.6 Sol** 全程执行。项目需求、设备操作、视觉验收与最终发布决定由维护者 XYLELV 负责。

发布前审查记录见 [v5.3.1 Alpha 1 发布说明](docs/RELEASE_5.3.1_ALPHA_1.md)。
