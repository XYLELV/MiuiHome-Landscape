# AI_HANDOFF_5.0_ALPHA_C.md

> 主线交接文档  
> 项目：MiuiHome Landscape，LSPosed 模块  
> 分支：v4.x Alpha freeze / V5.0 deferred handoff  
> 当前建议以 v4.1.43 作为 stable alpha APK 封版；v4.1.53 为后续修复过程中产生的 unstable dev branch，不应被当作稳定基线。

---

## 当前版本

- Version: v4.1.43 stable alpha APK / v4.1.53 unstable dev branch
- Date: 2026-05-03
- Project status: Paused / Frozen。当前不建议继续冲刺新增功能，也不建议在 v4.1.53 上继续无控制叠补丁。
- Stable APK candidate: v4.1.43。当前开发者实测认为这是 v4.1.42-v4.1.53 这一段里最稳定、最适合公开发布 / 封版保留的 APK。
- Source state: v4.1.53 为 unstable / broken dev branch，存在大量 Recents、返回桌面、横竖屏同步、后台稳定性、数据污染、MIUI 动画冲突和旧 bug 复发问题，不建议作为稳定发布版。
- v4.1.43 source status: 原始源码快照已丢失，目前只保留 APK；后续如需分析 v4.1.43，只能通过 APK 反编译、文件时间、开发者实测反馈、v4.1.53 源码残留和行为对比来重建线索。反编译结果只能作为参考，不能视为完整原源码。
- Status: v4.x 主框架已经完成并进入封版整理阶段。横屏 overlay 桌面、分页、Dock、文件夹、拖动、基础后台等核心框架已经搭好；但 v4.1.43 之后原本只计划优化 Recents / 返回桌面 / 后台流畅度，实际却引入大量系统级回归，因此当前优先保留 v4.1.43 成果，不再把 v4.1.53 视为“更新更好”的版本。
- 本次文档修订：补充并重写 v4.1.42-v4.1.53 未完整记录的后续开发段，明确 v4.1.43 为当前 stable alpha candidate，v4.1.53 为 unstable dev branch；同时记录 v4.1.43 原始源码快照丢失、后续只能通过 APK 反编译作为参考。
- Main route: 横屏使用独立 overlay 桌面，自维护布局、Dock、点击、拖动、分页、文件夹、数据和部分 Recents 相关 UI。竖屏必须保持 MIUI Home 原版。
- 当前封版建议: 对外发布 / 归档优先使用 v4.1.43 APK；v4.1.53 只保留为事故分析、源码残留和后续排查参考，不建议作为稳定版。
- 后续方向: 暂停新增功能和大规模修复；如未来恢复 V5.0，应先做系统级稳定性排查，而不是继续堆功能。优先问题包括 Recents、返回桌面、横竖屏后台同步、底部白线、布局重算、数据污染、旧 bug 复发和 MIUI 原生动画冲突。
- 禁止路线: Magisk 资源开关、MIUI launcher 数据库改写、DeviceConfig 改网格、Workspace/CellLayout 原生改造、自动迁移竖屏布局、基于 v4.1.53 继续无控制叠补丁、把 v4.1.53 当作稳定发布版、用反编译结果直接覆盖现有工程。

---

## 参与过的 AI

- ChatGPT 5.4 Thinking（网页版）、Codex ChatGPT 5.4 Thinking、OpenAI Codex GPT-5.5、Claude Code Opus 4.7、Claude Sonnet 4.6、Claude Opus 4.7（网页版）均参与过不同阶段。
- Codex: 主导或参与 v4 主线大量连续迭代。当前必须注意：Codex 没有完整记录 v4.1.42 之后的小版本变更，且 v4.1.53 出现大量回归；后续不能默认 Codex 后续版本比 v4.1.43 更稳定。
- Codex GPT-5.4: 仅在有明确文档证据时记录，例如 `HANDOVER_TO_CODEX_beta16-reset.md` 中的 Codex ChatGPT 5.4 Thinking 交接内容；历史讨论中也出现过 Codex GPT-5.4 Thinking。
- Claude Code / Claude Opus 4.7: 参与 v3 reset、v4 部分 Recents / 文档 / 交接相关工作；Claude 分支与 Codex 主线存在平行版本，不能混成同一条稳定线。
- Claude Opus 4.7（网页版）— 早期数据层路线：`v1.6.0-DIAG` / `v1.7.0` / `v1.7.5-beta-6x3` / `v1.7.6` / `v1.7.7` / `v1.8.0` / `v1.9.0` / `v1.9.1` / `v2.0.0` / `v2.0.1` / `v2.1.0` / `MiuiHomeLandscape_HANDOFF_for_Codex.zip`。这一段属于 v3 重构之前的 DeviceConfig + 双 launcher DB 路线，已经被后续 v3/v4 主线明确弃用，仅保留为踩坑历史。
- Claude Sonnet 4.6（网页版）：参与早期 Magisk 分叉路线及 LSPosed v1.0-v1.5 失败探索期。
- 重要提醒: 不要默认相信任何旧结论。后续只信代码、真实数据库、真实日志、真实界面结果；尤其不要默认“版本号越新越稳定”。

---

## AI 模型与文档命名规则

