# AI_HANDOFF_4.0_ALPHA.md

> 主线交接文档  
> 项目：MiuiHome Landscape，LSPosed 模块  
> 分支：v4.0 Alpha  
> 后续如果进入 v4.1 或新主线，请新建交接文档，不要混写到本文件。

---

## 当前版本

- Version: v4.0.47 (doc rev: v4.0.47-doc-OC1)
- Date: 2026-04-30
- Status: v4.0.47 修复 v4.0.46 双小白条、底部白线、退出后台短暂竖屏态问题：删除模块自绘 pill，只保留系统小白条；退出后台时延迟隐藏横屏 Recents 遮罩，避免先露出底层原生桌面；已 ADB 安装并验证关键日志顺序。
- 本次文档修订：记录 OpenAI Codex GPT-5.5 对 v4.0.31-v4.0.47 的代码修改，不改写 Claude 管辖版本历史。
- Main route: 横屏使用独立 overlay 桌面，自维护布局、Dock、点击、拖动、分页、数据。竖屏必须保持 MIUI Home 原版。
- 禁止路线: Magisk 资源开关、MIUI launcher 数据库改写、DeviceConfig 改网格、Workspace/CellLayout 原生改造、自动迁移竖屏布局。

---

## 参与过的 AI
- Chatpgt 5.4thinking （网页版）, Codex ChatGPT 5.4 Thinking , claude code opus 4.7 ,claud sonnet 4.6,Claude Opus 4.7（网页版）
- Codex: 本文档中只负责整理 Codex 自己参与过、或能由本地证据确认的版本记录。当前主力按用户说明记为 OpenAI Codex GPT-5.5。Codex 主导版本：`v4.0.0-codex-fixed`、`v4.0.1` ~ `v4.0.27`。
- Codex GPT5.4: 仅在有明确文档证据时记录，例如 `HANDOVER_TO_CODEX_beta16-reset.md` 中的 Codex ChatGPT 5.4 Thinking 交接内容；历史讨论中也出现过 Codex GPT-5.4 Thinking。
-  Claude cowork Opus 4.7 （也算是Claude Opus 4.7（网页版））参与的版本：`v3.0.0-beta16-reset_fix1` / `fix2-8x2-independent` / `fix3-hardzoned-8x2` / Claude 分支的 `v4.0.0`（与 Codex 主线 `v4.0.0-codex-fixed → v4.0.30` 平行，不要混）；Claude Code Opus 4.7 的 `v4.0.28` / `v4.0.29` / `v4.0.30` / `v4.0.30-doc-OC`。
- Claude Opus 4.7（网页版）— 早期数据层路线（由本会话补充，下文 v1.6.0-DIAG ~ v2.1.0 / HANDOFF 包条目）：`v1.6.0-DIAG` / `v1.7.0` / `v1.7.5-beta-6x3` / `v1.7.6` / `v1.7.7` / `v1.8.0` / `v1.9.0` / `v1.9.1` / `v2.0.0` / `v2.0.1` / `v2.1.0` / `MiuiHomeLandscape_HANDOFF_for_Codex.zip`。这一段属于 v3 重构之前的"DeviceConfig + 双 launcher DB"路线，已经被后续 v3/v4 主线明确弃用，仅保留为踩坑历史。
- Claude sonnet 4.6（网页版）早期magisk（分叉路线但被废弃）及lsposed V1.0-1.5路线开发。
- 重要提醒: 不要默认相信任何旧结论。后续只信代码、真实数据库、真实日志、真实界面结果。

---

## AI 模型与文档命名规则
- 注意Codex Chatgpt 5.4/5.4Thinking 和Chatgpt 5.4thinkg是两个不同模型，一个为codex另一个是网页版GPT。
 claude 同理 带code为电脑，没有为网页。
- 当前主力: OpenAI Codex GPT-5.5,Claude Code opus4.7半主力。
- 历史参与（现在不在参与编程）:Codex GPT-5.4 Thinking 参与过方案编辑，分析和交接判断,大概上为（v2.1-v3.0.0-beta16-reset)；
 Claude （网页版）Opus 4.7 `V1.0 → v4.0.0`（与 Codex 主线 `v4.0.0-codex-fixed → v4.0.30` 平行，不要混）
 ChatGpt 5.4 thinking:代补充
 Claude Sonnet 4.6（网页版）：参与了早期 magisk（分叉路线但被废弃）及LSPosed 模块开发（v1.0-1.5) 。负责范围大致为早期 hook 方案探索（前台强制横屏 / `IS_TABLET` / `Configuration` 等失败路线）。不要依赖此字段做版本归因判断，证据不足。
- `AI_HANDOFF_4.0_ALPHA_OC.md`: `OC` 表示 OpenAI Codex 和 Claude 都编辑过或需要共同交接的本地存档。普通迭代默认使用这个文件名，除非用户明确要求别的尾号。
- `AI_HANDOFF_4.0_ALPHA_C.md`: `C` 表示最后一个版本由 Claude 编辑。
- `AI_HANDOFF_4.0_ALPHA_G.md`: `G` 表示最后一个版本由 Codex / GPT 编辑。
- 除非用户主动提出添加尾号，或明确要求使用 `C`、`G`、`OC`，否则普通交接文档默认输出为 `AI_HANDOFF_4.0_ALPHA_OC.md`。
- 每次编辑新版本或新交接文档时，必须同步更新 Version Log，说明该版本或该文档改了什么、谁改的、证据是否足够。

---

## 当前已实现

以下只表示项目历史中已经实现过或基本实现过，不代表全部已经最终稳定：

- 横屏独立 overlay 桌面。
- 横屏主区域分页布局。
- 横屏 Dock 独立区域。
- 横屏 app 点击启动。
- 横屏拖动、交换、移动到 Dock。
- 边缘自动翻页。
- 横屏移除 app 只从横屏移除，不影响竖屏桌面和真实安装状态。
- 模块自有数据库 `miui_home_landscape_overlay.db`。
- 竖屏原则上恢复 MIUI Home 原版 Workspace、Hotseats、DragLayer、ScreenContent。
- 横屏禁用负一屏，避免触发横竖分离失效。

---

## 仍需测试

- v4.0.29 竖屏触摸恢复是否在用户实机完全正常。
- v4.0.28 到 v4.0.30 的 overlay/recents 新方向是否稳定。
- 横屏 Recents 是否有原生卡片漏底、视觉污染、任务关闭失败。
- 不同 MIUI Home 版本、不同分辨率、折叠屏、平板比例下的适配。
- 横屏返回后台再回桌面，横竖分离是否继续有效。

---

## 已知风险

### Bug-001: 横屏 Recents 可能漏底

- Bug: v4.0.29 起不再强制 GONE 原生 RecentsContainer，只依赖 z-order 覆盖。理论上原生 RecentsContainer 仍可能在底层渲染并漏出。
- Impact: 视觉污染。
- Status: 待实机观察。
- Notes: 如果必须重新处理，优先在 `setVisibility` 的 beforeHook 改入参，避免 afterHook 递归和误恢复。

### Bug-002: Recents 反射可能因 ROM 差异失效

- Bug: 不同 MIUI 版本中 RecentsModel、TaskStack、Task 字段名可能不同。
- Impact: 横屏 Recents 可能显示空列表或关闭失败。
- Status: 需要更多 ROM 验证。

### Bug-003: Recents dismiss 路径不稳定

- Bug: v4.0.27 的原生 Recents dismiss 走 `TaskViewDismissedEvent`，但 v4.0.28 后改为 overlay/recents 新方向。两条路线不能混用。
- Impact: 部分 ROM 上左滑关闭可能无效。
- Status: 待实机验证。
- Notes: v4.0.27 的 `dismissRecentsTaskView` 可以作为参考，但不要未经用户同意直接恢复。

---

## 后续 AI 修改权限规则

- 可以直接做: 文档更新、日志分析、代码阅读、方案对比、低风险小修。
- 必须先问用户: 新功能、架构重构、Recents 核心改动、文件夹功能、Dock 结构变化、原生 MIUI hook 增删、系统级 touch/drag/drop 改动。
- 禁止直接做: 清数据库、卸载模块、重置桌面布局、`pm clear`、恢复 MIUI launcher DB 路线、自动迁移竖屏布局。
- 任何可能影响竖屏原版行为的修改，都必须先获得用户确认。

---

## Version Log

> 记录原则：这里只写能从本地文档、构建产物、截图文件名、当前工程文件或已知交接内容确认的事实。证据不足的字段统一写 `Unknown / not enough evidence`，不要补脑。当前目录不是 git repo，所以本轮不使用 commit 作为依据。
> 责任边界：Codex 不替 Claude 维护的版本写变动细节；Claude 管辖版本如需补充，请交给 Claude 或以独立证据重新确认。

> 这一整段属于Magisk尝试路线的旧路线（"想办法不重写桌面，而是直接撬 MIUI Home 原生桌面的横屏能力"），结论是彻底失败、仅作为纪念可以无视。如果有兴趣慢慢看，不然请直接跳转V4.0。（AI编辑阅读请无视）

###### 历代模型(Magisk)

### magisk v1.0.0（Gpt版本）
- Date: 2026-04-18
- AI involved: Chatgpt 5.4(web)
- Changed: 梦开始的地方，Chatgpt 5.4thinking (web) 产出第一版 Magisk 试验模块，目标是只对 `com.miui.home` 做横屏开启，路线为 RRO / fabricated overlay 资源覆盖；同时补了作者字段、说明文字和可改配置。
- Fixed: 初步验证了“MIUI Home 横屏”可以先从资源层开关路线尝试，而不是全局强制横屏。
- Still broken: 打包层级错误，`module.prop` 等文件不在 zip 根目录，Magisk 直接报 **This zip is not a Magisk module**；功能未真正进入测试。
- New bugs: 安装失败。
- Risk / Notes: 这是最早的试探包；后续已证明这条 overlay 路线在用户机型环境上走不通。
- Next AI should know: 这版只有历史意义，不要拿来当功能基线。
- User approval required before: 继续把 RRO / fabricated overlay 当主路线。
- Test result: 用户实机安装失败，Magisk 不识别模块。

### magisk v1.0.1-flat
- Date: 2026-04-18
- AI involved: Chatgpt 5.4(web)
- Changed: 重新打包，修正 zip 结构，把模块文件放到根目录，解决“不是 Magisk 模块”的问题。
- Fixed: Magisk 能正常识别并安装模块。
- Still broken: 开机后日志直接出现 `cmd overlay unavailable; aborting`，模块实际没有改到桌面横屏。
- New bugs: 无新增功能性 bug，但彻底暴露出 overlay/service 路线在该 ROM 上不兼容。
- Risk / Notes: 这版确认了“不是打包问题，而是运行时环境问题”。
- Next AI should know: 安装成功 ≠ 功能成功；这版是格式修复，不是路线修复。
- User approval required before: 继续沿用 overlay 命令路线。
- Test result: 用户实机可安装，但不生效。

### magisk v1.1.0-diag
- Date: 2026-04-18
- AI involved: Chatgpt 5.4(web)
- Changed: 产出纯诊断版 Magisk 模块，不改系统行为，只收集 `cmd/overlay/pm`、`com.miui.home` 路径、版本、prefs/资源候选等信息。
- Fixed: 明确定位了旧路线失败原因：Magisk 的 `service.sh` 环境里系统服务调用不稳定，`cmd overlay` / `pm path` 存在 transaction failed；同时确认 `MiuiHome` 存在 pad / landscape 资源，但没有简单的一刀开关。
- Still broken: 不是功能模块，不能实现横屏。
- New bugs: 无。
- Risk / Notes: 这是旧路线最重要的证据包；证明“纯 Magisk + overlay/service”不适合该机。
- Next AI should know: 后续讨论旧路线时，应以这版日志为依据，而不是再猜。
- User approval required before: 把 DIAG 当功能包继续安装使用。
- Test result: 用户提供诊断日志；日志结论明确，旧路线判死。

### magisk v1.2.0-force
- Date: 2026-04-18
- AI involved: Chatgpt 5.4(web)
- Changed: 放弃 overlay，尝试前台检测 `com.miui.home` 后强制旋转的 workaround 方案；进入桌面时切横，离开桌面时恢复。
- Fixed: 提供了一个不依赖 overlay 的“能直接试”的临时方案。
- Still broken: 本质上还是碰系统级旋转状态，无法做到只影响桌面。
- New bugs: 用户反馈 **打开其他应用也会被横屏锁定**，副作用严重，不可用。
- Risk / Notes: 这版证明“纯 Magisk 外围控制旋转”不符合需求，即使能转也会污染其他 app。
- Next AI should know: 不要再回到这种前台检测 + 全局旋转状态控制的思路。
- User approval required before: 再次尝试任何全局/半全局旋转 workaround。
- Test result: 用户实机判定彻底失败。

### chatgpt-5.4(web)Magisk的路线失败，并转向Lsposed
- Date: 2026-04-18
- AI involved: Chatgpt 5.4(web)
- Changed: 除了上面几个试验模块外，还做过旧路线的日志阅读、失败归因、方案筛选、给 Claude / Codex 的交接压缩；明确指出过：
  1. `DeviceConfig` 改对不等于桌面 View/命中区域自动重建；
  2. 纯 Magisk + overlay/service 路线在该机型上不兼容；
  3. 纯 Magisk 外围旋转 workaround 会污染其他 app。
- Fixed: 主要修的是“认知错误”，不是产品功能。
- Still broken: 旧路线整体仍然失败。
- New bugs: 无。
- Risk / Notes: 这一段只属于历史交接，不属于当前 v4 主线。
- Next AI should know: 本 AI 参与的内容全部属于 v3/v4 之前的早期试错与诊断，不要混入当前 overlay 独立桌面主线。
- User approval required before: 把这些旧方案恢复成主路线继续开发。
- Test result: 结论全部已体现在后续路线放弃和文档交接中。

> 这一整段属于Lsposed模块V1.0-V2.0,如需知道目前技术路线，请直接跳转V4.0（AI编辑阅读请无视）。

