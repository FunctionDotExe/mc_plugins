package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.WeaponsPlugin;

/**
 * Every tunable the Fallen King's court systems run on, read live from
 * {@code bosses.fallen_king.<key>}.
 * <p>
 * Same reasoning as {@code NecroConfig}: {@link dev.rbm72.weaponsplugin.boss.Boss}'s config helpers are
 * {@code protected}, and a boss whose mechanics span nine collaborating objects cannot thread forty
 * constructor parameters down from its definition without the defaults ending up a file away from the
 * code that means something by them.
 * <p>
 * Read on every call rather than cached, so {@code /bossreload} retunes a live fight.
 */
final class KingConfig {

    private static final String PREFIX = "bosses.fallen_king.";

    private final WeaponsPlugin plugin;

    KingConfig(WeaponsPlugin plugin) {
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
