package dev.rbm72.weaponsplugin.boss.bosses.bane;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable the Threefold Bane's tempo runs on, read live from {@code bosses.threefold_bane.<key>}.
 * Read on every call rather than cached, so {@code /bossreload} retunes a live fight.
 */
final class BaneConfig {

    private static final String PREFIX = "bosses.threefold_bane.";

    private final WeaponsPlugin plugin;

    BaneConfig(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    double dbl(String key, double def) {
        return plugin.getConfig().getDouble(PREFIX + key, def);
    }

    int num(String key, int def) {
        return plugin.getConfig().getInt(PREFIX + key, def);
    }

    boolean flag(String key, boolean def) {
        return plugin.getConfig().getBoolean(PREFIX + key, def);
    }
}
