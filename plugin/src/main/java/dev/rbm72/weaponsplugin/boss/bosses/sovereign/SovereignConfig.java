package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable the Void Sovereign's terrain systems run on, read live from
 * {@code bosses.void_sovereign.<key>}. Same reasoning as {@code KingConfig}/{@code FrostConfig}/
 * {@code StormConfig}/{@code PlagueConfig}.
 */
final class SovereignConfig {

    private static final String PREFIX = "bosses.void_sovereign.";

    private final WeaponsPlugin plugin;

    SovereignConfig(WeaponsPlugin plugin) {
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
