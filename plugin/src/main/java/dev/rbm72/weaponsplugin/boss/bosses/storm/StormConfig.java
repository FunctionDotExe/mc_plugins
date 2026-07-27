package dev.rbm72.weaponsplugin.boss.bosses.storm;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable the Storm Tyrant's terrain systems run on, read live from {@code bosses.storm_tyrant.<key>}.
 * Same reasoning as {@code KingConfig}/{@code FrostConfig}: read on every call rather than cached, so
 * {@code /bossreload} retunes a live fight.
 */
final class StormConfig {

    private static final String PREFIX = "bosses.storm_tyrant.";

    private final WeaponsPlugin plugin;

    StormConfig(WeaponsPlugin plugin) {
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
