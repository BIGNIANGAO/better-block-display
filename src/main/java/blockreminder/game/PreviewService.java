package blockreminder.game;

import blockreminder.BlockReminderMod;
import blockreminder.api.PreviewRegistry;
import blockreminder.core.BlockCalculator;
import blockreminder.core.BlockEvent;
import blockreminder.core.BlockForecast;
import blockreminder.core.CombatSnapshot;
import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Only computes while the player can act; never replaces an action manager. */
public final class PreviewService {
    private final BlockCalculator calculator = new BlockCalculator();
    private final Set<String> loggedFailures = new HashSet<>();
    private BlockForecast forecast;
    private float elapsed;
    private AbstractPlayer owner;
    private BlockForecast settlingForecast;
    private AbstractPlayer settlingOwner;
    private AbstractRoom settlingRoom;
    private GameActionManager settlingManager;

    public void invalidate() { forecast = null; owner = null; elapsed = 0.1f; }
    public BlockForecast forecast() { return forecast; }

    public void beginSettlement() {
        AbstractPlayer player = AbstractDungeon.player;
        if (holdingFor(player) || !availableFor(player)) return;
        elapsed = 0.1f;
        update();
        if (!availableFor(player)) return;
        settlingForecast = forecast;
        settlingOwner = player;
        settlingRoom = AbstractDungeon.getCurrRoom();
        settlingManager = AbstractDungeon.actionManager;
    }

    public void finishSettlement() {
        settlingForecast = null;
        settlingOwner = null;
        settlingRoom = null;
        settlingManager = null;
    }

    public boolean holdingFor(AbstractPlayer player) {
        if (settlingForecast == null) return false;
        if (!BlockReminderMod.SETTINGS.enabled || player == null || player != settlingOwner
                || player != AbstractDungeon.player || player.isDead || player.isDying
                || player.currentHealth <= 0 || AbstractDungeon.getCurrMapNode() == null
                || AbstractDungeon.getCurrRoom() != settlingRoom
                || AbstractDungeon.actionManager != settlingManager
                || settlingRoom.phase != AbstractRoom.RoomPhase.COMBAT || settlingRoom.isBattleOver) {
            finishSettlement();
            return false;
        }
        return true;
    }

    public BlockForecast displayForecastFor(AbstractPlayer player) {
        return holdingFor(player) ? settlingForecast : forecast;
    }

    public boolean displayVisibleFor(AbstractPlayer player) {
        BlockForecast displayed = displayForecastFor(player);
        return (holdingFor(player) || availableFor(player))
                && (displayed.finalBlock > 0 || BlockReminderMod.SETTINGS.showZero);
    }

    public boolean availableFor(AbstractPlayer player) {
        return forecast != null && owner == player && canPreview();
    }

    public boolean visibleFor(AbstractPlayer player) {
        return availableFor(player)
                && (forecast.finalBlock > 0 || BlockReminderMod.SETTINGS.showZero);
    }

    public void update() {
        try {
            holdingFor(AbstractDungeon.player);
            if (!canPreview()) { invalidate(); return; }
            finishSettlement();
            elapsed += Gdx.graphics.getDeltaTime();
            if (elapsed < 0.1f && owner == AbstractDungeon.player) return;
            elapsed = 0;
            owner = AbstractDungeon.player;
            CombatSnapshot s = GameSnapshot.capture(owner, BlockReminderMod.SETTINGS.ethereal);
            List<String> warnings = new ArrayList<>();
            PreviewRegistry.Result extra = PreviewRegistry.evaluate(s, this::report);
            warnings.addAll(extra.warnings);
            if (!s.includeEthereal && s.has("com.megacrit.cardcrawl.powers.FeelNoPainPower"))
                warnings.add(BlockReminderMod.TEXT[12]);
            forecast = calculator.calculate(s, extra.events, warnings, extra.coveredClasses);
        } catch (RuntimeException | LinkageError ex) {
            finishSettlement();
            invalidate();
            report(ex);
        }
    }

    private boolean canPreview() {
        AbstractPlayer p = AbstractDungeon.player;
        if (!BlockReminderMod.SETTINGS.enabled || p == null || p.isDead || p.isDying
                || p.currentHealth <= 0 || p.endTurnQueued || p.isEndingTurn) return false;
        if (AbstractDungeon.getCurrMapNode() == null || AbstractDungeon.actionManager == null
                || AbstractDungeon.overlayMenu == null) return false;
        AbstractRoom room = AbstractDungeon.getCurrRoom();
        return room != null && room.phase == AbstractRoom.RoomPhase.COMBAT && !room.isBattleOver
                && AbstractDungeon.overlayMenu.endTurnButton.enabled
                && !AbstractDungeon.actionManager.turnHasEnded
                && AbstractDungeon.actionManager.currentAction == null
                && AbstractDungeon.actionManager.actions.isEmpty()
                && AbstractDungeon.actionManager.preTurnActions.isEmpty()
                && AbstractDungeon.actionManager.cardQueue.isEmpty()
                && !AbstractDungeon.isScreenUp;
    }

    private void report(Throwable ex) {
        String key = ex.getClass().getName() + ":" + ex.getMessage();
        if (loggedFailures.size() < 16 && loggedFailures.add(key))
            BlockReminderMod.LOG.warn("Preview failure isolated; combat was not modified", ex);
    }
}
