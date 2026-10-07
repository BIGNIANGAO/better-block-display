package blockreminder.core;

import blockreminder.api.PreviewContributor;
import blockreminder.api.PreviewRegistry;
import java.util.*;
import static blockreminder.core.CombatSnapshot.Kind.*;

/** Explicit regression scenarios, no third-party test framework required. */
public final class BlockCalculatorTest {
    private static final String P = "com.megacrit.cardcrawl.powers.";
    private static final String R = "com.megacrit.cardcrawl.relics.";
    private static final String O = "com.megacrit.cardcrawl.orbs.";
    private static final String C = "com.megacrit.cardcrawl.cards.";
    private static int count;

    private static final class Fixture {
        int block, focus, maxOrbs = 3;
        String stance = "Neutral";
        boolean ethereal = true;
        List<CombatSnapshot.Entity> entities = new ArrayList<>();
        Fixture block(int n) { block = n; return this; }
        Fixture focus(int n) { focus = n; return this; }
        Fixture calm() { stance = "Calm"; return this; }
        Fixture add(CombatSnapshot.Kind kind, String type, int amount, Object... pairs) {
            Map<String,Integer> values = new LinkedHashMap<>();
            for (int i = 0; i < pairs.length; i += 2) values.put((String)pairs[i], (Integer)pairs[i+1]);
            entities.add(new CombatSnapshot.Entity(kind, type, type, type, amount, values));
            return this;
        }
        Fixture relic(String n) { return add(RELIC, R+n, 0); }
        Fixture power(String n, int amount) { return add(POWER, P+n, amount); }
        Fixture frost(int n) { return add(ORB, O+"Frost", n); }
        Fixture empty() { return add(ORB, O+"EmptyOrbSlot", 0); }
        Fixture card(String n, Object... values) { return add(CARD, C+n, 0, values); }
        CombatSnapshot snapshot() {
            return new CombatSnapshot(block, 80, focus, maxOrbs, stance, ethereal, entities, Collections.emptyList());
        }
    }

    private static void check(String name, Fixture f, int total) {
        BlockForecast forecast = new BlockCalculator().calculate(f.snapshot());
        if (forecast.finalBlock != total)
            throw new AssertionError(name + ": expected " + total + ", got " + forecast.finalBlock);
        count++;
    }

