package dev.rbm72.weaponsplugin.boss.bosses.leviathan;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable number the Tide Leviathan's water systems run on, read live from
 * {@code bosses.tide_leviathan.<key>}.
 * <p>
 * Same rationale as {@code NecroConfig}/{@code StormConfig}: this boss's mechanics span half a dozen
 * collaborating objects (the fluid engine, conduits, bubble columns, the whirlpool, guardians, the
 * breath system), and threading forty constructor parameters down from {@link
 * dev.rbm72.weaponsplugin.boss.bosses.TideLeviathan} would put every default a file away from the code
 * that means something by it. Read on every call rather than cached, so {@code /bossreload} retunes a
 * live fight.
 */
final class LeviathanConfig {

    private static final String PREFIX = "bosses.tide_leviathan.";

    private final WeaponsPlugin plugin;

    LeviathanConfig(WeaponsPlugin plugin) {
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
