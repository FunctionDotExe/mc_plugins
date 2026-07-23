package dev.rbm72.weaponsplugin.boss;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * A boss's claimed patch of the world: a center, a radius, no permanent
 * changes. Never edits blocks — arena "mechanics" are pure particle/damage
 * effects layered over whatever terrain already exists here.
 */
public final class Arena {

    private final Location center;
    private final double radius;

    public Arena(Location center, double radius) {
        this.center = center.clone();
        this.radius = radius;
    }

    public Location center() {
        return center.clone();
    }

    public double radius() {
        return radius;
    }

    public World world() {
        return center.getWorld();
    }

    public boolean isInside(Location loc) {
        World world = center.getWorld();
        if (world == null || loc.getWorld() == null || !loc.getWorld().equals(world)) {
            return false;
        }
        return loc.distanceSquared(center) <= radius * radius;
    }

    public List<Player> playersInside() {
        return playersWithin(radius);
    }

    /**
     * Players within {@code radius + buffer}. Used for UI presence (boss bar, phase titles)
     * so a knockback/dash attack that briefly shoves a player past the strict arena radius
     * doesn't hide their bar or make them miss a one-shot phase-transition title.
     */
    public List<Player> playersNear(double buffer) {
        return playersWithin(radius + buffer);
    }

    private List<Player> playersWithin(double effectiveRadius) {
        return playersNear(center, effectiveRadius);
    }

    /**
     * Players within {@code radius} of an arbitrary point — for live-combat checks that need to
     * track wherever the fight actually is right now (the boss's current location), not this
     * arena's fixed spawn-time {@link #center()}. A boss that chases its target or a knockback/dash
     * attack routinely drifts a fight well away from where it started; anything anchored to the
     * frozen spawn point instead of the boss's live position silently stops seeing players who are
     * still standing right next to it.
     */
    public static List<Player> playersNear(Location point, double radius) {
        World world = point.getWorld();
        if (world == null) {
            return List.of();
        }
        double radiusSq = radius * radius;
        return world.getPlayers().stream()
                .filter(p -> p.getLocation().distanceSquared(point) <= radiusSq)
                .toList();
    }
}
