package dev.rbm72.weaponsplugin.boss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A soft arena wall that keeps players in the fight: a vanilla {@link WorldBorder} shown only to
 * the players currently in the arena, centered on the arena's fixed spawn point. Uses Paper's
 * per-player world border, so it never touches the world's real border or anyone outside the fight,
 * and edits no blocks — the wall is pure client-side vanilla (the red warning haze plus the hard
 * "can't walk past" barrier). Every trapped player's border is restored when the fight ends.
 */
public final class ArenaBarrier {

    /** How close to the wall the red warning haze kicks in, in blocks. */
    private static final int WARNING_DISTANCE = 3;

    /**
     * A player counts as "in the arena" (and so gets/keeps the wall) out to this multiple of the
     * radius. The border is a square whose half-side is the radius, so a player standing in a corner
     * sits ~1.41× the radius from center; capturing out to 1.5× makes sure a legitimately-in-arena
     * corner-hugger is never mistaken for having left.
     */
    private static final double CAPTURE_FACTOR = 1.5;

    /**
     * Only release a trapped player once they're this far past the wall — well beyond any corner or
     * knockback overshoot. In practice the wall stops anyone from walking out, so this only fires
     * when a player leaves by other means (died and respawned elsewhere, was teleported away),
     * which is exactly when their arena border should be lifted.
     */
    private static final double RELEASE_FACTOR = 3.0;

    private final Location center;
    private final double radius;
    private final Set<UUID> bordered = new HashSet<>();

    ArenaBarrier(Location center, double radius) {
        this.center = center.clone();
        this.radius = radius;
    }

    private WorldBorder freshBorder() {
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(center.getX(), center.getZ());
        border.setSize(radius * 2.0); // square side = diameter; circumscribes the circular arena
        border.setWarningDistance(WARNING_DISTANCE);
        return border;
    }

    /**
     * Traps every player currently in the arena and releases anyone who has since ended up well
     * outside it. Called every tick.
     */
    void update() {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        double captureSq = squared(radius * CAPTURE_FACTOR);
        double releaseSq = squared(radius * RELEASE_FACTOR);

        for (Player player : Arena.playersNear(center, radius * CAPTURE_FACTOR)) {
            if (bordered.add(player.getUniqueId())) {
                player.setWorldBorder(freshBorder());
            }
        }

        bordered.removeIf(id -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                return true; // a relog already reset their border to the world's
            }
            Location loc = player.getLocation();
            boolean gone = loc.getWorld() == null || !loc.getWorld().equals(world)
                    || loc.distanceSquared(center) > releaseSq;
            if (gone) {
                player.setWorldBorder(null);
                return true;
            }
            return false;
        });
    }

    /** Restores every trapped player's normal (world) border — the fight is over. */
    void clear() {
        for (UUID id : bordered) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.setWorldBorder(null);
            }
        }
        bordered.clear();
    }

    private static double squared(double v) {
        return v * v;
    }
}
