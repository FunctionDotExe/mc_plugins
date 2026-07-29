package dev.rbm72.weaponsplugin.boss.bosses.colossus;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable number the Solar Colossus's body-as-arena systems run on, read live from
 * {@code bosses.solar_colossus.<key>}.
 * <p>
 * Same rationale as {@code NecroConfig}: this boss's mechanics span half a dozen collaborating
 * objects (joints, the kneel cycle, beacons, pillars, debris), so a constructor-argument list handed
 * down from {@code SolarColossus} would run to dozens of parameters spread across five files. Read on
 * every call rather than cached, so {@code /bossreload} retunes a live fight.
 */
final class ColossusConfig {

    private static final String PREFIX = "bosses.solar_colossus.";

    private final WeaponsPlugin plugin;

    ColossusConfig(WeaponsPlugin plugin) {
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