### lsp-source v0.1.0
- Date: 2026-04-19
- AI involved: Chatgpt 5.4(web)
- Changed: 产出第一套 LSPosed 模块源码工程草案，目标包 `com.miui.home`，补了 `AndroidManifest.xml`、`assets/xposed_init`、模块入口和若干候选 Hook 思路，用于从 Magisk 路线转向进程内 Hook 路线。
- Fixed: 完成了从“外围 Magisk 方案”转向“LSPosed 进程内逻辑修改”的架构切换准备。
- Still broken: 仅为源码工程，不是已验证可用的成品 APK；未在用户真机上完成闭环验证。
- New bugs: 无已确认新增 bug。
- Risk / Notes: 这版更多是过渡工程和思路支架，不应被当成稳定基线。
- Next AI should know: 这版的价值在于转路线，不在于功能成熟度。
- User approval required before: 直接把这版源码当可交付模块继续扩展。
- Test result: 仅生成源码工程，未形成可靠实机结论。

###### 历代模型（LSPosed 模块 v1.0 ~ v1.5 失败探索期）

### v1.0
- Date: 2026-04-19
- AI involved: Claude Sonnet 4.6 (web)
- Changed: 第一个真正的 LSPosed 模块实现。**为什么用 LSP 构建：** Magisk overlay / service 路线已证明在用户机型上不兼容（`cmd overlay unavailable` / transaction failed），且只能改资源不能改运行时行为。MIUI Home 的横屏布局计算在 `DeviceConfig.calcGridSize` / `loadCellsCountConfig` 等 Java 方法里，字段如 `sCellCountX/Y`、`sLauncherDatabaseName` 需要在方法执行时动态修改，**LSPosed（Xposed 框架现代版）是唯一能在进程内 hook Java 方法、改参数/返回值/字段的技术路线**。v1.0 尝试用 Magisk overlay 改资源 + LSP hook `Activity.setRequestedOrientation` 前台强制横屏的混合方案。
- Fixed: 证明了 LSP hook 技术可行，模块能加载进 `com.miui.home` 进程。
- Still broken: 前台强制横屏不解决布局计算；仍然是 4×6 硬塞横屏，图标挤压。
- New bugs: 无。
- Risk / Notes: 这是 LSP 路线第一版，证明方向可行但方案不对。
- Next AI should know: v1.0 最重要的是确立了"LSP hook Java 方法"这条主线。
- User approval required before: 回到纯 Magisk 路线。
- Test result: LSP 模块加载成功，但功能失败。

### v1.1 ~ v1.2
- Date: 2026-04-19
- AI involved: Claude Sonnet 4.6 (web)
- Changed: 继续前台强制横屏路线，尝试不同的 orientation 值（`FULL_USER` / `SENSOR_LANDSCAPE`）和 hook 时机。
- Fixed: 无。
- Still broken: 布局仍是 4×6，强制横屏只改了方向不改网格。
- New bugs: 无。
- Risk / Notes: 这两版只是参数微调，方向本身就是错的。
- Next AI should know: 跳过，无关键价值。
- User approval required before: —
- Test result: 失败。

### v1.3 ~ v1.4
- Date: 2026-04-19
- AI involved: Claude Sonnet 4.6 (web)
- Changed: 尝试全局改 `IS_TABLET` 常量 + 篡改 `Configuration` 对象，让 MIUI Home 以为自己在平板上跑。
- Fixed: 无。
- Still broken: `IS_TABLET` 影响太多其他逻辑（状态栏、导航栏、锁屏），破坏副作用大；`Configuration` 篡改不稳定。
- New bugs: 其他 MIUI 组件行为异常。
- Risk / Notes: 全局常量篡改是错误方向。
- Next AI should know: 不要动全局配置，只改目标方法。
- User approval required before: 恢复 `IS_TABLET` / `Configuration` 篡改。
- Test result: 失败，副作用太大。

### v1.5
- Date: 2026-04-19
- AI involved: Claude Sonnet 4.6 (web)
- Changed: 首次尝试改 `DeviceConfig` 字段：`USER_LANDSCAPE` 硬锁 + hook `calcGridSize` afterHook 对调 `sCellCountX/Y`。
- Fixed: 首次触碰到了正确的 hook 点（`DeviceConfig`），但时机不对。
- Still broken: `calcGridSize` afterHook 在 `sLauncherDatabaseName = getDatabaseNameBySuffix(...)` **之后**才执行，db 名已经按旧网格赋值完了，对调字段不刷新 db 名。
- New bugs: 无。
- Risk / Notes: 方向对了（改 `DeviceConfig`），但 hook 时机错了。
- Next AI should know: v1.5 是重要节点——首次发现 `calcGridSize` 方法体里有 db 名赋值，afterHook 太晚了，需要改钩 `loadCellsCountConfig`（在 `calcGridSize` 方法体内部被调，时机更早）。
- User approval required before: —
- Test result: `sCellCountX/Y` 改成功了，但 db 名还是 `launcher4x6.db`。

### v1.6.0-DIAG
- Date: 2026-04-20
- AI involved: Claude Opus 4.7 (web)
- Changed: 纯诊断版，加了大量 `XposedBridge.log` 输出，hook 了 `DeviceConfig.calcGridSize` / `loadCellsCountConfig` / `loadScreenSize` / `confirmCellsCount` 用于读字段，**不修改任何字段**。
- Fixed: —（诊断版无修复）
- Still broken: 一切。这版只为收集真机日志。
- New bugs: 无。
- Risk / Notes: 仅观察。
- Next AI should know: 这版日志是后续所有数据层修复的基线证据；当时确认了：横屏下 `sCellCountX/Y` 仍是 `4/6`、`sCellVerticalSpacing=0`、`sFolderCellHeight=105`、横屏 dimen 资源里 `workspace_cell_vertical_spacing_navigation` 直接给 0。
- User approval required before: 把 DIAG 当作生产版安装。
- Test result: 用户提交了 `landscape_log.txt` 实机日志。

### v1.7.0
- Date: 2026-04-20
- AI involved: Claude Opus 4.7 (web)
- Changed: 在 `loadCellsCountConfig` afterHook 里横屏对调成 `sCellCountX=6 / sCellCountY=4`。
- Fixed: 横屏数据库切到 `launcher6x4.db`（首次实证 MIUI 按 grid 名分库的机制）。
- Still broken: 6×4 太密，用户改要求为 6×3。
- New bugs: 无。
- Risk / Notes: 第一次改静态字段，无重入挡板。
- Next AI should know: 这版证明了"只改 `sCellCountX/Y`，sLauncherDatabaseName 会自动跟着切"这条最关键的机制。
- User approval required before: —
- Test result: 切换 db 名生效，但用户视觉觉得拥挤。

### v1.7.5-beta-6x3A（Chatpgt 5.4 （web）） 
- Date: 2026-04-20
- AI involved: ChatGPT 5.4(web) / 早期网页对话线（待你自己最终确认）
- Changed: 首次明确尝试把横屏目标从默认逻辑改成 6×3，目标是让横屏 grid 变为 6 列 3 行。
- Fixed: 提出了“6×3 横屏目标”这个版本分支。
- Still broken: 实机日志显示 6×3 被错误回退成 6×6，并且 vSpace=0、folderH=105，说明当时只是“想做 6×3”，实际没有成功。
- New bugs: 由于 clamp / fallback 逻辑，横屏目标被错误抬成 6×6。
- Risk / Notes: 这是“目标写成 6×3，但运行结果不是 6×3”的早期典型版本。
- Next AI should know: 不要把这个版本当成“6×3 已经成功”的基线；它更像是“第一次提出 6×3，但实测失败”的版本。
- User approval required before: 把这版当成功版本继续叠改。
- Test result: ❌ 实机日志显示 grid=6x6，不是目标 6x3

### v1.7.5-beta-fixed（Chatpgt 5.4 （web）） (来自v1.7.5-beta-6x3A版本)
- Date: 2026-04-20
- AI involved: ChatGPT 5.4(web) 
- Changed: 从文件名看，这是 `v1.7.5-beta-6x3` 之后的一个修正版，推测是在继续修 6×3 回退成 6×6 的问题。
- Fixed: Unknown / not enough evidence
- Still broken: Unknown / not enough evidence
- New bugs: Unknown / not enough evidence
- Risk / Notes: 当前只有文件名证据，没有可靠日志 / 源码 / 截图，不能写成“已明确修好什么”。
- Next AI should know: 这是存在过的修正版 zip，但具体修了什么需要结合源码或当时测试记录再判断。
- User approval required before: 把这个版本写成“已确认修复成功”的节点。
- Test result: Unknown / not enough evidence

### v1.7.5-beta-6x3B (Claude Opus 4.7 (web))(来自v1.7.5-beta-fixed版本)
- Date: 2026-04-20
- AI involved: Claude Opus 4.7 (web)
- Changed: 目标改 6×3。`decideLandscapeGrid` 用 `clamp(target, min, max)` 套约束。`confirmCellsCount` 横屏直接 `param.setResult(null)` 跳过整个方法防止 SP 污染。
- Fixed: 引入 6×3 目标。
- Still broken: 实测变成 **6×6**。根因：MIUI 的 `getCellCountYMin` 对竖屏有硬下限 6，`clamp(3, 6, 7)=6` 把目标被 minY 抬起，触发 fallback → 6×6。`confirmCellsCount` 全跳过会跳过数据库迁移/ItemInfo 加载副作用。
- New bugs: 6×3 → 6×6 错误回退；`confirmCellsCount` 副作用被吞。
- Risk / Notes: clamp 用错了——竖屏的 min 不能套到横屏。
- Next AI should know: 不要让 minY 影响横屏目标行数；不要用 `setResult(null)` 暴力跳过 `confirmCellsCount`，应该用"临时换值再恢复"的方式保护 SP。
- User approval required before: 重新引入 minY 约束。
- Test result: 实机日志显示 `final: grid=6x6 vSpace=0 folderH=105`，和目标差 3 列。

### v1.7.6a-maxonly （来自v1.7.5-beta-6x3B版本）
- Date: 2026-04-20
- AI involved: ChatGPT 5.4(web) 
- Changed: 从文件名看，这版大概率是在尝试“只用 max 约束，不用 min 约束”来修 6×3 被错误夹成 6×6 的问题。
- Fixed: 方向上很可能是在修 `6×3 → 6×6` 的错误回退。
- Still broken: Unknown / not enough evidence
- New bugs: Unknown / not enough evidence
- Risk / Notes: 命名和后面 `v1.7.7` 里“忽略 minY、只看 maxX/maxY”的修正思路是对得上的，但当前没有独立源码/日志证据，不要写死。
- Next AI should know: 可以把它视为“max-only 试探版”，但不能当作已验证成功的基线。
- User approval required before: 把这个版本写成“完全修好 6×3”的版本。
- Test result: Unknown / not enough evidence

### v1.7.6 (v1.7.5-beta-6x3B版本)
- Date: 2026-04-20
- AI involved: Claude Opus 4.7 (web)
- Changed: `confirmCellsCount` 改成 before/after"临时换值再恢复"方案——before 时把 `sCellCountX/Y` 临时写成竖屏默认值，让原方法把竖屏值写进 SP；after 时再恢复成 6×3。Activity lifecycle hook 由"全量 hook `Activity.class`" 收紧到"精确 hook `com.miui.home.launcher.Launcher`"。新增 `onConfigurationChanged` afterHook 主动触发 `calcGridSize`。`tryGetMax` 失败时返回 null 不对调（不再用硬编码默认值）。
- Fixed: SP 污染（临时换值方案）；lifecycle 开销大（精确 hook）。
- Still broken: `decideLandscapeGrid` 仍然 `clamp(3, minY=6, maxY=7) = 6`，6×3 还是被回退成 6×6。
- New bugs: 无。
- Risk / Notes: minY 的根因没解决。
- Next AI should know: minY 在横屏路径里必须忽略。
- User approval required before: —
- Test result: SP 不再污染，但 grid 仍是 6×6。

### v1.7.7
- Date: 2026-04-20
- AI involved: Claude Opus 4.7 (web)
- Changed: `decideLandscapeGrid` **彻底忽略 minY**（竖屏约束对横屏没意义）；只用 maxX/maxY 做越界保护。补刀点从 `loadCellsCountConfig` afterHook 移到 `calcGridSize` afterHook（因为 `calcGridSize` 方法体里 `loadCellsCountConfig` 之后还会继续覆盖 `sCellVerticalSpacing` / `sFolderCellHeight`，必须在外层 afterHook 才能压住）。强制写 `sCellVerticalSpacing >= 11`（横屏 dimen 是 0 会挤爆）。`sFolderCellHeight = round(sFolderWorkingHeight / sCellCountY)` 主动重算。`triggerProfileRecalc` 让 `mActiveProfile.calculateCellSize` 用 6×3 重算 cell 像素。
- Fixed: 6×3 真正落地（实机日志：`final: grid=6x3 vSpace=11 folderH=209`）；db 名实测切到 `launcher6x3.db`；竖屏切回 `launcher4x6.db`；SP 不污染。**DeviceConfig 数据层至此完全正确。**
- Still broken: 用户报告 UI 还是乱（图标重叠、第三页滑不动、文件夹内容丢失、点击区域错位）。这不是数据层 bug，是 View 层 / 数据库层的更深问题。
- New bugs: 无新增（v1.7.7 自身没有数据层 bug）。
- Risk / Notes: 此时项目已经从"调参数"变成"调内部状态机"。
- Next AI should know: **数据层完全正确不等于桌面正常**——CellLayout 缓存了旧 count、Workspace scrollRange 不刷新、launcher6x3.db 实际是从 launcher4x6.db 克隆来的（v2.0.x 才发现）。
- User approval required before: 把 v1.7.7 当稳定基线。
- Test result: 实机日志 6×3 全链路正确；视觉/交互仍然异常。

