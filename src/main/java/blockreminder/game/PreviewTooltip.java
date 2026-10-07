package blockreminder.game;

import blockreminder.BlockReminderMod;
import blockreminder.core.BlockForecast;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Player-facing text only. The forecast retains complete diagnostic reasons. */
public final class PreviewTooltip {
    private PreviewTooltip() { }

    public static String signed(int amount) { return (amount >= 0 ? "+" : "") + amount; }

    public static String body(BlockForecast forecast, int sourceLimit) {
        String arrow = FontHelper.tipBodyFont.getData().hasGlyph('\u2192') ? " \u2192 " : " -> ";
        StringBuilder body = new StringBuilder().append(forecast.initialBlock).append(arrow)
                .append(forecast.finalBlock).append(" (").append(signed(forecast.delta)).append(")");
        if (forecast.lines.isEmpty()) body.append(" NL ").append(text(14));
        int shown = Math.min(Math.max(0, sourceLimit), forecast.lines.size());
        for (int i = 0; i < shown; i++) {
            BlockForecast.Line line = forecast.lines.get(i);
            body.append(" NL ").append(shortName(line.source)).append(": ").append(signed(line.delta));
        }
        if (shown < forecast.lines.size())
            body.append(" NL ").append(format(15, forecast.lines.size() - shown));
        if (!forecast.isExact()) {
            body.append(" NL NL \u2248 ").append(text(10));
            Set<String> reasons = new LinkedHashSet<>();
            for (String warning : forecast.uncertainties) reasons.add(playerReason(warning));
            int count = 0;
            for (String reason : reasons) {
                if (count++ == 2) break;
                body.append(" NL ").append(reason);
            }
            if (reasons.size() > 2) body.append(" NL ").append(format(23, reasons.size() - 2));
        }
        return body.toString();
    }

    private static String playerReason(String warning) {
        String prefix = "Unmodeled end-turn effect: ";
        if (warning.startsWith(prefix)) {
            // This is the format produced by GameSnapshot, not an arbitrary
            // regex that might accidentally remove a mod's gameplay text.
            int classStart = warning.lastIndexOf(" (");
            String name = warning.substring(prefix.length(), classStart < prefix.length() ? warning.length() : classStart);
            return format(17, shortName(name));
        }
        if (warning.equals(BlockReminderMod.TEXT[12])) return text(12);
        if (warning.equals("HP loss may trigger further effects")
                || warning.equals("Regret HP loss may trigger further effects")) return text(18);
        if (warning.startsWith("Enemy block modifier: ")) return text(19);
        if (warning.startsWith("Extension failed: ")) return text(20);
        if (warning.startsWith("Unstable Molecules counter unavailable: ")) return text(21);
        if (warning.startsWith("Modded character may change end-turn rules: ")) return text(22);
        return text(16);
    }

    private static String shortName(String name) {
        String safe = name.replace(" NL ", " ").replace('#', ' ').replace('[', '(')
                .replace(']', ')').replace('\n', ' ').replace('\r', ' ').trim();
        int count = safe.codePointCount(0, safe.length());
        if (count > 80) safe = safe.substring(0, safe.offsetByCodePoints(0, 80)) + "...";
        // Native text can wrap ordinary names, including Gold-Plated Cables.
        // Only a single unbreakable token needs shortening in word-wrap mode.
        if (Settings.lineBreakViaCharacter) return safe;
        StringBuilder result = new StringBuilder();
        for (String word : safe.split(" +")) {
            if (result.length() > 0) result.append(' ');
            result.append(shortWord(word));
        }
        return result.toString();
    }

    private static String shortWord(String word) {
        float width = 210 * Settings.scale;
        if (FontHelper.getWidth(FontHelper.tipBodyFont, word, 1) <= width) return word;
        int count = word.codePointCount(0, word.length());
        while (count > 0) {
            String shortened = word.substring(0, word.offsetByCodePoints(0, --count)) + "...";
            if (FontHelper.getWidth(FontHelper.tipBodyFont, shortened, 1) <= width) return shortened;
        }
        return "...";
    }

    private static String text(int index) { return BlockReminderMod.TEXT[index]; }
    private static String format(int index, Object value) {
        return String.format(Locale.ROOT, text(index), value);
    }
}
