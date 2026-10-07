package energizedSpire.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.orbs.AbstractOrb;

/** Test fixture for the publicly documented field layout, not the third-party mod. */
public final class UnstableMoleculesPatches {
    @SpirePatch(clz = AbstractOrb.class, method = SpirePatch.CLASS)
    public static class UnstableMoleculesFieldPatch {
        public static SpireField<Integer> value = new SpireField<>(() -> null);
    }
}