### v1.8.0
- Date: 2026-04-21
- AI involved: Claude Opus 4.7 (web)
- Changed: **错误方向尝试**——开始改 View 层。新增 `Launcher.onIdpChanged` / `reloadSearchBarIfNeed` afterHook、`CellLayout.onMeasure/onLayout` 注入 mCountX/mCountY。
- Fixed: 试图刷新 CellLayout 内部缓存。
- Still broken: 引入了点击区域错位、第三页滑不过去等 View 层新问题。
- New bugs: View 层注入扩散。
- Risk / Notes: 这是事故链的开端。
- Next AI should know: **不要碰 CellLayout / Workspace 这层 View 层 hook**；所有"修补 MIUI 内部状态机"的尝试都会引发更深的 bug。
- User approval required before: 重新引入 View 层 hook。
- Test result: 用户报告点击错位、分页错乱。

### v1.8.1-recreate-seed / v1.8.2-bindscreens / v1.8.3-modelreload
- Date: 2026-04-21
- AI involved: ChatGPT 5.4(web)
- Changed: 这三个版本属于同一阶段的连续尝试，核心方向是通过 `recreate`、`bindScreens`、`model reload` 一类方法，强行刷新 MIUI Home 的页面绑定、布局状态和模型重载，试图修复横屏分页、页面数量、图标位置和命中区域不同步的问题。
- Fixed: 试图从“重建页面 / 重绑 screens / 重载 model”这条线强行把横屏布局刷新正确。
- Still broken: 这条路线没有把问题真正修好，后续 UI 错乱、分页异常、点击错位、页面混排等问题依旧存在。
- New bugs: 这类强刷 / 重绑 / 重载路线本身就是高风险源，容易引入第二页跑到第一页、第三页异常、页面混排、数据库写脏、竖屏污染等更严重后果。
- Risk / Notes: 这三个版本可以视为同一条错误路线的连续试验版，不需要分开当成三个独立稳定节点看待。
- Next AI should know: `recreate` / `bindScreens` / `model reload` 这一整条路后来都被证明不该恢复；如果再看到类似思路，应直接视为高风险旧路线。
- User approval required before: 恢复任何 `recreate`、`bindScreens`、`removeScreens`、`model reload`、`startLoader`、`force reload` 相关逻辑。
- Test result: ❌ 没有成为稳定修复方案，后续事故链反而继续扩大。

### v1.9.0
- Date: 2026-04-21
- AI involved: Claude Opus 4.7 (web)
- Changed: View 层注入加剧——`CellLayout.onMeasure/onLayout` 同步 cellWidth/cellHeight；`Workspace.onMeasure` afterHook post `scrollTo` 修正 scrollRange；`onConfigurationChanged` 全局 rebind。
- Fixed: 试图修第三页滑动。
- Still broken: 图标重叠、双重图标、文件夹内容丢失、上半可点下半不可点。问题更严重。
- New bugs: 多页混排；FolderCellLayout 也被误注入；竖屏数据库被污染（用户报告"卸载模块后竖屏依然乱"）。
- Risk / Notes: 这是事故链的高峰。
- Next AI should know: 任何 `bindScreens` 前 `removeAllScreens`、任何对 `CellLayout.mCountX/Y` 不加方向守卫的注入都是地雷。
- User approval required before: 任何 View 层 hook 改动。
- Test result: 用户开始报告"竖屏也被污染了"——卸载模块后竖屏图标位置仍然错乱。

### v1.9.1
- Date: 2026-04-21
- AI involved: Claude Opus 4.7 (web)
- Changed: 紧急修复 v1.9.0 引入的事故。CellLayout 注入加双层守卫（必须横屏 + 必须是 Workspace 直接子 View，排除 FolderCellLayout）；删除 `bindScreens` hook 整个；删除 `onIdpChanged` / `reloadSearchBarIfNeed` / 任何 model reload 链；删除 `onConfigurationChanged` 里的 requestLayout 全局调用。
- Fixed: View 层注入扩散；FolderCellLayout 被污染；竖屏数据库被持续污染。
- Still broken: View 层路线整体方向就是错的。
- New bugs: 无新增；但事故已经把竖屏 launcher4x6.db 写脏了（用户数据残留），这是模块卸载也救不回来的——只能用户手动整理或恢复默认布局。
- Risk / Notes: 这是事故止血版。
- Next AI should know: View 层路线必须放弃；下一步必须想新方向。
- User approval required before: 重新引入任何 CellLayout/Workspace 层 hook。
- Test result: 症状改善但未完全恢复；用户已经决定放弃自动迁移路线，转向"双桌面"。

### v2.0.0
- Date: 2026-04-22
- AI involved: Claude Opus 4.7 (web)
- Changed: 路线大转向——放弃"4×6 → 6×3 自动迁移"主方案，改为"双桌面"：横屏独立 `launcher6x3.db`，首次进入空白桌面，用户自己拖图标。**全删 View 层 hook**，只保留 6 个 DeviceConfig 数据层 hook：`isRotatable` / `loadCellsCountConfig` / `calcGridSize` / `confirmCellsCount` / `loadScreenSize` / `setRequestedOrientation`。基于**错误假设**："MIUI 的 `tryToMigrateDefaultDatabase` 会用 default_workspace.xml 初始化新库"。
- Fixed: View 层事故链全部清掉。
- Still broken: 双桌面假独立——后续证据（platform-tools.zip）显示 launcher6x3.db 实际是 launcher4x6.db 的字节级克隆。
- New bugs: 因为基于错误假设，触发了 MIUI `tryToMigrateDefaultDatabase` 的真实行为——把竖屏 db 整库 rename/copy 成横屏名，竖屏 db 被吃掉。
- Risk / Notes: 这版从代码看"很干净"，但实际上把问题从 View 层推到了数据库层。
- Next AI should know: 不能假定 MIUI 会自己生成空模板；必须主动拦截 `tryToMigrateDefaultDatabase`。
- User approval required before: 把 v2.0.0 当作稳定基线。
- Test result: 数据层日志正确，但用户后来 dump 数据库发现两个 db MD5 完全相同。

### v2.0.1
- Date: 2026-04-22
- AI involved: Claude Opus 4.7 (web)
- Changed: 基于代码审查的清理版。删除 `hookLauncherLifecycle` 整块（冗余）；删除 `loadCellsCountConfig` 里的自调用 + `inSelfCall` 挡板；删除 `inSelfCall` / `inCalcGridGuard` / `lifecycleHooked` 三个 AtomicBoolean；删除 `calcGridSize` 里的"双保险对调"死代码；`triggerProfileRecalc` 改成只调 `mActiveProfile`（不再同时碰 portraitProfile）；`setRequestedOrientation` 收紧到 Launcher 活动；所有 DeviceConfig afterHook 第一行 `if (!isLandscape(ctx)) return;`。代码瘦到 367 行。
- Fixed: 把 v2.0.0 残留的 1.x 风格挡板/重入逻辑全部清掉；Hook 数量从 7 降到 6；3 个 AtomicBoolean 全删。
- Still broken: 仍然继承 v2.0.0 的根本问题——双桌面是假独立。
- New bugs: 无新增。
- Risk / Notes: 本地存在 `MiuiHomeLandscape_v2.0.1.zip` 和 `v2.0.1_audit_clean_MiuiHomeLandscapeModule.java`（已收进 HANDOFF zip）。
- Next AI should know: v2.0.1 代码审查是干净的，但**底层数据库假象**没修。
- User approval required before: 用 v2.0.1 作为新主线。
- Test result: 本地构建 zip 已生成；真机继承 v2.0.0 的双桌面假象问题。

### v2.1.0（A）
- Date: 2026-04-22
- AI involved: Claude Opus 4.7 (web)
- Changed: 真双数据库隔离尝试。新增 hook `tryToMigrateDefaultDatabase` beforeHook，横屏时 `param.setResult(null)` 跳过整个方法（让 MIUI 不再吃竖屏 db）。新增 `cleanupIfClonedDb()` 启动一次性清理：检查 launcher6x3.db 是否为 launcher4x6.db 的克隆（同大小 + 前 4KB 字节相同），如果是则删掉（连 journal/wal/shm 一起）。如果竖屏主文件已被吃掉（`launcher4x6.db` 主文件不在但 journal 在），保留 `launcher6x3.db` 不动避免删用户数据。候选方法名列表：`tryToMigrateDefaultDatabase` / `migrateDefaultDatabase` / `tryMigrateDefaultDatabase`，三个都试 hook。
- Fixed: 理论上阻止了 MIUI 的"竖屏 db 被吃"行为；启动时清理克隆的横屏 db。
- Still broken: 没有真机回归证据。后续 v3 路线放弃了"改 MIUI 原生 launcher DB"的方向，所以这版的修复方案没有被用户实测验证过。
- New bugs: Unknown / not enough evidence；如果 `tryToMigrateDefaultDatabase` 在 ROM 里被混淆改名，hook 会失效（但不造成事故，最坏情况退化成 v2.0.x 行为）。
- Risk / Notes: 这是 v1/v2 旧路线的最后一版；**之后用户决定彻底转向 v3 独立 overlay 方案，本路线不再继续**。launcher6x3.db 路径直接在 hook 类里 hardcode（适配 11T Pro / xiaomi.eu MIUI 14.0.5.0）。
- Next AI should know: v2.1.0 的两个核心思路（拦截 `tryToMigrateDefaultDatabase` + 启动检查 db 克隆）是早期路线下唯一可能让"双 launcher db 真独立"的方案；但既然主线已经放弃改 MIUI 数据库，这版只作为"如果未来还想走原生 launcher DB 路线，必须先拦这两个东西"的踩坑记录。
- User approval required before: 恢复 v1/v2 路线作为主线；任何"读写 MIUI Home 的 launcher4x6.db / launcher6x3.db"代码。
- Test result: 本地有 `MiuiHomeLandscape_v2.1.0.zip`、`v2.1.0_latest_MiuiHomeLandscapeModule.java`；真机回归未做。

### MiuiHomeLandscape_HANDOFF_for_Codex.zip（2.1.0交接包）
- Date: 2026-04-22
- AI involved: Claude Opus 4.7 (web)
- Changed: v2.1.0 之后用户要求"总结所有版本和 bug 路径，发给 codex"。本会话产出 4 类内容打包到 zip：
  1. `PROJECT_HISTORY.md`（331 行）—— v1.0 ~ v2.1.0 全版本演进、每版 bug 根因、jadx 反编译关键发现、9 条已验证不能走的路线、8 条已验证正确的设计原则。
  2. `VERSION_BUGS_MATRIX.md` —— 17 种 bug × 9 个版本的出现/修复矩阵。
  3. `README_FOR_CODEX.md` —— 给 Codex 的入口文档。
  4. `source_snapshots/` —— v1.7.5 / v1.8.3 / v2.0.1 / v2.1.0 四个关键节点 Java 源码。
  5. `buildable_projects/` —— v2.0.1 / v2.1.0 完整可编译工程 zip。
  6. `evidence/` —— launcher4x6.db / launcher6x3.db（MD5 一致铁证）+ db_dir_after_bug 文本证据 + 实机日志。
- Fixed: 把"为什么不能改 DeviceConfig / 不能改 CellLayout / 不能依赖 MIUI 自己的 launcher DB"这套踩坑结论一次性整理完，方便后续 Codex/Claude Code 直接读历史不要重复踩坑。
- Still broken: 不影响 APK；仅文档。
- New bugs: 无。
- Risk / Notes: 总文件 118KB / 20 个文件。这是 Claude Opus 4.7 网页版在本项目的最后一份产出，之后主线转 v3。
- Next AI should know: **如果未来想搞清楚"为什么 v1/v2 路线被弃用"，直接读这个 zip 比读后续文档更省事**。所有铁证（MD5 一致的两个 db 文件 + 主文件被吃掉的目录列表）都在 evidence/ 里。
- User approval required before: 把 HANDOFF zip 内容当作可继续开发的代码基线（它是历史归档，不是新主线）。
- Test result: 文档归档；不影响 APK。

### v2.1.0（B）
- Date: 2026-04-22
- AI involved: Codex ChatGPT 5.4 
- Changed: 本地存在 `MiuiHomeLandscape_v2.1.0.zip`、`MiuiHomeLandscape_v2.1.0(1)` 和 `v2.1.0_latest_MiuiHomeLandscapeModule.java`。
- Fixed: Unknown / not enough evidence
- Still broken: 证据报告指出 `launcher6x3.db` 虽然存在，但只是 `launcher4x6.db` 的克隆，横屏库没有真正独立。
- New bugs: 横竖屏数据库互相迁移可能造成竖屏污染、页面重叠、文件夹异常。
- Risk / Notes: `miui_home_landscape_full_error_report_en_updated_from_zip.txt` 明确说 v2.0.x “dual desktop” claim 没有按预期工作；v2.1.0 仍属于这条高风险线。
- Next AI should know: 不要再把 `launcher6x3.db` 的存在当成横屏独立成功。
- User approval required before: 读取或写入 MIUI Home 原生 launcher 数据库、做自动迁移、恢复 6x3 原生横屏库方案。
- Test result: 有真实 DB 文件和错误报告；结论是旧 DB 路线不可靠。

### v2.1.1-proof
- Date: 2026-04-22
- AI involved: Codex ChatGPT 5.4
- Changed: 本地存在 `MiuiHomeLandscape_v2.1.1-proof` 目录，名称显示这是证明/验证性质版本。
- Fixed: Unknown / not enough evidence
- Still broken: 后续仍进入 v3 独立 overlay 重构，说明 v2.1.1-proof 没有成为可靠最终方案。
- New bugs: Unknown / not enough evidence
- Risk / Notes: “proof” 只代表当时尝试证明某个方向，不代表方向最终正确。
- Next AI should know: v2.1.1-proof 的主要价值是历史证据和踩坑记录。
- User approval required before: 以 proof 版本为基线恢复旧架构。
- Test result: Unknown / not enough evidence

