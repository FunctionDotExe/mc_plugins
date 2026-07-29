package dev.rbm72.weaponsplugin.boss.bosses.choir;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable the Hollow Choir's noise model runs on, read live from
 * {@code bosses.hollow_choir.<key>}. Read on every call rather than cached, so {@code /bossreload}
 * retunes a live fight.
 */
final class ChoirConfig {

    private static final String PREFIX = "bosses.hollow_choir.";

    private final WeaponsPlugin plugin;

    ChoirConfig(WeaponsPlugin plugin) {
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