- 注意 Codex ChatGPT 5.4 / 5.4 Thinking 和 ChatGPT 5.4 Thinking 是不同上下文；Claude Code 与 Claude 网页版也不是同一环境。
- 当前项目状态: 暂停维护 / 封版整理，没有继续指定主力开发 AI。
- 如果未来恢复开发，必须先确认基线：默认以 v4.1.43 APK 行为作为 stable alpha 参考，以 v4.1.53 源码作为 unstable 事故现场参考，不能反过来。
- `AI_HANDOFF_5.0_ALPHA_C.md`: 当前文件为 Claude/ChatGPT 后补整理后的 V5.0 deferred handoff，不代表 V5.0 已正式开始开发。
- `AI_HANDOFF_4.0_ALPHA_OC.md`: `OC` 表示 OpenAI Codex 和 Claude 都编辑过或需要共同交接的本地存档。
- `AI_HANDOFF_4.0_ALPHA_C.md`: `C` 表示最后一个版本由 Claude 编辑。
- `AI_HANDOFF_4.0_ALPHA_G.md`: `G` 表示最后一个版本由 Codex / GPT 编辑。
- 每次编辑新版本或新交接文档时，必须同步更新 Version Log，说明该版本或该文档改了什么、谁改的、证据是否足够。证据不足必须写 `Unknown / not enough evidence`。

---

## 当前已实现（以 v4.1.43 stable alpha APK 为准）

以下表示 v4.x 已经搭好的主框架；这些是项目成果，不代表 v4.1.53 稳定：

- 横屏独立 overlay 桌面，不再走 MIUI Home 原生数据库迁移路线。
- 横屏主区域分页布局、Dock 独立区域、分页指示器。
- 横屏 app 点击启动、拖动、交换、插入、移动到 Dock、边缘自动翻页。
- 横屏移除 app 只从横屏移除，不影响竖屏桌面和真实安装状态。
- 横屏文件夹基础框架：打开、命名、内部拖动、拖出到桌面、拖入文件夹、直接 drop 到文件夹。
- 模块自有数据层：`LandscapeStore` / SharedPreferences v4 store；不触碰 MIUI Home 原生 launcher DB。
- 竖屏原则：恢复 MIUI Home 原版 Workspace、Hotseats、DragLayer、ScreenContent，不污染竖屏布局。
- 横屏禁用负一屏，避免触发横竖分离失效。
- 横屏 Recents / 后台方向已经建立过可运行路线，但属于最高风险区域，不能因为“能显示”就当作稳定完成。

---

## 未解决问题 / 如未来恢复开发的接手重点

- 基线确认：先固定 v4.1.43 APK 作为 stable alpha 行为参考；v4.1.53 只能作为 unstable dev branch 对比，不可直接当继续开发基线。
- Recents / 后台：横屏后台流畅度、退出后台手势、横竖后台数据同步、清空后台系统级同步、后台列表方向、任务关闭路径仍是最高风险区域。
- 返回桌面：从应用返回横屏桌面时，仍需排查底部白线、短暂露出竖屏横向状态、布局高度重算、MIUI 原生动画与 overlay 动画抢控制权。
- 数据污染：v4.1.53 出现数据污染和旧 bug 复发现象，未来必须先定位污染来源，不能直接清数据库或强行迁移。
- 文件夹：内部拖动动画、首位/中间换位、拖出桌面、拖入打开文件夹、直接 drop 到文件夹，仍需用真实手势反复验证。
- 适配：不同 MIUI Home 版本、不同分辨率、折叠屏、平板比例下的 overlay / insets / touch 兼容性仍不可靠。
- 性能与动画：拖动动画、后台动画、文件夹动画仍偏临时实现；但在稳定性问题解决前，不应优先追求“官方级动画”。

---

## 已知风险

### Risk-001: v4.1.53 是 unstable dev branch，不是稳定新版

- Bug: v4.1.53 后续开发版出现大量 Recents、返回桌面、横竖屏同步、后台稳定性、数据污染、旧 bug 复发和 MIUI 动画冲突问题。
- Impact: 如果后续 AI 默认“v4.1.53 比 v4.1.43 新所以更好”，可能会把事故分支当成稳定基线，继续扩大回归。
- Status: 当前应以 v4.1.43 APK 作为 stable alpha candidate；v4.1.53 只作为事故现场和源码残留参考。
- Notes: v4.1.43 原始源码快照已丢失，目前只能通过 APK 反编译辅助分析。反编译结果不能视为完整源码，也不能直接覆盖现有工程。

### Risk-002: Recents / 返回桌面仍是最高风险模块

- Bug: 横屏 Recents 涉及系统任务数据、MIUI 原生后台、手势条、窗口动画、返回桌面动画、z-order、横竖分离和 overlay 层级，ROM 差异大。
- Impact: 后台列表方向、清后台同步、退出后台手势、左侧漏桌面、底部白线、布局重算、卡顿、数据污染和旧 bug 复发都可能出现。
- Status: 若未来恢复 V5.0，应先做稳定性诊断，而不是直接做动画美化。
- Notes: 不要把 v4.0.27 native dismiss、v4.0.28 overlay recents、v4.1.43 stable APK、v4.1.53 unstable branch 的逻辑混用，除非有日志、源码和实机证据。

### Risk-003: v4.x 事件模型能用，但仍偏补丁堆叠

- Bug: v4.x 已经搭出 overlay 桌面框架，但拖动、文件夹、Recents、insets、返回桌面等链路经过大量小修，存在状态机交叉风险。
- Impact: 某些边界手势可能复发：文件夹误触、拖动丢失、返回桌面短暂错态、后台难划出、布局重算。
- Status: 当前暂停继续叠补丁；若未来重启，应先拆分事件模型和 Recents / 返回桌面链路。
- Notes: 不要在 v4.x 继续无限小修；必须先保留 v4.1.43 APK、v4.1.53 源码、反编译结果和文档快照。

### Risk-004: 文件夹功能已可用但未达到 MIUI 原生级