### v1-2(Claude Opus 4.7 网页版补 v1.x~v2.1.0 早期路线，不影响 APK)（请无视，这是补充，只在文档显示没有影响apk）
- Date: 2026-04-25
- AI involved: Claude Opus 4.7 (**非** Claude Code 客户端）
- Changed: 在原 Version Log 头部插入 Claude Opus 4.7 网页版补充段（11 条新条目）：
  - `v1.6.0-DIAG`：纯诊断版，建立后续修复的基线证据。
  - `v1.7.0`：首次实证 MIUI 按 `sCellCountX/Y` 自动切 db 名机制。
  - `v1.7.5-beta-6x3`：clamp 用错 minY 导致 6×3 回退成 6×6。
  - `v1.7.6`：confirmCellsCount 改"临时换值再恢复"，lifecycle 收紧。
  - `v1.7.7`：忽略 minY，补刀点移到 calcGridSize；DeviceConfig 数据层至此完全正确。
  - `v1.8.0`：错误方向开端，开始改 View 层。
  - `v1.9.0`：View 层注入加剧，竖屏 db 被污染（事故链高峰）。
  - `v1.9.1`：紧急止血，删 View 层 hook；竖屏数据残留无法救回。
  - `v2.0.0`：路线大转向到双桌面，基于"MIUI 会自己生成空模板"的错误假设。
  - `v2.0.1`：代码审查清理，6 个 hook，367 行（原文档错标 Codex GPT-5.4，本次按本会话事实补正为 Claude Opus 4.7 web）。
  - `v2.1.0`：拦截 `tryToMigrateDefaultDatabase` + 启动清理克隆 db；v1/v2 路线最后一版（原文档错标 Codex GPT-5.4，本次同样补正）。
  - `MiuiHomeLandscape_HANDOFF_for_Codex.zip`：v2.1.0 之后给 Codex 的交接包，包含 4 类内容（PROJECT_HISTORY / VERSION_BUGS_MATRIX / README / source_snapshots / buildable_projects / evidence），118KB / 20 文件。
  原 Codex 维护的 v2.0.0 / v2.0.1 / v2.1.0 / v2.1.1-proof 四条旧条目**保留原貌作为审计痕迹**，加了分界说明"两段不冲突时以前段 Claude 直接产出事实为准"。当前版本号标记由 v4.0.30 → v4.0.30-doc-OC3。
- Fixed: 文档里 Claude Opus 4.7 网页版参与的早期 11 个版本之前没有记录或被错标；本次补全。
- Still broken: 仅文档；不影响 APK 功能；不修任何代码。Codex 主线（`v4.0.0-codex-fixed` / `v4.0.1` ~ `v4.0.27`）和 Claude Code 主线（`v4.0.0`(Claude 分支) / `v4.0.28` / `v4.0.29` / `v4.0.30` / `v4.0.30-doc-OC` / `v4.0.30-doc-OC2`）一字未动。
- New bugs: 无。
- Risk / Notes: 关于"非 Claude Code"——按文档第 30-32 行的命名规则，"带 code 为电脑（客户端），没有为网页"。本会话是网页版 Claude Opus 4.7。原文档已经存在的 `v3.0.0-beta16-reset_fix1/2/3` 标的"Claude (Opus 4.x，本会话)"和原 `v4.0.30-doc-OC2` 标的"Claude（本会话）"——根据本会话历史这些是另一个 Claude 网页会话做的，不是本次修订的会话；本次修订未动那几条。本条目末尾的 OC3 尾号表示本次修订在 OC2 基础上叠加（OC2 已经是 Codex+Claude 联合归档），OC3 由 Claude 网页版独立产出。
- Next AI should know: 如果以后还要追溯 v1.x/v2.x 旧路线为什么被弃用，前面 Claude 段比下面 Codex 段更详细；下面那段（v2.0.0 / v2.0.1 / v2.1.0 / v2.1.1-proof 旧记录）有些字段是 Codex 仅凭文件名/zip 推断的，前面段是 Claude 直接产出代码的事实记录。
- User approval required before: 删除前面 Claude 段；恢复"v1/v2 路线"作为主线；改 OC3 尾号规则。
- Test result: 文档更新；不构建、不安装、不修改代码。

> 这一整段属于 v3 重构之前的旧路线（"改 MIUI 原生 DeviceConfig + 双 launcher DB"），结论是被 v3/v4 主线弃用、仅作为踩坑参考。如果未来主线有需要回看为什么"不能改 DeviceConfig / 不能依赖 MIUI 自己的 launcher DB / 不能动 CellLayout / Workspace / Folder"，对应理由都在这段历史里。
### v3.0.0-beta1
- Date: 2026-04-22 / 2026-04-23
- AI involved: Unknown / not enough evidence
- Changed: v3 线开始从旧的 MIUI 原生横屏、DeviceConfig、launcher 数据库路线切到独立 overlay 路线。目标是在 `com.miui.home` 上挂自定义横屏桌面，竖屏保持 MIUI 原版。
- Fixed: 明确关闭 v1/v2 的 DeviceConfig、launcher DB、自动迁移竖屏布局路线，避免继续污染竖屏桌面。
- Still broken: beta1 目标不是最终 8x2 分页，只是先跑通独立 overlay 链路。
- New bugs: Unknown / not enough evidence
- Risk / Notes: `MiuiHomeLandscape_v3.0.0-beta1-CONTEXT-for-Codex.md` 明确写过：只要依赖 MIUI 自己的两个 DB，就有数据污染风险。
- Next AI should know: 这个版本的有效结论是“横屏独立、竖屏原版、不要碰 MIUI launcher DB”。不要把旧 DB 路线当可恢复基线。
- User approval required before: 恢复 DeviceConfig、LauncherProvider、SQLiteOpenHelper、tryToMigrateDefaultDatabase、Workspace、CellLayout、HotSeats 等原生改造路线。
- Test result: Unknown / not enough evidence

### v3.0.0-beta1-4x2
- Date: 2026-04-23
- AI involved: Codex Gpt5.4
- Changed: 以单页 4x2 主区域和 4 格 Dock 做稳定性原型；空位不应做成调试网格；视觉目标是干净的横屏桌面。
- Fixed: 比早期 6x3 / 分页 overlay 更保守，减少分页、命中、拖拽复杂度。
- Still broken: 容量太小，不满足用户后续“横屏一页显示更多 app”的目标。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 文档里当时选择 4x2 是为了先稳定，不是最终产品目标。
- Next AI should know: 4x2 是踩刹车版本，不应作为最终布局基线。
- User approval required before: 回退到 4x2、5x2、6x2 低密度布局或取消分页目标。
- Test result: 有 `MiuiHomeLandscape_v3.0.0-beta1-4x2.zip`、截图和上下文文档，但完整真机结论 Unknown / not enough evidence

### v3.0.0-beta2-15
- Date: 2026-04-23
- AI involved: Codex Gpt5.4
- Changed: 此版本为Codex Gpt5.4自动安装测试版本。
- Fixed: Unknown。
- Still broken: Unknown。
- New bugs: Unknown / not enough evidence
- Risk / Notes: Unknown。
- Next AI should know: Unknown。
- User approval required before: Unknown。
- Test result: Unknown

### v3.0.0-beta16-reset
- Date: 2026-04-23 / 2026-04-24
- AI involved: Codex GPT5.4 
- Changed: 重新转向 8x2 分页模型，使用模块自己的 SQLite：`miui_home_landscape_overlay.db`，grid 和 Dock 分离，横屏只导入 launcher 可见 app，不复制竖屏坐标。
- Fixed: 识别假 8x2 的关键根因：旧 `HorizontalScrollView + LinearLayout + GridLayout` 页宽沿用错误测量值，导致下一页 app 看得到但点不到、第 3/4 页异常。
- Still broken: 交接文档明确说真机回归未验证，需要 Codex 打包、安装、抓日志。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 不能再把 `LandscapePagedGridView` 换回旧 `HorizontalScrollView` 方案。
- Next AI should know: 8x2 必须是真 page model、真 hit-test、真 drag/drop，不是视觉上摆 8 个图标。
- User approval required before: 恢复 MIUI launcher DB、DeviceConfig 或 HorizontalScrollView 旧分页方案。
- Test result: 源码层交接，真机结论 Unknown / not enough evidence

### v3.0.0-beta16-reset_fix1
- Date: 2026-04-24
- AI involved:Claude Opus 4.7
- Changed: 在 beta16-reset 源码上做的第一轮真机回归修复。补全 `LandscapeController` 上对 `LandscapePagedGridView.Listener` 第三个回调 `onEmptySlotLongPress()` 的实现（之前只实现了 2/3 导致编译失败）。在 `refreshOverlay()` 里加 first-landscape seed：当 orientation==LANDSCAPE 且 `store.maxAbsoluteIndexPlusOne()==0` 时，调用 `seedGridFromInstalledApps(store)` 把 `LauncherApps#getActivityList` 枚举到的 launcher 可见 app 顺序写进 grid，避免"第一次进横屏一片空白"。
- Fixed: 编译断裂；first-landscape 空白 grid。
- Still broken: 用户实测仍然是视觉 4×2，而不是 8×2；overlay 父容器还挂在 `screen_content`（继承 MIUI 竖屏宽度 1080px）。
- New bugs: 无新增。
- Risk / Notes: fix1 只动了 Listener 完整性和首次播种；page-width 根因没碰。
- Next AI should know: fix1 不是输入模型修复，是先把"能跑"修通。视觉/输入仍然假 8×2。
- User approval required before: 把 fix1 当稳定基线继续开发。
- Test result: 用户真机回归不通过，触发 fix2。

### v3.0.0-beta16-reset_fix2-8x2-independent
- Date: 2026-04-24
- AI involved: Claude Opus 4.7
- Changed: 针对 fix1 真机回归不过的根因修复 —— overlay 之前挂在 `screen_content`（MIUI 给竖屏 Workspace 准备的容器，横屏不会重算尺寸 → overlay 继承 1080px 宽，8 列被挤成视觉 4 列）。改成优先挂 `android.R.id.content`（Activity 的 contentView FrameLayout，由 Window 按物理屏幕尺寸管理），fallback `drag_layer`，`screen_content` 永久弃用。`LandscapeOverlayView.onMeasure` 加 DisplayMetrics 兜底：当父给的 widthSpec < 物理横屏宽度的 98% 时强制覆写。`LandscapePagedGridView` 改成自定义 `ViewGroup` 自管 `onMeasure`/`onLayout`，每页严格 = `getWidth()`，加 `Scroller` 吸附翻页。`onConfigurationChanged`/`onResume` 里 `refreshOverlay()` 都 post 到下一帧避免拿到陈旧 width。
- Fixed: overlay 继承竖屏宽度问题；page width != viewport width 问题。
- Still broken: 用户实测照片显示页 1 右半屏漏出页 2 应用、第三行溢出到 dock。视觉看起来"画"对了一些，但**输入模型（长按拖拽落点）仍是 4×?**。
- New bugs: 无新增。
- Risk / Notes: fix2 只把布局骨架修对；Pager 内 PageView 仍 `setClipChildren(false)`，cell 可画出 rect。父布局还是 LinearLayout+weight，软几何容易被父 clip 夹小。
- Next AI should know: fix2 是布局修，不是输入模型修；触摸/长按还在走原生 MIUI 路径。
- User approval required before: 把 fix2 当稳定基线。
- Test result: 用户真机回归不通过，发了带红/橙标注的失败照片，触发 fix3。

### v3.0.0-beta16-reset_fix3-hardzoned-8x2
- Date: 2026-04-24
- AI involved: Claude Opus 4.7
- Changed: 针对 fix2 视觉 bleed 的最后一轮"画面"修复。`LandscapeOverlayView` 从 `LinearLayout(VERTICAL) + grid(weight=1) + dock(fixed)` 彻底换成自实现 `ViewGroup`，`onMeasure`/`onLayout` 硬分两个矩形：`grid rect = (0, 0, W, H - dockH)`，`dock rect = (0, H - dockH, W, H)`，两块都强制 `MeasureSpec.EXACTLY`，全链路 `setClipChildren(true)` + `setClipToPadding(true)`。`LandscapePagedGridView.PageView` 把 `setClipChildren(false)` 翻成 `true`，堵死跨 cell 视觉漏出。
- Fixed: 7 条不变量里前 4 条（page width ≡ viewport width / page bounds 严格在 grid rect 内 / 每页只 layout `min(childCount,16)` / 第 17 个 app 走下一页）和第 7 条（rect 外画都被 clip）。
- Still broken: **用户实测后明确指出：长按拖动 app 时暴露出来的真实拖拽/命中模型仍然是 4×?，不是 8×2**。说明 fix3 修的还是"画面"层；底层 MIUI Workspace/DragLayer 仍在接事件。这条反馈是 v4.0 重构的直接触发器。
- New bugs: 无新增；但暴露"v3 链路里的所有 fix 都只在视觉层，输入模型从未被 overlay 接管"这个根本结构性问题。
- Risk / Notes: hardzoned 是 v3 的最后一次"补漏"，再叠补丁没意义；下一步必须重构事件链。
- Next AI should know: 不要再把这类问题当 UI 微调修；validation 必须靠 adb logcat，而不是截图。
- User approval required before: 把 fix3 当稳定基线；恢复 LinearLayout+weight 软布局。
- Test result: 用户真机不过；发了"现在请直接进入 v4.0 方向"的指令，要求重建输入模型。

> 以下为可正常运行的lsposed V4.0模块，并开始改进,但4.0.x-4.0.30多为修bug和增加功能有参考意义。

### v4(Claude 补 Claude 自己版本细节，不影响 APK)（请无视，这是补充，只在文档显示没有影响apk）
- Date: 2026-04-25
- AI involved: Claude Opus 4.7
- Changed: 按用户指示"补充你有关的细节，codex 负责编辑的版本不要动"补 4 条 Version Log 条目（之前都是 `Unknown / not enough evidence`，现在补成 Claude 在本会话能直接证伪的事实）：
  - `v3.0.0-beta16-reset_fix1`: 编译断裂修复 + first-landscape seed
  - `v3.0.0-beta16-reset_fix2-8x2-independent`: parent 从 `screen_content` 切到 `android.R.id.content` + DisplayMetrics 兜底 + Pager 自管 measure
  - `v3.0.0-beta16-reset_fix3-hardzoned-8x2`: Overlay 改自实现 ViewGroup 硬分区 + clipChildren 全开
  - `v4.0.0`(Claude 分支): `onTouchEvent return true` + nativeWorkspace/Hotseats GONE + DragLayer/ScreenContent NATIVE_BLOCKER + 5 类日志全链路
  并明确：本 Claude 的 v4.0.0 和 Codex 主线 `v4.0.0-codex-fixed → v4.0.30` 是平行分支，不要混。
- Fixed: 文档里 Claude 参与版本的"AI involved/Changed/Fixed/Still broken/Test result" 全部由 `Unknown` 补成实证。Codex 主线（`v4.0.0-codex-fixed` / `v4.0.1` ~ `v4.0.30` / `v4.0.30-doc-OC`）一字未动。
- Still broken: 文档归档；不影响 APK 功能；不修任何代码。
- New bugs: 无。
- Risk / Notes: 此条目仅是文档同步；未编译、未安装、未跑 adb。Claude v4.0.0 zip 在 `MiuiHomeLandscape_v4.0.0.zip`、配套验证文档在 `V4.0_VERIFY_INPUT_MODEL.md`。
- Next AI should know: 后续如果要把 Claude v4.0.0 那条分支并回主线，必须先和用户对齐 `nativeWorkspace=GONE` / `NATIVE_BLOCKER` 这两个改动是否与当前 v4.0.30 路线兼容（v4.0.29 修过 `setNativeGone(nativeRecentsContainer, false)` 引发的竖屏触摸事故，逻辑相似但 view 不同）。
- User approval required before: 把 Claude v4.0.0 替换主线；改文档尾号规则；改本条目本身。
- Test result: 文档更新；不构建、不安装、不修改代码。

### v4.0.0Alpha
- Date: 2026-04-24
- AI involved: Claude Opus 4.7（Web)
- Changed: 在 fix3 用户反馈"长按拖拽仍是 4×?"之后做的输入模型重构，不再调 UI。具体改动：
  1. `LandscapeOverlayView.onTouchEvent()` **一律 `return true`** —— 任何子 view 没消费的事件都留在 overlay，不再回落给底层 MIUI。覆盖所有 action（含 POINTER_DOWN 等非 DOWN/MOVE/UP/CANCEL 的兜底）。
  2. `LandscapeController.applyOrientation(land=true)`：原生 `nativeWorkspace` / `nativeHotseats` 从 `INVISIBLE` 改成 `View.GONE`（彻底退出 layout 与 hit-test）；`nativeDragLayer` / `nativeScreenContent` 不能 GONE（MIUI 自己会 NPE），改挂 `NATIVE_BLOCKER` `OnTouchListener`，吞所有 touch 并打 `[touch] BLOCKED on native ...`。
  3. 全链路 5 类日志：`[parent]` overlay parent 类名/可用 native view；`[touch] dispatch act=DOWN x=.. y=.. visible=true`（overlay.dispatchTouchEvent）；`[hit] down x=.. y=.. scrollX=.. viewportW=.. currentPage=N/M`（pager.onInterceptTouchEvent）；`[touch] click target=grid p=P s=S row=R col=C` / `[touch] longClick target=EMPTY p=.. s=..`（cell 级）；`[drag] start source=overlay/grid p=.. s=.. cellModel=8x2` + `[drag] pager ACTION_DRAG_STARTED ours=true`；`[drop] target=grid p=.. s=.. row=.. col=.. from=..`。bind 时打印 `page0 slot=0..15` 每个 cell 的真实 `(left,top,right,bottom)`，真机一眼判断 hit-test rect 是不是 8×2。
  4. 版本号同步更新：`app/build.gradle versionCode 50; versionName "4.0.0"`；`MiuiHomeLandscapeModule.VERSION = "4.0.0"`；Manifest 注释、README、`build_apk.sh` / `build_apk.bat` 产物名全部改 `MiuiHomeLandscape_v4.0.0.apk`。
