package blockreminder.patches;

import blockreminder.BlockReminderMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.ui.buttons.EndTurnButton;

/** Holds only the displayed forecast across the player's end-turn actions. */
public final class TurnEndDisplayPatch {
    @SpirePatch(clz = EndTurnButton.class, method = "disable", paramtypez = {boolean.class})
    public static class Begin {
        @SpirePrefixPatch
        public static void prefix(EndTurnButton __instance, boolean isEnemyTurn) {
            if (isEnemyTurn) BlockReminderMod.PREVIEW.beginSettlement();
        }
    }

    @SpirePatch(clz = MonsterGroup.class, method = "queueMonsters")
    public static class EnemyTurn {
        @SpirePrefixPatch
        public static void prefix() { BlockReminderMod.PREVIEW.finishSettlement(); }
    }

    @SpirePatch(clz = AbstractPlayer.class, method = "applyStartOfTurnRelics")
    public static class PlayerTurn {
        @SpirePrefixPatch
        public static void prefix() { BlockReminderMod.PREVIEW.finishSettlement(); }
    }
}
