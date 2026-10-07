package blockreminder.game;

import blockreminder.core.CombatSnapshot;
import blockreminder.core.CombatSnapshot.Entity;
import blockreminder.core.CombatSnapshot.Kind;
import com.megacrit.cardcrawl.blights.AbstractBlight;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.stances.AbstractStance;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The only layer that sees live combat. All access here is read-only. */
public final class GameSnapshot {
    private static final String P = "com.megacrit.cardcrawl.powers.";
    private static final String R = "com.megacrit.cardcrawl.relics.";
    private static final String O = "com.megacrit.cardcrawl.orbs.";
    private static final String C = "com.megacrit.cardcrawl.cards.";
    private static final Set<String> MODELED = new HashSet<>(Arrays.asList(
        P + "MetallicizePower", P + "PlatedArmorPower", P + "watcher.LikeWaterPower",
        P + "FeelNoPainPower", P + "ConstrictedPower", P + "IntangiblePlayerPower",
        R + "Orichalcum", R + "CloakClasp", R + "FrozenCore", R + "GoldPlatedCables",
        O + "Frost", O + "EmptyOrbSlot", O + "Dark", O + "Plasma",
        C + "status.Burn", C + "curses.Decay", C + "curses.Doubt", C + "curses.Shame",
        "com.megacrit.cardcrawl.stances.NeutralStance",
        "com.megacrit.cardcrawl.stances.CalmStance",
        "com.megacrit.cardcrawl.stances.WrathStance"
    ));
    private static final Map<Class<?>, Boolean> RELEVANT = new HashMap<>();
    private static final Map<Class<?>, Boolean> DAMAGE_RELEVANT = new HashMap<>();
    private static final Map<Class<?>, List<Field>> SCALARS = new HashMap<>();

    private GameSnapshot() { }

    public static CombatSnapshot capture(AbstractPlayer p, boolean includeEthereal) {
        List<Entity> entities = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int focus = 0;
        boolean unstable = false;
        for (AbstractRelic r : p.relics) {
            Map<String, Integer> values = values(r, AbstractRelic.class);
            if (r.getClass().getName().equals(R + "Orichalcum")) {
                // Read the flag; never call onPlayerEndTurn to discover it.
                values.put("trigger", ((com.megacrit.cardcrawl.relics.Orichalcum) r).trigger ? 1 : 0);
            }
            if (r.getClass().getName().equals("energizedSpire.relics.UnstableMolecules")) unstable = true;
            add(entities, warnings, r, Kind.RELIC, r.relicId, r.name, r.counter, values);
        }
        for (AbstractPower power : p.powers) {
            if (power.getClass().getName().equals(P + "FocusPower")) focus = power.amount;
            add(entities, warnings, power, Kind.POWER, power.ID, power.name, power.amount,
                    values(power, AbstractPower.class));
        }
        for (AbstractOrb orb : p.orbs) {
            Map<String, Integer> values = values(orb, AbstractOrb.class);
            values.put("evoke", orb.evokeAmount);
            if (unstable && orb.getClass().getName().equals(O + "Frost")) {
                Integer remaining = OptionalFields.spireInteger(orb,
                    "energizedSpire.patches.UnstableMoleculesPatches$UnstableMoleculesFieldPatch", "value");
                if (remaining != null) values.put("unstableRemaining", remaining);
                else warnings.add("Unstable Molecules counter unavailable: " + orb.name);
            }
            add(entities, warnings, orb, Kind.ORB, orb.ID, orb.name, orb.passiveAmount, values);
        }
        for (AbstractCard card : p.hand.group) {
            Map<String, Integer> values = values(card, AbstractCard.class);
            values.put("ethereal", card.isEthereal ? 1 : 0);
            values.put("retain", card.retain ? 1 : 0);
            values.put("selfRetain", card.selfRetain ? 1 : 0);
            values.put("exhaust", card.exhaust ? 1 : 0);
            values.put("upgraded", card.upgraded ? 1 : 0);
            add(entities, warnings, card, Kind.CARD, card.cardID, card.name, card.baseMagicNumber, values);
            if (card.getClass().getName().equals(C + "curses.Regret"))
                warnings.add("Regret HP loss may trigger further effects");
        }
        if (p.stance != null) {
            add(entities, warnings, p.stance, Kind.STANCE, p.stance.ID, p.stance.name, 0,
                    values(p.stance, AbstractStance.class));
        }
        for (AbstractBlight blight : p.blights) {
            add(entities, warnings, blight, Kind.BLIGHT, blight.blightID, blight.name, blight.counter,
                    values(blight, AbstractBlight.class));
        }
        if (AbstractDungeon.getCurrMapNode() != null && AbstractDungeon.getCurrRoom().monsters != null) {
            for (AbstractMonster monster : AbstractDungeon.getCurrRoom().monsters.monsters) {
                if (monster.isDead || monster.isDying || monster.escaped) continue;
                for (AbstractPower power : monster.powers) {
                    if (overrides(power.getClass(), AbstractPower.class, "onPlayerGainedBlock", float.class)) {
                        add(entities, warnings, power, Kind.ENEMY_POWER, power.ID, power.name,
                                power.amount, values(power, AbstractPower.class));
                        warnings.add("Enemy block modifier: " + power.name);
                    }
                }
            }
        }
        if (!p.getClass().getName().startsWith("com.megacrit.cardcrawl.characters."))
            warnings.add("Modded character may change end-turn rules: " + p.getClass().getSimpleName());
        return new CombatSnapshot(p.currentBlock, p.currentHealth, focus, p.maxOrbs,
                p.stance == null ? "" : p.stance.ID, includeEthereal, entities, warnings);
    }

