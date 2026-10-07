package blockreminder.game;

import blockreminder.BlockReminderMod;
import blockreminder.core.BlockForecast;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import java.lang.reflect.Field;

/** Reuses the native Block icon and value; explanation stays in the tooltip. */
public final class PreviewRenderer {
    private static Field healthYOffset;
    private PreviewRenderer() { }

    public static void initialize() {
        ensureApproximationGlyph();
        try {
            healthYOffset = AbstractCreature.class.getDeclaredField("hbYOffset");
            healthYOffset.setAccessible(true);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot read native health offset", ex);
        }
    }

    private static void ensureApproximationGlyph() {
        BitmapFont font = FontHelper.tipBodyFont;
        BitmapFont.BitmapFontData data = font.getData();
        if (data.hasGlyph('\u2248')) return;
        // Pack the missing symbol into the existing atlas so incremental font
        // growth keeps the original page indices and all other glyphs intact.
        try {
            Field field = FreeTypeFontGenerator.FreeTypeBitmapFontData.class.getDeclaredField("packer");
            field.setAccessible(true);
            PixmapPacker packer = (PixmapPacker) field.get(data);
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(
                    Gdx.files.internal("font/zhs/NotoSansMonoCJKsc-Regular.otf"));
            try {
                FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
                parameter.characters = "\u2248";
                parameter.size = Math.round(22 * Settings.scale);
                parameter.gamma = 0.9f;
                parameter.shadowColor = Settings.QUARTER_TRANSPARENT_BLACK_COLOR;
                parameter.shadowOffsetX = (int) (3 * Settings.scale);
                parameter.shadowOffsetY = (int) (3 * Settings.scale);
                parameter.packer = packer;
                FreeTypeFontGenerator.FreeTypeBitmapFontData symbols = generator.generateData(parameter);
                BitmapFont.Glyph glyph = symbols.getGlyph('\u2248');
                if (glyph == null) throw new IllegalStateException("Approximation glyph unavailable");
                Texture texture = font.getRegion().getTexture();
                packer.updateTextureRegions(font.getRegions(), texture.getMinFilter(), texture.getMagFilter(), false);
                glyph.yoffset += Math.round(symbols.ascent - data.ascent);
                data.setGlyphRegion(glyph, font.getRegion(glyph.page));
                data.setGlyph('\u2248', glyph);
            } finally { generator.dispose(); }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot add approximation glyph to tooltip font", ex);
        }
    }

    /** Shared with runtime QA so hover targets use the actual measured layout. */
    public static final class Bounds {
        public final float x, y, width, height;
        public final String number;
        private Bounds(float x, float y, float width, float height, String number) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.number = number;
        }
        public float centerY() { return y + height / 2; }
        public boolean contains(float mx, float my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }

    public static Bounds bounds(AbstractPlayer p, BlockForecast forecast) {
        try { return bounds(p, forecast, healthYOffset.getFloat(p)); }
        catch (IllegalAccessException ex) { throw new IllegalStateException(ex); }
    }

    private static Bounds bounds(AbstractPlayer p, BlockForecast forecast, float hbYOffset) {
        float scale = Settings.scale;
        String number = Integer.toString(forecast.finalBlock);
        float width = 64 * scale;
        float height = 44 * scale;
        float cx = p.hb.x - 14 * scale;
        float cy = p.hb.y + hbYOffset - 14 * scale;
        float x = cx - width / 2;
        float y = cy - height / 2;
        return new Bounds(x, y, width, height, number);
    }

    public static boolean usesPreview(AbstractCreature creature) {
        return localDisplayTarget(creature)
                && BlockReminderMod.PREVIEW.availableFor((AbstractPlayer) creature);
    }

    public static boolean usesDisplay(AbstractCreature creature) {
        return localDisplayTarget(creature)
                && (BlockReminderMod.PREVIEW.availableFor((AbstractPlayer) creature)
                    || BlockReminderMod.PREVIEW.holdingFor((AbstractPlayer) creature));
    }

    private static boolean localDisplayTarget(AbstractCreature creature) {
        return creature instanceof AbstractPlayer
                && creature == AbstractDungeon.player
                && !Settings.hideCombatElements
                && BlockReminderMod.TEXT != null;
    }

    public static boolean hoveringPreview(AbstractPlayer player) {
        return usesPreview(player) && BlockReminderMod.PREVIEW.visibleFor(player)
                && bounds(player, BlockReminderMod.PREVIEW.forecast())
                .contains(InputHelper.mX, InputHelper.mY);
    }

    public static void render(AbstractPlayer p, SpriteBatch sb) {
        if (!usesPreview(p) || !BlockReminderMod.PREVIEW.visibleFor(p)) return;
        BlockForecast f = BlockReminderMod.PREVIEW.forecast();
        Bounds b = bounds(p, f);
        if (b == null) return;
        if (b.contains(InputHelper.mX, InputHelper.mY)) renderTooltip(b, f);
    }

    private static void renderTooltip(Bounds b, BlockForecast f) {
        float scale = Settings.scale;
        float margin = 24 * scale;
        float maxHeight = Settings.HEIGHT - 2 * margin;
        int sources = 6;
        String body = PreviewTooltip.body(f, sources);
        while (tooltipHeight(body) > maxHeight && sources > 0)
            body = PreviewTooltip.body(f, --sources);
        float height = tooltipHeight(body);
        float x = b.x + b.width + 12 * scale;
        if (x + 329 * scale > Settings.WIDTH - margin) x = b.x - 332 * scale;
        x = Math.max(margin, Math.min(x, Settings.WIDTH - margin - 329 * scale));
        // Raise the tooltip above the native shield and health row. GenericTip
        // uses drawY as the body origin; account for its cap and shadow.
        float y = Math.max(margin + height - 32 * scale,
                Math.min(b.centerY() + 44 * scale + height - 32 * scale,
                        Settings.HEIGHT - margin - 32 * scale));
        TipHelper.renderGenericTip(x, y, BlockReminderMod.TEXT[1], body);
    }

    public static float tooltipHeight(String body) {
        return -FontHelper.getSmartHeight(FontHelper.tipBodyFont, body,
                280 * Settings.scale, 26 * Settings.scale) + 103 * Settings.scale;
    }
}
