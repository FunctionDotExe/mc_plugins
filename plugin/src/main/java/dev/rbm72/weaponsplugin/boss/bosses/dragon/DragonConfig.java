package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable number the Dragon Elder's aerial systems run on, read live from
 * {@code bosses.dragon_elder.<key>}.
 * <p>
 * Same rationale as {@code NecroConfig}: this boss's mechanics span half a dozen collaborating objects
 * (the aerial rig, four pillars, a wing-membrane meter, a continuous fireball spawner, strafing runs,
 * fire lanes) and a constructor-argument list handed down from {@code DragonElder} would run to dozens
 * of parameters spread across files that mean nothing without the code beside them. Read on every call,
 * never cached, so {@code /bossreload} retunes a live fight.
 */
final class DragonConfig {

    private static final String PREFIX = "bosses.dragon_elder.";

    private final WeaponsPlugin plugin;

    DragonConfig(WeaponsPlugin plugin) {
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
