package blockreminder;

import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import java.io.IOException;
import java.util.Properties;

public final class ModSettings {
    // Keep the legacy total setting readable for existing configuration files.
    // Native shield replacement always displays the forecast total.
    public boolean enabled = true, total = false, ethereal = true, showZero = false;
    private SpireConfig config;

    public void load() throws IOException {
        Properties defaults = new Properties();
        defaults.setProperty("enabled", "true");
        defaults.setProperty("total", "false");
        defaults.setProperty("ethereal", "true");
        defaults.setProperty("showZero", "false");
        config = new SpireConfig(BlockReminderMod.ID, "config", defaults);
        enabled = config.getBool("enabled");
        total = config.getBool("total");
        ethereal = config.getBool("ethereal");
        showZero = config.getBool("showZero");
    }

    public void save() throws IOException {
        if (config == null) return;
        config.setBool("enabled", enabled);
        config.setBool("total", total);
        config.setBool("ethereal", ethereal);
        config.setBool("showZero", showZero);
        config.save();
    }
}
