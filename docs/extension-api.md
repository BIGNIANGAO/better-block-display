# 第三方扩展

初始化时使用 PreviewRegistry.register 注册 PreviewContributor。输入是不可变 CombatSnapshot：当前格挡、集中、姿态、球容量，以及实体的类名、ID、名称、整数/布尔字段；没有活的玩家、卡牌、球、队列或随机数对象。

~~~java
PreviewRegistry.register(new PreviewContributor() {
    public String id() { return "my-mod:moon-armor"; }
    public boolean supports(CombatSnapshot.Entity e) {
        return e.className.equals("my.mod.powers.MoonArmorPower");
    }
    public List<BlockEvent> preview(CombatSnapshot s) {
        CombatSnapshot.Entity armor = s.find("my.mod.powers.MoonArmorPower");
        if (armor == null) return Collections.emptyList();
        return Collections.singletonList(
            BlockEvent.gain(BlockEvent.Phase.POWER, armor.name, armor.amount));
    }
});
~~~

此例只说明接口，不是 The Servant / Moon Phase 的真实算法。

supports 声明完整建模的具体类。仅在本次扩展成功返回有效事件后，对应未建模提示才被覆盖，其他不确定性仍保留。不要对未处理效果声明支持，也不要对已支持的原生来源重复追加事件。

1.0.9 将未适配能力/遗物的受伤回调记录为实体标志 `unmodeledDamageCallback`。只要本次事件包含非零 DAMAGE，就保留对应部分预估提示，即使伤害被格挡完全吸收；没有伤害事件时不单独因此添加提示。成功声明支持该具体类的扩展可以覆盖此提示，但必须完整建模它的相关反应。未知受伤算法只提示，不执行真实回调。

阶段依次为 RELIC、POWER、ORB、END_TURN_CARD、LATE_POWER、EXHAUST，同阶段保持顺序。GAIN 是直接获得格挡，DAMAGE 是会消耗格挡的伤害。接口未模拟任意姿态改变、随机效果、球替换或网络同步；需要这些行为时应扩展纯模型，不能试运行游戏回调。

实例整数/布尔字段以 field:完整声明类名.字段名 读取。不能把缺失字段当作已确认值。扩展必须保持纯函数，不访问 AbstractDungeon 或真实效果/网络；错误和无效事件会隔离并标为部分预估。
