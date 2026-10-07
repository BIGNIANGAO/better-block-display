package blockreminder.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.core.AbstractCreature;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

@SpirePatch(clz = AbstractCreature.class, method = "renderBlockIconAndValue")
public final class RenderBlockValuePatch {
    @SpireInstrumentPatch
    public static ExprEditor instrument() {
        return new ExprEditor() {
            @Override public void edit(FieldAccess field) throws CannotCompileException {
                if (field.isReader() && field.getFieldName().equals("currentBlock"))
                    field.replace("{ $_ = blockreminder.patches.RenderPreviewPatch.displayedBlock($0, $proceed()); }");
                else if (field.isReader() && (field.getFieldName().equals("blockColor")
                        || field.getFieldName().equals("blockTextColor")))
                    field.replace("{ $_ = blockreminder.patches.RenderPreviewPatch.shieldColor($0, $proceed(), "
                            + field.getFieldName().equals("blockTextColor") + "); }");
                else if (field.isReader() && (field.getFieldName().equals("blockScale")
                        || field.getFieldName().equals("blockOffset")))
                    field.replace("{ $_ = blockreminder.patches.RenderPreviewPatch.shieldAnimation($0, $proceed(), "
                            + field.getFieldName().equals("blockScale") + "); }");
            }
        };
    }
}