- Bug: 文件夹逻辑是 overlay 自实现，不是完整调用 MIUI Home 原生 Folder 展开层。
- Impact: 动画、换位、拖出/拖入、命名输入法联动仍可能不如原版。
- Status: 暂不建议优先大改；文件夹不是当前最紧急风险，Recents / 返回桌面 / 数据污染优先级更高。
- Notes: 如果未来追求“原生 MIUI 文件夹”，需要先逆向 MIUI Folder / FolderIcon / DragController，而不是继续只调 UI。

---

## 后续 AI 修改权限规则

- 可以直接做: 文档更新、日志分析、代码阅读、方案对比、低风险小修、反编译结果旁路对比。
- 必须先问开发者: 新功能、架构重构、Recents 核心改动、文件夹核心逻辑、Dock 结构变化、原生 MIUI hook 增删、系统级 touch / drag / drop 改动、以 v4.1.53 为基线继续开发。
- 禁止直接做: 清数据库、卸载模块、重置桌面布局、`pm clear`、恢复 MIUI launcher DB 路线、自动迁移竖屏布局、删除或替换 v4.1.43 APK、把 v4.1.53 当稳定发布版、用反编译结果直接覆盖现有工程。
- 任何可能影响竖屏原版行为、Recents、返回桌面、系统动画或数据层的修改，都必须先获得开发者明确确认。

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
- Risk / Notes: 这是最早的试探包；后续已证明这条 overlay 路线在目标机型环境上走不通。
- Next AI should know: 这版只有历史意义，不要拿来当功能基线。
- User approval required before: 继续把 RRO / fabricated overlay 当主路线。
- Test result: 实机安装失败，Magisk 不识别模块。

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
- Test result: 实机可安装，但不生效。

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
- Test result: 开发者提供诊断日志；日志结论明确，旧路线判死。

### magisk v1.2.0-force
- Date: 2026-04-18
- AI involved: Chatgpt 5.4(web)
- Changed: 放弃 overlay，尝试前台检测 `com.miui.home` 后强制旋转的 workaround 方案；进入桌面时切横，离开桌面时恢复。
- Fixed: 提供了一个不依赖 overlay 的“能直接试”的临时方案。
- Still broken: 本质上还是碰系统级旋转状态，无法做到只影响桌面。
- New bugs: 实机反馈 **打开其他应用也会被横屏锁定**，副作用严重，不可用。
- Risk / Notes: 这版证明“纯 Magisk 外围控制旋转”不符合需求，即使能转也会污染其他 app。
- Next AI should know: 不要再回到这种前台检测 + 全局旋转状态控制的思路。
- User approval required before: 再次尝试任何全局/半全局旋转 workaround。
- Test result: 实机判定彻底失败。

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
- Still broken: 仅为源码工程，不是已验证可用的成品 APK；未在开发者真机上完成闭环验证。
- New bugs: 无已确认新增 bug。
- Risk / Notes: 这版更多是过渡工程和思路支架，不应被当成稳定基线。
- Next AI should know: 这版的价值在于转路线，不在于功能成熟度。
- User approval required before: 直接把这版源码当可交付模块继续扩展。
- Test result: 仅生成源码工程，未形成可靠实机结论。

###### 历代模型（LSPosed 模块 v1.0 ~ v1.5 失败探索期）

### v1.0
- Date: 2026-04-19
- AI involved: Claude Sonnet 4.6 (web)
- Changed: 第一个真正的 LSPosed 模块实现。**为什么用 LSP 构建：** Magisk overlay / service 路线已证明在目标机型上不兼容（`cmd overlay unavailable` / transaction failed），且只能改资源不能改运行时行为。MIUI Home 的横屏布局计算在 `DeviceConfig.calcGridSize` / `loadCellsCountConfig` 等 Java 方法里，字段如 `sCellCountX/Y`、`sLauncherDatabaseName` 需要在方法执行时动态修改，**LSPosed（Xposed 框架现代版）是唯一能在进程内 hook Java 方法、改参数/返回值/字段的技术路线**。v1.0 尝试用 Magisk overlay 改资源 + LSP hook `Activity.setRequestedOrientation` 前台强制横屏的混合方案。
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
- Test result: 开发者提交了 `landscape_log.txt` 实机日志。

### v1.7.0
- Date: 2026-04-20
- AI involved: Claude Opus 4.7 (web)
- Changed: 在 `loadCellsCountConfig` afterHook 里横屏对调成 `sCellCountX=6 / sCellCountY=4`。
- Fixed: 横屏数据库切到 `launcher6x4.db`（首次实证 MIUI 按 grid 名分库的机制）。
- Still broken: 6×4 太密，开发者改要求为 6×3。
- New bugs: 无。
- Risk / Notes: 第一次改静态字段，无重入挡板。
- Next AI should know: 这版证明了"只改 `sCellCountX/Y`，sLauncherDatabaseName 会自动跟着切"这条最关键的机制。
- User approval required before: —
- Test result: 切换 db 名生效，但开发者视觉觉得拥挤。

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
- Still broken: 开发者报告 UI 还是乱（图标重叠、第三页滑不动、文件夹内容丢失、点击区域错位）。这不是数据层 bug，是 View 层 / 数据库层的更深问题。
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
- Test result: 开发者报告点击错位、分页错乱。

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
- New bugs: 多页混排；FolderCellLayout 也被误注入；竖屏数据库被污染（开发者报告"卸载模块后竖屏依然乱"）。
- Risk / Notes: 这是事故链的高峰。
- Next AI should know: 任何 `bindScreens` 前 `removeAllScreens`、任何对 `CellLayout.mCountX/Y` 不加方向守卫的注入都是地雷。
- User approval required before: 任何 View 层 hook 改动。
- Test result: 开发者开始报告"竖屏也被污染了"——卸载模块后竖屏图标位置仍然错乱。

