# 1.0.8 发布前检查

修复状态：两项 P2 已在 1.0.9 修复并验证。未知受伤场景现在正确显示部分预估，PowerShell 5.1 构建、审计和 smoke 通过；截图尺寸检查也已加入正式 smoke。最新验证见 [验证记录](verification.md)。以下内容保留为 1.0.8 的原始发现和证据，不代表 1.0.9 仍存在这两项问题。

检查日期：2026-10-07。检查对象：当前源码重新构建的「更好的格挡显示」1.0.8。检查没有修改正式 Java 源码、资源、版本号或发布脚本，没有安装到正常游戏或发布到工坊。

结论：发现两项已复现问题，建议处理后再发布。原版已建模场景、加载、资源与打包检查通过，不代表任意第三方组合或完整对局已验证。

## 应修复的问题

### P2：受伤回调未纳入未知效果检测，错误预估仍标为准确

位置：`src/main/java/blockreminder/game/GameSnapshot.java:131`、`:139`，以及 `src/main/java/blockreminder/core/BlockCalculator.java:134`。

未知能力/遗物的相关性检查覆盖回合末、消耗和获得格挡等回调，没有覆盖 `onAttacked(DamageInfo, int)` 等受伤回调。原版灼伤、腐朽和缠绕经过玩家受伤流程，即使伤害完全被格挡吸收，部分回调仍会执行。计算器只在预计损失生命时添加通用警告，不能覆盖这一情况。

隔离真实游戏探针构造当前格挡 3、手中一张普通灼伤、以及仅重写 `onAttacked` 的测试能力；该能力在回调中获得 4 格挡。预览读取没有执行这个回调，随后由原版 `Burn.use` 排入原版 `DamageAction`，真实结算得到：

```text
FINDING reactive onAttacked-only power: forecast=1, actual=5, exact=true, warnings=[]
```

这不是某个第三方原始 mod 的实战复现，而是可检测回调遗漏的最小复现。未知算法不必强行计算，但此场景应显示部分预估，不能继续声称准确。

建议：依据真实伤害流程补齐未知受伤/失去格挡等相关回调检测，在可能执行这些回调的已建模事件中保留不确定性；添加真实受伤动作回归，确认预览仍不会执行回调。避免仅扩大数值猜测或通过试执行实际效果计算。

证据：`build/runtime/prerelease-probe.txt`、`build/prerelease-probe/runtime.log`、`build/prerelease-probe/src/blockreminder/probe/ReleaseProbe.java`。复现命令：在先运行普通 smoke 后，用 PowerShell 7 执行 `./build/prerelease-probe/run.ps1`。探针和 `ReleaseProbe.jar` 只属于 build 内隔离检查材料，不能随正式包分发。

### P2：默认发布脚本在 Windows PowerShell 5.1 中错误解码中文路径

位置：`build.ps1:20`、`audit.ps1:4`、`smoke.ps1:9`。

这些脚本采用无 BOM 的 UTF-8，并在源码中直接包含中文产物名。Windows 自带 PowerShell 5.1 按系统默认编码读取无 BOM 脚本，默认产物路径被错误解码。README 未明确要求 PowerShell 7。

