package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable the Worldender's eight phases run on, read live from {@code bosses.worldender.<key>}.
 * Same reasoning as every other reworked boss's config wrapper: read on every call rather than cached,
 * so {@code /bossreload} retunes a live fight.
 */
final class WorldenderConfig {

    private static final String PREFIX = "bosses.worldender.";

    private final WeaponsPlugin plugin;

    WorldenderConfig(WeaponsPlugin plugin) {
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