### v1.9.1
- Date: 2026-04-21
- AI involved: Claude Opus 4.7 (web)
- Changed: 紧急修复 v1.9.0 引入的事故。CellLayout 注入加双层守卫（必须横屏 + 必须是 Workspace 直接子 View，排除 FolderCellLayout）；删除 `bindScreens` hook 整个；删除 `onIdpChanged` / `reloadSearchBarIfNeed` / 任何 model reload 链；删除 `onConfigurationChanged` 里的 requestLayout 全局调用。
- Fixed: View 层注入扩散；FolderCellLayout 被污染；竖屏数据库被持续污染。
- Still broken: View 层路线整体方向就是错的。
- New bugs: 无新增；但事故已经把竖屏 launcher4x6.db 写脏了（开发者数据残留），这是模块卸载也救不回来的——只能开发者手动整理或恢复默认布局。
- Risk / Notes: 这是事故止血版。
- Next AI should know: View 层路线必须放弃；下一步必须想新方向。
- User approval required before: 重新引入任何 CellLayout/Workspace 层 hook。
- Test result: 症状改善但未完全恢复；开发者已经决定放弃自动迁移路线，转向"双桌面"。

### v2.0.0
- Date: 2026-04-22
- AI involved: Claude Opus 4.7 (web)
- Changed: 路线大转向——放弃"4×6 → 6×3 自动迁移"主方案，改为"双桌面"：横屏独立 `launcher6x3.db`，首次进入空白桌面，开发者自己拖图标。**全删 View 层 hook**，只保留 6 个 DeviceConfig 数据层 hook：`isRotatable` / `loadCellsCountConfig` / `calcGridSize` / `confirmCellsCount` / `loadScreenSize` / `setRequestedOrientation`。基于**错误假设**："MIUI 的 `tryToMigrateDefaultDatabase` 会用 default_workspace.xml 初始化新库"。
- Fixed: View 层事故链全部清掉。
- Still broken: 双桌面假独立——后续证据（platform-tools.zip）显示 launcher6x3.db 实际是 launcher4x6.db 的字节级克隆。
- New bugs: 因为基于错误假设，触发了 MIUI `tryToMigrateDefaultDatabase` 的真实行为——把竖屏 db 整库 rename/copy 成横屏名，竖屏 db 被吃掉。
- Risk / Notes: 这版从代码看"很干净"，但实际上把问题从 View 层推到了数据库层。
- Next AI should know: 不能假定 MIUI 会自己生成空模板；必须主动拦截 `tryToMigrateDefaultDatabase`。
- User approval required before: 把 v2.0.0 当作稳定基线。
- Test result: 数据层日志正确，但开发者后来 dump 数据库发现两个 db MD5 完全相同。

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
- Changed: 真双数据库隔离尝试。新增 hook `tryToMigrateDefaultDatabase` beforeHook，横屏时 `param.setResult(null)` 跳过整个方法（让 MIUI 不再吃竖屏 db）。新增 `cleanupIfClonedDb()` 启动一次性清理：检查 launcher6x3.db 是否为 launcher4x6.db 的克隆（同大小 + 前 4KB 字节相同），如果是则删掉（连 journal/wal/shm 一起）。如果竖屏主文件已被吃掉（`launcher4x6.db` 主文件不在但 journal 在），保留 `launcher6x3.db` 不动避免删开发者数据。候选方法名列表：`tryToMigrateDefaultDatabase` / `migrateDefaultDatabase` / `tryMigrateDefaultDatabase`，三个都试 hook。
- Fixed: 理论上阻止了 MIUI 的"竖屏 db 被吃"行为；启动时清理克隆的横屏 db。
- Still broken: 没有真机回归证据。后续 v3 路线放弃了"改 MIUI 原生 launcher DB"的方向，所以这版的修复方案没有被开发者实测验证过。
- New bugs: Unknown / not enough evidence；如果 `tryToMigrateDefaultDatabase` 在 ROM 里被混淆改名，hook 会失效（但不造成事故，最坏情况退化成 v2.0.x 行为）。
- Risk / Notes: 这是 v1/v2 旧路线的最后一版；**之后开发者决定彻底转向 v3 独立 overlay 方案，本路线不再继续**。launcher6x3.db 路径直接在 hook 类里 hardcode（适配 11T Pro / xiaomi.eu MIUI 14.0.5.0）。
- Next AI should know: v2.1.0 的两个核心思路（拦截 `tryToMigrateDefaultDatabase` + 启动检查 db 克隆）是早期路线下唯一可能让"双 launcher db 真独立"的方案；但既然主线已经放弃改 MIUI 数据库，这版只作为"如果未来还想走原生 launcher DB 路线，必须先拦这两个东西"的踩坑记录。
- User approval required before: 恢复 v1/v2 路线作为主线；任何"读写 MIUI Home 的 launcher4x6.db / launcher6x3.db"代码。
- Test result: 本地有 `MiuiHomeLandscape_v2.1.0.zip`、`v2.1.0_latest_MiuiHomeLandscapeModule.java`；真机回归未做。

### MiuiHomeLandscape_HANDOFF_for_Codex.zip（2.1.0交接包）
- Date: 2026-04-22
- AI involved: Claude Opus 4.7 (web)
- Changed: v2.1.0 之后需求要求"总结所有版本和 bug 路径，发给 codex"。本会话产出 4 类内容打包到 zip：
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
- Still broken: 容量太小，不满足开发者后续“横屏一页显示更多 app”的目标。
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
- Still broken: 开发者实测仍然是视觉 4×2，而不是 8×2；overlay 父容器还挂在 `screen_content`（继承 MIUI 竖屏宽度 1080px）。
- New bugs: 无新增。
- Risk / Notes: fix1 只动了 Listener 完整性和首次播种；page-width 根因没碰。
- Next AI should know: fix1 不是输入模型修复，是先把"能跑"修通。视觉/输入仍然假 8×2。
- User approval required before: 把 fix1 当稳定基线继续开发。
- Test result: 开发者真机回归不通过，触发 fix2。

