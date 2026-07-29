package dev.rbm72.weaponsplugin.boss.bosses.bulk;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable the Amalgamated Bulk's growth economy runs on, read live from
 * {@code bosses.amalgamated_bulk.<key>}. Read on every call rather than cached, so {@code /bossreload}
 * retunes a live fight.
 */
final class BulkConfig {

    private static final String PREFIX = "bosses.amalgamated_bulk.";

    private final WeaponsPlugin plugin;

    BulkConfig(WeaponsPlugin plugin) {
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
