# 架构说明

## 原则

MIUI Home 继续拥有 Activity 生命周期、竖屏桌面和后台可见状态。模块只在目标 Launcher Activity 的横屏会话内拥有自己的 Overlay。所有跨层状态都由一个 `LandscapeController` 收敛，避免旧版的进程静态 Recents 标记、定时脉冲和多处重复 Hook。

## 状态流

```text
Launcher lifecycle / exact Recents visibility
                    │
                    ▼
          LandscapeController
          ├─ portrait / paused ──► restore leases, hide overlay
          ├─ landscape home ─────► 8×3 grid + 9-slot dock
          └─ native Recents ─────► read tasks → layout → takeover
                                      │ failure/timeout
                                      └──────────────► restore native Recents
```

## 兼容门禁

`DeviceProfile` 的硬门禁校验设备代号、SDK 与 MIUI Home versionCode。型号和完整固件 fingerprint 仍回传用于诊断，但 xiaomi.eu 可能改写这些字符串，因此不作为硬失败条件。门禁在每个带副作用的生命周期入口前执行；真正未匹配时不创建 Controller。

## 旋转

目标 MIUI Home Manifest 锁定竖屏，所以仅拦截未来的 `setRequestedOrientation()` 不够。Controller 在会话建立时主动请求选定策略；Hook 同时阻止 MIUI 后续重新锁回竖屏。原始 requestedOrientation 被记录，控制器退出时尝试恢复。

## 原生 View 租约

横屏 Home 只隐藏 `workspace` 与 `hotseat`。`NativeViewLease` 保存 visibility、alpha 与 accessibility 值；不覆盖 Listener、不改 LayoutParams、不移除原生 View。`WindowLease` 只作用于当前 Launcher Window，并恢复 cutout/system-bar 状态。

## Hook 优先级

方向锁、Launcher 生命周期、后台可见性和手势完成事件的 Xposed 回调都使用 `XC_MethodHook.PRIORITY_HIGHEST`，让本模块在同一个被 Hook 方法的回调队列中优先处理。该优先级不等于 LSPosed 模块装载顺序；LSPosed 没有向模块提供全局“第一个启动”的公开接口。

## 全局编辑模式

桌面空白长按由系统 `ViewConfiguration` 的标准长按时长触发，Controller 统一持有编辑状态。进入后 Grid 与 Dock 同时显示移除入口，拖放结束不会自动退出；顶部工具栏提供添加应用和完成。编辑模式在进入后台、转为竖屏、数据保护回退或 Controller 销毁时强制退出，避免状态泄漏到下一次会话。

## 数据层

布局以一个 JSON 快照表达 Grid、Dock、Folder、nextFolderId、初始化标志和 revision。每次 mutation 在深拷贝上完成，提交前验证：

- Grid/Dock 范围合法；
- 同一组件在全局只出现一次；
- Folder 引用、成员和 ID 一致；
- 数量与字符串长度不超过防御上限。

提交时同时写入上一版快照、当前快照和 revision。读取先尝试当前，再尝试备份；两者都坏则返回 `unreadable`，Controller 恢复 MIUI 原生桌面并拒绝自动填充。只有设置页二次确认的 reset 能覆盖该状态。

## 后台任务

MIUI 的 `RecentsContainer` 可见性是唯一权威信号。自定义后台遵循以下门槛：

1. 精确 Hook 收到新的 visible epoch；
2. 原生容器变为可见时立即保持 `VISIBLE` 并以 `alpha=0` 遮蔽，保留其 overview、测量和手势状态；
3. 全屏手势收到 MIUI 自己的 `FsGestureEnterRecentsCompleteEvent`（三键导航使用 520ms 保守回退）；
4. API 33 `IActivityTaskManager#getRecentTasks(III)` 成功；
5. 原始任务可投影；
6. 自定义 View 已完成非零尺寸 PreDraw；
7. 原生 View 仍附着且权威状态未变化。

任务数据与自定义布局全部就绪后仍保留原生容器为 `VISIBLE + alpha=0`，由全屏自定义 View 接管命中测试。进入 Recents 导致的 Launcher pause 会保留同一 ViewRoot 内的后台会话；真正离开、转竖屏、3 秒超时、异常或代际过期才恢复原生状态。单卡移除只发送一条 MIUI `TaskViewDismissedEvent`，并在后台线程确认目标 taskId 已从系统最近任务消失；清空使用一条 Binder mutation。移除/清空产生的短暂原生隐藏信号由 mutation guard 吸收，不会退出自定义后台。

退出后台或转入竖屏时不只改变 View：先调用目标 `RecentsContainer.dismissRecentsToHome()`，再同步提交 `LauncherState.NORMAL` 并把原生容器设为 `GONE`。这样配置变化不会从 Launcher 状态机中复活原生 Overview。

## 并发

布局读取、写入、应用枚举和图标预热使用 Controller 的单线程 worker 串行执行；UI View 只在主线程更新。回调携带 generation，暂停、旋转或销毁后旧结果不能重新显示 Overlay。