- Fixed: v3 全链路只在视觉层接管，触摸/长按/拖拽实际仍走原生 MIUI 的根因 —— 用 `onTouchEvent return true` + native view GONE + DragLayer/ScreenContent touch blocker 三道防线把事件焊死在 overlay 内。同时建立"adb logcat 取证"的验证原则。
- Still broken: 此版本只有源码层证据；真机回归未做。Codex 在 `v4.0.0-codex-fixed` 节点报告过黑屏/app 消失/拖动失败 —— 但那是 Codex 主线分支，此处 Claude 版 v4.0.0 不直接对应。两条 v4.0.0 是平行线，不要混。
- New bugs: 未实测；`NATIVE_BLOCKER` 强行吞 touch 可能让 MIUI 内部某些非 touch-driven 状态机出现意外（待真机验证）。
- Risk / Notes: 配套文件 `MiuiHomeLandscape_v4.0.0.zip`、`V4.0_VERIFY_INPUT_MODEL.md`（adb 验证手册，含"必须看到的日志"和"看到这些 → 还没成功"对照表）；`README.md` 已重写 v4.0 验证步骤章节。注意：此版本是 Claude 单会话产出的源码包，没经过 Codex 的 `-codex-fixed` 修复链。
- Next AI should know: (a) v4 核心是输入模型，不是 UI；(b) 任何后续"修触摸/拖拽"的迭代不要回 v3 的 LinearLayout+weight 软布局；(c) 不要用截图判断成功，必须 `adb logcat -s Xposed` 抓 5 类标签的日志；(d) 此 v4.0.0 和 Codex 的 `v4.0.0-codex-fixed → v4.0.30` 是不同分支，主线已由 Codex 推进到 v4.0.30。
- User approval required before: 删除 overlay 输入接管；把 `nativeWorkspace`/`nativeHotseats` 从 GONE 改回 VISIBLE；移除 `NATIVE_BLOCKER`；以本 Claude v4.0.0 替换主线 v4.0.30。
- Test result: 仅源码层；未真机验证。

### v4.0.0-codex-fixed
- Date: 2026-04-24
- AI involved: Codex GPT5.5
- Changed: 构建产物显示 `MiuiHomeLandscape_v4.0.0-codex-fixed.apk` 和源码包；截图包含 `apps_restored`、`final`、`dbfix`、`noblack` 等线索。
- Fixed: 可能修复了早期 v4 的 app 消失、数据库空、黑底或显示恢复问题。
- Still broken: 后续版本继续大改布局、编辑、Dock、Recents，说明此版不是最终稳定态。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 文件名能说明修复方向，但不能证明所有交互已稳定。
- Next AI should know: 该版本可作为 v4.0.0 事故后的恢复节点参考，不要夸大稳定性。
- User approval required before: 回退到该版本作为长期主线。
- Test result: 有截图和 APK，完整真机结论 Unknown / not enough evidence

### v4.0.1
- Date: 2026-04-24
- AI involved: Codex GPT5.5
- Changed: 截图显示测试过 8x3 布局和菜单状态。
- Fixed: Unknown / not enough evidence
- Still broken: Unknown / not enough evidence
- New bugs: Unknown / not enough evidence
- Risk / Notes: 用户提出过可以往下移动变成 8x3，并增加移除 app 功能。
- Next AI should know: 8x3 是后续用户偏好尝试，不等于 v4 最初 8x2 目标。
- User approval required before: 在当前主线继续改变主区域行列数。
- Test result: Unknown / not enough evidence

### v4.0.2
- Date: 2026-04-24
- AI involved: Codex Codex GPT5.5
- Changed: 文件名显示 `8x3-edit-x`，开始处理长按编辑态和左上角 X 移除入口。
- Fixed: 横屏编辑态中加入或调整 X 移除按钮。
- Still broken: 用户后来指出 X 没在圆圈中心，圆圈和颜色样式需要改。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 删除语义必须是“从横屏移除”，不能卸载 app，不能影响竖屏。
- Next AI should know: 横屏 X 是 remove-from-landscape，不是 uninstall。
- User approval required before: 改成真正卸载或影响 MIUI 竖屏数据。
- Test result: 有 `current_phone_screen_v4_0_2_edit_x.png`，完整结论 Unknown / not enough evidence

### v4.0.3
- Date: 2026-04-24
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `8x3-remove-only`。
- Fixed: 横屏删除逻辑改成只从横屏 overlay 移除，app 保留在系统和竖屏桌面。
- Still broken: 后续仍要求移除前双重确认、X 样式修正、编辑态行为区分。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这是重要语义节点：删除按钮在横屏里叫“移除”。
- Next AI should know: 不要把横屏移除和系统卸载混在一起。
- User approval required before: 改横屏移除语义或接入系统卸载。
- Test result: 有截图，完整结论 Unknown / not enough evidence

### v4.0.4
- Date: 2026-04-24
- AI involved: Codex GPT5.5
- Changed: 截图显示检查过模块 APK UI 或模块激活状态。
- Fixed: 可能处理过 LSP 模块页面“模块未激活”显示问题，但证据不足。
- Still broken: Unknown / not enough evidence
- New bugs: Unknown / not enough evidence
- Risk / Notes: 只有截图证据，不能确认代码改动。
- Next AI should know: 模块激活状态显示如果再出现，需要读 APK UI 和 LSPosed 作用域状态，不要只看桌面。
- User approval required before: 大改模块 UI 或 LSPosed 检测逻辑。
- Test result: Unknown / not enough evidence

### v4.0.5
- Date: 2026-04-24
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `remove-confirm-add-ui`；截图包括模块页、横屏页、编辑徽标、移除确认弹窗。
- Fixed: 加入横屏移除确认流程，并继续处理“添加横屏”或相关 UI。
- Still broken: 用户后续要求双重确认、X 圆圈样式、单个 app 编辑行为进一步调整。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 确认弹窗不能阻断竖屏原版行为，也不能触发卸载。
- Next AI should know: 这是移除确认线的节点，不是文件夹或 Recents 线。
- User approval required before: 改移除确认层级、删除确认文案或真实删除行为。
- Test result: 有多张截图，完整结论 Unknown / not enough evidence

### v4.0.6
- Date: 2026-04-24
- AI involved: Codex GPT5.5
- Changed: 只看到 `current_phone_screen_v4_0_6_home.png` 证据。
- Fixed: Unknown / not enough evidence
- Still broken: Unknown / not enough evidence
- New bugs: Unknown / not enough evidence
- Risk / Notes: 证据不足，不应从版本号推断功能。
- Next AI should know: 需要源码包、日志或用户反馈才能定位该版本意义。
- User approval required before: 把 v4.0.6 当稳定节点回退。
- Test result: Unknown / not enough evidence

### v4.0.7
- Date: 2026-04-24
- AI involved: Codex GPT5.5
- Changed: 截图显示测试过 single edit。
- Fixed: 可能开始实现“长按一个 app 只影响那个 app”的编辑逻辑。
- Still broken: 后续仍需要空白长按进入全局编辑。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 单个 app 编辑和全局编辑必须分开，不能长按空白就冒出某个 app。
- Next AI should know: 用户明确希望长按一个 app 只影响该 app。
- User approval required before: 恢复长按任意位置都进入全局编辑。
- Test result: Unknown / not enough evidence

### v4.0.8
- Date: 2026-04-24 / 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `single-global-edit`，截图有 single edit 和 global edit。
- Fixed: 区分单个 app 编辑与全局编辑。
- Still broken: 空白长按触发时间后来被要求缩短到 3 秒，再到 2 秒。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 全局编辑不能被普通短按空白误触发。
- Next AI should know: 单个编辑和全局编辑是两个模式，不应混用状态。
- User approval required before: 改编辑模式触发规则。
- Test result: 有截图，完整结论 Unknown / not enough evidence

### v4.0.9
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 截图显示 `blank_3s_global`，窗口层级文件 `window_v409.xml` 存在。
- Fixed: 空白长按进入全局编辑调整到约 3 秒。
- Still broken: 用户后来要求简短到 2 秒。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 空白长按不能显示“某个 app”的编辑态，必须是全局编辑。
- Next AI should know: 如果空白处短按或误长按出现 app，是 bug。
- User approval required before: 改空白长按触发时长或行为。
- Test result: 有截图和窗口 XML，完整结论 Unknown / not enough evidence

### v4.0.10
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 截图显示 `dock_hidden` 测试。
- Fixed: 可能处理过 Dock 显示/隐藏或专属区域表现。
- Still broken: 后续用户强调 Dock 及那一栏必须是 Dock 专属空间，8x3 不能拖进 Dock 空间格子。
- New bugs: Unknown / not enough evidence
- Risk / Notes: Dock 是独立数据库和独立空间，不能和主区域坐标混用。
- Next AI should know: Dock 问题不要用主网格坐标补丁硬糊。
- User approval required before: 改 Dock 数据模型或 Dock 专属空间。
- Test result: Unknown / not enough evidence

### v4.0.11 / v4.0.12
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 主要为测试部分，这一版把数据库上下文改到 device-protected storage，目标是让横屏 Grid/Dock 独立库更稳定，不再频繁掉进内 存兜底。接下来我编译、安装、重启 MIUI Home，再确认原生 Dock 隐藏、模块 Dock 9 位、空白长按 3 秒和数据库读取状态。
- Fixed: Unknown / not enough evidence
- Still broken: Unknown / not enough evidence
- New bugs: Unknown / not enough evidence
- Risk / Notes: 当前目录未看到明确 v4.0.11 或 v4.0.12 APK/src zip 产物。
- Next AI should know: 不要编造这两个版本的功能。
- User approval required before: 把 v4.0.11 / v4.0.12 当作可回退节点。
- Test result: 衔接v4.0.13

