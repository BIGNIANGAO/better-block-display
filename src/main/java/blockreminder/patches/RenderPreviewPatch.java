package blockreminder.patches;

import blockreminder.BlockReminderMod;
import blockreminder.game.PreviewRenderer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.Color;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

@SpirePatch(clz = AbstractCreature.class, method = "renderHealth")
public final class RenderPreviewPatch {
    private static boolean reported;

    @SpireInstrumentPatch
    public static ExprEditor instrument() {
        return new ExprEditor() {
            @Override public void edit(FieldAccess field) throws CannotCompileException {
                if (field.isReader() && field.getFieldName().equals("currentBlock"))
                    field.replace("{ $_ = blockreminder.patches.RenderPreviewPatch.shieldVisibility($0, $proceed()); }");
            }
        };
    }

    public static int displayedBlock(AbstractCreature creature, int actual) {
        try {
            return PreviewRenderer.usesDisplay(creature)
                    ? BlockReminderMod.PREVIEW.displayForecastFor((AbstractPlayer) creature).finalBlock : actual;
        } catch (RuntimeException | LinkageError ex) {
            report(ex);
            return actual;
        }
    }

    public static int shieldVisibility(AbstractCreature creature, int actual) {
        try {
            // Keep hidden zero forecasts active so rendering cannot fall back
            // to a positive actual value. No combat field is written.
            if (!PreviewRenderer.usesDisplay(creature)) return actual;
            return BlockReminderMod.PREVIEW.displayVisibleFor((AbstractPlayer) creature) ? 1 : 0;
        } catch (RuntimeException | LinkageError ex) {
            report(ex);
            return actual;
        }
    }

    public static Color shieldColor(AbstractCreature creature, Color original, boolean text) {
        if (!PreviewRenderer.usesDisplay(creature)
                || creature.currentBlock > 0 && !BlockReminderMod.PREVIEW.holdingFor((AbstractPlayer) creature))
            return original;
        return new Color(text ? 0.9f : 0.6f, text ? 0.9f : 0.93f,
                text ? 0.9f : 0.98f, creature.hbAlpha);
    }

    public static float shieldAnimation(AbstractCreature creature, float original, boolean scale) {
        return PreviewRenderer.usesDisplay(creature)
                && (creature.currentBlock <= 0 || BlockReminderMod.PREVIEW.holdingFor((AbstractPlayer) creature))
                ? (scale ? 1 : 0) : original;
    }

    @SpirePostfixPatch
    public static void postfix(AbstractCreature __instance, SpriteBatch sb) {
        if (__instance != AbstractDungeon.player || !(__instance instanceof AbstractPlayer)) return;
        try { PreviewRenderer.render((AbstractPlayer) __instance, sb); }
        catch (RuntimeException | LinkageError ex) {
            report(ex);
        }
    }

    private static void report(Throwable ex) {
        BlockReminderMod.PREVIEW.invalidate();
        BlockReminderMod.PREVIEW.finishSettlement();
        if (!reported) {
            reported = true;
            BlockReminderMod.LOG.warn("Preview rendering failure isolated", ex);
        }
    }
}