    private static void add(List<Entity> entities, List<String> warnings, Object object,
                            Kind kind, String id, String name, int amount, Map<String, Integer> values) {
        String className = object.getClass().getName();
        entities.add(new Entity(kind, className, id, name, amount, values));
        if (!MODELED.contains(className) && relevant(object, kind))
            warnings.add("Unmodeled end-turn effect: " + name + " (" + className + ")");
    }

    private static boolean relevant(Object object, Kind kind) {
        Class<?> type = object.getClass();
        Boolean cached = RELEVANT.get(type);
        if (cached != null) return cached;
        boolean result;
        switch (kind) {
            case POWER:
            case ENEMY_POWER:
                result = overrides(type, AbstractPower.class, "atEndOfTurnPreEndTurnCards", boolean.class)
                    || overrides(type, AbstractPower.class, "atEndOfTurn", boolean.class)
                    || overrides(type, AbstractPower.class, "onExhaust", AbstractCard.class)
                    || overrides(type, AbstractPower.class, "onGainedBlock", float.class)
                    || overrides(type, AbstractPower.class, "onPlayerGainedBlock", float.class)
                    || overrides(type, AbstractPower.class, "onChannel", AbstractOrb.class);
                break;
            case RELIC:
                result = overrides(type, AbstractRelic.class, "onPlayerEndTurn")
                    || overrides(type, AbstractRelic.class, "onPlayerGainedBlock", float.class)
                    || overrides(type, AbstractRelic.class, "onExhaust", AbstractCard.class);
                break;
            case CARD:
                result = overrides(type, AbstractCard.class, "triggerOnEndOfTurnForPlayingCard")
                    || overrides(type, AbstractCard.class, "triggerOnEndOfPlayerTurn")
                    || overrides(type, AbstractCard.class, "triggerOnExhaust");
                break;
            case ORB: result = true; break;
            case STANCE: result = overrides(type, AbstractStance.class, "onEndOfTurn"); break;
            default: result = true; break;
        }
        RELEVANT.put(type, result);
        return result;
    }

    private static boolean overrides(Class<?> type, Class<?> base, String name, Class<?>... args) {
        try {
            Method m = type.getMethod(name, args); // inspect only; never invoke
            return m.getDeclaringClass() != base;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return true;
        }
    }

    private static boolean damageRelevant(Class<?> type, Class<?> base) {
        Boolean cached = DAMAGE_RELEVANT.get(type);
        if (cached != null) return cached;
        boolean result = overrides(type, base, "onAttacked", DamageInfo.class, int.class)
                || overrides(type, base, "onAttackedToChangeDamage", DamageInfo.class, int.class)
                || overrides(type, base, "onAttackToChangeDamage", DamageInfo.class, int.class)
                || overrides(type, base, "onAttack", DamageInfo.class, int.class, AbstractCreature.class)
                || overrides(type, base, "onLoseHp", int.class);
        if (base == AbstractPower.class) {
            result |= overrides(type, base, "wasHPLost", DamageInfo.class, int.class)
                    || overrides(type, base, "onInflictDamage", DamageInfo.class, int.class, AbstractCreature.class);
        } else {
            result |= overrides(type, base, "wasHPLost", int.class)
                    || overrides(type, base, "onLoseHpLast", int.class)
                    || overrides(type, base, "onBlockBroken", AbstractCreature.class)
                    || overrides(type, base, "onBloodied");
        }
        DAMAGE_RELEVANT.put(type, result);
        return result;
    }

    /** Useful scalar fields for value-only integrations; no object references. */
    private static Map<String, Integer> values(Object object, Class<?> base) {
        Class<?> type = object.getClass();
        List<Field> fields = SCALARS.get(type);
        if (fields == null) {
            fields = new ArrayList<>();
            for (Class<?> c = type; c != null && c != base; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if (f.getType() != int.class && f.getType() != boolean.class) continue;
                    try { f.setAccessible(true); fields.add(f); }
                    catch (RuntimeException ignored) { }
                }
            }
            SCALARS.put(type, fields);
        }
        Map<String, Integer> values = new LinkedHashMap<>();
        for (Field field : fields) {
            try {
                Object value = field.get(object);
                values.put("field:" + field.getDeclaringClass().getName() + "." + field.getName(),
                        value instanceof Boolean ? ((Boolean) value ? 1 : 0) : (Integer) value);
            } catch (IllegalAccessException ignored) { }
        }
        if ((base == AbstractPower.class || base == AbstractRelic.class)
                && !MODELED.contains(type.getName()) && damageRelevant(type, base))
            values.put("unmodeledDamageCallback", 1);
        return values;
    }
}
