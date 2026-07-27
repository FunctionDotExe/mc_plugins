package dev.rbm72.weaponsplugin.boss.meter;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Everything a {@link MeterCondition} is allowed to look at for one player, for one pulse.
 * <p>
 * Conditions get a context object rather than {@code (instance, player)} because two of the four
 * skins need state the player object simply does not carry: how far they moved since the last pulse
 * (Void Echo decays on <em>continuous</em> movement, which a single position snapshot cannot answer)
 * and how full their own meter already is (a skin that ramps as it fills). Recomputing movement
 * inside every condition would need a second position history per condition and would disagree with
 * the one the meter itself keeps.
 * <p>
 * Built fresh each pulse and thrown away — never store one.
 */
public record MeterContext(BossInstance instance, Player player, double value, double cap,
                           double stepSeconds, double blocksMovedThisStep) {

    /** 0..1 fill, the same number the readout bar shows. */
    public double fraction() {
        return cap <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, value / cap));
    }

    public Location location() {
        return player.getLocation();
    }

    public World world() {
        return player.getWorld();
    }

    /** The block the player's feet occupy — the one that answers "am I standing in fire/water/powder snow". */
    public Block blockAtFeet() {
        return player.getLocation().getBlock();
    }

    /** The block being stood <em>on</em>, which is what "standing on ice" and "on corrupted ground" mean. */
    public Block blockBelow() {
        return player.getLocation().getBlock().getRelative(0, -1, 0);
    }

    /**
     * Horizontal distance to the boss, or {@link Double#MAX_VALUE} across worlds.
     * <p>
     * Horizontal on purpose: every "closer to her means colder" rule in the designs is about standing
     * in melee range, and a boss that is mid-leap or hovering (Storm Tyrant spends a whole phase in
     * the air) must not read as far away to a player standing directly underneath it.
     */
    public double distanceToBoss() {
        return flatDistance(player.getLocation(), instance.entity().getLocation());
    }

    /** How fast the player is actually travelling this pulse, in blocks per second. */
    public double blocksPerSecond() {
        return stepSeconds <= 0 ? 0.0 : blocksMovedThisStep / stepSeconds;
    }

    /** Horizontal distance only, null/cross-world safe. Shared by the condition library. */
    public static double flatDistance(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null
                || !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