### v3.0.0-beta16-reset_fix2-8x2-independent
- Date: 2026-04-24
- AI involved: Claude Opus 4.7
- Changed: 针对 fix1 真机回归不过的根因修复 —— overlay 之前挂在 `screen_content`（MIUI 给竖屏 Workspace 准备的容器，横屏不会重算尺寸 → overlay 继承 1080px 宽，8 列被挤成视觉 4 列）。改成优先挂 `android.R.id.content`（Activity 的 contentView FrameLayout，由 Window 按物理屏幕尺寸管理），fallback `drag_layer`，`screen_content` 永久弃用。`LandscapeOverlayView.onMeasure` 加 DisplayMetrics 兜底：当父给的 widthSpec < 物理横屏宽度的 98% 时强制覆写。`LandscapePagedGridView` 改成自定义 `ViewGroup` 自管 `onMeasure`/`onLayout`，每页严格 = `getWidth()`，加 `Scroller` 吸附翻页。`onConfigurationChanged`/`onResume` 里 `refreshOverlay()` 都 post 到下一帧避免拿到陈旧 width。
- Fixed: overlay 继承竖屏宽度问题；page width != viewport width 问题。
- Still broken: 开发者实测照片显示页 1 右半屏漏出页 2 应用、第三行溢出到 dock。视觉看起来"画"对了一些，但**输入模型（长按拖拽落点）仍是 4×?**。
- New bugs: 无新增。
- Risk / Notes: fix2 只把布局骨架修对；Pager 内 PageView 仍 `setClipChildren(false)`，cell 可画出 rect。父布局还是 LinearLayout+weight，软几何容易被父 clip 夹小。
- Next AI should know: fix2 是布局修，不是输入模型修；触摸/长按还在走原生 MIUI 路径。
- User approval required before: 把 fix2 当稳定基线。
- Test result: 开发者真机回归不通过，发了带红/橙标注的失败照片，触发 fix3。

### v3.0.0-beta16-reset_fix3-hardzoned-8x2
- Date: 2026-04-24
- AI involved: Claude Opus 4.7
- Changed: 针对 fix2 视觉 bleed 的最后一轮"画面"修复。`LandscapeOverlayView` 从 `LinearLayout(VERTICAL) + grid(weight=1) + dock(fixed)` 彻底换成自实现 `ViewGroup`，`onMeasure`/`onLayout` 硬分两个矩形：`grid rect = (0, 0, W, H - dockH)`，`dock rect = (0, H - dockH, W, H)`，两块都强制 `MeasureSpec.EXACTLY`，全链路 `setClipChildren(true)` + `setClipToPadding(true)`。`LandscapePagedGridView.PageView` 把 `setClipChildren(false)` 翻成 `true`，堵死跨 cell 视觉漏出。
- Fixed: 7 条不变量里前 4 条（page width ≡ viewport width / page bounds 严格在 grid rect 内 / 每页只 layout `min(childCount,16)` / 第 17 个 app 走下一页）和第 7 条（rect 外画都被 clip）。
- Still broken: **开发者实测后明确指出：长按拖动 app 时暴露出来的真实拖拽/命中模型仍然是 4×?，不是 8×2**。说明 fix3 修的还是"画面"层；底层 MIUI Workspace/DragLayer 仍在接事件。这条反馈是 v4.0 重构的直接触发器。
- New bugs: 无新增；但暴露"v3 链路里的所有 fix 都只在视觉层，输入模型从未被 overlay 接管"这个根本结构性问题。
- Risk / Notes: hardzoned 是 v3 的最后一次"补漏"，再叠补丁没意义；下一步必须重构事件链。
- Next AI should know: 不要再把这类问题当 UI 微调修；validation 必须靠 adb logcat，而不是截图。
- User approval required before: 把 fix3 当稳定基线；恢复 LinearLayout+weight 软布局。
- Test result: 开发者真机不过；发了"现在请直接进入 v4.0 方向"的指令，要求重建输入模型。

> 以下为可正常运行的lsposed V4.0模块，并开始改进,但4.0.x-4.1.41多为修bug和增加功能有参考意义，而v4.5.0没有参考意义。

### v4(Claude 补 Claude 自己版本细节，不影响 APK)（请无视，这是补充，只在文档显示没有影响apk）
- Date: 2026-04-25
- AI involved: Claude Opus 4.7
- Changed: 按指示"补充你有关的细节，codex 负责编辑的版本不要动"补 4 条 Version Log 条目（之前都是 `Unknown / not enough evidence`，现在补成 Claude 在本会话能直接证伪的事实）：
  - `v3.0.0-beta16-reset_fix1`: 编译断裂修复 + first-landscape seed
  - `v3.0.0-beta16-reset_fix2-8x2-independent`: parent 从 `screen_content` 切到 `android.R.id.content` + DisplayMetrics 兜底 + Pager 自管 measure
  - `v3.0.0-beta16-reset_fix3-hardzoned-8x2`: Overlay 改自实现 ViewGroup 硬分区 + clipChildren 全开
  - `v4.0.0`(Claude 分支): `onTouchEvent return true` + nativeWorkspace/Hotseats GONE + DragLayer/ScreenContent NATIVE_BLOCKER + 5 类日志全链路
  并明确：本 Claude 的 v4.0.0 和 Codex 主线 `v4.0.0-codex-fixed → v4.0.30` 是平行分支，不要混。
