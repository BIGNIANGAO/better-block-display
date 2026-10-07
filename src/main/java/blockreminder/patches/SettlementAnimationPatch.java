package blockreminder.patches;

import blockreminder.BlockReminderMod;
import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

/** Suppress only native gain visuals already represented by the forecast. */
@SpirePatch(clz = AbstractCreature.class, method = "addBlock")
public final class SettlementAnimationPatch {
    public static boolean suppress(AbstractCreature creature) {
        return creature instanceof AbstractPlayer
                && BlockReminderMod.PREVIEW.holdingFor((AbstractPlayer) creature);
    }

    @SpireInstrumentPatch
    public static ExprEditor instrument() {
        return new ExprEditor() {
            @Override public void edit(FieldAccess field) throws CannotCompileException {
                if (field.isWriter() && (field.getFieldName().equals("blockScale")
                        || field.getFieldName().equals("blockTextColor")))
                    field.replace("{ if (!blockreminder.patches.SettlementAnimationPatch.suppress($0)) $proceed($$); }");
            }
        };
    }

    @SpirePatch(clz = AbstractCreature.class, method = "gainBlockAnimation")
    public static class InitialGain {
        @SpireInstrumentPatch
        public static ExprEditor instrument() {
            return new ExprEditor() {
                @Override public void edit(FieldAccess field) throws CannotCompileException {
                    if (!field.isWriter()) return;
                    if (field.getFieldName().equals("blockAnimTimer"))
                        field.replace("{ $proceed(blockreminder.patches.SettlementAnimationPatch.suppress(this) ? 0f : $1); }");
                    else if (field.getClassName().equals("com.badlogic.gdx.graphics.Color")
                            && field.getFieldName().equals("a"))
                        field.replace("{ $proceed(blockreminder.patches.SettlementAnimationPatch.suppress(this) ? this.hbAlpha : $1); }");
                }
            };
        }
    }
}
