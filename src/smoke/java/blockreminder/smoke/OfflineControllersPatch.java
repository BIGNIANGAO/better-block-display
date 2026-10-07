package blockreminder.smoke;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;

/** Test-only: offline UI QA needs neither Steam Utils nor controller threads. */
@SpirePatch(clz = CardCrawlGame.class, method = "create")
public final class OfflineControllersPatch {
    @SpireInstrumentPatch
    public static ExprEditor instrument() {
        return new ExprEditor() {
            @Override public void edit(NewExpr expression) throws CannotCompileException {
                if (expression.getClassName().equals("com.codedisaster.steamworks.SteamUtils")
                        || expression.getClassName().equals("com.megacrit.cardcrawl.helpers.steamInput.SteamInputHelper"))
                    expression.replace("{ $_ = null; }");
            }
        };
    }
}
