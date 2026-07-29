package dev.rbm72.weaponsplugin.boss.bosses.wyrm;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable the Voidwyrm's four phase systems run on, read live from {@code bosses.voidwyrm.<key>}.
 * Same reasoning as {@code StormConfig}/{@code ColossusConfig}: read on every call rather than cached,
 * so {@code /bossreload} retunes a live fight.
 */
final class WyrmConfig {

    private static final String PREFIX = "bosses.voidwyrm.";

    private final WeaponsPlugin plugin;

    WyrmConfig(WeaponsPlugin plugin) {
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