### v4.0.13
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `dock9-kvstore`，有 Dock drop 测试截图。
- Fixed: 加强 Dock 独立数据模型，可能支持 9x1 理论容量或 KV 存储。
- Still broken: 后续仍需保证主区域不能拖进 Dock 专属空间，Dock app 可从主区域拖入。
- New bugs: Unknown / not enough evidence
- Risk / Notes: Dock 独立数据库是重要方向，但不能破坏主区域 grid 数据。
- Next AI should know: Dock 不是 8x3 的第四行，也不是主 grid 的扩展。
- User approval required before: 合并 Dock 和主 grid 坐标模型。
- Test result: 有 Dock drop 截图，完整结论 Unknown / not enough evidence

### v4.0.14
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `separation-guard`；截图包括 after recents 和 after app home。
- Fixed: 针对横屏进后台、进 Recents、打开 app 再回桌面后横竖分离失效的问题增加保护。
- Still broken: 用户后续仍报告 Recents/后台方向和渲染存在严重 bug。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 横屏禁用负一屏是防止横竖分离失效的重要条件。
- Next AI should know: 不能随意恢复横屏负一屏；用户已明确“横屏不禁用负一屏会触发横竖分离失效 bug”。
- User approval required before: 改负一屏禁用策略或 separation guard。
- Test result: 有截图，完整真机结论 Unknown / not enough evidence

### v4.0.15
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `drag-animation`。
- Fixed: 尝试让横屏拖动动画不那么僵硬，更接近 MIUI 原版体验。
- Still broken: 用户后续仍认为动画僵硬。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 动画应优先不影响 hit-test、drag/drop 数据一致性。
- Next AI should know: 动画是体验层，不要为了动画牺牲拖动正确性。
- User approval required before: 大改拖拽动画框架或接回 MIUI 原生拖拽。
- Test result: Unknown / not enough evidence

### v4.0.16
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `edge-autopage`。
- Fixed: 支持拖动 app 到屏幕左右边缘自动翻页，模拟 MIUI 原版拖动翻页体验。
- Still broken: Unknown / not enough evidence
- New bugs: Unknown / not enough evidence
- Risk / Notes: 自动翻页必须使用 overlay 自己的 page model，不能触发原生 Workspace 翻页。
- Next AI should know: 自动翻页和 hit-test/page index 必须同步。
- User approval required before: 改自动翻页阈值、时长或 page model。
- Test result: Unknown / not enough evidence

### v4.0.17
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `folders`，尝试横屏文件夹，拖动 app 到 app 上创建文件夹。
- Fixed: 开始补横屏没有 apk 文件夹的问题。
- Still broken: 用户后来明确“文件夹不要目前版本，必须和 MIUI 的逻辑一样，文件夹 app 不能移除”。
- New bugs: 文件夹逻辑不符合 MIUI 原版预期，具体表现 Unknown / not enough evidence
- Risk / Notes: 文件夹是高风险功能，不能半套实现。
- Next AI should know: 文件夹功能当前不应继续旧实现，必须重新对齐 MIUI 行为后再做。
- User approval required before: 继续文件夹功能、恢复 v4.0.17 文件夹逻辑、允许文件夹 app 被移除。
- Test result: Unknown / not enough evidence

### v4.0.18
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `insert-shift`。
- Fixed: 尝试实现拖动 app 到两个 app 中间时，后面的 app 自动后退一格，接近 MIUI 原版插入逻辑。
- Still broken: Unknown / not enough evidence
- New bugs: Unknown / not enough evidence
- Risk / Notes: 插入移位必须保持 page/slot 连续性，不能导致跨页穿帮或 Dock 混入。
- Next AI should know: 插入移位是主 grid 行为，不应影响 Dock 数据。
- User approval required before: 大改排序、换位、插入策略。
- Test result: Unknown / not enough evidence

### v4.0.19
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `nav-folder-render`。
- Fixed: 尝试处理横屏隐藏小白条、横屏打开 app 左侧黑色不渲染区域、文件夹渲染等方向。
- Still broken: 后续截图 `current_recents_bug.png` 显示 Recents 问题仍存在。
- New bugs: Recents/后台开始成为明显问题线，具体根因 Unknown / not enough evidence
- Risk / Notes: 隐藏小白条只应隐藏视觉，不应改变系统导航功能。
- Next AI should know: nav、folder、render、Recents 混在同一阶段，后续要拆开验证。
- User approval required before: 继续改系统导航条、文件夹或 app 渲染策略。
- Test result: 后续仍有 Recents bug 证据

### v4.0.20
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `recents-guard`。
- Fixed: 开始处理最近任务界面 overlay 污染问题，避免横屏桌面图标渲染到后台/Recents。
- Still broken: 后续继续 v4.0.21-v4.0.27 多版 Recents 修复，说明此版未彻底解决。
- New bugs: Unknown / not enough evidence
- Risk / Notes: Recents 是高风险区域，容易影响竖屏最近任务和横屏渲染。
- Next AI should know: 不要把 Recents guard 和桌面 separation guard 混为一谈。
- User approval required before: 大改 Recents hook 或隐藏原生 RecentsContainer。
- Test result: Unknown / not enough evidence

### v4.0.21
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `recents-landscape`。
- Fixed: 继续尝试横屏最近任务界面。
- Still broken: 后续 v4.0.22-v4.0.27 继续修，说明仍未完成。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 证据不足，不要推断具体实现。
- Next AI should know: 这只是 Recents 修复链中的中间节点。
- User approval required before: 回退到该版本作为 Recents 基线。
- Test result: Unknown / not enough evidence

### v4.0.22
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 继续 Recents landscape；有 `current_recents_v4_0_22.png`。
- Fixed: Unknown / not enough evidence
- Still broken: 后续继续 v4.0.23-v4.0.27。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 只有截图，不能确认内部修复。
- Next AI should know: 需要结合代码和日志判断 Recents 是否真横向。
- User approval required before: 回退或恢复该 Recents 实现。
- Test result: 有截图，完整结论 Unknown / not enough evidence

### v4.0.23
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `recents-taskstack`。
- Fixed: 开始针对 TaskStack 做横屏化或事件处理。
- Still broken: 后续仍继续修 Recents 横向和缩略图旋转。
- New bugs: Unknown / not enough evidence
- Risk / Notes: TaskStack 反射依赖 ROM 内部结构，容易版本漂移。
- Next AI should know: TaskStack 相关 hook 必须有 ROM 兼容失败保护。
- User approval required before: 新增或恢复 TaskStack 深层 hook。
- Test result: 有截图，完整结论 Unknown / not enough evidence

### v4.0.24
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `recents-horizontal`。
- Fixed: 把 Recents 容器或任务栈继续推向横向布局。
- Still broken: v4.0.25 仍处理 rotation，v4.0.26 仍处理 thumbnail no-rotate。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 横向容器和缩略图内容方向是两个问题，不能混修。
- Next AI should know: 先确认容器方向，再确认 thumbnail 内容方向。
- User approval required before: 大改 Recents 布局容器。
- Test result: 有截图，完整结论 Unknown / not enough evidence

### v4.0.25
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 文件名显示 `recents-rotation0`。
- Fixed: 强制 Recents 视觉或容器 rotation 为 0，并测试横屏尺寸。
- Still broken: 截图/记录显示 overlay 污染基本修掉，但缩略图仍旋转。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 强制 rotation 可能影响不同 ROM 的任务卡片绘制。
- Next AI should know: v4.0.25 不是最终 Recents 修复，只是中间节点。
- User approval required before: 恢复或扩大 rotation 强制逻辑。
- Test result: `current_recents_v4_0_25_now.png` 显示仍有缩略图方向问题

### v4.0.26
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 加入 `TaskViewThumbnail` no-rotate hook，规范化 `BitmapShaderDrawInfo`。
- Fixed: Recents 卡片内容变横向，overlay 图标不再渲染到 Recents 上。
- Still broken: 左滑关闭任务未成功。
- New bugs: Unknown / not enough evidence
- Risk / Notes: thumbnail hook 依赖 MIUI 内部类和字段，ROM 差异风险高。
- Next AI should know: v4.0.26 是 Recents 视觉修复重要节点，但 dismiss 还没好。
- User approval required before: 删除 thumbnail no-rotate 或改其反射路径。
- Test result: 构建、安装、测试成功；`current_recents_v4_0_26_after_swipe.png` 显示左滑未清理

### v4.0.27
- Date: 2026-04-25
- AI involved: Codex GPT5.5
- Changed: 在 `TaskStackViewTouchHandler` 上加入横屏 Recents 左滑 dismiss hook，记录 down/up，构造 MIUI 原生 `TaskViewDismissedEvent`，并提供 EventBus / removeTask fallback。
- Fixed: 横屏 Recents 卡片左滑触发原生 MIUI dismiss。日志确认 `leftDismiss=true` 和 `source=TaskStackView#onMessageEvent`。
- Still broken: 后续 v4.0.28 删除这条 native Recents hook 路线，转向 `overlay/recents/`，所以 v4.0.27 是可参考实验，不是当前主线。
- New bugs: 可能污染竖屏 Recents 或依赖 ROM 内部实现，具体证据 Unknown / not enough evidence
- Risk / Notes: 这是 Codex 参与的明确节点；native Recents hook 有价值但高风险。
- Next AI should know: v4.0.27 的三段 dismiss fallback 可参考，但不要未经用户同意恢复。
- User approval required before: 把 v4.0.27 native Recents hook 重新合回当前路线。
- Test result: 构建、安装、测试成功；截图 `current_recents_v4_0_27_after_left_dismiss.png`，日志确认 dismiss

### v4.0.28
- Date: 2026-04-25
- AI involved: Claude Code Opus 4.7
- Changed: 删除 `MiuiHomeLandscapeModule` 中约 750 行 Recents 改造 hook（包括全局 `View#setVisibility` 拦截、`forceRecentsLandscapeFields`、`ensureTaskStackHorizontal`、`hookTaskViewThumbnailNoRotate`、`hookRecentsLeftDismiss` 等 12 处），仅保留单一 `hookRecentsVisibilitySignal`（信号型 hook，不修改任何字段）。新增 `overlay/recents/` 包：`LandscapeRecentsModel`（反射 MIUI RecentsModel/TaskStack 取任务列表 + dismiss/launch）、`LandscapeRecentsCardView`（24dp 圆角卡片 + 上滑关闭手势 + 头部图标/标题）、`LandscapeRecentsView`（HorizontalScrollView 横排卡片 + 清空按钮 + 空态占位）、`LandscapeRecentsController`（Activity 生命周期管理 + 挂载到 `android.R.id.content`）。
- Fixed: 竖屏 Recents 不再被全局 hook 污染（路径上零 hook 命中）；放弃旧的"修补 MIUI 内部字段"路线，改为外挂式 overlay。
- Still broken: 引入了 v4.0.29 才修掉的竖屏触摸事故；横屏 iPad 卡片 UI 未实机验证；dismiss 路径未在新模型层完全实现（仅 EventBus + ActivityManager.removeTask 两段）。
- New bugs: **竖屏触摸全失效**——`LandscapeController.applyOrientation` 竖屏分支调用了 `setNativeGone(nativeRecentsContainer, false, ...)`，把本应 GONE 的 RecentsContainer 强制 VISIBLE，覆盖了 Workspace，所有触摸被 RecentsContainer 吃掉。
- Risk / Notes: iPad-style 卡片 UI 未实机验证；反射依赖 MIUI 内部类，跨 ROM 风险高（Bug-002）。
- Next AI should know: `overlay/recents/` 是新方向，**不要**回退到 hook MIUI 内部字段的旧路线；不要给 `nativeRecentsContainer` 调 `setVisibility(VISIBLE)`——它的默认状态本就是 GONE。
- User approval required before: Roadmap 任何项；恢复 v4.0.27 的 native Recents hook 路线。
- Test result: ❌ 竖屏严重事故（Portrait Touch Regression），已在 v4.0.29 紧急修复。

### v4.0.29
- Date: 2026-04-25
- AI involved: Claude Code Opus 4.7
- Changed: 紧急修复 v4.0.28 引入的竖屏触摸事故。`LandscapeController.applyOrientation` 竖屏分支：删除 `setNativeGone(nativeRecentsContainer, false, ...)`，**完全不再触碰** RecentsContainer 的 visibility（MIUI 自己管）。横屏分支：删除 `setNativeGone(nativeRecentsContainer, true, ...)`，避免与 `setVisibility` afterHook 形成回环。`setRecentsVisible(true)` 横屏路径也不再 GONE RecentsContainer，纯靠 z-order（`addView` 到 `android.R.id.content` + `bringToFront`）+ `onTouchEvent return true` 压制原生。
- Fixed: **竖屏触摸全失效**（v4.0.28 Portrait Touch Regression）。根因：`setNativeGone(nativeRecentsContainer, false)` 强制 VISIBLE 把 RecentsContainer 拉到 Workspace 之上吃光所有触摸事件。竖屏路径现在对原生 view 零修改。
- Still broken: Bug-001（横屏 Recents 可能漏底）；Bug-002（反射跨 ROM 兼容）；Bug-003（dismiss 路径不全）。
- New bugs: 横屏 Recents 视觉漏底（Bug-001）尚未实机观察确认。
- Risk / Notes: 横屏 Recents 现仅靠 z-order 压制原生 RecentsContainer，理论上原生 RecentsContainer 仍在底层渲染，可能透出。若漏底，方案是在 `setVisibility` 的 **before**Hook 改入参 `args[0]=GONE`，避开 afterHook 二次触发。
- Next AI should know: **凡是给原生 view 调 `setVisibility` 都要先想清楚 MIUI 自己的预期 visibility**——很多容器（RecentsContainer 等）默认就是 GONE，"恢复成 VISIBLE" 是错误前提。竖屏路径的硬规则：零修改、零 hook 命中。
- User approval required before: 任何 Recents 进一步改造；任何对 `nativeRecentsContainer.setVisibility` 的调用。
- Test result: ⏳ 待用户实机确认竖屏触摸恢复 + 横屏 iPad 卡片 Recents 行为。

