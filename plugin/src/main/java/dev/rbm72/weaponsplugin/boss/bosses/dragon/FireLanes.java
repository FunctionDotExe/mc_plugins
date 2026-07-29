package dev.rbm72.weaponsplugin.boss.bosses.dragon;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/**
 * Real, persisting ground fire — batch-2 §4's Strafing Run ("a lane of real fire that persists") and
 * Fire Breath ("sustained heavy damage + burning ground"). Pure block work, no damage logic of its own:
 * {@link StrafingRuns} and {@code DiveBombPhase}'s breath cycle each compute their own hit geometry and
 * call in here only for the terrain half of the mechanic.
 * <p>
 * Written with {@link Grief#setMechanicBlock} rather than {@link Grief#setBlock}. A scorched lane is the
 * fight's own physical memory of a run the group did or didn't dodge, and it is the literal counterplay
 * surface P4 asks players to route around ("using burnt lanes as no-go zones") — treating it as
 * grief-gated collateral would mean a server with grief off loses the tell along with the block. Every
 * lane is still ledgered exactly like a destructive write, so it comes back out at fight end regardless
 * of whether its own extinguish timer got to run first.
 */
final class FireLanes {

    private final DragonFight fight;

    FireLanes(DragonFight fight) {
        this.fight = fight;
    }

    /** A line of fire from {@code from} to {@code to}, sampled every {@code stepSize} blocks. */
    void igniteLine(Location from, Location to, double stepSize, int durationTicks) {
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length < 1.0E-4) {
            igniteColumn(from, durationTicks);
            return;
        }
        direction = direction.normalize();
        int steps = Math.max(1, (int) (length / Math.max(0.5, stepSize)));
        for (int i = 0; i <= steps; i++) {
            igniteColumn(from.clone().add(direction.clone().multiply(i * stepSize)), durationTicks);
        }
    }

    /**
     * A fan of fire sampled across a cone — {@code direction} need only be roughly horizontal, which is
     * all a breath attack's aim vector ever is.
     */
    void igniteCone(Location apex, Vector direction, double range, double halfAngleDegrees, int durationTicks) {
        Vector flatDir = direction.clone().setY(0);
        if (flatDir.lengthSquared() < 1.0E-4) {
            return;
        }
        flatDir.normalize();
        double stepSize = 1.6;
        for (double dist = stepSize; dist <= range; dist += stepSize) {
            double arcWidth = 2 * halfAngleDegrees;
            int samplesAtRing = Math.max(1, (int) (dist * Math.toRadians(arcWidth) / stepSize));
            for (int s = 0; s <= samplesAtRing; s++) {
                double angleDeg = -halfAngleDegrees + (arcWidth * s) / Math.max(1, samplesAtRing);
                Vector rotated = rotateY(flatDir, Math.toRadians(angleDeg));
                igniteColumn(apex.clone().add(rotated.multiply(dist)), durationTicks);
            }
        }
    }

    private void igniteColumn(Location point, int durationTicks) {
        World world = point.getWorld();
        if (world == null) {
            return;
        }
        int y = world.getHighestBlockYAt(point.getBlockX(), point.getBlockZ());
        Block ground = world.getBlockAt(point.getBlockX(), y, point.getBlockZ());
        Block above = ground.getRelative(0, 1, 0);
        if (!above.getType().isAir()) {
            return;
        }
        if (!Grief.setMechanicBlock(fight.griefContext(), above, Material.FIRE)) {
            return;
        }
        fight.instance().trackTask(fight.plugin().getServer().getScheduler().runTaskLater(fight.plugin(), () -> {
            if (above.getType() == Material.FIRE) {
                Grief.setMechanicBlock(fight.griefContext(), above, Material.AIR);
            }
        }, Math.max(1, durationTicks)));
    }

    private static Vector rotateY(Vector v, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(v.getX() * cos - v.getZ() * sin, v.getY(), v.getX() * sin + v.getZ() * cos);
    }
}