    public static void main(String[] args) {
        check("empty combat", new Fixture(), 0);
        check("current block survives until enemies", new Fixture().block(13), 13);
        check("Orichalcum at zero", new Fixture().relic("Orichalcum"), 6);
        check("Orichalcum blocked", new Fixture().block(1).relic("Orichalcum"), 1);
        check("Orichalcum trigger flag", new Fixture().block(8).add(RELIC,R+"Orichalcum",0,"trigger",1), 14);
        check("Orichalcum plus other gains", new Fixture().relic("Orichalcum").power("MetallicizePower",3).frost(2), 11);
        check("Orichalcum plus CloakClasp", new Fixture().relic("CloakClasp").relic("Orichalcum").card("red.Defend_Red"), 7);
        check("Cloak includes entire current hand", new Fixture().relic("CloakClasp")
                .card("red.AscendersBane","ethereal",1).card("green.Defend_Green","retain",1), 2);
        check("Metallicize and plating", new Fixture().power("MetallicizePower",3).power("PlatedArmorPower",5), 8);
        check("Like Water in Calm", new Fixture().calm().power("watcher.LikeWaterPower",5), 5);
        check("Like Water outside Calm", new Fixture().power("watcher.LikeWaterPower",5), 0);
        check("Frost uses live passive", new Fixture().focus(4).frost(6), 6);
        check("negative passive clamped", new Fixture().frost(-2), 0);
        check("Gold Cables first Frost twice", new Fixture().relic("GoldPlatedCables").frost(4).frost(2), 10);
        check("Gold Cables does not double later Frost", new Fixture().relic("GoldPlatedCables")
                .add(ORB,O+"Lightning",3).frost(4), 4);
        check("empty first slot is not later Frost", new Fixture().relic("GoldPlatedCables").empty().frost(4), 4);
        check("Frozen Core empty slot", new Fixture().relic("FrozenCore").empty(), 2);
        check("Frozen Core with Focus", new Fixture().focus(3).relic("FrozenCore").empty(), 5);
        check("Frozen Core no empty slot", new Fixture().relic("FrozenCore").frost(2), 2);
        check("Frozen Core first empty only", new Fixture().relic("FrozenCore").empty().empty(), 2);
        check("new Frozen Core orb doubled by Cables", new Fixture().relic("FrozenCore")
                .relic("GoldPlatedCables").empty(), 4);
        check("new orb later slot not doubled", new Fixture().relic("FrozenCore")
                .relic("GoldPlatedCables").add(ORB,O+"Lightning",3).empty(), 2);
        check("Frozen Core negative Focus", new Fixture().focus(-3).relic("FrozenCore").empty(), 0);
        Fixture noSlots = new Fixture().relic("FrozenCore").empty(); noSlots.maxOrbs = 0;
        check("no orb capacity", noSlots, 0);
        check("FNP only Ethereal", new Fixture().power("FeelNoPainPower",3)
                .card("red.AscendersBane","ethereal",1).card("red.Impervious","exhaust",1), 3);
        check("stacked FNP", new Fixture().power("FeelNoPainPower",7)
                .card("red.AscendersBane","ethereal",1).card("red.GhostlyArmor","ethereal",1), 14);
        check("retain to limbo", new Fixture().power("FeelNoPainPower",3)
                .card("red.GhostlyArmor","ethereal",1,"retain",1), 0);
        check("self retain to limbo", new Fixture().power("FeelNoPainPower",3)
                .card("red.GhostlyArmor","ethereal",1,"selfRetain",1), 0);
        check("Runic Pyramid does not stop Ethereal", new Fixture().power("FeelNoPainPower",3)
                .relic("RunicPyramid").card("red.GhostlyArmor","ethereal",1), 3);
        check("Equilibrium does not stop Ethereal", new Fixture().power("FeelNoPainPower",3)
                .power("EquilibriumPower",1).card("red.GhostlyArmor","ethereal",1), 3);
        Fixture disabled = new Fixture().power("FeelNoPainPower",3).card("red.GhostlyArmor","ethereal",1);
        disabled.ethereal = false; check("FNP toggle", disabled, 0);
        check("Frail and Dexterity not applied to direct block", new Fixture().power("FrailPower",1)
                .power("DexterityPower",10).power("MetallicizePower",3).frost(2), 5);
        check("Panic Button restriction is card only", new Fixture().power("NoBlockPower",2)
                .relic("Orichalcum").power("PlatedArmorPower",4).frost(2), 12);
        check("Calipers is next player turn", new Fixture().block(20).relic("Calipers").frost(2), 22);
        check("block cap", new Fixture().block(998).frost(10), 999);
        check("overflow cannot wrap", new Fixture().block(998).power("MetallicizePower",Integer.MAX_VALUE), 999);
        check("Burn spends early block", new Fixture().block(3).frost(2).add(CARD,C+"status.Burn",2), 3);
        check("upgraded Burn", new Fixture().block(6).add(CARD,C+"status.Burn",4), 2);
        check("Decay spends block", new Fixture().block(6).card("curses.Decay"), 4);
        check("Regret bypasses block", new Fixture().block(6).card("curses.Regret"), 6);
        check("FNP resolves after Burn", new Fixture().power("FeelNoPainPower",3)
                .card("red.AscendersBane","ethereal",1).add(CARD,C+"status.Burn",2), 3);
        check("Constricted before exhaustion", new Fixture().block(3).power("ConstrictedPower",5)
                .power("FeelNoPainPower",3).card("red.AscendersBane","ethereal",1), 3);
        check("Intangible reduces Burn", new Fixture().block(6).power("IntangiblePlayerPower",1)
                .add(CARD,C+"status.Burn",4), 5);
        check("block cap before later damage", new Fixture().block(998).frost(4).add(CARD,C+"status.Burn",2), 997);
        check("fake vanilla ID not modeled", new Fixture().add(POWER,"some.mod.MetallicizePower",90), 0);
        check("Unstable Molecules exhausted passive", new Fixture()
                .add(ORB,O+"Frost",2,"unstableRemaining",0), 0);
        check("Unstable Molecules positive counter", new Fixture()
                .add(ORB,O+"Frost",2,"unstableRemaining",1), 2);
        check("Cables callbacks precede counter reductions", new Fixture().relic("GoldPlatedCables")
                .add(ORB,O+"Frost",2,"unstableRemaining",1), 4);
        check("Cryogenetics does not change own Frost amount", new Fixture().frost(2)
                .add(POWER,"tisCardPack.powers.CryoPower",1), 2);

        CombatSnapshot snapshot = new Fixture().block(13).frost(2).snapshot();
        BlockForecast forecast = new BlockCalculator().calculate(snapshot);
        expect("+2",forecast.display(false),"delta mode");
        expect("15",forecast.display(true),"total mode");
        BlockForecast partial = new BlockCalculator().calculate(snapshot,Collections.emptyList(),Arrays.asList("unknown"));
        expect("+2",partial.display(false),"partial display stays numeric");
        expect("15",partial.display(true),"partial total stays numeric");
        expect(false,partial.isExact(),"numeric display retains uncertainty");
        try { snapshot.entities.clear(); throw new AssertionError("Mutable entities"); }
        catch (UnsupportedOperationException expected) { count++; }
        try { snapshot.entities.get(0).values.put("unsafe",1); throw new AssertionError("Mutable scalar fields"); }
        catch (UnsupportedOperationException expected) { count++; }
        for (int i=0;i<10000;i++) new BlockCalculator().calculate(snapshot);
        expect(13,snapshot.block,"preview leaves input untouched");

        PreviewRegistry.register(new PreviewContributor() {
            public String id() { return "broken"; }
            public boolean supports(CombatSnapshot.Entity e) { return false; }
            public List<BlockEvent> preview(CombatSnapshot s) { throw new IllegalStateException("broken provider"); }
        });
        List<String> warnings = new ArrayList<>();
        List<BlockEvent> events = PreviewRegistry.collect(snapshot,warnings,e -> { });
        expect(0,events.size(),"broken provider contributes nothing");
        expect(1,warnings.size(),"broken provider is isolated");
        PreviewRegistry.unregister("broken");
        PreviewRegistry.register(new PreviewContributor() {
            public String id() { return "armor"; }
            public boolean supports(CombatSnapshot.Entity e) { return e.className.equals("test.Armor"); }
            public List<BlockEvent> preview(CombatSnapshot s) {
                return Collections.singletonList(BlockEvent.gain(BlockEvent.Phase.POWER,"armor",4));
            }
        });
        Fixture extended = new Fixture().add(POWER,"test.Armor",4);
        CombatSnapshot custom = new CombatSnapshot(0,80,0,0,"Neutral",true,extended.entities,
                Arrays.asList("Unmodeled end-turn effect: armor (test.Armor)"));
        PreviewRegistry.Result contribution = PreviewRegistry.evaluate(custom,e -> { });
        BlockForecast projection = new BlockCalculator().calculate(custom,contribution.events,
                contribution.warnings,contribution.coveredClasses);
        expect(4,projection.finalBlock,"extension contributes block");
        expect(true,projection.isExact(),"successful coverage resolves its warning");
        PreviewRegistry.unregister("armor");
        PreviewRegistry.register(new PreviewContributor() {
            public String id() { return "invalid"; }
            public boolean supports(CombatSnapshot.Entity e) { return true; }
            public List<BlockEvent> preview(CombatSnapshot s) { return Arrays.asList((BlockEvent)null); }
        });
        contribution = PreviewRegistry.evaluate(custom,e -> { });
        expect(0,contribution.events.size(),"invalid contribution rejected atomically");
        expect(0,contribution.coveredClasses.size(),"invalid extension cannot claim coverage");
        PreviewRegistry.unregister("invalid");
        Fixture reactive = new Fixture().block(3).add(POWER, "test.Reactive", 4, "unmodeledDamageCallback", 1);
        BlockCalculator calculator = new BlockCalculator();
        expect(true, calculator.calculate(reactive.snapshot()).isExact(), "no damage does not warn about reactive callback");
        reactive.add(CARD, C + "status.Burn", 2);
        BlockForecast reactiveForecast = calculator.calculate(reactive.snapshot());
        expect(1, reactiveForecast.finalBlock, "reactive callback does not change known calculation");
        expect(false, reactiveForecast.isExact(), "blocked damage still warns about reactive callback");
        expect(1, reactiveForecast.uncertainties.size(), "reactive warning is unique");
        reactiveForecast = calculator.calculate(reactive.snapshot(), Collections.emptyList(), Collections.emptyList(),
                Collections.singleton("test.Reactive"));
        expect(true, reactiveForecast.isExact(), "successful extension can cover damage reaction");
        Fixture reactionOnly = new Fixture().block(3).add(RELIC, "test.ReactiveRelic", 0, "unmodeledDamageCallback", 1);
        expect(false, calculator.calculate(reactionOnly.snapshot(),
                Collections.singletonList(BlockEvent.damage(BlockEvent.Phase.LATE_POWER, "extension damage", 2)),
                Collections.emptyList()).isExact(), "extension damage also warns about reactive relic");
        expect(true, calculator.calculate(reactionOnly.snapshot(),
                Collections.singletonList(BlockEvent.damage(BlockEvent.Phase.LATE_POWER, "zero damage", 0)),
                Collections.emptyList()).isExact(), "zero damage does not add reactive warning");
        System.out.println("PASS " + count + " core regression assertions");
    }

    private static void expect(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) throw new AssertionError(name+": "+expected+" != "+actual);
        count++;
    }
}
