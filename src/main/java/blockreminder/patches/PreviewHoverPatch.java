package blockreminder.patches;

import blockreminder.game.PreviewRenderer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.characters.AbstractPlayer;

@SpirePatch(clz = AbstractPlayer.class, method = "renderPlayerBattleUi")
public final class PreviewHoverPatch {
    @SpirePrefixPatch
    public static SpireReturn<Void> prefix(AbstractPlayer __instance, SpriteBatch sb) {
        return PreviewRenderer.hoveringPreview(__instance)
                ? SpireReturn.Return() : SpireReturn.Continue();
    }
}
