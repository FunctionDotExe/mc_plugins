package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable number the Inferno Warlord's foundry systems run on, read live from
 * {@code bosses.inferno_warlord.<key>}. Same reasoning as {@code NecroConfig}/{@code StormConfig}: this
 * boss's mechanics span eight collaborating objects (cauldrons, rising lava, fire trails, TNT clusters,
 * magma hazards, burning logs, Cinder Nova, the Burning meter), too many to thread through constructor
 * argument lists without the defaults drifting away from the code that means something by them. Read on
 * every call rather than cached, so {@code /bossreload} retunes a live fight.
 */
final class InfernoConfig {

    private static final String PREFIX = "bosses.inferno_warlord.";

    private final WeaponsPlugin plugin;

    InfernoConfig(WeaponsPlugin plugin) {
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
