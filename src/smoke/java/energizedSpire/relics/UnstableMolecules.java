package energizedSpire.relics;

import com.megacrit.cardcrawl.relics.AbstractRelic;

/** Test fixture: supplies the recognized class; borrows a vanilla test texture. */
public final class UnstableMolecules extends AbstractRelic {
    public UnstableMolecules() { super("FrozenCore", "frozenOrb.png", RelicTier.BOSS, LandingSound.CLINK); }
    @Override public String getUpdatedDescription() { return "Smoke fixture"; }
    @Override public AbstractRelic makeCopy() { return new UnstableMolecules(); }
}
