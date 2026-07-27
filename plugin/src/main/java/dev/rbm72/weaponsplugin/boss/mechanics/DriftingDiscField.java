package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A handful of safe circles that wander the arena floor — heat pockets in a killing cold, shade under
 * a burning sun, dry ground under a downpour.
 * <p>
 * The point of making them <em>move</em> is that a static safe spot is solved once and then ignored:
 * the group walks over, stands on it, and the mechanic stops existing. Drifting discs keep the demand
 * alive for the whole phase without ever pausing the fight, which is exactly the shape the rework is
 * after — you keep fighting, and you keep having to relocate while you do it.
 * <p>
 * Discs bounce off the arena wall rather than wrapping, so they never drift somewhere unreachable,
 * and they are drawn as rings rather than filled areas to stay well inside the particle budget.
 */
public final class DriftingDiscField implements MechanicField {

    private static final int RING_POINTS = 22;

    private final int discCount;
    private final double radius;
    private final double blocksPerSecond;
    private final Color color;
    private final double placementFraction;

    private final List<Disc> discs = new ArrayList<>();

    /**
     * @param discCount        how many safe circles exist at once; 1 makes the whole group share one
     * @param radius           radius of each circle in blocks
     * @param blocksPerSecond  drift speed — fast enough to matter, slow enough to follow on foot
     * @param placementFraction how far out from the arena centre they spawn, as a fraction of radius
     */
    public DriftingDiscField(int discCount, double radius, double blocksPerSecond, Color color,
                              double placementFraction) {
        this.discCount = Math.max(1, discCount);
        this.radius = Math.max(1.0, radius);
        this.blocksPerSecond = Math.max(0.0, blocksPerSecond);
        this.color = color;
        this.placementFraction = Math.max(0.05, Math.min(0.9, placementFraction));
    }

    private static final class Disc {
        double x;
        double z;
        double dirX;
        double dirZ;
    }

    @Override
    public void start(BossInstance instance) {
        discs.clear();
        Location center = instance.arena().center();
        double spread = instance.arena().radius() * placementFraction;
        for (int i = 0; i < discCount; i++) {
            double angle = 2 * Math.PI * i / discCount;
            Disc disc = new Disc();
            disc.x = center.getX() + Math.cos(angle) * spread;
            disc.z = center.getZ() + Math.sin(angle) * spread;
            double heading = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            disc.dirX = Math.cos(heading);
            disc.dirZ = Math.sin(heading);
            discs.add(disc);
        }
    }

    @Override
    public void tick(BossInstance instance, int elapsedTicks) {
        Location center = instance.arena().center();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        // Discs must stay well inside the wall or their far edge becomes unreachable.
        double leash = Math.max(2.0, instance.arena().radius() - radius - 2.0);
        double step = blocksPerSecond / 20.0;

        for (Disc disc : discs) {
            disc.x += disc.dirX * step;
            disc.z += disc.dirZ * step;

            double dx = disc.x - center.getX();
            double dz = disc.z - center.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > leash && dist > 0.0001) {
                // Reflect the heading about the inward normal, then pull the disc back inside so it
                // cannot creep further out over successive bounces.
                double nx = dx / dist;
                double nz = dz / dist;
                double dot = disc.dirX * nx + disc.dirZ * nz;
                disc.dirX -= 2 * dot * nx;
                disc.dirZ -= 2 * dot * nz;
                disc.x = center.getX() + nx * leash;
                disc.z = center.getZ() + nz * leash;
            }

            Location at = discLocation(world, disc);
            Fx.coloredRing(at, color, 1.3f, radius, RING_POINTS, elapsedTicks * 0.05);
            if (elapsedTicks % 10 == 0) {
                Fx.coloredRing(at.clone().add(0, 0.6, 0), color, 1.0f, radius * 0.55, 12, -elapsedTicks * 0.08);
            }
        }
    }

    @Override
    public boolean contains(BossInstance instance, Player player) {
        Location at = player.getLocation();
        World world = instance.arena().center().getWorld();
        if (world == null || at.getWorld() == null || !at.getWorld().equals(world)) {
            return false;
        }
        double radiusSq = radius * radius;
        for (Disc disc : discs) {
            double dx = at.getX() - disc.x;
            double dz = at.getZ() - disc.z;
            if (dx * dx + dz * dz <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Location nearestAnchor(BossInstance instance, Player player) {
        World world = instance.arena().center().getWorld();
        if (world == null || discs.isEmpty()) {
            return null;
        }
        Disc best = null;
        double bestSq = Double.MAX_VALUE;
        Location at = player.getLocation();
        for (Disc disc : discs) {
            double dx = at.getX() - disc.x;
            double dz = at.getZ() - disc.z;
            double sq = dx * dx + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = disc;
            }
        }
        return best == null ? null : discLocation(world, best);
    }

    @Override
    public void stop(BossInstance instance) {
        discs.clear();
    }

    /** Rides the terrain, so a disc crossing a rise stays drawn on the ground rather than inside it. */
    private Location discLocation(World world, Disc disc) {
        int blockX = (int) Math.floor(disc.x);
        int blockZ = (int) Math.floor(disc.z);
        double y = world.getHighestBlockYAt(blockX, blockZ) + 1.1;
        return new Location(world, disc.x, y, disc.z);
    }
}