### v4.0.30
- Date: 2026-04-25
- AI involved: Claude Code Opus 4.7
- Changed: 把 `AI_HANDOFF_4.0_ALPHA.md` 打包进 APK assets（`app/src/main/assets/AI_HANDOFF_4.0_ALPHA.md`）+ 源码根目录（`C:\MIHL Claude\v4.0.30-src\AI_HANDOFF_4.0_ALPHA.md`）+ 一次性导出到 `C:\Users\heiwo\AI_HANDOFF_4.0_ALPHA.md`（C 盘根目录写入受限，回退到用户目录）。`MiuiHomeLandscapeModule.VERSION` 由 `4.0.29` → `4.0.30`。无业务逻辑、无 hook 改动、无 overlay/recents 改动。
- Fixed: —（纯打包/版本号 bump）
- Still broken: 继承 v4.0.29 的 Bug-001 / Bug-002 / Bug-003。竖屏触摸恢复仍待用户实机最终确认。
- New bugs: 无。
- Risk / Notes: 纯文档打包风险极低；APK 中多了一个 11 KB 的 markdown 资源，不影响运行时行为。
- Next AI should know: 本文随 APK 一起分发，跨 AI 接手时可直接从设备 `assets/AI_HANDOFF_4.0_ALPHA.md` 读取最新交接状态。源码当前在 `C:\MIHL Claude\v4.0.30-src\`。
- User approval required before: 任何 Recents 进一步改造；任何 Roadmap 项；任何修改 hook 路径或 overlay 行为的改动。
- Test result: ✅ Build SUCCESSFUL (gradlew assembleDebug, 11s)；✅ `adb install -r` Success；✅ `adb shell am force-stop com.miui.home` + `monkey HOME 1`；⏳ 实机功能验证（竖屏触摸 + 横屏 Recents）待用户确认。

### v4.0.30-doc-OC(此版本仅是codex和claude联合工作存档，不影响apk)
- Date: 2026-04-25
- AI involved: OpenAI Codex GPT-5.5/Claude Code Opus 4.7
- Changed: 按用户要求补充 Codex 使用模型说明、文档尾号规则、普通迭代默认输出 `AI_HANDOFF_4.0_ALPHA_OC.md` 的规则，并将当前版本标记统一为 v4.0.30。
- Fixed: 修正交接文档里“参与过的 AI”范围，明确 Codex 只写自己参与或能确认的版本，不替 Claude 管辖版本写细节。
- Still broken: 这只是文档归档更新，不代表 APK 功能已修复。
- New bugs: Unknown / not enough evidence
- Risk / Notes: `OC` 代表 OpenAI Codex 和 Claude 都参与过编辑或交接；`C` 代表最后由 Claude 编辑；`G` 代表最后由 Codex GPT 编辑。
- Next AI should know: 每次新版本或新交接文档必须同步更新 Version Log，说明本次改了什么。
- User approval required before: 改变文档尾号规则，或把 Claude 管辖版本细节改成 Codex 记录。
- Test result: 文档更新，未构建、未安装、未运行 ADB、未修改代码。

### v4.0.31
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 基于 `C:\MIHL Claude\v4.0.30-src\` 拷贝出 `C:\MIHL\MiuiHomeLandscape_v4.0.31\`。版本号从 `4.0.30` 升到 `4.0.31`。横屏 Recents 改为模块自己的 2 行横向任务卡布局（2 x N），背景改为不透明遮罩，并增加 portrait guard，避免竖屏路径创建横屏 Recents。横屏文件夹从系统 `AlertDialog` 改为挂在 `android.R.id.content` 上的模块内全屏展开层：背景暗化，主 overlay 在 Android 12+ 上加 blur，中央大圆角面板显示文件夹名称和网格 App。
- Fixed: 尝试修复横屏后台漏出竖屏/原生任务视图的问题；尝试修复文件夹打开后桌面整体左缩、底部出现白线的问题，根因是系统 Dialog 触发窗口让位/重布局。
- Still broken: 未经真机确认。当前文件夹是模块内复刻 MIUI 风格展开层，不是直接调用 MIUI Home 内部 Folder 类；原生 MIUI 文件夹动画和完全一致的内部行为仍未接入。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 2 x N Recents 仍依赖 `LandscapeRecentsModel` 取任务和 dismiss，若 MIUI ROM 的 Recents 数据/清理路径变化，仍可能需要进一步逆向。文件夹 overlay 使用 `RenderEffect` 仅在 Android 12+ 生效，低版本只保留暗化和圆角面板。
- Next AI should know: 不要把文件夹再退回 `AlertDialog`，那会复现桌面缩放/底部白线。横屏 Recents 必须由模块 view 覆盖并消费 touch，竖屏路径继续交给 MIUI 原版。
- User approval required before: 直接 hook MIUI Home 原生 Folder 类；直接 hook/隐藏原生 RecentsContainer；安装 APK、清数据、ADB 操作或继续大改 Recents dismiss。
- Test result: ✅ `gradlew assembleDebug` 构建成功（使用 JBR 17；系统默认 Java 26 会导致 Gradle 8.2 报 `Unsupported class file major version 70`）。APK 输出：`C:\MIHL\MiuiHomeLandscape_v4.0.31\MiuiHomeLandscape_v4.0.31-debug.apk`。未安装、未运行 ADB、未做真机测试。

### v4.0.32
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 根据 v4.0.31 ADB 安装后的真机日志继续修正横屏 Recents 分离逻辑。`enforceLandscapeSeparation()` 在 `recentsVisible/sRecentsVisible` 为 true 时不再调用 `suspendForRecents()`，避免 watchdog 把原生 workspace/hotseats 恢复成可见，改为保持原生桌面隐藏并只记录状态。
- Fixed: v4.0.31 测试中发现打开横屏 Recents 后约 0.5 秒 watchdog 触发 `[recents] overlay suspended reason=enforce:watchdog ws=V hs=V overlay=G`，这会造成原生桌面/原生后台与模块 Recents 混层风险。v4.0.32 已改成 Recents 期间不恢复 native workspace/hotseats。
- Still broken: 横屏 Recents 现在是 2 x N 结构，但卡片尺寸/位置仍偏左、偏大，属于后续视觉优化；文件夹仍是 MIUI 风格复刻层，不是直接调用 MIUI Home 内部 Folder 类。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这次只修 watchdog 分离逻辑，不改 dismiss 路径、不改 MIUI 原生 RecentsContainer visibility。
- Next AI should know: 看到 `recentsVisible` 时不要再调用 `suspendForRecents()`；该方法会恢复 native workspace/hotseats，容易复现横竖混层。
- User approval required before: 继续调整 Recents 卡片尺寸/动画；hook MIUI 原生 Folder；hook 原生 RecentsContainer；清数据。
- Test result: ✅ v4.0.31 已通过 ADB 安装；✅ 文件夹截图显示无系统弹窗白线/无左缩；✅ Recents 截图显示 2 行模块卡片；⚠️ 日志发现 watchdog 混层风险，因此生成 v4.0.32 并需再次安装测试。

### v4.0.33
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 根据 v4.0.32 ADB 复测日志继续收紧横屏 Recents 分离。`setRecentsVisible(true)` 和 `enforceLandscapeSeparation()` 在横屏 Recents 期间显式隐藏 native workspace/hotseats，并阻断 drag_layer/screen_content touch；仍不触碰 native RecentsContainer visibility。
- Fixed: v4.0.32 日志显示虽然不再 `suspendForRecents()`，但 Recents 期间 `ws=V hs=V`，说明 MIUI 原生桌面仍可能被系统拉回可见。v4.0.33 强制把 workspace/hotseats 压回 GONE。
- Still broken: Recents 卡片 UI 仍偏左、偏大；任务缩略图是否完整渲染仍需更多应用样本确认；文件夹仍是 MIUI 风格复刻层，不是直接调用 MIUI Home 内部 Folder 类。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 只隐藏 workspace/hotseats，不隐藏 RecentsContainer，避免复现 v4.0.28 的竖屏触摸事故。
- Next AI should know: 横屏 Recents 分离的安全边界是：native workspace/hotseats 可隐藏，native RecentsContainer 不要直接改 visibility。
- User approval required before: 继续改 Recents 卡片尺寸/动画；hook MIUI 原生 Folder；hook 原生 RecentsContainer；清数据。
- Test result: ✅ `gradlew assembleDebug` 构建成功；✅ `adb install -r` 成功，设备确认 `versionName=4.0.33 / versionCode=83`；✅ 横屏 Recents 截图显示 2 行模块卡片；✅ watchdog 日志显示 `keep native hidden during landscape recents ... ws=G hs=G overlay=G`，未再出现 `overlay suspended reason=enforce:watchdog ws=V hs=V`；✅ v4.0.31 文件夹截图曾验证无系统弹窗白线/无左缩，v4.0.33 未改文件夹实现。

### v4.0.34
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 根据用户反馈修正两个实机问题。横屏 Recents 的 `HorizontalScrollView` 和任务行强制 LTR，默认从左侧开始，不再 `fullScroll(RIGHT)`。文件夹展开层加深外层遮罩，并把面板背景从半透明暗色改成更接近不透光的 MIUI 暗色，减少背景被 blur 的桌面图标透进文件夹面板。
- Fixed: 任务后台从右到左的问题；文件夹面板过透明导致背景图标混入面板、观感异常的问题。
- Still broken: 文件夹仍是 MIUI 风格复刻层，不是直接调用 MIUI Home 内部 Folder 类；Recents 卡片尺寸仍可能需要继续视觉调优。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 不触碰 native RecentsContainer visibility，不改 dismiss 路径。
- Next AI should know: Recents 默认方向必须保持 LTR/FOCUS_LEFT；文件夹面板不要再使用明显透明背景。
- User approval required before: hook MIUI 原生 Folder；继续做 Recents 动画/尺寸大改；清数据。
- Test result: ✅ `gradlew assembleDebug` 构建成功；✅ `adb install -r` 成功，设备确认 `versionName=4.0.35 / versionCode=85`；✅ Recents 截图确认默认从左侧开始；✅ 文件夹截图确认面板内不再出现上一版那种强 blur 背景污染。

### v4.0.35
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 根据 v4.0.34 截图继续修文件夹。取消文件夹打开时对主 overlay 的 `RenderEffect` blur，改成更深的全屏 dim 遮罩和完全不透明的 MIUI 暗色圆角面板，避免背景桌面图标被模糊后透进面板内部。保留 v4.0.34 的 Recents LTR/FOCUS_LEFT 改动。
- Fixed: 文件夹面板内仍能看到大量被放大的模糊背景图标的问题。
- Still broken: 文件夹仍是 MIUI 风格复刻层，不是直接调用 MIUI Home 内部 Folder 类；背景现在是暗化而不是 blur+暗化，优先保证不污染面板。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这次牺牲一部分 MIUI 原生 blur 观感，换取文件夹层视觉隔离稳定。
- Next AI should know: 如果以后要恢复 blur，必须保证 blur 不能进入 panel 内部；否则会复现“背景图标透进文件夹”的 bug。
- User approval required before: hook MIUI 原生 Folder；恢复 blur；继续做 Recents 动画/尺寸大改。
- Test result: ✅ `gradlew assembleDebug` 构建成功；✅ `adb install -r` 成功，设备确认 `versionName=4.0.36 / versionCode=86`；✅ 文件夹截图确认面板在最上层，桌面图标只保留在暗化背景里，不再覆盖到文件夹内容上。

### v4.0.36
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 修复文件夹层级问题。`enforceLandscapeSeparation()` 每次把主 overlay `bringToFront()` 后，会再次把 `folderOverlay` 提到最上层；文件夹打开时也设置高 elevation/translationZ 并 `bringToFront()`。
- Fixed: v4.0.35 截图显示文件夹暗色面板在底层、横屏桌面图标被 overlay/guard 拉到上层，造成“整个桌面跑进文件夹”的视觉 bug。
- Still broken: 文件夹仍是 MIUI 风格复刻层，不是直接调用 MIUI Home 内部 Folder 类。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这次只改 z-order，不改文件夹数据和 Recents 方向。
- Next AI should know: 任何 `overlay.bringToFront()` 后，如果文件夹还开着，必须随后 `folderOverlay.bringToFront()`。
- User approval required before: hook MIUI 原生 Folder；恢复 blur；继续做 Recents 动画/尺寸大改。
- Test result: 待 ADB 安装复测。

### v4.0.40
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 根据用户反馈继续修横屏后台。`LandscapeRecentsView` 改成 RTL 布局，任务列按从右到左显示。`LandscapeRecentsController` 在清后台时先隐藏横屏 Recents overlay，再调用 `LandscapeRecentsModel.clearAllTasks()`，让 MIUI 原生 Recents 清理控件有机会接管。`LandscapeRecentsModel` 增加原生清理候选按钮扫描：优先点击可见的 MIUI 原生清理按钮，再走 RecentsModel / EventBus / ActivityManager.removeTask / IActivityTaskManager fallback。
- Fixed: v4.0.39 实机反馈“后台方向仍是左到右”；尝试修复“横屏 X 只清横屏 UI，竖屏后台仍保留”的同步问题。
- Still broken: 清后台同步需要用户实机验证；如果 ROM 不暴露原生清理按钮或反射路径，仍可能失败。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这版不改文件夹视觉，不清数据库，不改 MIUI Home 原生数据库。
- Next AI should know: 清后台同步的关键是不要只 `view.clearAll()`，必须先让 native Recents 或系统任务栈真正清掉数据。
- User approval required before: 继续 hook MIUI RecentsContainer 内部字段；清数据；改系统 Recents 任务栈策略。
- Test result: ✅ `gradlew assembleDebug` 构建成功；APK 输出 `C:\MIHL\MiuiHomeLandscape_v4.0.31\MiuiHomeLandscape_v4.0.40-debug.apk`。未安装、未 ADB 实机复测。

### v4.0.41
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 修复用户反馈的横屏后台 5 项问题。`LandscapeRecentsView` 改为“最新任务在右，旧任务向左”的连续流重排；dismiss 后按 currentTasks 重新生成列，避免第 1 个移除后留下空洞。增大卡片横/纵间距，top padding 从 56dp 降到 30dp，让整体更靠上。`LandscapeRecentsController` 清空后台时不再先隐藏横屏 Recents view，避免露出 MIUI 竖屏清空动画。`LandscapeController.applyOrientation()` 在 recents 可见时不再调用 `suspendForRecents()`，避免返回时短暂恢复 native workspace/hotseats 导致竖屏态闪现。
- Fixed: 后台第一个 app 移除后后续 app 不能自动补位；后台卡片间距不足；后台整体偏下；清空后台时露出竖屏清空动画；从后台返回时短暂显示竖屏横屏状态。
- Still broken: 清空后台是否同步竖屏后台仍需用户用多个真实后台任务确认；MIUI 原生清理入口反射/扫描可能因 ROM 差异不稳定。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这版不改文件夹视觉，不改数据库，只改横屏 Recents UI/流程。
- Next AI should know: 清后台时不要先隐藏横屏 Recents view，否则会重新露出原生竖屏清理动画；recents 可见时不要走 `suspendForRecents()`，否则 native workspace/hotseats 会短暂恢复。
- User approval required before: hook MIUI RecentsContainer 内部字段；清数据；改系统 Recents 任务栈策略。
- Test result: ✅ `gradlew assembleDebug` 构建成功；✅ `adb install -r` 成功，设备确认 `versionName=4.0.41 / versionCode=91`；✅ 截图显示单卡固定在右上，整体不再居中漂浮。清后台同步待用户实测。

### v4.0.42
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 针对用户反馈“后台退不出桌面，滑到后台超级僵硬，太卡了”修改横屏 Recents。`LandscapeRecentsController.show()` 去掉整层 220ms alpha/translation 入场动画，显示时直接 reset alpha/translation 并 requestFocus。`hide()` 也会 cancel 动画并复位 translation，避免残留位移。`exitRecentsToHome()` 不再只依赖 MIUI 原生 Recents visibility 回调，而是在发 HOME Intent 前主动调用 `LandscapeController.onRecentsVisibilityChanged(false, ...)`，并延迟二次兜底恢复横屏桌面。`LandscapeRecentsView` 增加 BACK 键消费，回退键直接走退出桌面逻辑；`HorizontalScrollView` 关闭 smooth scrolling/over-scroll，卡片 dismiss/restore 动画从 180ms 缩短到 100ms。
- Fixed: 横屏后台退出后桌面 overlay 可能不恢复；后台整层入场动画和卡片动画叠加造成手感僵硬、卡顿。
- Still broken: 清后台是否同步竖屏后台仍需用户用多个真实后台任务确认；如果 MIUI 原生 Recents hook 没有发 visibility=false，v4.0.42 已主动兜底，但仍需实机确认不会引入返回闪屏。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这版只动横屏 Recents 控制/动画，不改文件夹、不改桌面布局、不改数据库。
- Next AI should know: 退出后台时必须主动恢复 `LandscapeController` 的 recentsVisible 状态，不能只等 MIUI native hook；后台入口不要再加整层 translation 动画，容易和系统手势动画叠加导致卡顿。
- User approval required before: 继续 hook MIUI RecentsContainer 内部字段；清数据；重写系统 Recents 手势/动画链。
- Test result: ✅ `gradlew assembleDebug` 构建成功；✅ `adb install -r` 成功，设备确认 `versionName=4.0.42 / versionCode=92`；✅ ADB 进入横屏后台后按 BACK，截图 `test_recents_v4.0.42_home.png` 显示已返回横屏 overlay 桌面。滑动手感仍需用户手动实测确认。

### v4.0.43
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 根据用户反馈“卡，僵硬；回来有远古失去竖屏转横屏的 bug”继续修横屏后台。撤销 v4.0.42 在 `LandscapeRecentsController.exitRecentsToHome()` 中提前调用 `LandscapeController.onRecentsVisibilityChanged(false, ...)` 的做法，避免在 MIUI 原生 Recents 还没真正收起时提前恢复桌面 overlay。`LandscapeController` 新增 `setNativeRecentsMuted()`，横屏后台显示时只把原生 RecentsContainer alpha 置 0 并阻断 touch/drag，不改它的 visibility，避免 setVisibility hook 回环，同时减少原生后台与横屏 overlay 双层可见渲染。
- Fixed: v4.0.42 可能让横屏后台返回时过早恢复 overlay，重新触发原生 workspace/hotseats 短暂冒出、横竖分离修复脉冲的问题。
- Still broken: “后台滑动僵硬/卡”需要用户手动体感确认；原生 MIUI Recents 手势状态机仍会运行，模块只能降低视觉双渲染，不能完全替代系统手势动画链。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这版不改文件夹、不改桌面布局、不清数据库；只改横屏 Recents 恢复时机和原生 RecentsContainer 静音策略。
- Next AI should know: 不要在 Recents 退出刚开始时提前恢复 `recentsVisible=false`，否则会重现旧的横竖分离闪现/污染；如果要兜底，也必须延迟到 MIUI 原生 RecentsContainer 已经 GONE 之后。
- User approval required before: 继续 hook MIUI RecentsContainer 内部动画/手势字段；彻底替换系统 Recents 手势；清数据。
- Test result: ✅ `gradlew assembleDebug` 构建成功；✅ `adb install -r` 成功，设备确认 `versionName=4.0.44 / versionCode=94`；✅ ADB 进入后台后退出，日志显示 `visible=false` 后先 repair，且 `beforeWs=G beforeHs=G afterWs=G afterHs=G`，说明隐藏横屏后台层前原生 workspace/hotseats 没有再暴露。截图 `test_recents_v4.0.44_home.png` 显示已返回横屏桌面。后台滑动体感仍需用户手动确认。

### v4.0.44
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 继续修 v4.0.43 验证日志暴露的后台退出闪现窗口。`LandscapeController.setRecentsVisible(false)` 的横屏分支改成先 `resolveNativeViews()`，强制 `workspace/hotseats` GONE 并阻断 `drag_layer/screen_content` touch，再 `applyOrientation()` 准备横屏 overlay，最后才解除原生 RecentsContainer 静音并隐藏 `LandscapeRecentsView`。这样返回桌面时横屏桌面先在后台准备好，避免先隐藏后台层导致 MIUI 原生 workspace/hotseats 暴露。
- Fixed: 横屏后台退出瞬间仍可能出现旧的竖屏转横屏/横竖分离闪现窗口。
- Still broken: 后台“卡、僵硬”仍需用户手动体感确认；系统原生 Recents 手势状态机仍会运行，模块当前只做视觉静音和顺序修复。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这版只调整 Recents 退出恢复顺序，不改数据库、不改文件夹、不改桌面布局。
- Next AI should know: 退出后台时不要先 hide 横屏 Recents overlay；必须先把桌面 overlay 和 native workspace/hotseats 隔离状态准备好，再移除遮罩层。
- User approval required before: hook MIUI RecentsContainer 内部动画/手势字段；彻底替换系统 Recents 手势；清数据。
- Test result: ✅ `gradlew assembleDebug` 构建成功；✅ `adb install -r` 成功，设备确认 `versionName=4.0.45 / versionCode=95`；✅ 进入后台日志先出现 `restore system ui reason=recents-enter`，约 120ms 后才出现 `native container muted=true reason=show-delayed`，说明小白条先恢复、横屏 overlay 延迟接管；✅ 不删除任务直接返回桌面，日志显示 `beforeWs=G beforeHs=G afterWs=G afterHs=G`。后台滑动体感仍需用户手动确认。

### v4.0.45
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 根据用户反馈“首先得把隐藏的小白条召唤，僵硬到后台，不删应用后台会桌面僵硬”继续修 Recents 手势链。`applyGestureHandleVisibility()` 新增 recentsActive 判断，只要横屏后台正在显示/进入，就不再执行 `hide navigationBars()`，而是强制 `restoreSystemUi()` 让小白条参与 MIUI 原生后台手势。`setRecentsVisible(true)` 进入后台时先恢复系统 UI，再延迟 120ms 才 mute 原生 RecentsContainer 并显示横屏 Recents overlay，避免刚进入后台就把 MIUI 原生过渡动画掐断。`setRecentsVisible(false)` 返回桌面后追加 `scheduleSeparationPulses("recents-exit")`，用于捕捉“不删任务直接返回桌面”后 native workspace/hotseats 延迟偷跑导致的桌面僵硬。
- Fixed: 后台期间小白条被横屏隐藏设置重新按掉；进入后台时横屏 overlay 过早覆盖/静音原生 Recents 导致过渡僵硬；返回桌面后原生 workspace/hotseats 延迟偷跑窗口。
- Still broken: 后台滑动手感仍需用户实机确认；系统原生 Recents 手势状态机仍在运行，模块没有完全替代底层动画。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这版只改后台手势/UI 状态时序，不改文件夹、不改桌面布局、不清数据库。
- Next AI should know: 小白条隐藏功能不能在 Recents active 期间生效，否则会让 MIUI NavStubView 手势链变硬；横屏 Recents overlay 不要在 RecentsContainer 刚 VISIBLE 的同一帧立即接管。
- User approval required before: hook MIUI NavStubView/GestureStubView 内部字段；彻底替换系统 Recents 手势；清数据。
- Test result: ✅ `gradlew assembleDebug` 构建成功；✅ `adb install -r` 成功，设备确认 `versionName=4.0.46 / versionCode=96`；✅ 横屏进入后台日志显示 `restore system ui reason=recents-enter` → `native container muted=true reason=show-delayed` → `RecentsCtl show`；✅ 等待约 3.6 秒后出现 `auto-hide system ui reason=recents-enter...+3600`，确认系统小白条自动隐藏。截图 `test_recents_v4.0.46_land_recents.png` 已保存；动画体感仍需用户实机手滑确认。

### v4.0.46
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 根据用户要求“召唤出小白条后自动等候 3-4 秒自动消失，搞一套专属的小白条召唤及后台超级流畅优雅的动画”继续修横屏后台。`LandscapeController` 在 Recents enter 时调用 `scheduleRecentsNavAutoHide()`，如果用户开启隐藏小白条，则先恢复系统导航条，3.6 秒后在仍处于横屏后台时自动 `hideSystemUiNow()`。`LandscapeRecentsView` 新增模块自己的底部白色 gesture pill，进入后台时淡入，3.4 秒后自动淡出；同时新增 `playEnterAnimation()`，只对任务 row 和清后台按钮做轻量 alpha/translationY 入场，避免整屏大动画和 MIUI 原生手势动画打架。
- Fixed: 小白条被召唤后不会长期停留；横屏后台 overlay 入场过于生硬。
- Still broken: 后台“超级流畅”体感必须用户手动确认；系统原生 Recents 手势链仍在底层运行，模块动画只能做上层 overlay 的轻量润色。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这版不改后台数据、不改清后台同步、不改文件夹；只改 Recents 小白条显示时序和 overlay 入场动画。
- Next AI should know: 不要恢复 v4.0.42 那种整层 Recents view 大位移动画；当前动画只动 row/clear 按钮/pill，目的是降低 GPU/布局压力。
- User approval required before: hook MIUI NavStubView/GestureStubView 内部动画；彻底替换系统 Recents 手势；改系统动画比例。
- Test result: ✅ `gradlew assembleDebug` 构建成功；✅ `adb install -r` 成功，设备确认 `versionName=4.0.47 / versionCode=97`；✅ 横屏进入后台日志显示 `restore system ui reason=recents-enter` → `show-delayed` → `RecentsCtl show`，未再出现模块自绘 pill；✅ 等待约 3.6 秒后出现 `auto-hide system ui ... +3600`；✅ 退出后台日志顺序为先 `visible=false` / `orientation land=true overlay=VISIBLE ws=G hs=G`，再 `RecentsCtl hide source=exit-delayed`，说明遮罩延迟隐藏，降低短暂露出竖屏态风险。实机体感仍需用户确认。

### v4.0.47
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 根据用户反馈“后台召唤在白条中间召唤会有两个小白条，退不出去；到后台第一时间底部有一条白线；退到桌面会短暂显示竖屏的横屏状态”修复 v4.0.46。删除 `LandscapeRecentsView` 的模块自绘 `gesturePill`，只保留系统小白条，避免双白条和底部白线。`LandscapeRecentsController.exitRecentsToHome()` 不再一开始就 `hide()` 横屏后台 view，而是先发 HOME intent，延迟 220ms 再隐藏横屏 Recents 遮罩，让 `LandscapeController` 有时间先恢复 overlay/native 隔离状态，减少退桌面瞬间暴露底层竖屏态。
- Fixed: v4.0.46 双小白条；进入后台第一时间底部白线；退出后台时先隐藏遮罩导致短暂露出原生竖屏/横屏过渡态。
- Still broken: 需要用户实机确认“退不出去”是否完全解除；如果 MIUI HOME intent 回调慢于 220ms，仍可能需要进一步改成由 `visible=false` 回调统一隐藏。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这版保留系统小白条 3.6s 自动隐藏逻辑，只删除模块自绘 pill。
- Next AI should know: 不要再同时显示系统导航条和模块自绘 pill；退出 Recents 时不要先 hide overlay 遮罩。
- User approval required before: hook MIUI NavStubView/GestureStubView 内部字段；彻底替换系统 Recents 手势；改系统动画比例。
- Test result: 待构建、安装和实机验证。

## 文件位置速查

- 当前文档: `C:\MIHL\MIHL MD\AI_HANDOFF_4.0_ALPHA_OC.md`
- 默认本地存档: `C:\MIHL\MIHL MD\AI_HANDOFF_4.0_ALPHA_OC.md`
- 历史 APK / 截图 / 日志: `C:\Users\heiwo\Documents\Codex\2026-04-22-files-mentioned-by-the-user-miuihomelandscape\`
- 当前工程目录: `C:\MIHL\MiuiHomeLandscape_v4.0.31\`
- 入口类: `MiuiHomeLandscapeModule.java`
- 主控制器: `overlay/LandscapeController.java`
- Recents 新方向: `overlay/recents/`
- 数据层: `store/LandscapeStore.java` 和 `miui_home_landscape_overlay.db`
