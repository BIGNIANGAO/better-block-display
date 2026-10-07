# 研究记录

核对日期：2026-10-06。依据工坊说明、全部评论、本机当前游戏字节码及第三方公开实现。

## 原 mod 和评论

[Block Reminder，1974165911](https://steamcommunity.com/sharedfiles/filedetails/?id=1974165911) 原说明支持奥利哈刚、披风扣、金属化、多层护甲、如水、冰霜。通过公开分页接口读取全部 **15 条评论**，下表仅保留问题摘要与处理方式；不公开评论者用户名、逐条时间戳或评论原文。原始讨论可通过上述工坊链接查看。

| 问题 / 请求 | 处理 |
| --- | --- |
| 镀金缆线漏算 | 第 0 个球位额外触发 |
| 冰冻核心新球漏算 | 只在快照中生成新冰霜 |
| 无惧疼痛漏算 | 支持实际回合末虚无消耗，可关闭 |
| 希望显示最终总量 | 增加设置 |
| 不稳定分子移除冰霜 | 不执行回调，只读计数 |
| Cryogenics 联机问题 | 找到 Cryogenetics / CryoPower 网络副作用，完全不调用 |
| 新角色、The Servant / Moon Phase | 提供扩展接口和未适配提示，未宣称全部精确 |
| “crash” | 无堆栈，不能复现特定故障；增加生命周期守卫和错误隔离 |

Google 返回重定向，DuckDuckGo 返回验证挑战，未据此猜测算法。随后改用 GitHub 公开仓库搜索、作者下载页和本机安装包核对。

## 当前游戏结算依据

检查本机 desktop-1.0.jar，版本 **12-18-2022**，Steam build **10180494**。相关类仅在项目 build/research 中检查，不随发布包分发。

- GameActionManager.callEndOfTurnActions：先遗物，再前置能力，再排入球结算，随后触发回合末自动手牌和姿态。
- AbstractRoom.applyEndOfTurnRelics：遗物条件先全部检查，排队的格挡动作随后结算。奥利哈刚不能用“加上其他来源之后”的小计判断。
- TriggerEndOfTurnOrbsAction.update：所有球触发后，有 Cables 时第 0 个非空球再触发一次。
- FrozenCore.onPlayerEndTurn：有空位时立即生成新冰霜，位于球结算动作之前。
- Frost.onEndOfTurn：将当前 passiveAmount 传入 GainBlockAction。
- AbstractOrb.applyFocus：现有球值已经包含集中；冰冻核心虚拟新球按 max(0, 2 + focus) 计算。
- GainBlockAction.update / AbstractCreature.addBlock：直接加格挡，没有卡牌 modifyBlock 流程，不套敏捷、脆弱、恐慌按钮；逐次遵守上限。
- AbstractRoom.endTurn / DiscardAtEndOfTurnAction.update / AbstractCard.triggerOnEndOfPlayerTurn：显式保留先移到 limbo，再对剩余手牌触发虚无；金字塔/均衡只改变丢弃。
- FeelNoPainPower.onExhaust：每张实际消耗牌产生一次格挡，手中未打出的“消耗”牌不会自动计入。
- Burn.use / Decay.use / ConstrictedPower.atEndOfTurn：按不同阶段消耗格挡，虚无消耗的无惧疼痛增益在后续。
- Regret.use：生命损失不消耗格挡，但可触发其他效果，因此标为部分预估。
- GameActionManager.getNextAction：格挡清除和符文圆盘扣减在下一次玩家回合开始，不能从当前敌人行动前的结果扣除。

## Energized Spire

公开仓库：[JohnnyBazooka89/StSModEnergizedSpire](https://github.com/JohnnyBazooka89/StSModEnergizedSpire)。

核对文件：

- [UnstableMoleculesPatches.java](https://github.com/JohnnyBazooka89/StSModEnergizedSpire/blob/master/src/main/java/energizedSpire/patches/UnstableMoleculesPatches.java)
- [减少次数动作](https://github.com/JohnnyBazooka89/StSModEnergizedSpire/blob/master/src/main/java/energizedSpire/actions/UnstableMoleculesDecreaseValueInOrbAction.java)
- [移除球动作](https://github.com/JohnnyBazooka89/StSModEnergizedSpire/blob/master/src/main/java/energizedSpire/actions/UnstableMoleculesRemoveOrbAction.java)
- [旧预览兼容代码](https://github.com/JohnnyBazooka89/StSModEnergizedSpire/blob/master/src/main/java/energizedSpire/patches/UnstableMoleculesBlockReminderInteraction.java)

该遗物注入剩余次数，冰霜 onEndOfTurn 后排入减少次数动作；0 次时跳过被动，减少至 0 后再排入移除。缆线第二次回调发生在减少次数的队列动作之前，因此剩 1 次的首位冰霜仍可能排入两次格挡，不能用剩余次数直接限制触发数。

旧兼容代码检查旧 mod 的 BlockPreview.isPreview。新版独立 ID，不伪装旧类，不依赖这个全局标记；预览不调用 onEndOfTurn，根本不会排入减少次数或移除动作。

真实加载测试确认 MTS 会将 SpireField 替换为生成的访问器子类，直接反射其私有 field 无法读取实际存储。新版通过框架 SpireField.get 只读已知注入字段，不调用默认值供应器或 set；API 不可用时提示部分预估。

## Together in Spire

[作者版本页](https://github.com/Draco9990/togetherinspire)。检查公开主 mod **6.4.20** 和官方卡包 **1.0.5**。卡包对应名称是 Cryogenetics，能力为 tisCardPack.powers.CryoPower。

CryoPower.OnEndOfTurnPatch 在联网且能力生效时遍历联机玩家，直接调用远端玩家 addBlock(passiveAmount)；激发补丁也有类似行为。执行冰霜回调来预览会进入联机副作用路径。

新版只读本地冰霜，不触发回调或网络消息。这里的兼容是“本地预览不干扰联机”，不是预测队友未来行动，也不是对所有私有/付费版本作保证。未进行多人联网对局验证。

## 降级边界

任意第三方回调可能改变球、消耗随机数、写文件、发网络消息。替换行动队列或事后恢复格挡无法恢复这些副作用。

默认采用只读快照和纯计算。未知相关回调、角色、敌人格挡修改器和读取失败在悬浮提示中显示 ≈ 及原因；外置数字不显示标记。扩展作者可注册值对象算法。全局补丁可能不可检测，限制已公开写入 README。