- Fixed: 文档里 Claude 参与版本的"AI involved/Changed/Fixed/Still broken/Test result" 全部由 `Unknown` 补成实证。Codex 主线（`v4.0.0-codex-fixed` / `v4.0.1` ~ `v4.0.30` / `v4.0.30-doc-OC`）一字未动。
- Still broken: 文档归档；不影响 APK 功能；不修任何代码。
- New bugs: 无。
- Risk / Notes: 此条目仅是文档同步；未编译、未安装、未跑 adb。Claude v4.0.0 zip 在 `MiuiHomeLandscape_v4.0.0.zip`、配套验证文档在 `V4.0_VERIFY_INPUT_MODEL.md`。
- Next AI should know: 后续如果要把 Claude v4.0.0 那条分支并回主线，必须先和开发者对齐 `nativeWorkspace=GONE` / `NATIVE_BLOCKER` 这两个改动是否与当前 v4.0.30 路线兼容（v4.0.29 修过 `setNativeGone(nativeRecentsContainer, false)` 引发的竖屏触摸事故，逻辑相似但 view 不同）。
- User approval required before: 把 Claude v4.0.0 替换主线；改文档尾号规则；改本条目本身。
- Test result: 文档更新；不构建、不安装、不修改代码。

### v4.0.0Alpha
- Date: 2026-04-25 / 2026-04-30 evidence mixed
- AI involved: Claude / Codex mixed, exact split incomplete
- Changed: v4 主线开始从“修 MIUI Home 原生横屏”转为“横屏独立 overlay 桌面”。目标是让横屏自维护布局、点击、拖动、Dock、分页，不再依赖竖屏坐标迁移。
- Fixed: 建立 v4 方向：不改 MIUI launcher DB，不自动迁移竖屏布局，不再走 DeviceConfig / launcher6x3.db 路线。
- Still broken: 早期 overlay 尚不稳定，touch / drag / hit-test / page model 都还在重建。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这是 v4 方向节点，不是可长期使用版本。
- Next AI should know: v4 的核心价值是“独立 overlay 桌面框架”，不是某个具体 UI 参数。
- User approval required before: 恢复原生 DB / DeviceConfig / 自动迁移路线。
- Test result: Unknown / not enough evidence

### v4.0.0-codex-fixed
- Date: 2026-04-25
- AI involved: OpenAI Codex GPT-5.5
- Changed: Codex 主线修正 v4.0.0 overlay 初始化、touch 接管、hit-test / drag / drop 方向，开始证明横屏必须自己接管交互模型，而不是让 MIUI Home 原生 4x? 模型继续工作。
- Fixed: 明确 overlay 必须消费 touch，阻止事件穿透到底层 Workspace。
- Still broken: 拖动、分页、Recents、文件夹等还未稳定。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这是 Codex v4 主线关键入口。
- Next AI should know: 后续 v4.x 都应以 overlay 自维护为基线。
- User approval required before: 删除 overlay 接管逻辑或恢复原生 Workspace 交互。
- Test result: Unknown / not enough evidence

### v4.0.1-v4.0.12（早期 overlay / page / touch 测试段）
- Date: 2026-04-25 至 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 连续测试横屏 overlay 的 attach parent、page size、hit-test、longClick、drag/drop、Dock 基础交互。
- Fixed: 逐步确认“只改视觉 8x2/8x3 不够”，底层 page / hit / drag 必须一致。
- Still broken: 这段版本多为试验和事故修复，缺少稳定长期价值。
- New bugs: 早期出现过黑屏、app 消失、拖动异常、MIUI Home 报错等。
- Risk / Notes: 已压缩为区间记录；不要逐个版本回滚参考，除非需要查具体历史事故。
- Next AI should know: 该段只保留结论：overlay 模型必须完整接管。
- User approval required before: 恢复这段任一临时实现作为主线。
- Test result: 多轮实机反馈失败 / 部分失败。

### v4.0.13
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 数据层从 SQLite 方向切到 SharedPreferences/KV 的 `LandscapeStore` v4 store，原因是目标设备上 MIUI Home 数据目录反复出现 `SQLITE_IOERR_FSTAT` 类问题。
- Fixed: 横屏布局数据不再依赖 MIUI Home 原生 launcher DB，也不依赖容易失败的 SQLite 文件访问。
- Still broken: UI 和交互仍需大量修复。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 这是 v4 数据层关键节点。
- Next AI should know: V5.0 如要换数据层，必须先迁移/兼容 v4 store，不能直接清库。
- User approval required before: 清横屏数据、改回 SQLite、触碰 MIUI Home 原生 DB。
- Test result: Unknown / not enough evidence

### v4.0.14-v4.0.27（Recents / Dock / 手势实验段）
- Date: 2026-04-30
- AI involved: OpenAI Codex GPT-5.5
- Changed: 继续补横屏桌面、Dock、手势条、Recents、后台任务显示/清理等功能；v4.0.27 是 native Recents dismiss 参考点。
- Fixed: 部分横屏后台、Dock 和拖动问题被推进。
- Still broken: Recents 路线不稳定，native dismiss 与 overlay recents 两条方向不能混用。
- New bugs: 后台排序、清后台同步、退出后台、卡顿、左侧漏桌面等问题在后续继续出现。
- Risk / Notes: v4.0.27 可作为 native dismiss 历史参考，但不能未经开发者同意合回。
- Next AI should know: Recents 是 V5.0 高风险重构目标。
- User approval required before: 合回 v4.0.27 native Recents 路线。
- Test result: 部分构建/安装成功，但实机反馈仍有问题。

