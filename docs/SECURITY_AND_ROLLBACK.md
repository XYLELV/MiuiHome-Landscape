# 安全与回滚

## IPC

控制 action 使用模块 applicationId 前缀，并显式发往 `com.miui.home`。目标动态 Receiver 要求模块定义的 signature 权限；普通第三方应用无法发送导入、重置或设置命令。设置 ACK 使用一次性随机 nonce，未经请求的 PONG 不改变连接状态。

## Hook 范围

模块入口同时限制 packageName 和 processName 为 `com.miui.home`。Xposed scope 只有该包。代码不进入 `android/system_server`，不修改所有应用的 cutout 策略，也不使用全局 `View#setVisibility` Hook。

## 故障回退

- 非目标设备/系统/桌面版本：默认不运行。
- Overlay attach 失败：保留 MIUI 原生 View。
- 布局不可读：隐藏模块 Overlay、恢复 MIUI 原生 View，拒绝自动写入。
- Recents 读取、投影、布局、Binder 或超时失败：恢复捕获的原生 Recents 状态。
- Activity pause/destroy：取消代际任务、恢复租约并移除自定义 View。

## 用户回滚

1. 转回竖屏。
2. 在 LSPosed 关闭模块作用域。
3. 强制停止 `com.miui.home` 或重启设备。
4. 必要时卸载模块应用。

模块布局存放在 MIUI Home 进程自己的独立 SharedPreferences 名称下，不修改 MIUI 原生 Workspace 数据库。不要同时启用旧版模块；两个模块同时改 Launcher 生命周期的行为不在支持范围内。

