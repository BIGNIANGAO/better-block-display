# 更好的格挡显示

《杀戮尖塔》PC 版的回合末格挡预览 mod。按旧版 [Block Reminder](https://steamcommunity.com/sharedfiles/filedetails/?id=1974165911) 的公开说明独立重实现，并处理评论指出的遗漏和预览副作用。未复制旧 mod 源码或素材。

1.0.9 直接让原版护盾内的数字显示预计回合末总格挡，不再绘制额外图标或外置数字。例如当前 13、回合末增加 6 时，护盾显示 19；悬停显示「13 → 19（+6）」及来源。当前格挡为零、预估大于零时也显示原版护盾。预计值为零时，仅勾选「始终显示」才显示护盾与 0；关闭时隐藏护盾、数字及对应悬浮提示，即使当前还有格挡或预估有 ≈ 提示。部分预估仅在悬浮提示中以 ≈ 标记。实际格挡和伤害结算不变。点击结束回合后，在玩家回合末结算期间保留预计值，抑制护盾重复增加的放大、闪色和弹入动画；结算期间不显示预估悬浮提示。敌人行动前恢复实际值，受击扣格挡仍实时显示。出牌结算或关闭模组时保持原版实际值；未适配效果导致实际结算偏离预估时，以实际结果为准。

游戏内设置与预估提示支持游戏的全部 24 种正常语言，随游戏语言切换，包括简体中文、繁体中文、英文、日文、韩文、法文、德文、俄文等；缺少语言资源时回退英文。来源名称使用游戏或对应模组提供的名称。ModTheSpire 模组列表中的名称与简介目前固定中文。完整列表与翻译范围见 [语言支持](docs/localization.md)。

![简洁界面，来自隔离游戏场景](docs/screenshots/compact-ui-zhs.png)

## 安装

1. 订阅并启用 ModTheSpire、BaseMod。
2. 从 [GitHub Releases](https://github.com/BIGNIANGAO/better-block-display/releases) 下载 `better-block-display-<version>.jar`，或自行构建生成 dist/更好的格挡显示.jar，然后复制到 `<game-directory>\mods`。更新时移走旧版本 JAR 和旧 BlockReminderReborn.jar，避免同一模组加载两份。
3. 在 ModTheSpire 中启用「更好的格挡显示」，停用旧 Block Reminder，然后启动游戏。
4. 主菜单 Mods 设置依次为「启用」「计入虚无牌消耗与无惧疼痛」「始终显示」。第二项计入虚无牌在回合末消耗时触发无惧疼痛的格挡；第三项用于预估为零时仍显示护盾。护盾固定显示预计总量，旧「显示总量」配置保留兼容但不再影响显示。

本项目已生成可用 JAR，未自动安装到游戏目录。验证使用项目内隔离环境。

## 支持范围

| 来源 | 规则 |
| --- | --- |
| 奥利哈刚 Orichalcum | 当前格挡为 0 或触发标记生效时计入 6，可与其他回合末增益叠加 |
| 披风扣 Cloak Clasp | 按当前整个手牌数量，在丢弃/虚无消耗前计算 |
| 金属化、多层护甲 | 当前层数 |
| 如水 Like Water | 当前姿态为平静时计入 |
| 冰霜球 | 读取已包含集中等变化的被动值，不重复加集中 |
| 镀金缆线 | 第一个球位为冰霜时额外触发一次 |
| 冰冻核心 | 有空球位和球容量时预估新冰霜，也参加当回合/缆线结算 |
| 无惧疼痛 | 计入回合末会实际消耗的虚无牌；不会计算所有带“消耗”关键词的手牌 |
| 保留/虚无 | 显式 retain/selfRetain 先移入临时区；符文金字塔/均衡本身不会阻止虚无消耗 |
| 灼伤、腐朽、缠绕 | 按阶段扣减格挡；玩家无实体减少相应伤害 |
| 敏捷、脆弱、恐慌按钮 | 不错误套用到直接获得格挡的遗物、能力、冰霜被动 |
| 格挡上限 | 普通规则下按 999 上限逐次结算 |
| Energized Spire 不稳定分子 | 只读剩余次数，0 次不计入；读取失败提示部分预估 |
| Together in Spire / Cryogenetics | 不执行冰霜回调、不发送联机格挡；仅预估本地玩家回合末 |

新增语言只影响设置及提示文字，格挡计算规则和界面布局保持一致。

兼容不等于精确计算全部第三方机制。The Servant 的 Moon Phase、其他角色的专有机制、随机消耗、全局改写规则及未知格挡修改器没有通用精确算法。遇到未适配相关效果时在悬浮提示中显示 ≈ 并列出原因，保留已知来源的预览。第三方全局补丁可能绕开可检测回调，无法保证任意组合都能识别所有变化。远端玩家未来行动不在预览范围内。

## 构建和验证

需要 JDK 8 或更新版本，以及 Windows PowerShell 5.1 或 PowerShell 7。含中文路径的发布脚本使用 UTF-8 BOM，修改时应保留该编码。脚本从 Steam 注册表和库清单寻找依赖，产物兼容 Java 8，不打包游戏/框架 JAR。不需要 Maven。

~~~powershell
.\test.ps1
.\build.ps1
.\smoke.ps1
.\smoke.ps1 -WithStSLib
.\smoke.ps1 -Language ZHS -WithStSLib
.\audit.ps1
~~~

纯计算测试不依赖游戏、不需要联网。build.ps1 默认先测试再编译、打包和生成 SHA-256。自动发现失败时，build.ps1 接受 -GameDir、-BaseModJar、-ModTheSpireJar、-JdkHome 四个显式路径参数。

smoke.ps1 复制游戏和测试依赖到 build/runtime，用游戏附带的 Java 8 运行真实 ModTheSpire。短暂打开测试窗口，运行真实游戏类断言、保存截图后退出。测试专用补丁关闭发行平台与 Steam 控制器接口；存档、APPDATA、LOCALAPPDATA、user.home 使用隔离目录。**SmokeHarness.jar 和测试夹具不能安装到正常游戏。**

研究依据见 [docs/research.md](docs/research.md)，验证范围见 [docs/verification.md](docs/verification.md)，扩展接口见 [docs/extension-api.md](docs/extension-api.md)。

原额外图标的 SVG 和 PNG 留作历史设计素材，1.0.5 不再加载或绘制，直接复用游戏原版护盾与字体。构建不需要图像服务或 API key。

## 结构

- src/main/java/blockreminder/core：不可变快照和纯计算器。
- src/main/java/blockreminder/game：只读适配、缓存、显示。
- src/main/java/blockreminder/patches：原版护盾显示值、显示条件和悬浮提示补丁。
- src/test：核心回归；src/smoke：实际加载与渲染测试。
- dist：本机构建生成的发布 JAR 和校验文件，不提交到源码仓库；下载见 GitHub Releases。
- build、.tools：本机验证/研究材料，不属于发布内容。

独立实现采用 MIT 许可证。游戏和第三方依赖归各自权利人；发布包仅包含自身代码和资源。

文档中的游戏截图和演示图片用于展示模组效果，其中的游戏与第三方美术、界面及商标归各自权利人，不纳入本项目的 MIT 授权范围。