### v4.0.28-v4.0.30（Claude overlay/recents 新方向与交接节点）
- Date: 2026-04-30
- AI involved: Claude Code Opus 4.7 & OpenAI Codex GPT-5.5 
- Changed: Claude 方向推进 overlay/recents 新方案；v4.0.30 是文档/打包交接节点，无业务逻辑大改。
- Fixed: v4.0.29 修过 v4.0.28 引入的竖屏触摸事故方向。
- Still broken: 横屏 Recents 仍需实机确认。
- New bugs: v4.0.28 曾引入竖屏触摸事故。
- Risk / Notes: 这段是 OC 混合交接，不要把 Claude 分支和 Codex 分支细节混写。
- Next AI should know: v4.0.30 后进入大量 v4.0.31+ 实机修 bug 阶段。
- User approval required before: 重写 Claude 管辖版本细节。
- Test result: Unknown / not enough evidence

### v4.0.31-v4.0.41（Recents / 文件夹 / 返回桌面大修段）
- Date: 2026-04-30 至 2026-05-01
- AI involved: OpenAI Codex GPT-5.5 & Claude Code Opus 4.7 
- Changed: 围绕横屏后台、文件夹视觉、返回桌面白线、手势条召唤、横竖分离短暂失效做连续修复。
- Fixed: 部分文件夹视觉、后台位置、reflow、返回桌面布局重算、白线问题被阶段性修复。
- Still broken: 后台退出仍可能卡顿，Recents 同步清理仍不稳定，文件夹还未达到 MIUI 原生级。
- New bugs: 多轮出现左侧漏桌面、黑边、白线、后台难退、应用打开/退出短暂重排。
- Risk / Notes: 该区间版本多为实机快速补丁，长期价值是问题清单，不是架构基线。
- Next AI should know: V5.0 应把这些问题抽象为 insets/recents/folder 三条重构线。
- User approval required before: 恢复其中任一临时补丁为核心架构。
- Test result: 多轮构建安装成功；实机反馈有修复也有复发。

### v4.0.42-v4.0.58（返回桌面 / 后台 / 小白条收敛段）
- Date: 2026-05-01
- AI involved: OpenAI Codex GPT-5.5
- Changed: 重点修复打开/退出应用时底部白线、桌面重排、返回桌面短暂显示竖屏横向状态、后台难划出、手势条显示/隐藏。
- Fixed: 开发者后续反馈“打开应用和退出会像桌面重建”问题阶段性修复；v4.0.58 曾被设备显示为模块版本。
- Still broken: 当时开发者仍反馈问题依旧，后续继续修。
- New bugs: 后台敏感、退出后台困难、左侧漏桌面等仍出现。
- Risk / Notes: 这是 insets / gesture / lifecycle 补丁密集段。
- Next AI should know: V5.0 需要把“窗口 insets 变化导致重布局”的处理做成统一策略。
- User approval required before: 大改系统栏/手势条 hook。
- Test result: 构建安装成功；实机多次反馈仍需继续修。

### v4.0.59-v4.0.69（v4.0 后期稳定化段）
- Date: 2026-05-01
- AI involved: OpenAI Codex GPT-5.5
- Changed: 继续收敛后台、返回桌面、文件夹和拖动细节，为 v4.1 文件夹大修做准备。
- Fixed: 最终反馈一段时间“目前没有问题了”，随后开始优化应用文件夹。
- Still broken: 文件夹功能仍不完整。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 该段压缩为区间，不再逐条保留测试版本。
- Next AI should know: v4.0.69 之后重点从后台/白线转向文件夹。
- User approval required before: 回退到 v4.0 后期任一测试 APK。
- Test result: 部分版本已确认问题收敛。

### v4.5.0（取消）（尝试miui原版逻辑方向重构文件夹）
- Date: 2026-05-01 
- AI involved: OpenAI Codex GPT-5.5 
- Changed: 曾尝试进入 v4.5 重构文件夹方向。
- Fixed: 无长期有效结论。
- Still broken: 实机反馈“只有一个透明文件夹框，还是在文件夹桌面下面，完全失败”。
- New bugs: 文件夹层级/显示失败。
- Risk / Notes: 已按需求要求取消 v4.5，回退到上一个版本继续优化。
- Next AI should know: 不要恢复 v4.5 失败方向。
- User approval required before: 重新启用 v4.5 分支。
- Test result: 实机否定。

### v4.1.0-v4.1.11（文件夹大修第一段）
- Date: 2026-05-01 至 2026-05-02
- AI involved: Claude Code Opus 4.7 & OpenAI Codex GPT-5.5 
- Changed: 进入 v4.1：文件夹名称编辑、文件夹内 app 拖动/换位、拖出到桌面、遮罩/输入法联动、点击空白误启动等问题连续修复。
- Fixed: 文件夹第一个 app 不能换位、拖动动画方向乱跳、文件夹空白处启动最近 app 等问题被定位/修过。
- Still broken: 开发者后续仍反馈文件夹移动/编辑被干报废、长按逻辑不一致等问题。
- New bugs: 文件夹移动/编辑功能曾被修坏，出现“移动编辑全消失”。
- Risk / Notes: v4.1.0 代表大规模修 bug 入口，不是最终稳定点。
- Next AI should know: 文件夹 bug 要看真实手势和日志，不能只凭截图。
- User approval required before: 重写文件夹模型或切 v4.5/v5.0。
- Test result: 多轮构建安装成功；实机反馈反复。

