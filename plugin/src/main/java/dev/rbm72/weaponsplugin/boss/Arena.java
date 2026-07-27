package dev.rbm72.weaponsplugin.boss;

import org.bukkit.GameMode;
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
    private Location liveCenter;
    private final double radius;

    public Arena(Location center, double radius) {
        this.center = center.clone();
        this.liveCenter = center.clone();
        this.radius = radius;
    }

    /**
     * Fixed spawn point of the fight — never moves. Anchor for arena-wide geometry that must stay
     * put regardless of where the boss wanders: region guard, ambient FX, and AoE telegraphs that
     * blanket "the arena" (ground rings, danger zones, safe-spot pillars).
     */
    public Location center() {
        return center.clone();
    }

    /**
     * Updates the live combat anchor to the boss's current location — called every tick. Combat/UI
     * presence checks ({@link #playersInside()}, {@link #playersNear(double)}) follow this, not the
     * frozen {@link #center()}: a boss that chases its target or a knockback/dash attack routinely
     * drifts a fight well away from where it started, and anything anchored to the spawn point
     * silently stops seeing players standing right next to the boss — which reads in-game as the
     * boss bar vanishing and the boss going passive (no target found, so no attacks selected).
     */
    public void updateLiveCenter(Location loc) {
        if (loc != null && loc.getWorld() != null) {
            this.liveCenter = loc.clone();
        }
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
        return playersNear(liveCenter, effectiveRadius);
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

    /**
     * True for a player a mechanic should be allowed to pick as a target/victim or count as "present
     * for" health-scaling — same filter {@link dev.rbm72.weaponsplugin.boss.ai.TargetSelector} already
     * applies to normal attack targeting. Spectators and creative players are just watching; letting
     * one get locked in as e.g. a duel opponent (who nobody else can then damage the boss around) or
     * counted toward the arena's player count softlocks or inflates the fight for no reason.
     */
    public static boolean isCombatant(Player p) {
        return p.isOnline() && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE;
    }

    /**
     * {@link #playersNear(Location, double)} filtered to {@link #isCombatant}. Every mechanic/gate/
     * attack call site picking a target, victim, or "is anyone doing the thing" check wants this, not
     * the raw distance filter — {@code playersNear} itself stays UI-facing (it backs the instance
     * {@link #playersInside()}/{@link #playersNear(double)} presence checks used for boss bars and
     * titles, which spectators should still see).
     */
    public static List<Player> combatants(Location point, double radius) {
        return playersNear(point, radius).stream().filter(Arena::isCombatant).toList();
    }
}
