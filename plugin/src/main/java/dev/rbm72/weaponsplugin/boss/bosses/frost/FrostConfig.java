package dev.rbm72.weaponsplugin.boss.bosses.frost;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable the Frost Queen's terrain systems run on, read live from {@code bosses.frost_queen.<key>}.
 * Same reasoning as {@code KingConfig}: her mechanics span half a dozen collaborating objects and cannot
 * thread their defaults down from the boss definition without landing a file away from the code that
 * means something by them. Read on every call rather than cached, so {@code /bossreload} retunes a live fight.
 */
final class FrostConfig {

    private static final String PREFIX = "bosses.frost_queen.";

    private final WeaponsPlugin plugin;

    FrostConfig(WeaponsPlugin plugin) {
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
