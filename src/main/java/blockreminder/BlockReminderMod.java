package blockreminder;

import basemod.BaseMod;
import basemod.ModLabel;
import basemod.ModLabeledToggleButton;
import basemod.ModPanel;
import basemod.interfaces.EditStringsSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import blockreminder.game.PreviewService;
import blockreminder.game.PreviewRenderer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.UIStrings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;

@SpireInitializer
public final class BlockReminderMod implements EditStringsSubscriber,
        PostInitializeSubscriber, PostUpdateSubscriber {
    public static final String ID = "block-reminder-reborn";
    public static final Logger LOG = LogManager.getLogger(ID);
    public static final ModSettings SETTINGS = new ModSettings();
    public static String[] TEXT;
    public static final PreviewService PREVIEW = new PreviewService();

    public static void initialize() { BaseMod.subscribe(new BlockReminderMod()); }

    @Override public void receiveEditStrings() {
        String language = Settings.language == null ? "eng"
                : Settings.language.name().toLowerCase(Locale.ROOT);
        String resource = "blockreminder/localization/" + language + "/UIStrings.json";
        if (!Gdx.files.internal(resource).exists()) {
            LOG.warn("No localization for {}; using English", language);
            resource = "blockreminder/localization/eng/UIStrings.json";
        }
        BaseMod.loadCustomStringsFile(UIStrings.class, resource);
    }

    @Override public void receivePostInitialize() {
        TEXT = CardCrawlGame.languagePack.getUIString(ID + ":UI").TEXT;
        PreviewRenderer.initialize();
        try { SETTINGS.load(); }
        catch (IOException | RuntimeException ex) { LOG.warn("Using default settings", ex); }
        ModPanel panel = new ModPanel();
        toggle(panel, 2, 720, SETTINGS.enabled, v -> SETTINGS.enabled = v);
        toggle(panel, 4, 660, SETTINGS.ethereal, v -> SETTINGS.ethereal = v);
        toggle(panel, 5, 600, SETTINGS.showZero, v -> SETTINGS.showZero = v);
        panel.addUIElement(new ModLabel(TEXT[6], 350, 470, FontHelper.tipBodyFont, panel, l -> { }));
        panel.addUIElement(new ModLabel(TEXT[7], 350, 430, FontHelper.tipBodyFont, panel, l -> { }));
        BaseMod.registerModBadge(ImageMaster.loadImage("blockreminder/images/badge.png"),
                TEXT[0], "芝士龙", TEXT[7], panel);
        LOG.info("Read-only block preview initialized");
    }

    private void toggle(ModPanel panel, int text, float y, boolean initial, Consumer<Boolean> change) {
        panel.addUIElement(new ModLabeledToggleButton(TEXT[text], 350, y, Color.WHITE,
                FontHelper.charDescFont, initial, panel, label -> { }, button -> {
                    change.accept(button.enabled);
                    PREVIEW.invalidate();
                    try { SETTINGS.save(); }
                    catch (IOException | RuntimeException ex) { LOG.warn("Cannot save settings", ex); }
                }));
    }

    @Override public void receivePostUpdate() { PREVIEW.update(); }
}
