package dev.rbm72.weaponsplugin.fx;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Personal, per-player particle density for weapon effects and boss effects, stored directly on
 * each player's own {@link org.bukkit.persistence.PersistentDataContainer} so it survives relogs
 * and restarts without a separate data file. Values are a percentage (0-200, default 100) that
 * {@link Fx} multiplies its already-scaled particle counts by, per viewer, so two players standing
 * in the same fight can see different amounts of clutter without affecting anyone else.
 */
public final class PlayerParticlePrefs {

    public static final int DEFAULT_PERCENT = 100;
    public static final int MIN_PERCENT = 0;
    public static final int MAX_PERCENT = 200;

    private static NamespacedKey weaponKey;
    private static NamespacedKey bossKey;

    private PlayerParticlePrefs() {
    }

    /** Called once from the plugin's onEnable, mirroring {@link Fx#init}. */
    public static void init(Plugin plugin) {
        weaponKey = new NamespacedKey(plugin, "particle-scale-weapon");
        bossKey = new NamespacedKey(plugin, "particle-scale-boss");
    }

    public static int weaponPercent(Player player) {
        return percent(player, weaponKey);
    }

    public static int bossPercent(Player player) {
        return percent(player, bossKey);
    }

    public static void setWeaponPercent(Player player, int percent) {
        player.getPersistentDataContainer().set(weaponKey, PersistentDataType.INTEGER, clamp(percent));
    }

    public static void setBossPercent(Player player, int percent) {
        player.getPersistentDataContainer().set(bossKey, PersistentDataType.INTEGER, clamp(percent));
    }

    /** The multiplier {@link Fx} applies to an already-scaled particle count for this viewer/category. */
    static double multiplier(Player viewer, Fx.ParticleCategory category) {
        int percent = category == Fx.ParticleCategory.BOSS ? bossPercent(viewer) : weaponPercent(viewer);
        return percent / 100.0;
    }

    public static int clamp(int percent) {
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
    }

    private static int percent(Player player, NamespacedKey key) {
        if (key == null) {
            return DEFAULT_PERCENT;
        }
        Integer value = player.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return value == null ? DEFAULT_PERCENT : value;
    }
}
