package dev.rbm72.weaponsplugin.boss.bosses.weeping;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable the Weeping Colossus's closing room runs on, read live from
 * {@code bosses.weeping_colossus.<key>}. Read on every call rather than cached, so {@code /bossreload}
 * retunes a live fight.
 */
final class WeepingConfig {

    private static final String PREFIX = "bosses.weeping_colossus.";

    private final WeaponsPlugin plugin;

    WeepingConfig(WeaponsPlugin plugin) {
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
