package blockreminder.smoke;

import basemod.BaseMod;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostRenderSubscriber;
import blockreminder.BlockReminderMod;
import blockreminder.ModSettings;
import blockreminder.core.*;
import blockreminder.game.GameSnapshot;
import blockreminder.game.PreviewRenderer;
import blockreminder.game.PreviewTooltip;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.google.gson.JsonParser;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.curses.AscendersBane;
import com.megacrit.cardcrawl.cards.red.GhostlyArmor;
import com.megacrit.cardcrawl.cards.status.Burn;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.orbs.*;
import com.megacrit.cardcrawl.powers.*;
import com.megacrit.cardcrawl.relics.*;
import com.megacrit.cardcrawl.rooms.*;
import java.util.*;

/** Exercises the shipped adapter, services and render patch inside real MTS. */
@SpireInitializer
public final class SmokeMod implements PostInitializeSubscriber, PostRenderSubscriber {
    private AbstractPlayer player;
    private GameActionManager manager;
    private MapRoomNode node;
    private OverlayMenu overlay;
    private int assertions, frames;
    private boolean ready;

    public static void initialize() {
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            ex.printStackTrace(); System.exit(1);
        });
        BaseMod.subscribe(new SmokeMod());
    }

    @Override public void receivePostInitialize() {
        try {
            verifyLocalization();
            player = CardCrawlGame.characterManager.getCharacter(AbstractPlayer.PlayerClass.DEFECT).newInstance();
            AbstractDungeon.player = player;
            AbstractDungeon.actionManager = new GameActionManager();
            AbstractDungeon.currMapNode = new MapRoomNode(0, 0);
            AbstractDungeon.currMapNode.room = new MonsterRoom();
            AbstractDungeon.getCurrRoom().phase = AbstractRoom.RoomPhase.COMBAT;
            AbstractDungeon.getCurrRoom().monsters = new MonsterGroup(new AbstractMonster[0]);
            AbstractDungeon.overlayMenu = new OverlayMenu(player);
            manager = AbstractDungeon.actionManager;
            node = AbstractDungeon.currMapNode;
            overlay = AbstractDungeon.overlayMenu;
            ModSettings config = new ModSettings();
            config.load(); config.enabled = false; config.total = true;
            config.ethereal = false; config.showZero = true; config.save();
            ModSettings reloaded = new ModSettings(); reloaded.load();
            truth(!reloaded.enabled && reloaded.total && !reloaded.ethereal && reloaded.showZero,
                    "all settings persisted");
            config.enabled = true; config.total = false; config.ethereal = true; config.showZero = false; config.save();
            reset();
            player.relics.add(new Orichalcum());
            player.powers.add(new MetallicizePower(player, 3));
            player.orbs.add(new Frost());
            forecast(11, "real Orichalcum + Metallicize + Frost");

            reset();
            player.relics.add(new GoldPlatedCables());
            player.orbs.add(new Frost()); player.orbs.add(new Frost());
            forecast(6, "real Gold-Plated Cables");

            reset();
            player.relics.add(new energizedSpire.relics.UnstableMolecules());
            player.relics.add(new GoldPlatedCables());
            Frost limited = new Frost();
            player.orbs.add(limited);
            energizedSpire.patches.UnstableMoleculesPatches.UnstableMoleculesFieldPatch.value.set(limited, 0);
            forecast(0, "read injected Unstable Molecules zero counter");
            energizedSpire.patches.UnstableMoleculesPatches.UnstableMoleculesFieldPatch.value.set(limited, 1);
            forecast(4, "queued Cables triggers before Unstable counter reduction");
            for (int i = 0; i < 1000; i++) GameSnapshot.capture(player, true);
            eq(1, energizedSpire.patches.UnstableMoleculesPatches.UnstableMoleculesFieldPatch.value.get(limited),
                    "preview does not decrement injected counter");

            reset();
            player.relics.add(new FrozenCore()); player.relics.add(new GoldPlatedCables());
            player.orbs.add(new EmptyOrbSlot()); player.orbs.add(new EmptyOrbSlot());
            forecast(4, "real Frozen Core + Cables");
            int orbCount = player.orbs.size();
            for (int i = 0; i < 1000; i++) GameSnapshot.capture(player, true);
            eq(orbCount, player.orbs.size(), "preview does not channel new orbs");
            truth(player.orbs.get(0) instanceof EmptyOrbSlot, "empty slot unchanged");

            reset();
            player.powers.add(new FeelNoPainPower(player, 3));
            player.hand.group.add(new AscendersBane());
            player.hand.group.add(new GhostlyArmor());
            forecast(6, "real Ethereal / Feel No Pain");
            player.hand.group.get(0).retain = true;
            forecast(3, "real retain moves card out of exhaust candidates");
            player.hand.group.get(1).selfRetain = true;
            forecast(0, "real self-retain");

            reset();
            player.powers.add(new NoBlockPower(player, 2, false));
            player.powers.add(new FrailPower(player, 2, false));
            player.powers.add(new DexterityPower(player, 20));
            player.powers.add(new PlatedArmorPower(player, 4));
            player.orbs.add(new Frost());
            forecast(6, "real direct gains bypass card-only modifiers");

            // Adversarial callbacks fail the test if preview invokes them.
            player.powers.add(new AbstractPower() {
                { ID = "SmokeDanger"; name = "Danger"; owner = player; amount = 2; }
                @Override public void atEndOfTurn(boolean isPlayer) { throw new AssertionError("power callback invoked"); }
                @Override public void onGainedBlock(float block) { throw new AssertionError("gain callback invoked"); }
            });
            player.orbs.add(new Frost() {
                @Override public void onEndOfTurn() { throw new AssertionError("orb callback invoked"); }
                @Override public void applyFocus() { super.applyFocus(); }
            });
            List<AbstractOrb> beforeOrbs = new ArrayList<>(player.orbs);
            List<AbstractPower> beforePowers = new ArrayList<>(player.powers);
            for (int i = 0; i < 1000; i++) new BlockCalculator().calculate(GameSnapshot.capture(player, true));
            truth(beforeOrbs.equals(player.orbs), "orb identity and order unchanged");
            truth(beforePowers.equals(player.powers), "powers unchanged");
            eq(0, AbstractDungeon.actionManager.actions.size(), "action queue untouched");
            eq(0, player.currentBlock, "live block untouched");
            truth(!new BlockCalculator().calculate(GameSnapshot.capture(player,true)).isExact(),
                    "unmodeled callbacks marked partial");

            verifyTurnEndDisplay();
            verifyZeroVisibility();
            reset();
            player.currentBlock = 13;
            player.relics.add(new GoldPlatedCables());
            player.orbs.add(new Frost()); player.orbs.add(new Frost());
            player.healthHb.move(520 * Settings.scale, 520 * Settings.scale);
            AbstractDungeon.overlayMenu.endTurnButton.enabled = true;
            BlockReminderMod.SETTINGS.enabled = true;
            BlockReminderMod.SETTINGS.total = false;
            BlockReminderMod.PREVIEW.invalidate();
            BlockReminderMod.PREVIEW.update();
            truth(BlockReminderMod.PREVIEW.visibleFor(player), "service visible in player turn");
            eq(19, BlockReminderMod.PREVIEW.forecast().finalBlock, "service final block");
            player.endTurnQueued = true;
            truth(!BlockReminderMod.PREVIEW.visibleFor(player), "hidden after end turn queued");
            player.endTurnQueued = false;
            AbstractDungeon.actionManager.turnHasEnded = true;
            truth(!BlockReminderMod.PREVIEW.visibleFor(player), "hidden during enemy turn");
            AbstractDungeon.actionManager.turnHasEnded = false;
            AbstractDungeon.actionManager.addToBottom(new com.megacrit.cardcrawl.actions.common.GainBlockAction(player,1));
            BlockReminderMod.PREVIEW.update();
            truth(BlockReminderMod.PREVIEW.forecast() == null, "stale preview cleared during queued action");
            AbstractDungeon.actionManager.actions.clear();
            BlockReminderMod.PREVIEW.update();
            verifyCompactUI();
            player.maxHealth = 80; player.currentHealth = 64;
            player.powers.add(new StrengthPower(player, 2));
            player.powers.add(new DexterityPower(player, 2));
            player.powers.add(new ArtifactPower(player, 1));
            placeHealthUI(520 * Settings.scale);
            ready = true;
            Gdx.files.local("smoke-assertions.txt").writeString("PASS " + assertions + " real-game assertions\n", false, "UTF-8");
            System.out.println("SMOKE_ASSERTIONS_PASS " + assertions);
        } catch (Throwable ex) { fail(ex); }
    }

    private void reset() {
        player.relics.clear(); player.powers.clear(); player.orbs.clear(); player.hand.group.clear();
        player.maxOrbs = 3; player.currentBlock = 0; player.currentHealth = 80;
        player.isDead = false; player.isDying = false; player.endTurnQueued = false; player.isEndingTurn = false;
        AbstractDungeon.actionManager.actions.clear();
        AbstractDungeon.actionManager.cardQueue.clear();
        AbstractDungeon.actionManager.turnHasEnded = false;
    }
    private void verifyReactiveDamage() {
        reset();
        final int[] calls = {0};
        player.currentBlock = 3;
        player.powers.add(new AbstractPower() {
            { ID = "SmokeReactiveBlock"; name = "Reactive Block"; owner = player; amount = 4; }
            @Override public int onAttacked(DamageInfo info, int damage) {
                calls[0]++;
                owner.addBlock(amount);
                return damage;
            }
        });
        truth(new BlockCalculator().calculate(GameSnapshot.capture(player, true)).isExact(),
                "damage-only callback does not mark a turn without damage partial");
        Burn burn = new Burn();
        player.hand.group.add(burn);
        BlockForecast forecast = new BlockCalculator().calculate(GameSnapshot.capture(player, true));
        eq(1, forecast.finalBlock, "reactive damage keeps known projection");
        eq(0, calls[0], "damage preview never invokes reactive callback");
        truth(!forecast.isExact(), "reactive damage must be marked partial even when fully blocked");
        truth(PreviewTooltip.body(forecast, 6).contains("\u2248"), "reactive damage tooltip marks uncertainty");
        burn.dontTriggerOnUseCard = true;
        burn.use(player, null);
        int steps = 0;
        while (!manager.actions.isEmpty()) {
            AbstractGameAction action = manager.actions.remove(0);
            while (!action.isDone) {
                if (++steps > 100000) throw new AssertionError("Reactive damage action did not complete");
                action.update();
            }
        }
        eq(1, calls[0], "native Burn action invokes reactive callback once");
        eq(5, player.currentBlock, "native blocked Burn can trigger additional Block");
        eq(80, player.currentHealth, "fully blocked Burn does not lose HP");
        reset();
        player.currentBlock = 3;
        player.relics.add(new Calipers() {
            @Override public int onAttacked(DamageInfo info, int damage) {
                throw new AssertionError("Preview invoked relic damage callback");
            }
        });
        truth(new BlockCalculator().calculate(GameSnapshot.capture(player, true)).isExact(),
                "damage-only relic remains exact without damage");
        player.hand.group.add(new com.megacrit.cardcrawl.cards.curses.Decay());
        truth(!new BlockCalculator().calculate(GameSnapshot.capture(player, true)).isExact(),
                "Decay with reactive relic is partial");
        reset();
        player.currentBlock = 3;
        player.powers.add(new AbstractPower() {
            { ID = "SmokeHpReaction"; name = "HP Reaction"; owner = player; amount = 1; }
            @Override public void wasHPLost(DamageInfo info, int damage) {
                throw new AssertionError("Preview invoked HP loss callback");
            }
        });
        player.powers.add(new ConstrictedPower(player, player, 2));
        truth(!new BlockCalculator().calculate(GameSnapshot.capture(player, true)).isExact(),
                "Constricted with unknown HP reaction is partial");
        reset();
    }

    private void verifyTurnEndDisplay() throws Exception {
        reset();
        BlockReminderMod.SETTINGS.enabled = true;
        BlockReminderMod.SETTINGS.showZero = false;
        AbstractDungeon.overlayMenu.endTurnButton.enabled = true;
        Settings.hideCombatElements = false;
        player.currentBlock = 10;
        player.powers.add(new MetallicizePower(player, 2));
        placeHealthUI(520 * Settings.scale);
        BlockReminderMod.PREVIEW.invalidate();
        BlockReminderMod.PREVIEW.update();
        eq(12, blockreminder.patches.RenderPreviewPatch.displayedBlock(player, 10),
                "turn end starts from displayed forecast 12");
        AbstractDungeon.overlayMenu.endTurnButton.disable(true);
        BlockReminderMod.PREVIEW.update();
        eq(12, blockreminder.patches.RenderPreviewPatch.displayedBlock(player, 10),
                "turn end must not rewind displayed Block from 12 to 10");
        eq(10, player.currentBlock, "holding forecast leaves actual pre-settlement Block unchanged");
        truth(!PreviewRenderer.hoveringPreview(player), "settling forecast is not an active tooltip");
        for (int value = 11; value <= 12; value++) {
            new com.megacrit.cardcrawl.actions.common.GainBlockAction(player, 1).update();
            eq(value, player.currentBlock, "native gain settles actual Block");
            BlockReminderMod.PREVIEW.update();
            eq(12, blockreminder.patches.RenderPreviewPatch.displayedBlock(player, value),
                    "native gain does not replay forecast number");
            truth(blockreminder.patches.RenderPreviewPatch.shieldAnimation(player, 5f, true) == 1f,
                    "settlement suppresses native shield enlargement");
            truth(blockreminder.patches.RenderPreviewPatch.shieldAnimation(player, 16f, false) == 0f,
                    "settlement suppresses native shield movement");
            truth(blockreminder.patches.RenderPreviewPatch.shieldColor(player, Color.GOLD, true).r < 1f,
                    "settlement suppresses native gain flash");
            java.lang.reflect.Field scale = AbstractCreature.class.getDeclaredField("blockScale");
            scale.setAccessible(true);
            truth(scale.getFloat(player) == 1f, "native settlement does not leave a queued enlargement");
        }
        AbstractDungeon.getMonsters().queueMonsters();
        eq(12, blockreminder.patches.RenderPreviewPatch.displayedBlock(player, 12),
                "enemy phase returns settled actual Block");
        player.loseBlock(3);
        eq(9, blockreminder.patches.RenderPreviewPatch.displayedBlock(player, player.currentBlock),
                "enemy damage reduces native value immediately");
        truth(blockreminder.patches.RenderPreviewPatch.shieldAnimation(player, 5f, true) == 5f,
                "enemy phase retains native animation");
        reset();
        AbstractDungeon.overlayMenu.endTurnButton.enabled = true;
        player.powers.add(new MetallicizePower(player, 2));
        placeHealthUI(520 * Settings.scale);
        for (String name : Arrays.asList("blockColor", "blockTextColor")) {
            java.lang.reflect.Field color = AbstractCreature.class.getDeclaredField(name);
            color.setAccessible(true);
            ((Color)color.get(player)).a = 0;
        }
        BlockReminderMod.PREVIEW.invalidate();
        BlockReminderMod.PREVIEW.update();
        AbstractDungeon.overlayMenu.endTurnButton.disable(true);
        new com.megacrit.cardcrawl.actions.common.GainBlockAction(player, 2).update();
        eq(2, player.currentBlock, "settlement gains Block from zero normally");
        for (String name : Arrays.asList("blockColor", "blockTextColor")) {
            java.lang.reflect.Field color = AbstractCreature.class.getDeclaredField(name);
            color.setAccessible(true);
            truth(((Color)color.get(player)).a == 1f, "initial settlement restores native shield opacity");
        }
        truth(blockreminder.patches.SettlementAnimationPatch.suppress(player),
                "zero-current settlement suppresses gain visuals");
        BlockReminderMod.SETTINGS.enabled = false;
        truth(!blockreminder.patches.SettlementAnimationPatch.suppress(player),
                "disabled mod does not suppress native gains");
        eq(2, blockreminder.patches.RenderPreviewPatch.displayedBlock(player, 2),
                "disabled settlement returns actual Block");
        BlockReminderMod.SETTINGS.enabled = true;
        reset();
        AbstractDungeon.overlayMenu.endTurnButton.enabled = true;
    }
    private void verifyZeroVisibility() throws Exception {
        BlockReminderMod.SETTINGS.enabled = true;
        AbstractDungeon.overlayMenu.endTurnButton.enabled = true;
        Settings.hideCombatElements = false;
        for (int scenario = 0; scenario < 3; scenario++) {
            reset();
            if (scenario == 1) player.powers.add(new JuggernautPower(player, 5));
            if (scenario == 2) {
                player.currentBlock = 2;
                player.hand.group.add(new Burn());
            }
            placeHealthUI(520 * Settings.scale);
            BlockReminderMod.SETTINGS.showZero = false;
            BlockReminderMod.PREVIEW.invalidate();
            BlockReminderMod.PREVIEW.update();
            eq(0, BlockReminderMod.PREVIEW.forecast().finalBlock, "zero forecast fixture " + scenario);
            truth(!BlockReminderMod.PREVIEW.visibleFor(player),
                    "unchecked Always Show hides zero forecast " + scenario);
            eq(0, blockreminder.patches.RenderPreviewPatch.shieldVisibility(player, player.currentBlock),
                    "unchecked Always Show hides native zero shield " + scenario);
            eq(0, blockreminder.patches.RenderPreviewPatch.displayedBlock(player, player.currentBlock),
                    "hidden zero does not restore positive actual Block " + scenario);
            PreviewRenderer.Bounds bounds = PreviewRenderer.bounds(player, BlockReminderMod.PREVIEW.forecast());
            InputHelper.mX = (int)(bounds.x + bounds.width / 2);
            InputHelper.mY = (int)bounds.centerY();
            truth(!PreviewRenderer.hoveringPreview(player), "hidden shield has no hover tooltip " + scenario);
            BlockReminderMod.SETTINGS.showZero = true;
            truth(BlockReminderMod.PREVIEW.visibleFor(player), "checked Always Show reveals zero " + scenario);
            eq(1, blockreminder.patches.RenderPreviewPatch.shieldVisibility(player, player.currentBlock),
                    "checked Always Show enables native zero shield " + scenario);
            truth(PreviewRenderer.hoveringPreview(player), "shown zero retains hover tooltip " + scenario);
            eq(scenario == 2 ? 2 : 0, player.currentBlock, "zero visibility leaves actual Block unchanged");
        }
        player.endTurnQueued = true;
        eq(2, blockreminder.patches.RenderPreviewPatch.shieldVisibility(player, 2),
                "end turn restores actual shield after hidden zero");
        BlockReminderMod.SETTINGS.showZero = false;
    }
    private void forecast(int expected, String label) {
        eq(expected, new BlockCalculator().calculate(GameSnapshot.capture(player, true)).finalBlock, label);
    }
    private void eq(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", actual " + actual);
        assertions++;
    }
    private void truth(boolean ok, String label) { if (!ok) throw new AssertionError(label); assertions++; }
    private void verifyLocalization() {
        String language = Settings.language.name().toLowerCase(Locale.ROOT);
        String expectedTitle = localizedTitle(language);
        truth(expectedTitle.equals(BlockReminderMod.TEXT[0]), "game language selects its own localization");
        Settings.GameLanguage originalLanguage = Settings.language;
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            Settings.language = Settings.GameLanguage.IND;
            new BlockReminderMod().receiveEditStrings();
            truth(localizedTitle("ind").equals(CardCrawlGame.languagePack.getUIString(BlockReminderMod.ID + ":UI").TEXT[0]),
                    "resource lookup works with a Turkish system locale");
            Settings.language = Settings.GameLanguage.WWW;
            new BlockReminderMod().receiveEditStrings();
            truth(localizedTitle("eng").equals(CardCrawlGame.languagePack.getUIString(BlockReminderMod.ID + ":UI").TEXT[0]),
                    "unknown language falls back to English");
        } finally {
            Settings.language = originalLanguage;
            Locale.setDefault(originalLocale);
            new BlockReminderMod().receiveEditStrings();
            BlockReminderMod.TEXT = CardCrawlGame.languagePack.getUIString(BlockReminderMod.ID + ":UI").TEXT;
        }
        Set<Character> missing = new LinkedHashSet<>();
        for (String text : BlockReminderMod.TEXT) {
            for (char glyph : text.toCharArray()) {
                if (!Character.isWhitespace(glyph) && !FontHelper.tipBodyFont.getData().hasGlyph(glyph))
                    missing.add(glyph);
            }
        }
        truth(missing.isEmpty(), "native tooltip font covers localized text: " + missing);
        for (int index = 2; index <= 7; index++) {
            com.badlogic.gdx.graphics.g2d.BitmapFont font = index <= 5 ? FontHelper.charDescFont : FontHelper.tipBodyFont;
            for (char glyph : BlockReminderMod.TEXT[index].toCharArray()) {
                if (!Character.isWhitespace(glyph) && !font.getData().hasGlyph(glyph))
                    missing.add(glyph);
            }
            truth(FontHelper.getWidth(font, BlockReminderMod.TEXT[index], 1)
                            < Settings.WIDTH - 390 * Settings.scale,
                    "localized settings text fits screen: " + index);
        }
        truth(missing.isEmpty(), "native settings font covers localized text: " + missing);
        System.out.println("LOCALIZATION_PASS " + language + " " + expectedTitle);
    }
    private String localizedTitle(String language) {
        return new JsonParser().parse(Gdx.files.internal("blockreminder/localization/" + language
                + "/UIStrings.json").readString("UTF-8"))
                .getAsJsonObject().get(BlockReminderMod.ID + ":UI").getAsJsonObject()
                .getAsJsonArray("TEXT").get(0).getAsString();
    }
    private void verifyCompactUI() throws Exception {
        BlockForecast normal = new BlockCalculator().calculate(GameSnapshot.capture(player, true));
        List<String> warnings = Collections.singletonList(
                "Unmodeled end-turn effect: Juggernaut (com.megacrit.cardcrawl.powers.JuggernautPower)");
        BlockForecast partial = new BlockForecast(997, 999,
                Collections.singletonList(new BlockForecast.Line("Frost", 2)), warnings);
        String body = PreviewTooltip.body(partial, 6);
        truth(body.contains("\u2248 " + BlockReminderMod.TEXT[10]) && !body.contains("~"),
                "partial marker uses approximation only in tooltip");
        truth(FontHelper.tipBodyFont.getData().hasGlyph('\u2248'), "tooltip font renders approximation glyph");
        truth(!body.contains("com.megacrit") && !body.contains("Unmodeled end-turn effect"),
                "tooltip hides internal names and localizes known warnings");
        truth(partial.uncertainties.get(0).contains("com.megacrit"), "full diagnostics retained");
        truth(!PreviewTooltip.body(normal, 6).contains(BlockReminderMod.TEXT[10]),
                "exact tooltip omits partial disclaimer");
        BlockForecast exactPartial = new BlockForecast(partial.initialBlock, partial.finalBlock,
                partial.lines, Collections.emptyList());
        placeHealthUI(520 * Settings.scale);
        PreviewRenderer.Bounds exactBounds = PreviewRenderer.bounds(player, exactPartial);
        PreviewRenderer.Bounds partialBounds = PreviewRenderer.bounds(player, partial);
        truth(exactBounds.width == partialBounds.width && exactBounds.number.equals(partialBounds.number),
                "uncertainty does not change native shield number or layout");
        truth(partialBounds.number.equals("999"), "native shield displays total without a marker");
        truth(Math.abs(partialBounds.x + partialBounds.width / 2
                        - (player.hb.x - 14 * Settings.scale)) < 0.01f,
                "tooltip target matches native shield center");
        truth(blockreminder.patches.RenderPreviewPatch.displayedBlock(player, 13) == 19,
                "native number substitutes forecast total");
        eq(13, player.currentBlock, "display substitution leaves actual Block untouched");
        truth(blockreminder.patches.RenderPreviewPatch.shieldVisibility(player, 0) == 1,
                "forecast can render native shield at zero current Block");
        player.endTurnQueued = true;
        eq(13, blockreminder.patches.RenderPreviewPatch.displayedBlock(player, 13),
                "native value restored after end turn");
        player.endTurnQueued = false;
        BlockReminderMod.SETTINGS.enabled = false;
        eq(13, blockreminder.patches.RenderPreviewPatch.displayedBlock(player, 13),
                "disabled mod restores native value");
        BlockReminderMod.SETTINGS.enabled = true;
        InputHelper.mX = (int)(exactBounds.x + exactBounds.width / 2);
        InputHelper.mY = (int)exactBounds.centerY();
        truth(PreviewRenderer.hoveringPreview(player), "native shield hover selects forecast tooltip");
        InputHelper.mX = (int)player.healthHb.cX;
        truth(!PreviewRenderer.hoveringPreview(player), "health center preserves power tooltips");
        List<BlockForecast.Line> longSources = new ArrayList<>();
        for (int i = 0; i < 20; i++) longSources.add(new BlockForecast.Line(
                "VeryLongModdedSourceNameThatMustNotOverflowTheNativeTooltipColumn", 1));
        String compact = PreviewTooltip.body(new BlockForecast(0, 20, longSources, warnings), 6);
        truth(PreviewRenderer.tooltipHeight(compact) < Settings.HEIGHT - 48 * Settings.scale,
                "bounded long-source tooltip fits the viewport");
    }
    private void field(String name, Object value) throws Exception {
        java.lang.reflect.Field f = AbstractCreature.class.getDeclaredField(name);
        f.setAccessible(true); f.set(player, value);
    }
    private void placeHealthUI(float cx) throws Exception {
        player.healthHb.move(cx, 520 * Settings.scale);
        player.hb.move(cx, player.healthHb.cY + player.healthHb.height / 2 + player.hb.height / 2);
        player.healthBarRevivedEvent(); player.hbAlpha = 1;
        // Test setup only: the menu does not run a creature's health animation.
        // Populate its ordinary settled UI state so native UI is truly visible.
        field("hbYOffset", 0f); field("healthHideTimer", 1f); field("blockScale", 1f);
        for (String name : Arrays.asList("hbTextColor", "blueHbBarColor", "redHbBarColor",
                "blockColor", "blockTextColor", "blockOutlineColor")) {
            java.lang.reflect.Field f = AbstractCreature.class.getDeclaredField(name);
            f.setAccessible(true); ((Color)f.get(player)).a = 1;
        }
    }
    private void fail(Throwable ex) {
        ex.printStackTrace();
        Gdx.files.local("smoke-failure.txt").writeString(ex.toString(), false, "UTF-8");
        System.exit(1);
    }
    @Override public void receivePostRender(SpriteBatch sb) {
        if (!ready) return;
        try {
            // Main menu initialization resets AbstractDungeon after postInit.
            // Rebind the isolated scene rather than relying on menu globals.
            AbstractDungeon.player = player;
            AbstractDungeon.currMapNode = node;
            AbstractDungeon.actionManager = manager;
            AbstractDungeon.overlayMenu = overlay;
            AbstractDungeon.isScreenUp = false;
            Settings.hideCombatElements = false;
            overlay.endTurnButton.enabled = true;
            if (frames == 0) {
                List<AbstractPower> powers = new ArrayList<>(player.powers);
                List<AbstractRelic> relics = new ArrayList<>(player.relics);
                List<AbstractOrb> orbs = new ArrayList<>(player.orbs);
                int block = player.currentBlock, health = player.currentHealth;
                verifyReactiveDamage();
                player.powers.addAll(powers); player.relics.addAll(relics); player.orbs.addAll(orbs);
                player.currentBlock = block; player.currentHealth = health;
                field("blockScale", 1f); field("blockOffset", 0f); field("blockAnimTimer", 0f);
                field("blockTextColor", Color.WHITE.cpy());
            }
            if (frames == 5) BlockReminderMod.SETTINGS.total = true;
            if (frames == 9) {
                BlockReminderMod.SETTINGS.total = false;
                player.powers.add(new JuggernautPower(player, 5));
            }
            if (frames == 13) {
                player.powers.clear(); player.relics.clear(); player.orbs.clear();
                player.hand.group.add(new Burn()); player.hand.group.add(new Burn());
            }
            if (frames == 17) {
                player.hand.group.clear(); player.currentBlock = 997;
                player.relics.add(new GoldPlatedCables());
                player.orbs.add(new Frost()); player.orbs.add(new Frost());
            }
            if (frames == 21)
                placeHealthUI(Settings.WIDTH - player.healthHb.width / 2 - 30 * Settings.scale);
            if (frames == 25) {
                player.currentBlock = 0;
                placeHealthUI(520 * Settings.scale);
                for (String name : Arrays.asList("blockColor", "blockTextColor")) {
                    java.lang.reflect.Field f = AbstractCreature.class.getDeclaredField(name);
                    f.setAccessible(true); ((Color)f.get(player)).a = 0;
                }
            }
            if (frames == 29) {
                player.endTurnQueued = true;
                player.currentBlock = 13;
                placeHealthUI(520 * Settings.scale);
            }
            if (frames == 33) {
                player.endTurnQueued = false;
                player.currentBlock = 2;
                for (int i = 0; i < 4; i++) player.hand.group.add(new Burn());
            }
            if (frames == 37) BlockReminderMod.SETTINGS.showZero = true;
            if (frames == 41) {
                reset();
                player.currentBlock = 10;
                player.powers.add(new MetallicizePower(player, 2));
                placeHealthUI(520 * Settings.scale);
            }
            if (frames == 45) overlay.endTurnButton.disable(true);
            if (frames == 49) new com.megacrit.cardcrawl.actions.common.GainBlockAction(player, 2).update();
            if (frames == 53) {
                AbstractDungeon.getMonsters().queueMonsters();
                manager.turnHasEnded = true;
                player.loseBlock(3);
            }
            BlockReminderMod.PREVIEW.invalidate();
            BlockReminderMod.PREVIEW.update();
            truth(BlockReminderMod.PREVIEW.visibleFor(player) == (frames < 29 || frames >= 37 && frames < 45),
                    "forecast visibility follows player turn at render time");
            eq(frames >= 53 ? player.currentBlock : frames < 29 || frames >= 37 ? 1 : frames < 33 ? player.currentBlock : 0,
                    blockreminder.patches.RenderPreviewPatch.shieldVisibility(player, player.currentBlock),
                    "native shield gate respects Always Show and turn fallback");
            sb.setColor(new Color(0.08f, 0.11f, 0.16f, 1));
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);
            sb.setColor(Color.WHITE);
            FontHelper.renderFontCentered(sb, FontHelper.tipHeaderFont,
                    "Block Reminder Reborn - isolated runtime QA", Settings.WIDTH / 2f,
                    Settings.HEIGHT - 100 * Settings.scale, Color.WHITE);
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont,
                    "Native health / Block / power UI + compact forecast (isolated scene)",
                    Settings.WIDTH / 2f, Settings.HEIGHT - 155 * Settings.scale, Color.WHITE);
            PreviewRenderer.Bounds bounds = BlockReminderMod.PREVIEW.forecast() == null ? null
                    : PreviewRenderer.bounds(player, BlockReminderMod.PREVIEW.forecast());
            if (bounds != null && (frames >= 5 && frames < 13 || frames >= 17)) {
                InputHelper.mX = (int)(bounds.x + bounds.width / 2);
                InputHelper.mY = (int)bounds.centerY();
            } else {
                InputHelper.mX = 0; InputHelper.mY = 0;
            }
            int actualBlock = player.currentBlock;
            player.renderPlayerBattleUi(sb);
            player.renderHealth(sb);
            eq(actualBlock, player.currentBlock, "native rendering leaves combat Block unchanged");
            TipHelper.render(sb);
            if (frames == 4) capture(sb,"smoke-delta.png");
            if (frames == 8) capture(sb,"smoke-total.png");
            if (frames == 12) capture(sb,"smoke-partial.png");
            if (frames == 16) capture(sb,"smoke-loss.png");
            if (frames == 20) capture(sb,"smoke-cap.png");
            if (frames == 24) capture(sb,"smoke-edge.png");
            if (frames == 26) capture(sb,"smoke-left-edge.png");
            if (frames == 28) capture(sb,"smoke-zero-current.png");
            if (frames == 32) capture(sb,"smoke-enemy-turn.png");
            if (frames == 36) capture(sb,"smoke-zero-hidden.png");
            if (frames == 40) capture(sb,"smoke-zero-forecast.png");
            if (frames == 44) capture(sb,"smoke-turn-end-before.png");
            if (frames == 48) capture(sb,"smoke-turn-end-hold.png");
            if (frames == 52) capture(sb,"smoke-turn-end-gain.png");
            if (frames == 56) capture(sb,"smoke-turn-end-damage.png");
            if (frames >= 45 && frames < 53)
                eq(12, blockreminder.patches.RenderPreviewPatch.displayedBlock(player, player.currentBlock),
                        "rendered turn-end value stays at forecast");
            if (frames++ >= 56) {
                Gdx.files.local("smoke-assertions.txt").writeString(
                        "PASS " + assertions + " real-game and render assertions\n", false, "UTF-8");
                Gdx.files.local("smoke-success.txt").writeString(
                        "PASS: MTS loading, adapter, read-only projection, native Block value and tooltip, turn fallback\n",
                        false, "UTF-8");
                System.out.println("SMOKE_SUCCESS");
                System.exit(0);
            }
        } catch (Throwable ex) { fail(ex); }
    }
    private void capture(SpriteBatch sb, String name) {
        sb.flush();
        eq(Settings.WIDTH, Gdx.graphics.getWidth(), "framebuffer width matches requested scene");
        eq(Settings.HEIGHT, Gdx.graphics.getHeight(), "framebuffer height matches requested scene");
        Pixmap image = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Pixmap flipped = new Pixmap(image.getWidth(), image.getHeight(), Pixmap.Format.RGBA8888);
        byte[] row = new byte[image.getWidth() * 4];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getPixels().position((image.getHeight() - 1 - y) * row.length);
            image.getPixels().get(row);
            flipped.getPixels().put(row);
        }
        flipped.getPixels().position(0);
        PixmapIO.writePNG(Gdx.files.local(name), flipped);
        flipped.dispose();
        image.dispose();
    }
}