### v4.1.19-v4.1.26（文件夹编辑恢复与长按策略段）
- Date: 2026-05-02
- AI involved: OpenAI Codex GPT-5.5
- Changed: 修复文件夹编辑态、长按进入全局编辑、移动后 X 显示/隐藏、文件夹全局编辑和横屏全局编辑状态同步等。
- Fixed: 部分文件夹编辑功能恢复；移动完成后 X 显示策略被调整。
- Still broken: 继续要求“长按才显示 X，移动后隐藏 X”“横屏全局打开文件夹不取消全局编辑”等。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 该段主要是交互策略调试，保留区间结论即可。
- Next AI should know: 长按时间和全局编辑状态必须与横屏桌面一致。
- User approval required before: 改变开发者已确认的编辑态策略。
- Test result: 构建安装成功；实机仍继续修。

### v4.1.33-v4.1.41（文件夹拖入/拖出收尾段，当前 v4 最后节点）
- Date: 2026-05-02
- AI involved: OpenAI Codex GPT-5.5
- Changed: 集中修复拖 app 到文件夹自动打开、打开后继续拖动放置、拖出文件夹回桌面、文件夹 hover 过快/过宽、直接 drop 到文件夹加入文件夹等。最终当前 APK 为 `versionName=4.1.41 / versionCode=161`。
- Fixed: v4.1.37 修复文件夹内 app 点击打不开；v4.1.39 把文件夹 hover 改为精准命中；v4.1.40 修复拖出文件夹后文件夹本体后退一格；v4.1.41 增加桌面/Dock app 直接拖到文件夹图标即可加入。
- Still broken: 文件夹动画、原生 MIUI 级拖拽体验、复杂多指翻页/拖动仍需 V5.0 继续验证和优化。
- New bugs: Unknown / not enough evidence
- Risk / Notes: v4.1.41 是 v4.x 当前收尾节点。它能作为 V5.0 起点，但不要把它当“完全原生级文件夹”。
- Next AI should know: `currentDragDescriptor`、precise folder hover、`removeFolderChildToGrid` 稳定落位、direct drop-to-folder 是当前文件夹链路关键点。
- User approval required before: 删除 hover-open、改文件夹内部排序策略、清横屏数据库、升级 V5.0 主线。
- Test result: `v4.1.41` 构建成功、adb 安装成功、设备确认 `versionName=4.1.41 / versionCode=161`，已非重启 force-stop 激活。

### v4.1.41/v5.0-prep（文档整理节点）
- Date: 2026-05-02
- AI involved: OpenAI Codex GPT-5.5
- Changed: 整理 v4.x 交接文档，把大量连续测试、调参和无长期意义版本合并为区间记录；保留关键架构节点、事故节点、有效结论和 V5.0 接手重点。
- Fixed: 文档从逐版本流水账改为可用于 V5.0 接手的历史归档。
- Still broken: 这是文档整理，不修功能 bug。
- New bugs: Unknown / not enough evidence
- Risk / Notes: 如后续需要找某个被压缩版本的精确细节，应查本地 APK、日志、截图或旧文档备份，不要从区间摘要反推事实。
- Next AI should know: v4.x 框架已搭好，V5.0 应以“优化/重构版”为目标，而不是继续在 v4.x 无限小修小补。
- User approval required before: 开始 V5.0 代码重构、清数据库、改 Recents 核心、改文件夹核心、改系统栏/手势 hook。
- Test result: 文档整理；未构建、未安装、未运行 adb、未改源码。

### v4.1.42-v4.1.53（未完整记录的后续开发段 / unstable branch）
- Date: 2026-05-03 / Unknown exact time
- AI involved: Codex / Claude mixed, exact split not fully recorded
- Changed: 这一段为 v4.1.41 之后的连续快速修复与开发尝试。原目标并不是新增大功能，而是在 v4.1.43 已较稳定的基础上，继续优化横屏 Recents、后台进入/退出、返回桌面、横竖屏后台同步、布局重算、底部白线和 MIUI 原生动画冲突，希望让后台体验更流畅、更稳定。
- Fixed: v4.1.43 被开发者实测认为是这一段中最稳定、最适合封版发布的 APK，可作为当前 stable alpha APK。
- Still broken: v4.1.53 后续开发版出现大量 Recents、返回桌面、横竖屏同步、后台稳定性、数据污染和 MIUI 动画冲突问题，整体稳定性明显下降。部分早期 v3.0 阶段已出现过的旧 bug / 旧症状也重新复发。
- New bugs: 后台任务异常、返回桌面异常、横竖屏后台同步、布局重算、数据污染、V3.0老 bug 复发、MIUI 原生动画与 overlay 冲突加剧。
- Risk / Notes: Codex 没有把 v4.1.42 之后的详细版本变更同步写入交接档案，当前无法可靠还原每个小版本的精确改动。更严重的是，v4.1.43 对应的原始源码快照已丢失，目前只保留 APK；后续如果需要分析 v4.1.43，只能通过 APK 反编译、文件时间、开发者实测反馈、v4.1.53 源码残留和行为对比来重建线索。反编译结果只能作为参考，不能视为完整原源码。
- Next AI should know: 不要默认 v4.1.53 比 v4.1.43 更稳定。v4.1.43 是当前已确认的最稳定 APK；v4.1.53 应视为 unstable dev branch。v4.1.43 之后的开发目标本来只是优化后台流畅度和稳定性，但实际引出了大量系统级回归，因此后续不能继续无脑叠补丁。
- User approval required before: 基于 v4.1.53 继续大修；覆盖 v4.1.43 stable alpha 定位；删除或替换 v4.1.43 APK；把 v4.1.53 当作稳定发布版；根据反编译结果重建源码并直接覆盖现有工程。
- Test result: v4.1.43 = 当前最稳定 APK / stable alpha candidate；v4.1.53 = bug 较多的 unstable dev branch，不建议作为稳定发布版。


