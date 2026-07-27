package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * A region of the arena a mechanic asks about every tick — "is this player somewhere that counts".
 * <p>
 * Pulled out of the mechanics themselves because the same handful of shapes keep recurring under
 * completely different fiction: a drifting patch of warm ground, the shade of a colossus, a shelter
 * from a downpour, the far edge of a heat aura. Keeping the shape separate from the rule means a
 * stack meter does not have to know whether the safe ground moves, and a moving-safe-ground field
 * does not have to know what standing in it is worth.
 * <p>
 * {@link #contains} is the only abstract method, so a boss that just needs "further than 8 blocks
 * from the boss" can pass a lambda instead of a class.
 */
public interface MechanicField {

    /** True when this player is inside the region right now. Called every tick — keep it cheap. */
    boolean contains(BossInstance instance, Player player);

    /** Spawn props / pick starting positions. */
    default void start(BossInstance instance) {
    }

    /** Move and draw the region. {@code elapsedTicks} counts from the owning mechanic's start. */
    default void tick(BossInstance instance, int elapsedTicks) {
    }

    /** Remove anything {@link #start} or {@link #tick} created. Must be safe to call twice. */
    default void stop(BossInstance instance) {
    }

    /** Where the nearest piece of this region is, for a "run that way" cue. Null when unknowable. */
    default Location nearestAnchor(BossInstance instance, Player player) {
        return null;
    }

    /** Everywhere is inside — a mechanic with no positional component at all. */
    static MechanicField everywhere() {
        return (instance, player) -> true;
    }

    /** Inside when the player is at least {@code minDistance} blocks from the boss, horizontally. */
    static MechanicField beyondBoss(double minDistance) {
        return (instance, player) -> {
            Location boss = instance.entity().getLocation();
            Location at = player.getLocation();
            if (at.getWorld() == null || boss.getWorld() == null || !at.getWorld().equals(boss.getWorld())) {
                return true;
            }
            double dx = at.getX() - boss.getX();
            double dz = at.getZ() - boss.getZ();
            return dx * dx + dz * dz >= minDistance * minDistance;
        };
    }
}
