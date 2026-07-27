package dev.rbm72.weaponsplugin.boss.bosses.necro;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable number the Necro Overlord's horde systems run on, read live from
 * {@code bosses.necro_overlord.<key>}.
 * <p>
 * {@link dev.rbm72.weaponsplugin.boss.Boss}'s own {@code configDouble}/{@code configInt} helpers are
 * {@code protected}, so nothing outside a {@code Boss} subclass can reach them — which is why the
 * older mechanics take their numbers as long constructor argument lists handed down from the boss
 * class. That does not survive a boss whose mechanics span five collaborating objects: the
 * constructor lists would run to forty parameters and the defaults would live a file away from the
 * code that means something by them. {@code boss.gates.Gates} already set the precedent of reading
 * the same key namespace through its own accessor; this is that, scoped to one boss.
 * <p>
 * Read on every call rather than cached, so {@code /bossreload} retunes a live fight.
 */
final class NecroConfig {

    private static final String PREFIX = "bosses.necro_overlord.";

    private final WeaponsPlugin plugin;

    NecroConfig(WeaponsPlugin plugin) {
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
