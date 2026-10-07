package blockreminder.core;

import blockreminder.core.CombatSnapshot.Entity;
import blockreminder.core.CombatSnapshot.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import static blockreminder.core.BlockEvent.Phase.*;

/**
 * Pure end-of-player-turn projection for the current PC game. Gains from
 * relics, powers and Frost go straight to addBlock: Dexterity, Frail and
 * Panic Button's card-only restriction must NOT be applied to them.
 */
public final class BlockCalculator {
    private static final String R = "com.megacrit.cardcrawl.relics.";
    private static final String P = "com.megacrit.cardcrawl.powers.";
    private static final String ORB_CLASS = "com.megacrit.cardcrawl.orbs.";
    private static final String CARD = "com.megacrit.cardcrawl.cards.";

    public BlockForecast calculate(CombatSnapshot s) {
        return calculate(s, new ArrayList<BlockEvent>(), new ArrayList<String>());
    }

    public BlockForecast calculate(CombatSnapshot s, List<BlockEvent> additional,
                                   List<String> extraWarnings) {
        return calculate(s, additional, extraWarnings, Collections.emptySet());
    }

    public BlockForecast calculate(CombatSnapshot s, List<BlockEvent> additional,
                                   List<String> extraWarnings, Set<String> coveredClasses) {
        List<BlockEvent> events = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (String warning : s.uncertainties) {
            boolean covered = false;
            for (String type : coveredClasses)
                if (warning.startsWith("Unmodeled end-turn effect:") && warning.endsWith("(" + type + ")"))
                    covered = true;
            if (!covered) warnings.add(warning);
        }
        warnings.addAll(extraWarnings);
        List<Entity> hand = s.ofKind(Kind.CARD);
        List<Entity> orbs = new ArrayList<>(s.ofKind(Kind.ORB));

        // All relic conditions are evaluated before their queued block actions
        // run. Orichalcum tests the present block, not a running sum of gains.
        for (Entity relic : s.ofKind(Kind.RELIC)) {
            switch (relic.className) {
                case R + "Orichalcum":
                    if (s.block == 0 || relic.flag("trigger"))
                        events.add(BlockEvent.gain(RELIC, relic.name, 6));
                    break;
                case R + "CloakClasp":
                    events.add(BlockEvent.gain(RELIC, relic.name, hand.size()));
                    break;
                case R + "FrozenCore":
                    for (int i = 0; i < orbs.size() && s.maxOrbs > 0; i++) {
                        if (orbs.get(i).className.equals(ORB_CLASS + "EmptyOrbSlot")) {
                            if (s.has(R + "DarkCore")) break;
                            java.util.Map<String, Integer> values = new java.util.LinkedHashMap<>();
                            values.put("newFrozenCore", 1);
                            orbs.set(i, new Entity(Kind.ORB, ORB_CLASS + "Frost", "Frost",
                                    relic.name, focused(2, s.focus), values));
                            break;
                        }
                    }
                    break;
                default: break;
            }
        }
        for (Entity power : s.ofKind(Kind.POWER)) {
            switch (power.className) {
                case P + "MetallicizePower":
                case P + "PlatedArmorPower":
                    events.add(BlockEvent.gain(POWER, power.name, power.amount));
                    break;
                case P + "watcher.LikeWaterPower":
                    if ("Calm".equals(s.stance))
                        events.add(BlockEvent.gain(POWER, power.name, power.amount));
                    break;
                default: break;
            }
        }
        Entity cablesRelic = s.find(R + "GoldPlatedCables");
        boolean cables = cablesRelic != null;
        for (int i = 0; i < orbs.size(); i++) {
            Entity orb = orbs.get(i);
            if (!orb.className.equals(ORB_CLASS + "Frost")) continue;
            // The live passiveAmount already includes Focus and mod changes.
            // Never call applyFocus, makeCopy or onEndOfTurn to find this value.
            int passive = Math.max(0, orb.amount);
            if (orb.value("unstableRemaining", -1) == 0) continue;
            int triggers = cables && i == 0 ? 2 : 1;
            for (int n = 0; n < triggers; n++)
                events.add(BlockEvent.gain(ORB,
                        n == 0 ? orb.name : orb.name + " / " + cablesRelic.name, passive));
        }

        // These self-damage sources resolve after early block/orbs and before
        // Ethereal exhaustion. HP-loss curses (Regret) do not spend block.
        for (Entity card : hand) {
            if (card.className.equals(CARD + "status.Burn"))
                events.add(BlockEvent.damage(END_TURN_CARD, card.name, card.amount));
            if (card.className.equals(CARD + "curses.Decay"))
                events.add(BlockEvent.damage(END_TURN_CARD, card.name, 2));
        }
        Entity constricted = s.find(P + "ConstrictedPower");
        if (constricted != null)
            events.add(BlockEvent.damage(LATE_POWER, constricted.name, constricted.amount));
        Entity fnp = s.find(P + "FeelNoPainPower");
        if (fnp != null && s.includeEthereal) {
            for (Entity card : hand) {
                // retain/selfRetain cards are moved to limbo first. Runic
                // Pyramid and Equilibrium leave them in hand: Ethereal still
                // exhausts them. "Exhaust" alone is only an on-play keyword.
                if (card.flag("ethereal") && !card.flag("retain") && !card.flag("selfRetain"))
                    events.add(BlockEvent.gain(EXHAUST, fnp.name + " / " + card.name, fnp.amount));
            }
        }
        events.addAll(additional);
        // Fully blocked damage still runs reactive callbacks. Mark unknown
        // reactions only when a damage event exists, without executing them.
        if (events.stream().anyMatch(e -> e.operation == BlockEvent.Operation.DAMAGE && e.amount > 0)) {
            for (Entity entity : s.entities) {
                if (entity.flag("unmodeledDamageCallback") && !coveredClasses.contains(entity.className))
                    addUnique(warnings, "Unmodeled end-turn effect: " + entity.name + " (" + entity.className + ")");
            }
        }
        events.sort(Comparator.comparing(e -> e.phase)); // stable within a phase
        int block = Math.min(999, s.block);
        List<BlockForecast.Line> lines = new ArrayList<>();
        boolean intangible = s.has(P + "IntangiblePlayerPower");
        for (BlockEvent event : events) {
            int before = block;
            if (event.operation == BlockEvent.Operation.GAIN) {
                block = (int)Math.min(999L, (long)block + event.amount);
            } else {
                int damage = intangible ? Math.min(1, event.amount) : event.amount;
                block = Math.max(0, block - damage);
                if (damage > before) {
                    addUnique(warnings, "HP loss may trigger further effects");
                }
            }
            if (block != before) lines.add(new BlockForecast.Line(event.source, block - before));
        }
        return new BlockForecast(s.block, block, lines, warnings);
    }

    private static int focused(int base, int focus) {
        return (int)Math.max(0L, Math.min(Integer.MAX_VALUE, (long)base + focus));
    }

    private static void addUnique(List<String> list, String message) {
        if (!list.contains(message)) list.add(message);
    }
}
