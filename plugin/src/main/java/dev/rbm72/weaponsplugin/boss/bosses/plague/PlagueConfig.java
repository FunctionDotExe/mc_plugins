package dev.rbm72.weaponsplugin.boss.bosses.plague;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable the Plague Warden's terrain systems run on, read live from {@code bosses.plague_warden.<key>}.
 * Same reasoning as {@code KingConfig}/{@code FrostConfig}/{@code StormConfig}.
 */
final class PlagueConfig {

    private static final String PREFIX = "bosses.plague_warden.";

    private final WeaponsPlugin plugin;

    PlagueConfig(WeaponsPlugin plugin) {
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