同一 JAR 的对照检查结果：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./audit.ps1
Exit 1: Exception calling OpenRead: Illegal characters in path.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./audit.ps1 -Artifact <正确的完整路径>
Exit 0: PASS release archive audit (36 classes, Java 8, 24 languages, SHA-256 verified)
```

本轮 PowerShell 7 的构建、审计和 smoke 正常。直接确认了 audit 的默认路径失败；build/smoke 具有相同编码条件，未在 PowerShell 5.1 中重新执行构建，以免生成误命名产物。

建议：为含中文的发布脚本采用 PowerShell 5.1 可识别的 UTF-8 BOM，或者在文档和脚本入口明确限定 PowerShell 7，并使用 `pwsh` 的可复现命令。证据：`build/prerelease-powershell51.log`、`build/prerelease-powershell51-explicit-path.log`。

## 已验证

| 检查 | 结果与范围 |
| --- | --- |
| 核心计算回归 | 63 项断言通过 |
| 从当前源码编译和打包 | 通过，JDK 25.0.2 使用 Java 8 目标 |
| 发布包 | 36 个 class，全部 major 52；无游戏、BaseMod、第三方和 smoke 类；许可证存在 |
| 资源一致性 | JAR 内全部正式资源与源码逐文件 SHA-256 一致 |
| 语言结构 | 24 种语言，每种 24 项非空文案，占位符一致 |
| 简中 + StSLib | 1280 × 720，282 项真实游戏及渲染断言通过 |
| 当前版本语言矩阵 | 24 种语言在 1280 × 720 各通过 282 项真实游戏及渲染断言 |
| 尺寸检查 | 英文 1920 × 1080 + StSLib、简中 2560 × 1440、简中 800 × 600、英文 2560 × 1080 各通过 282 项断言，截图实际尺寸匹配 |
| 3440 × 1440 | 断言通过，但实际截图仅 2580 × 1440、右侧场景被截断；此请求尺寸不算视觉验证通过 |
| 原版真实来源对照 | 4 组原生回调/行动结算与预估一致，见下表 |
| 正式代码副作用检查 | 预估只读快照，没有执行回合末效果或发送网络；配置保存和字体补字属于预期行为；正式包没有隔离平台/控制器测试补丁 |

真实来源探针对照：

| 场景 | 预估 / 实际 |
| --- | --- |
| 零格挡 + 奥利哈刚 + 金属化 3 + 冰霜 | 11 / 11 |
| 冰冻核心 + 首空位 + 镀金缆线 | 4 / 4 |
| 格挡 6 + 无实体 + 灼伤 | 5 / 5 |
| 格挡 998 + 冰霜 + 灼伤 | 997 / 997 |

探针显式按阶段调用原版来源回调并排空原版行动；没有把它当作完整战斗循环。现有 smoke 验证结束回合按钮、显示保持、分次增益、零预估开关、敌方阶段恢复实际值、设置持久化、语言回退和实际渲染不改变格挡。

矩阵共运行 29 次 smoke（24 种语言、4 个额外尺寸请求、补测 2560 × 1080；其中英文 1920 × 1080 含 StSLib），每次 282 项断言均通过。28 次的实际画面尺寸匹配；3440 × 1440 的断言结果不能代表该尺寸完整可见。矩阵摘要见 `build/prerelease-matrix.json`，日志和截图见 `build/prerelease-qa/`。矩阵之前还单独运行了简中 720p + StSLib，同样通过 282 项断言。人工抽查简中、英文、日文、希腊语、泰语以及新增尺寸的截图，没有发现匹配尺寸中的 mod 提示遮挡或越界；没有逐一人工检查全部截图。

`src/smoke/java/blockreminder/smoke/SmokeMod.java:553` 使用实际 framebuffer 保存截图，但现有断言未比较其尺寸与 `Settings.WIDTH/HEIGHT`。这属于验证工具的盲点：需新增尺寸一致性检查，避免将被本机窗口限制截断的画面报告为超宽屏通过。本轮辅助矩阵脚本已增加该检查，正式 smoke 脚本未修改。

发布 JAR：`dist/更好的格挡显示.jar`，73,359 字节。本轮 SHA-256：

```text
0a94c6e2ee43807f8ae7a3d509ee6eab626e978916f8675d6134f6195d2d9a4a
```

日志中的 BaseMod 控制台历史文件不存在来自隔离新用户目录，`WWW` 英文回退来自主动测试，OpenAL 退出清理提示没有导致 smoke 失败；没有将这些提示计为本 mod 的故障。

## 发布限制

- 当前目录没有 Git 元数据，无法核对提交、标签或远端一致性。发布前需保存可恢复的源码版本。
- 尚未完成完整对局行动循环、玩家死亡、战斗胜利、随机消耗、多人大局或任意第三方组合的实战回归。
- 本机未完成真实 3440 × 1440 的完整画面验证，已确认的超宽尺寸为 2560 × 1080。
- Together in Spire 联网实战、The Servant 专有机制、Energized Spire 原始二进制全组合仍未验证；测试夹具不能代替第三方原包。
- 自动断言、字体覆盖及截图抽查不能代替 24 种语言的母语审校。
- 工坊上传、自动安装和正常存档没有参与本轮检查。

## 建议发布顺序

1. 修复未知受伤回调的部分预估标记，加入对应真实动作回归。
2. 修复或明确 PowerShell 版本要求，复跑发布脚本。
3. 补做至少一次正常战斗从出牌、结束回合、敌方受击到下一回合的完整检查，并核对胜利/死亡后显示恢复。
4. 重建发布 JAR，更新检查记录及校验文件，再分发正式产物。
