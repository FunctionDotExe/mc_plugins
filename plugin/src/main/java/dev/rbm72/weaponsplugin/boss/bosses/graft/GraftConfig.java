package dev.rbm72.weaponsplugin.boss.bosses.graft;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable the Grafted Horror's circuitry runs on, read live from
 * {@code bosses.grafted_horror.<key>}. Same reasoning as {@code StormConfig}: read on every call rather
 * than cached, so {@code /bossreload} retunes a live fight.
 */
final class GraftConfig {

    private static final String PREFIX = "bosses.grafted_horror.";

    private final WeaponsPlugin plugin;

    GraftConfig(WeaponsPlugin plugin) {
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
